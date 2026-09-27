package dev.krul.builder.minecraft;

import dev.krul.builder.BuilderCategory;
import dev.krul.builder.blueprint.BlueprintDiff;
import dev.krul.builder.blueprint.BlueprintRenderer;
import dev.krul.builder.agent.AgentClient;
import dev.krul.builder.agent.CodexProcess;
import dev.krul.builder.context.ScanSession;
import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import dev.krul.builder.model.WorldSnapshot;
import dev.krul.builder.project.ProjectSession;
import dev.krul.builder.project.ProjectStore;
import dev.krul.builder.project.BuildPlan;
import dev.krul.builder.ui.BuilderController;
import dev.krul.builder.ui.BuilderHud;
import dev.krul.builder.ui.BuilderScreen;
import dev.krul.builder.ui.BuilderUiState;
import dev.krul.builder.visual.AttachmentPolicy;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.render.RenderAfterWorldEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Read-only selection plus explicit, reviewed Codex-assisted build planning. */
public final class BuilderModule extends Module {
    private static final Color OUTLINE = new Color(80, 190, 255, 220);
    private static final Color FILL = new Color(80, 190, 255, 20);
    private static final UUID PROJECT = UUID.nameUUIDFromBytes("kru-builder-project".getBytes(StandardCharsets.UTF_8));
    private final ScanSession scans = new ScanSession();
    private final Setting<String> executable = settings.getDefaultGroup().add(new StringSetting.Builder()
        .name("codex-executable").description("Codex CLI executable used by the isolated Builder profile.")
        .defaultValue("codex").onChanged(this::onExecutableChanged).build());
    private final List<CompletableFuture<WorldSnapshot>> freshWaiters = new ArrayList<>();
    private WLabel statusLabel;
    private String reportedStatus;
    private Object boundWorld, boundConnection, boundPlayer;
    private boolean freshAfterCurrent, freshScanActive;
    private long freshBaselineRevision;
    private ExecutorService io;
    private AttachmentPolicy attachmentPolicy;
    private WorldCapture worldCapture;
    private ProjectStore projectStore;
    private ProjectSession projectSession;
    private AgentClient agent;
    private BuilderController controller;
    private long runtimeGeneration;
    private BlueprintRenderer blueprintRenderer;
    private BuildPlan renderedPlan;
    private BlueprintDiff.Selection renderedSelection;

    public BuilderModule() {
        super(BuilderCategory.BUILDER, "builder", "Select and inspect bounded regions; review every proposed plan before use.");
        runInMainMenu = true;
        addAction("open-builder-chat", "Open the native Builder conversation window.", this::openBuilder);
        addAction("mark-first", "Mark the looked-at block as the first corner.", () -> mark(true));
        addAction("mark-second", "Mark the looked-at block as the second corner.", () -> mark(false));
        addAction("clear-selection", "Forget both corners and all scan evidence.", this::clearSelection);
        addAction("scan-selection", "Request a fresh bounded scan; keep the previous result until complete.", this::requestScan);
    }

    private void addAction(String name, String description, Runnable action) {
        settings.getDefaultGroup().add(new KeybindSetting.Builder().name(name).description(description)
            .action(() -> { if (isActive() && mc.isOnThread() && mc.currentScreen == null) action.run(); }).build());
    }

    private void syncContext() {
        if (!mc.isOnThread()) throw new IllegalStateException("Builder context requires the client thread");
        Object world = mc.world, connection = mc.getNetworkHandler(), player = mc.player;
        if (boundWorld != world || boundConnection != connection || boundPlayer != player) {
            boolean changed = boundWorld != null || boundConnection != null || boundPlayer != null;
            boundWorld = world; boundConnection = connection; boundPlayer = player;
            scans.bind(world, connection, player);
            if (changed) invalidateWorldSession("World session changed");
        } else scans.bind(world, connection, player);
    }

    @Override public void onActivate() {
        scans.reset(); projectSession = null; reportedStatus = null;
        boundWorld = boundConnection = boundPlayer = null;
        syncContext();
        startRuntime(++runtimeGeneration);
        ensureHud();
        report();
    }

    @Override public void onDeactivate() {
        ++runtimeGeneration;
        if (controller != null) controller.close();
        if (attachmentPolicy != null && io != null) io.execute(attachmentPolicy::close);
        if (io != null) io.shutdown();
        if (mc.currentScreen instanceof BuilderScreen) mc.setScreen(null);
        controller = null; agent = null; attachmentPolicy = null; worldCapture = null; projectStore = null;
        io = null; projectSession = null; freshWaiters.forEach(f -> f.completeExceptionally(new CancellationException("Builder deactivated")));
        freshWaiters.clear(); freshAfterCurrent = freshScanActive = false;
        scans.reset(); reportedStatus = null;
    }

    private void startRuntime(long generation) {
        io = Executors.newFixedThreadPool(2, task -> { Thread thread = new Thread(task, "builder-io"); thread.setDaemon(true); return thread; });
        Path root = mc.runDirectory.toPath().resolve("kru-builder");
        CompletableFuture.supplyAsync(() -> {
            try { return new AttachmentPolicy(root.resolve("attachments")); }
            catch (IOException failure) { throw new CompletionException(failure); }
        }, io).whenComplete((policy, failure) -> mc.execute(() -> {
            if (generation != runtimeGeneration || !isActive()) {
                if (policy != null) CompletableFuture.runAsync(policy::close);
                return;
            }
            if (failure != null) {
                warning("Builder attachment storage unavailable: %s", failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage());
                return;
            }
            initializeRuntime(root, policy);
        }));
    }

    private void initializeRuntime(Path root, AttachmentPolicy policy) {
        attachmentPolicy = policy;
        projectStore = new ProjectStore(root.resolve("projects"));
        worldCapture = new WorldCapture(mc, attachmentPolicy, io);
        MinecraftPlanRules rules = new MinecraftPlanRules();
        agent = new CodexProcess(root.resolve("codex-profile"), executable.get(), rules);
        controller = new BuilderController(PROJECT, executable.get(), this::freshSnapshot, () -> projectSession,
            agent, worldCapture::capture, attachmentPolicy, projectStore, mc::execute, io, this::restoreProject);
    }

    private void onExecutableChanged(String value) {
        if (controller == null || io == null || io.isShutdown()) return;
        MinecraftPlanRules rules = new MinecraftPlanRules();
        AgentClient replacement = new CodexProcess(mc.runDirectory.toPath().resolve("kru-builder/codex-profile"), value, rules);
        agent = replacement;
        controller.replaceAgent(replacement);
    }

    private void restoreProject(dev.krul.builder.project.BuildPlan plan) {
        if (projectSession == null) {
            controller.showNotice("Scan the current selection before reopening a saved plan"); return;
        }
        List<String> errors = projectSession.restoreAccepted(plan);
        controller.showNotice(errors.isEmpty() ? "Saved plan reopened as accepted revision " + plan.revision()
            : "Saved plan did not fit the current selection: " + String.join("; ", errors));
        renderedPlan = null; blueprintRenderer = null;
    }

    private CompletableFuture<WorldSnapshot> freshSnapshot() {
        if (!mc.isOnThread()) return CompletableFuture.failedFuture(new IllegalStateException("Fresh scan must start on client thread"));
        syncContext();
        if (!isActive() || attachmentPolicy == null) return CompletableFuture.failedFuture(new IllegalStateException("Builder runtime is unavailable"));
        if (scans.bounds().isEmpty()) return CompletableFuture.failedFuture(new IllegalStateException("Mark two corners before scanning"));
        var future = new CompletableFuture<WorldSnapshot>();
        freshWaiters.add(future);
        if (freshScanActive || freshAfterCurrent) return future;
        if (scans.status().startsWith("Scanning")) freshAfterCurrent = true;
        else startFreshScan();
        return future;
    }

    private void startFreshScan() {
        freshBaselineRevision = scans.snapshot().map(WorldSnapshot::revision).orElse(0L);
        scans.requestScan(new ClientWorldReader(mc, mc.world));
        if (scans.status().startsWith("Scanning")) freshScanActive = true;
        else failFreshWaiters(new IllegalStateException(scans.status()));
    }

    private void tickFreshScan() {
        boolean scanning = scans.status().startsWith("Scanning");
        if (freshAfterCurrent && !scanning) { freshAfterCurrent = false; startFreshScan(); return; }
        if (!freshScanActive || scanning) return;
        Optional<WorldSnapshot> complete = scans.snapshot();
        if (complete.isEmpty() || complete.get().revision() <= freshBaselineRevision) return;
        WorldSnapshot snapshot = complete.orElseThrow();
        if (projectSession == null) projectSession = new ProjectSession(PROJECT, snapshot, new MinecraftPlanRules());
        freshScanActive = false;
        List<CompletableFuture<WorldSnapshot>> completed = List.copyOf(freshWaiters); freshWaiters.clear();
        completed.forEach(future -> future.complete(snapshot));
    }

    private void failFreshWaiters(Throwable failure) {
        List<CompletableFuture<WorldSnapshot>> failed = List.copyOf(freshWaiters); freshWaiters.clear();
        failed.forEach(future -> future.completeExceptionally(failure));
        freshScanActive = false; freshAfterCurrent = false;
    }

    private void invalidateWorldSession(String reason) {
        if (controller != null) controller.worldChanged();
        invalidateSelectionState(reason);
    }

    private void invalidateSelection(String reason) {
        if (controller != null) controller.selectionChanged();
        invalidateSelectionState(reason);
    }

    private void invalidateSelectionState(String reason) {
        projectSession = null;
        renderedPlan = null; renderedSelection = null; blueprintRenderer = null;
        failFreshWaiters(new CancellationException(reason));
    }

    @EventHandler private void onJoined(GameJoinedEvent event) { syncContext(); }
    @EventHandler private void onLeft(GameLeftEvent event) { invalidateWorldSession("World disconnected"); scans.reset(); boundWorld = boundConnection = boundPlayer = null; }

    private void mark(boolean first) {
        if (!isActive()) { warning("Enable Builder before marking a selection."); return; }
        syncContext();
        if (mc.world == null || mc.player == null || mc.getCameraEntity() == null) { report(); return; }
        var hit = mc.getCameraEntity().raycast(mc.player.getBlockInteractionRange(), 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult block)) {
            warning("Look at a block to mark a selection corner."); return;
        }
        var pos = block.getBlockPos();
        var cell = new Cell(pos.getX(), pos.getY(), pos.getZ());
        if (first) scans.setFirst(cell); else scans.setSecond(cell);
        invalidateSelection("Selection changed");
        info("%s corner: %d, %d, %d", first ? "First" : "Second", cell.x(), cell.y(), cell.z());
        requestScan();
    }

    private void clearSelection() {
        syncContext(); scans.clearSelection(); invalidateSelection("Selection cleared"); report();
    }
    private void requestScan() {
        if (!isActive()) { warning("Enable Builder before scanning."); return; }
        syncContext();
        if (mc.world != null) scans.requestScan(new ClientWorldReader(mc, mc.world));
        report();
    }

    private void openBuilder() {
        if (!isActive()) { warning("Enable Builder to open the conversation."); return; }
        if (controller == null) { warning("Builder storage is unavailable; check the module status."); return; }
        mc.setScreen(new BuilderScreen(GuiThemes.get(), controller));
    }

    @EventHandler private void onTick(TickEvent.Post event) {
        syncContext(); scans.tick(); tickFreshScan();
        if (controller != null) scans.snapshot().ifPresent(controller::observeSnapshot);
        if (projectSession == null && !freshScanActive && !freshAfterCurrent)
            scans.snapshot().ifPresent(snapshot -> projectSession = new ProjectSession(PROJECT, snapshot, new MinecraftPlanRules()));
        report();
    }

    @EventHandler private void onRender(Render3DEvent event) {
        syncContext();
        if (!WorldCapture.suppressPreviewThisFrame()) scans.bounds().ifPresent(bounds -> event.renderer.box(
            bounds.min().x(), bounds.min().y(), bounds.min().z(),
            (double) bounds.max().x() + 1, (double) bounds.max().y() + 1, (double) bounds.max().z() + 1,
            FILL, OUTLINE, ShapeMode.Both, 0));
        if (controller != null && !WorldCapture.suppressPreviewThisFrame()) {
            var state = controller.state();
            BuildPlan plan = state.draft().isPresent() ? state.draft().orElseThrow() : state.accepted().orElse(null);
            String selectedStage = state.selectedStage();
            Integer selectedLayer = state.selectedLayer();
            if (plan != null && selectedStage != null && plan.stages().stream().noneMatch(stage -> stage.id().equals(selectedStage))) {
                controller.selectStage(0); state = controller.state();
            }
            if (plan != null && selectedLayer != null && plan.stages().stream()
                    .flatMap(stage -> stage.targets().keySet().stream()).noneMatch(cell -> cell.y() == selectedLayer)) {
                controller.showAllLayers(); state = controller.state();
            }
            var selection = new BlueprintDiff.Selection(state.selectedStage(), state.selectedLayer());
            if (!Objects.equals(renderedPlan, plan) || !Objects.equals(renderedSelection, selection)) {
                renderedPlan = plan; renderedSelection = selection;
                try { blueprintRenderer = plan == null ? null : new BlueprintRenderer(plan,
                    scans.bounds().orElseThrow(), selection); }
                catch (RuntimeException incompatible) { blueprintRenderer = null; }
            }
            if (blueprintRenderer != null) blueprintRenderer.render(event, scans.snapshot().orElse(null), false);
        }
    }

    @EventHandler private void onAfterWorld(RenderAfterWorldEvent event) {
        if (isActive() && worldCapture != null) worldCapture.onAfterWorld(event);
    }

    public Optional<WorldSnapshot> snapshot() { syncContext(); return isActive() ? scans.snapshot() : Optional.empty(); }
    public BuilderUiState uiState() { return controller == null ? null : controller.state(); }

    private void report() {
        String status = scans.status();
        if (statusLabel != null) statusLabel.set(status);
        if (mc.player != null && !status.equals(reportedStatus)) { info("%s", status); reportedStatus = status; }
    }
    @Override public String getInfoString() { syncContext(); return scans.status(); }
    @Override public WWidget getWidget(GuiTheme theme) {
        syncContext();
        var list = theme.verticalList();
        list.add(theme.label("Read only. Mark two corners; up to 32,768 cells, 2,048 reads/tick."));
        list.add(theme.label("Open the native chat window to review context and proposals."));
        statusLabel = list.add(theme.label(scans.status())).widget();
        list.add(theme.button("Open Builder conversation")).expandX().widget().action = this::openBuilder;
        list.add(theme.button("Mark first looked-at block")).expandX().widget().action = () -> mark(true);
        list.add(theme.button("Mark second looked-at block")).expandX().widget().action = () -> mark(false);
        list.add(theme.button("Clear selection")).expandX().widget().action = this::clearSelection;
        list.add(theme.button("Scan selection again")).expandX().widget().action = this::requestScan;
        return list;
    }

    private void ensureHud() {
        for (HudElement element : Hud.get()) if (element.info.name.equals(BuilderHud.INFO.name)) return;
        Hud.get().add(BuilderHud.INFO, 8, 140, meteordevelopment.meteorclient.systems.hud.XAnchor.Left,
            meteordevelopment.meteorclient.systems.hud.YAnchor.Top);
    }
}
