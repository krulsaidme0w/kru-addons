package dev.krul.builder.minecraft;

import com.google.gson.*;
import dev.krul.builder.agent.BuilderDynamicTools;
import dev.krul.builder.agent.AgentRequest;
import dev.krul.builder.agent.PlanResponseCodec;
import dev.krul.builder.agent.PlanReviewTracker;
import dev.krul.builder.blueprint.PlanPreviewModel;
import dev.krul.builder.catalog.BlockCatalog;
import net.minecraft.client.MinecraftClient;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/** Bridges the fixed Codex tool API to client-thread registry reads and one local model render. */
public final class ClientBlockCatalogTools implements BuilderDynamicTools.TurnAwareHandler {
    private final MinecraftClient client;
    private final ClientBlockCatalog catalog;
    private final BlockPreviewRenderer previews;
    private final BooleanSupplier current;
    private final PlanPreviewRenderer planPreviews;
    private volatile AgentRequest activeRequest;
    private final PlanReviewTracker reviewTracker = new PlanReviewTracker();
    public ClientBlockCatalogTools(MinecraftClient client, BooleanSupplier current) {
        this.client = Objects.requireNonNull(client); this.current = Objects.requireNonNull(current);
        catalog = new ClientBlockCatalog(client); previews = new BlockPreviewRenderer(client); planPreviews = new PlanPreviewRenderer(client);
    }
    @Override public synchronized void beginTurn(AgentRequest request) {
        activeRequest = Objects.requireNonNull(request); reviewTracker.reset();
    }
    @Override public synchronized BuilderDynamicTools.Review review(dev.krul.builder.project.BuildPlan finalPlan) {
        return reviewTracker.review(finalPlan);
    }
    @Override public synchronized void endTurn() { activeRequest = null; }
    @Override public JsonObject call(String name, JsonObject args, BooleanSupplier callCurrent) throws Exception {
        return onClient(() -> switch (name) {
            case "list_block_mods" -> mods(catalog.snapshot().listMods(args.get("page").getAsInt()));
            case "search_blocks" -> search(catalog.snapshot().search(args.get("query").getAsString(),
                Optional.ofNullable(args.has("mod") ? args.get("mod").getAsString() : null), args.get("page").getAsInt()));
            case "describe_block" -> describe(args.get("id").getAsString());
            case "inspect_build_plan" -> inspectPlan(args, callCurrent);
            default -> throw new IllegalArgumentException("Unsupported catalog tool");
        }, callCurrent);
    }
    private JsonObject inspectPlan(JsonObject args, BooleanSupplier callCurrent) {
        AgentRequest request = activeRequest;
        if (!reviewTracker.beginCall()) return failure(reviewTracker.review(null).failure());
        if (request == null) return failure("No active Builder request is available for plan inspection");
        try {
            JsonObject response = new JsonObject(); response.addProperty("message", "Local visual inspection"); response.add("plan", args.get("plan").deepCopy());
            var decoded = new PlanResponseCodec().decode(response.toString(), request, new MinecraftPlanRules());
            var plan = decoded.plan().orElseThrow(() -> new IOException("Inspection requires a non-empty plan"));
            if (!reviewTracker.canInspect(plan)) return failure(reviewTracker.review(null).failure());
            var projection = PlanPreviewModel.project(plan, request.context().bounds());
            byte[] png = planPreviews.renderNow(projection, () -> valid(callCurrent));
            if (!valid(callCurrent)) throw new IOException("Plan preview request was cancelled");
            String uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
            JsonArray items = new JsonArray();
            items.add(textItem("Plan validated against the current request bounds and local block registry. "
                + projection.cells().size() + " unique cells across " + plan.stages().size()
                + " stages. Image is an isometric schematic using local inventory-model sprites, not an in-world render."));
            JsonObject image = new JsonObject(); image.addProperty("type", "inputImage"); image.addProperty("imageUrl", uri); items.add(image);
            reviewTracker.complete(plan);
            return response(true, items);
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            message = message.replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1[redacted]");
            String reason = message.length() > 512 ? message.substring(0, 511) + "…" : message;
            reviewTracker.fail(reason);
            return failure(reason);
        }
    }
    private <T> T onClient(Callable<T> call, BooleanSupplier callCurrent) throws Exception {
        var result = new CompletableFuture<T>();
        var pending = new AtomicBoolean(true);
        client.execute(() -> {
            if (!pending.get() || !valid(callCurrent)) { result.completeExceptionally(new CancellationException("Builder catalog expired")); return; }
            try { T value = call.call(); if (!pending.get() || !valid(callCurrent)) result.completeExceptionally(new CancellationException("Builder catalog expired")); else result.complete(value); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        try { return result.get(20, TimeUnit.SECONDS); }
        catch (TimeoutException | InterruptedException failure) { pending.set(false); result.cancel(false); throw failure; }
        catch (ExecutionException failure) { Throwable cause = failure.getCause(); if (cause instanceof Exception e) throw e; throw new IllegalStateException(cause); }
    }
    private boolean valid(BooleanSupplier callCurrent) { return current.getAsBoolean() && callCurrent.getAsBoolean(); }
    private static JsonObject mods(BlockCatalog.ModPage page) {
        JsonArray list = new JsonArray(); for (var mod : page.mods()) {
            JsonObject item = new JsonObject(); item.addProperty("namespace", mod.namespace()); item.addProperty("name", mod.label()); item.addProperty("blocks", mod.blockCount()); list.add(item);
        }
        JsonObject data = new JsonObject(); data.add("mods", list); data.addProperty("total", page.total()); if (page.nextPage() != null) data.addProperty("nextPage", page.nextPage());
        return success("Local block mod namespaces (page size 20):\n" + data);
    }
    private static JsonObject search(BlockCatalog.BlockPage page) {
        JsonArray list = new JsonArray(); for (var block : page.blocks()) {
            JsonObject item = new JsonObject(); item.addProperty("id", block.id()); item.addProperty("name", block.label()); list.add(item);
        }
        JsonObject data = new JsonObject(); data.add("blocks", list); data.addProperty("total", page.total()); if (page.nextPage() != null) data.addProperty("nextPage", page.nextPage());
        return success("Local block search results (maximum 20):\n" + data);
    }
    private JsonObject describe(String exactId) throws Exception {
        BlockCatalog.BlockDetails details = catalog.describe(exactId).orElseThrow(() -> new IllegalArgumentException("Unknown exact block ID"));
        var preview = previews.renderNow(exactId);
        String properties = details.properties().entrySet().stream().map(e -> e.getKey() + "=[" + String.join(",", e.getValue()) + "]").collect(Collectors.joining("; "));
        String uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(preview.bytes());
        JsonObject image = new JsonObject(); image.addProperty("type", "inputImage"); image.addProperty("imageUrl", uri);
        JsonArray items = new JsonArray(); JsonObject text = textItem("Exact ID: " + details.id() + "\nName: " + details.label() + "\nValid properties: " + properties + "\nImage: inventory-model preview, " + preview.width() + "x" + preview.height() + ".");
        items.add(text); items.add(image); return response(true, items);
    }
    private static JsonObject success(String text) { JsonArray items = new JsonArray(); items.add(textItem(text)); return response(true, items); }
    private static JsonObject failure(String text) { JsonArray items = new JsonArray(); items.add(textItem(text)); return response(false, items); }
    private static JsonObject textItem(String value) { JsonObject item = new JsonObject(); item.addProperty("type", "inputText"); item.addProperty("text", value); return item; }
    private static JsonObject response(boolean ok, JsonArray content) { JsonObject result = new JsonObject(); result.addProperty("success", ok); result.add("contentItems", content); return result; }
}
