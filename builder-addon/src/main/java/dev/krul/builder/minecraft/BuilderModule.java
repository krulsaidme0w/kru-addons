package dev.krul.builder.minecraft;

import dev.krul.builder.BuilderCategory;
import dev.krul.builder.blueprint.BlueprintDiff;
import dev.krul.builder.blueprint.BlueprintRenderer;
import dev.krul.builder.bridge.BuilderBridgeServer;
import dev.krul.builder.context.BoundsContext;
import dev.krul.builder.context.BoundsAutoApply;
import dev.krul.builder.context.BoundsInput;
import dev.krul.builder.context.BoundsStore;
import dev.krul.builder.context.ScanSession;
import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.WorldSnapshot;
import dev.krul.builder.project.BuildPlan;
import dev.krul.builder.ui.BuilderOverlayVisibility;
import dev.krul.builder.ui.BuilderScreen;
import dev.krul.builder.ui.BuilderUiLaunchGate;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.render.RenderAfterWorldEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only bounded world scanner, local MCP bridge, and plan-preview renderer. */
public final class BuilderModule extends Module {
    private static final Color OUTLINE = new Color(80, 190, 255, 220);
    private static final Color FILL = new Color(80, 190, 255, 20);
    private final ScanSession scans = new ScanSession();
    private final BoundsAutoApply boundsAutoApply = new BoundsAutoApply();
    private final Setting<Boolean> renderOverlay = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("render-overlay").description("Render selection bounds and the MCP plan preview.")
        .defaultValue(true).build());
    private final BoundsStore boundsStore;
    private Object boundWorld, boundConnection, boundPlayer;
    private BoundsContext activeBoundsContext;
    private ExecutorService io;
    private WorldCapture worldCapture;
    private BuilderBridgeServer bridgeServer;
    private UUID planSessionId;
    private long bridgeWorldGeneration;
    private BuildPlan mcpPreviewPlan, renderedPlan;
    private BlueprintRenderer blueprintRenderer;
    private String bridgeFailure;
    private WTextBox[] boundsFields;
    private BoundsContext boundsDraftContext;
    private WLabel boundsFeedback, boundsDimensions;
    private String reportedStatus;
    private boolean openWindowPending;

    public BuilderModule() {
        super(BuilderCategory.BUILDER, "builder", "Select and scan a region; connect an external MCP client to render plans.");
        boundsStore = new BoundsStore(mc.runDirectory.toPath());
        runInMainMenu = true;
        addAction("open-builder", "Open the Builder selection window.", this::openBuilder);
    }

    private void addAction(String name, String description, Runnable action) {
        settings.getDefaultGroup().add(new KeybindSetting.Builder().name(name).description(description)
            .action(() -> { if (isActive() && mc.isOnThread() && mc.currentScreen == null) action.run(); }).build());
    }

    @Override public void onActivate() {
        ++bridgeWorldGeneration;
        planSessionId = UUID.randomUUID();
        scans.reset(); boundWorld = boundConnection = boundPlayer = null; activeBoundsContext = null;
        syncContext();
        io = Executors.newFixedThreadPool(2, task -> { Thread thread = new Thread(task, "builder-mcp-io"); thread.setDaemon(true); return thread; });
        Path root = mc.runDirectory.toPath().resolve("kru-builder");
        worldCapture = new WorldCapture(mc, root.resolve("mcp-captures"), io);
        try {
            bridgeServer = new BuilderBridgeServer();
            bridgeServer.start(new BuilderBridgeDataSource(mc, scans::snapshot, scans::bounds,
                () -> bridgeWorldGeneration, this::bridgeWorldIdentity, worldCapture, io, root,
                () -> planSessionId, this::publishMcpPreview));
            bridgeFailure = null;
            info("Builder local MCP bridge listening on 127.0.0.1:47653");
        } catch (IOException failure) {
            bridgeServer = null;
            bridgeFailure = failure.getMessage();
            warning("Builder MCP bridge could not start: %s", failure.getMessage());
        }
        report();
        openWindowPending = true;
    }

    @Override public void onDeactivate() {
        ++bridgeWorldGeneration;
        if (bridgeServer != null) bridgeServer.close();
        bridgeServer = null; worldCapture = null; planSessionId = null; bridgeFailure = null;
        openWindowPending = false;
        if (io != null) io.shutdownNow();
        io = null; mcpPreviewPlan = renderedPlan = null; blueprintRenderer = null;
        if (mc.currentScreen instanceof BuilderScreen) mc.setScreen(null);
        scans.reset(); boundWorld = boundConnection = boundPlayer = null; activeBoundsContext = null;
        boundsFields = null; boundsFeedback = boundsDimensions = null; boundsDraftContext = null; boundsAutoApply.reset();
        reportedStatus = null;
    }

    private void syncContext() {
        if (!mc.isOnThread()) throw new IllegalStateException("Builder context requires the client thread");
        Object world = mc.world, connection = mc.getNetworkHandler(), player = mc.player;
        BoundsContext nextContext = currentBoundsContext();
        boolean identityChanged = boundWorld != world || boundConnection != connection || boundPlayer != player;
        boolean contextChanged = !Objects.equals(activeBoundsContext, nextContext);
        if (identityChanged || contextChanged) {
            bridgeWorldGeneration++;
            Optional<Bounds> retained = scans.bounds();
            boolean changed = boundWorld != null || boundConnection != null || boundPlayer != null || activeBoundsContext != null;
            boundWorld = world; boundConnection = connection; boundPlayer = player;
            scans.reset(); scans.bind(world, connection, player); activeBoundsContext = nextContext;
            Optional<Bounds> restore = contextChanged ? (nextContext == null ? Optional.empty() : boundsStore.load(nextContext)) : retained;
            restore.ifPresent(this::setBounds);
            if (changed) invalidatePreview();
        } else scans.bind(world, connection, player);
    }

    private BoundsContext currentBoundsContext() {
        if (mc.world == null) return null;
        String dimension = mc.world.getRegistryKey().getValue().toString();
        if (mc.isInSingleplayer()) {
            if (mc.getServer() == null) return null;
            return BoundsContext.local(mc.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toString(), dimension);
        }
        var server = mc.getCurrentServerEntry();
        if (server == null || server.address == null || server.address.isBlank()) return null;
        return BoundsContext.remote(server.address, dimension);
    }

    private void setBounds(Bounds bounds) { scans.setFirst(bounds.min()); scans.setSecond(bounds.max()); }
    private void invalidatePreview() { mcpPreviewPlan = renderedPlan = null; blueprintRenderer = null; }

    @EventHandler private void onJoined(GameJoinedEvent event) { syncContext(); }
    @EventHandler private void onLeft(GameLeftEvent event) {
        bridgeWorldGeneration++; scans.reset(); boundWorld = boundConnection = boundPlayer = null; activeBoundsContext = null;
        invalidatePreview(); report();
    }
    @EventHandler private void onTick(TickEvent.Post event) {
        syncContext();
        if (mc.world != null && mc.player != null) scans.ensureScan(new ClientWorldReader(mc, mc.world));
        scans.tick(); report();
        if (BuilderUiLaunchGate.shouldOpen(openWindowPending, GuiThemes.get() != null, mc.currentScreen != null)) {
            openWindowPending = false;
            openBuilder();
        }
    }
    @EventHandler private void onAfterWorld(RenderAfterWorldEvent event) {
        if (isActive() && worldCapture != null) worldCapture.onAfterWorld(event);
    }

    private void openBuilder() {
        if (!isActive()) return;
        GuiTheme theme = GuiThemes.get();
        if (theme == null) { openWindowPending = true; return; }
        mc.setScreen(new BuilderScreen(theme, () -> boundsWidget(theme), this::refreshBoundsDraft,
            scans::status, this::bridgeStatus));
    }

    private String bridgeStatus() {
        return bridgeServer == null ? "MCP bridge unavailable" + (bridgeFailure == null ? "" : " · " + bridgeFailure)
            : "MCP · 127.0.0.1:47653 · local only · preview only";
    }

    private String bridgeWorldIdentity() {
        if (mc.world == null) return "";
        String server = mc.isInSingleplayer() ? "singleplayer" : Optional.ofNullable(mc.getCurrentServerEntry())
            .map(entry -> entry.address).orElse("unknown-server");
        return server + "|" + mc.world.getRegistryKey().getValue();
    }

    @EventHandler private void onRender(Render3DEvent event) {
        syncContext();
        boolean draw = BuilderOverlayVisibility.shouldRender(renderOverlay.get(), WorldCapture.suppressPreviewThisFrame());
        if (!draw) return;
        scans.bounds().ifPresent(bounds -> event.renderer.box(bounds.min().x(), bounds.min().y(), bounds.min().z(),
            (double) bounds.max().x() + 1, (double) bounds.max().y() + 1, (double) bounds.max().z() + 1,
            FILL, OUTLINE, ShapeMode.Both, 0));
        BuildPlan plan = mcpPreviewPlan;
        if (plan == null) return;
        if (!Objects.equals(renderedPlan, plan)) {
            renderedPlan = plan;
            try {
                blueprintRenderer = new BlueprintRenderer(plan, scans.bounds().orElseThrow(),
                    BlueprintDiff.Selection.completePlan());
            } catch (RuntimeException incompatible) { blueprintRenderer = null; }
        }
        if (blueprintRenderer != null) blueprintRenderer.render(event, scans.snapshot().orElse(null), false);
    }

    private void publishMcpPreview(BuildPlan plan) {
        if (!mc.isOnThread()) throw new IllegalStateException("MCP preview must be published on Minecraft client thread");
        mcpPreviewPlan = Objects.requireNonNull(plan); renderedPlan = null; blueprintRenderer = null;
        info("Builder MCP plan preview published; world unchanged.");
    }

    private void report() {
        String status = scans.status();
        if (mc.player != null && !status.equals(reportedStatus)) { info("%s", status); reportedStatus = status; }
    }
    @Override public String getInfoString() { syncContext(); return scans.status(); }
    @Override public WWidget getWidget(GuiTheme theme) { var list = theme.verticalList(); list.add(theme.button("Open Builder")).expandX().widget().action = this::openBuilder; return list; }

    public WWidget boundsWidget(GuiTheme theme) {
        syncContext(); boundsDraftContext = activeBoundsContext; boundsAutoApply.reset();
        Optional<Bounds> initial = scans.bounds(); String[] values = coordinateValues(initial.orElse(null));
        WTextBox[] fields = new WTextBox[6]; boundsFields = fields;
        String[] labels = {"X1", "Y1", "Z1", "X2", "Y2", "Z2"};
        var editor = theme.verticalList(); editor.add(theme.label("Selection bounds · max 32,768 cells")).expandX();
        boundsDimensions = editor.add(theme.label(boundsSummary(initial.orElse(null)))).expandX().widget();
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            WHorizontalList row = theme.horizontalList();
            for (int column = 0; column < 3; column++) { int index = rowIndex * 3 + column; row.add(theme.label(labels[index]));
                fields[index] = theme.textBox(values[index], labels[index]); fields[index].minWidth = 80; row.add(fields[index]); }
            editor.add(row).expandX();
        }
        boundsFeedback = editor.add(theme.label("Enter six coordinates; scanning starts automatically.")).expandX().widget();
        return editor;
    }

    private void refreshBoundsDraft() {
        if (boundsFields == null) return;
        syncContext();
        if (!Objects.equals(boundsDraftContext, activeBoundsContext)) {
            boundsDraftContext = activeBoundsContext; boundsAutoApply.reset();
            String[] restored = coordinateValues(scans.bounds().orElse(null));
            for (int i = 0; i < boundsFields.length; i++) boundsFields[i].set(restored[i]);
            if (boundsDimensions != null) boundsDimensions.set(boundsSummary(scans.bounds().orElse(null)));
            if (boundsFeedback != null) boundsFeedback.set("World changed · showing saved bounds for this world.");
            return;
        }

        String[] values = Arrays.stream(boundsFields).map(WTextBox::get).toArray(String[]::new);
        Bounds current = scans.bounds().orElse(null);
        Optional<Bounds> parsed = BoundsInput.parse(values[0], values[1], values[2], values[3], values[4], values[5]);
        Optional<Bounds> applied = boundsAutoApply.update(values, System.nanoTime() / 1_000_000L, current);
        if (parsed.isEmpty()) {
            if (current != null) { scans.clearSelection(); invalidatePreview(); }
            boundsDimensions.set("Incomplete or oversized bounds.");
            boundsFeedback.set("Enter six integers within 32,768 cells.");
            return;
        }
        if (applied.isPresent()) {
            if (activeBoundsContext == null) { boundsFeedback.set("Join a world before saving bounds."); return; }
            Bounds next = applied.orElseThrow();
            try {
                boundsStore.save(activeBoundsContext, next); setBounds(next); invalidatePreview();
                boundsDimensions.set(boundsSummary(next)); boundsFeedback.set("Saved · scanning automatically."); report();
            } catch (IOException failure) { boundsFeedback.set("Could not save bounds; check local storage."); }
            return;
        }
        if (!parsed.orElseThrow().equals(current)) {
            if (current != null) { scans.clearSelection(); invalidatePreview(); }
            boundsDimensions.set(boundsSummary(parsed.orElseThrow()));
            boundsFeedback.set("Bounds changed · waiting for typing to stop.");
        } else {
            boundsDimensions.set(boundsSummary(current));
            boundsFeedback.set("Saved · scanning automatically.");
        }
    }

    private static String[] coordinateValues(Bounds bounds) {
        if (bounds == null) return new String[]{"", "", "", "", "", ""};
        return new String[]{"" + bounds.min().x(), "" + bounds.min().y(), "" + bounds.min().z(),
            "" + bounds.max().x(), "" + bounds.max().y(), "" + bounds.max().z()};
    }
    private static String boundsSummary(Bounds bounds) {
        if (bounds == null) return "No bounds selected.";
        long width = (long) bounds.max().x() - bounds.min().x() + 1, height = (long) bounds.max().y() - bounds.min().y() + 1,
            depth = (long) bounds.max().z() - bounds.min().z() + 1;
        return "Dimensions " + width + " × " + height + " × " + depth + " · " + bounds.volume() + " cells";
    }
}
