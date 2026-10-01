package dev.krul.builder.minecraft;

import com.google.gson.JsonObject;
import dev.krul.builder.bridge.*;
import dev.krul.builder.catalog.BlockCatalog;
import dev.krul.builder.catalog.BlockCatalogFingerprint;
import dev.krul.builder.catalog.BlockCatalogSnapshotCodec;
import dev.krul.builder.catalog.BlockCatalogStore;
import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.WorldSnapshot;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;

/** Serves cached scan evidence and explicit framebuffer captures without doing Minecraft work on HTTP threads. */
public final class BuilderBridgeDataSource implements BridgeDataSource {
    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private static final int MAX_PNG_BYTES = 8 * 1024 * 1024;
    private final MinecraftClient client;
    private final Supplier<Optional<WorldSnapshot>> snapshots;
    private final Supplier<Optional<Bounds>> selection;
    private final LongSupplier worldGeneration;
    private final Supplier<String> worldIdentity;
    private final WorldCapture capture;
    private final Executor io;
    private final ClientBlockCatalog blockCatalog;
    private final BlockPreviewRenderer blockPreviews;
    private final BlockCatalogStore catalogStore;
    private volatile String persistedCatalogFingerprint;
    private final java.util.concurrent.atomic.AtomicLong catalogSaveGeneration = new java.util.concurrent.atomic.AtomicLong();

    public BuilderBridgeDataSource(MinecraftClient client, Supplier<Optional<WorldSnapshot>> snapshots,
                                   Supplier<Optional<Bounds>> selection, LongSupplier worldGeneration,
                                   Supplier<String> worldIdentity, WorldCapture capture, Executor io) {
        this.client = Objects.requireNonNull(client); this.snapshots = Objects.requireNonNull(snapshots);
        this.selection = Objects.requireNonNull(selection); this.worldGeneration = Objects.requireNonNull(worldGeneration);
        this.worldIdentity = Objects.requireNonNull(worldIdentity); this.capture = Objects.requireNonNull(capture);
        this.io = Objects.requireNonNull(io);
        this.blockCatalog = new ClientBlockCatalog(client);
        this.blockPreviews = new BlockPreviewRenderer(client);
        this.catalogStore = null;
    }

    public BuilderBridgeDataSource(MinecraftClient client, Supplier<Optional<WorldSnapshot>> snapshots,
                                   Supplier<Optional<Bounds>> selection, LongSupplier worldGeneration,
                                   Supplier<String> worldIdentity, WorldCapture capture, Executor io, Path builderDirectory) {
        this.client = Objects.requireNonNull(client); this.snapshots = Objects.requireNonNull(snapshots);
        this.selection = Objects.requireNonNull(selection); this.worldGeneration = Objects.requireNonNull(worldGeneration);
        this.worldIdentity = Objects.requireNonNull(worldIdentity); this.capture = Objects.requireNonNull(capture);
        this.io = Objects.requireNonNull(io); this.blockCatalog = new ClientBlockCatalog(client);
        this.blockPreviews = new BlockPreviewRenderer(client); this.catalogStore = new BlockCatalogStore(builderDirectory);
    }

    @Override public CompletionStage<BridgeProtocol.Response> status(BridgeProtocol.Request request) {
        return onClient(request, generation -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("connected", client.world != null && client.player != null);
            payload.addProperty("world", client.world == null ? "" : worldIdentity.get());
            Bounds bounds = selection.get().orElse(null);
            if (bounds != null) {
                JsonObject selected = new JsonObject(); selected.add("min", coordinate(bounds.min().x(), bounds.min().y(), bounds.min().z()));
                selected.add("max", coordinate(bounds.max().x(), bounds.max().y(), bounds.max().z())); payload.add("selection", selected);
            } else payload.add("selection", com.google.gson.JsonNull.INSTANCE);
            WorldSnapshot snapshot = snapshots.get().orElse(null);
            long revision = snapshot == null ? 0 : snapshot.revision();
            payload.addProperty("snapshotReady", snapshot != null);
            payload.addProperty("known", snapshot == null ? 0 : snapshot.known().size());
            payload.addProperty("unknown", snapshot == null ? 0 : snapshot.unknown().size());
            return ok(request, generation, revision, payload.toString(), null);
        });
    }

    @Override public CompletionStage<BridgeProtocol.Response> readSelection(BridgeProtocol.Request request) {
        return onClient(request, generation -> {
            WorldSnapshot snapshot = snapshots.get().orElseThrow(() -> new IllegalStateException("No complete scan is available"));
            checkExpected(request, generation, snapshot.revision());
            Bounds selected = selection.get().orElseThrow(() -> new IllegalStateException("No selection is active"));
            Bounds bounds = request.bounds() == null ? selected : request.bounds();
            BridgeRequestValidator.validateSelection(bounds, selected, 32_768);
            BridgeRequestValidator.validateSelection(bounds, snapshot.bounds(), 32_768);
            if (request.pageSize() < 1 || request.pageSize() > BridgeSnapshotPage.MAX_PAGE_SIZE)
                throw new IllegalArgumentException("Page size is out of range");
            return ok(request, generation, snapshot.revision(),
                BridgeSnapshotPage.encode(snapshot, bounds, request.page(), request.pageSize()), null);
        });
    }

    @Override public CompletionStage<BridgeProtocol.Response> captureView(BridgeProtocol.Request request) {
        CompletableFuture<BridgeProtocol.Response> result = new CompletableFuture<>();
        client.execute(() -> {
            long generation = worldGeneration.getAsLong();
            try {
                if (client.world == null) throw new IllegalStateException("No world is available to capture");
                WorldSnapshot snapshot = snapshots.get().orElse(null);
                long revision = snapshot == null ? 0 : snapshot.revision();
                checkExpected(request, generation, revision);
                var pending = capture.capture();
                pending.whenComplete((path, failure) -> {
                    if (failure != null) { result.complete(failed(request, generation, revision, failure)); return; }
                    CompletableFuture.supplyAsync(() -> encodeCapture(request, generation, revision, path), io)
                        .whenComplete((response, error) -> {
                            if (error != null) result.complete(failed(request, generation, revision, error));
                            else result.complete(response);
                        });
                });
                result.whenComplete((ignored, error) -> { if (result.isCancelled()) pending.cancel(true); });
            } catch (Throwable failure) { result.complete(failed(request, generation, 0, failure)); }
        });
        return result;
    }

    @Override public CompletionStage<BridgeProtocol.Response> listBlockMods(BridgeProtocol.Request request) {
        return onClient(request, generation -> {
            BlockCatalog.ModPage page = activeCatalog().listMods(request.page());
            JsonObject payload = new JsonObject(); var items = new com.google.gson.JsonArray();
            for (var mod : page.mods()) {
                JsonObject item = new JsonObject(); item.addProperty("namespace", mod.namespace()); item.addProperty("name", mod.label());
                item.addProperty("blocks", mod.blockCount()); items.add(item);
            }
            payload.add("mods", items); payload.addProperty("total", page.total());
            if (page.nextPage() != null) payload.addProperty("nextPage", page.nextPage());
            return ok(request, generation, scansRevision(), payload.toString(), null);
        });
    }

    @Override public CompletionStage<BridgeProtocol.Response> searchBlocks(BridgeProtocol.Request request) {
        return onClient(request, generation -> {
            Optional<String> namespace = Optional.ofNullable(request.namespace());
            BlockCatalog.BlockPage page = activeCatalog().search(Objects.requireNonNullElse(request.query(), ""), namespace, request.page());
            JsonObject payload = new JsonObject(); var items = new com.google.gson.JsonArray();
            for (var block : page.blocks()) {
                JsonObject item = new JsonObject(); item.addProperty("id", block.id()); item.addProperty("namespace", block.namespace());
                item.addProperty("name", block.label());
                JsonObject properties = new JsonObject(); block.properties().forEach((name, values) -> {
                    var array = new com.google.gson.JsonArray(); values.forEach(array::add); properties.add(name, array);
                });
                item.add("properties", properties); items.add(item);
                JsonObject defaults = new JsonObject(); block.defaultState().forEach(defaults::addProperty);
                item.add("defaultState", defaults); item.addProperty("hasBlockEntity", block.hasBlockEntity());
            }
            payload.add("blocks", items); payload.addProperty("total", page.total());
            if (page.nextPage() != null) payload.addProperty("nextPage", page.nextPage());
            return ok(request, generation, scansRevision(), payload.toString(), null);
        });
    }

    @Override public CompletionStage<BridgeProtocol.Response> describeBlock(BridgeProtocol.Request request) {
        return onClient(request, generation -> {
            if (request.blockId() == null || request.blockId().length() > 256) throw new IllegalArgumentException("Exact block ID is required");
            BlockCatalog.BlockDetails details = activeCatalog().describe(request.blockId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown exact block ID"));
            BlockPreviewRenderer.PreviewPng preview;
            try { preview = blockPreviews.renderNow(details.id()); }
            catch (IOException unavailable) { throw new IllegalStateException("Block preview is unavailable", unavailable); }
            JsonObject payload = new JsonObject(); payload.addProperty("id", details.id()); payload.addProperty("name", details.label());
            JsonObject properties = new JsonObject(); details.properties().forEach((name, values) -> {
                var array = new com.google.gson.JsonArray(); values.forEach(array::add); properties.add(name, array);
            });
            payload.add("properties", properties); payload.addProperty("previewState", "inventory-default");
            JsonObject defaults = new JsonObject(); details.defaultState().forEach(defaults::addProperty);
            payload.add("defaultState", defaults); payload.addProperty("hasBlockEntity", details.hasBlockEntity());
            return ok(request, generation, scansRevision(), payload.toString(), Base64.getEncoder().encodeToString(preview.bytes()));
        });
    }

    private long scansRevision() { return snapshots.get().map(WorldSnapshot::revision).orElse(0L); }

    private BlockCatalog activeCatalog() {
        BlockCatalog catalog = blockCatalog.snapshot();
        if (catalogStore == null) return catalog;
        BlockCatalogFingerprint fingerprint = blockCatalog.fingerprint();
        if (fingerprint.sha256().equals(persistedCatalogFingerprint)) return catalog;
        persistedCatalogFingerprint = fingerprint.sha256();
        long saveGeneration = catalogSaveGeneration.incrementAndGet();
        var snapshot = new BlockCatalogSnapshotCodec.Snapshot(fingerprint, catalog.entries());
        long world = worldGeneration.getAsLong();
        try {
            CompletableFuture.runAsync(() -> {
                try { catalogStore.save(snapshot, () -> saveGeneration == catalogSaveGeneration.get() && world == worldGeneration.getAsLong()); }
                catch (IOException failure) { persistedCatalogFingerprint = null; }
            }, io);
        } catch (RejectedExecutionException ignored) { persistedCatalogFingerprint = null; }
        return catalog;
    }

    private BridgeProtocol.Response encodeCapture(BridgeProtocol.Request request, long generation, long revision, Path path) {
        try {
            if (worldGeneration.getAsLong() != generation) throw new CancellationException("World changed during capture");
            long currentRevision = snapshots.get().map(WorldSnapshot::revision).orElse(0L);
            if (currentRevision != revision) throw new CancellationException("Snapshot changed during capture; capture again");
            byte[] png = Files.readAllBytes(path);
            BridgeRequestValidator.validatePng(png, MAX_PNG_BYTES);
            return ok(request, generation, revision, null, Base64.getEncoder().encodeToString(png));
        } catch (Throwable failure) { return failed(request, generation, revision, failure); }
        finally { capture.release(path); }
    }

    private <T> CompletableFuture<BridgeProtocol.Response> onClient(BridgeProtocol.Request request,
                                                                    Function<Long, BridgeProtocol.Response> action) {
        CompletableFuture<BridgeProtocol.Response> result = new CompletableFuture<>();
        client.execute(() -> {
            long generation = worldGeneration.getAsLong();
            try {
                if (client.world == null || client.player == null) throw new IllegalStateException("No connected Minecraft world");
                result.complete(action.apply(generation));
            } catch (Throwable failure) { result.complete(failed(request, generation, 0, failure)); }
        });
        return result;
    }

    private void checkExpected(BridgeProtocol.Request request, long generation, long revision) {
        if (request.expectedWorldGeneration() > 0 && request.expectedWorldGeneration() != generation)
            throw new IllegalStateException("World generation changed; read status again");
        if (request.expectedSnapshotRevision() > 0 && request.expectedSnapshotRevision() != revision)
            throw new IllegalStateException("Snapshot is stale; read status and selection again");
    }

    private static BridgeProtocol.Response ok(BridgeProtocol.Request request, long generation, long revision, String payload, String image) {
        return new BridgeProtocol.Response(BridgeProtocol.VERSION, request.id(), true, generation, revision, "ok", payload, image);
    }
    private static BridgeProtocol.Response failed(BridgeProtocol.Request request, long generation, long revision, Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        String message = cause.getMessage() == null ? "Bridge operation failed" : cause.getMessage();
        return new BridgeProtocol.Response(BridgeProtocol.VERSION, request.id(), false, generation, revision,
            message.length() > 512 ? message.substring(0, 512) : message, null, null);
    }
    private static JsonObject coordinate(int x, int y, int z) {
        JsonObject cell = new JsonObject(); cell.addProperty("x", x); cell.addProperty("y", y); cell.addProperty("z", z); return cell;
    }
}
