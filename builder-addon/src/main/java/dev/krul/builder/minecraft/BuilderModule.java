package dev.krul.builder.minecraft;

import dev.krul.builder.BuilderCategory;
import dev.krul.builder.blueprint.BlueprintDiff;
import dev.krul.builder.blueprint.BlueprintRenderer;
import dev.krul.builder.agent.AgentClient;
import dev.krul.builder.agent.CodexProcess;
import dev.krul.builder.agent.CodexExecutable;
import dev.krul.builder.agent.BuilderDynamicTools;
import dev.krul.builder.archive.ConversationArchiveStore;
import dev.krul.builder.archive.LegacyProjectMigration;
import dev.krul.builder.context.ScanSession;
import dev.krul.builder.bridge.BridgePairing;
import dev.krul.builder.bridge.BuilderBridgeServer;
import dev.krul.builder.context.BoundsContext;
import dev.krul.builder.context.BoundsInput;
import dev.krul.builder.context.BoundsStore;
import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import dev.krul.builder.model.WorldSnapshot;
import dev.krul.builder.project.ProjectSession;
import dev.krul.builder.project.ProjectStore;
import dev.krul.builder.project.BuildPlan;
import dev.krul.builder.ui.BuilderController;
import dev.krul.builder.ui.BuilderHud;
import dev.krul.builder.ui.BuilderOverlayVisibility;
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
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
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
    private final Setting<Boolean> renderOverlay = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("render-overlay").description("Render selection bounds and the current build-plan preview in the world.")
        .defaultValue(true).build());
    private final BoundsStore boundsStore;
    private final List<CompletableFuture<WorldSnapshot>> freshWaiters = new ArrayList<>();
    private WLabel statusLabel;
    private String reportedStatus;
    private BuilderFailureDedupe.State reportedFailure = BuilderFailureDedupe.State.empty();
    private Object boundWorld, boundConnection, boundPlayer;
    private BoundsContext activeBoundsContext;
    private boolean freshAfterCurrent, freshScanActive;
    private long freshBaselineRevision;
    private ExecutorService io;
    private AttachmentPolicy attachmentPolicy;
    private WorldCapture worldCapture;
    private ProjectStore projectStore;
    private ConversationArchiveStore conversationStore;
    private UUID activeConversation = PROJECT;
    private ProjectSession projectSession;
    private AgentClient agent;
    private BuilderController controller;
    private long runtimeGeneration;
    private long bridgeWorldGeneration;
    private BridgePairing bridgePairing;
    private BuilderBridgeServer bridgeServer;
    private BridgePairing.State reportedPairingState = BridgePairing.State.NONE;
    private BlueprintRenderer blueprintRenderer;
    private BuildPlan renderedPlan;
    private BlueprintDiff.Selection renderedSelection;
    private String legacyMigrationWarning = "";

    public BuilderModule() {
        super(BuilderCategory.BUILDER, "builder", "Select and inspect bounded regions; review every proposed plan before use.");
        boundsStore = new BoundsStore(mc.runDirectory.toPath());
        runInMainMenu = true;
        addAction("open-builder-chat", "Open the native Builder conversation window.", this::openBuilder);
    }

    private void addAction(String name, String description, Runnable action) {
        settings.getDefaultGroup().add(new KeybindSetting.Builder().name(name).description(description)
            .action(() -> { if (isActive() && mc.isOnThread() && mc.currentScreen == null) action.run(); }).build());
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
            scans.reset(); scans.bind(world, connection, player);
            activeBoundsContext = nextContext;
            Optional<Bounds> restore = contextChanged ? (nextContext == null ? Optional.empty() : boundsStore.load(nextContext)) : retained;
            restore.ifPresent(this::setBounds);
            if (changed) invalidateWorldSession("World session changed");
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

    private void setBounds(Bounds bounds) {
        scans.setFirst(bounds.min()); scans.setSecond(bounds.max());
    }

    @Override public void onActivate() {
        bridgeWorldGeneration++;
        bridgePairing = BridgePairing.secureDefault();
        scans.reset(); projectSession = null; reportedStatus = null;
        reportedFailure = BuilderFailureDedupe.State.empty();
        boundWorld = boundConnection = boundPlayer = null;
        activeBoundsContext = null;
        syncContext();
        startRuntime(++runtimeGeneration);
        ensureHud();
        report();
    }

    @Override public void onDeactivate() {
        ++runtimeGeneration;
        if (bridgeServer != null) bridgeServer.close();
        bridgeServer = null; bridgePairing = null; reportedPairingState = BridgePairing.State.NONE;
        if (controller != null) controller.close();
        if (attachmentPolicy != null && io != null) io.execute(attachmentPolicy::close);
        if (io != null) io.shutdown();
        if (mc.currentScreen instanceof BuilderScreen) mc.setScreen(null);
        controller = null; agent = null; attachmentPolicy = null; worldCapture = null; projectStore = null; conversationStore = null;
        io = null; projectSession = null; freshWaiters.forEach(f -> f.completeExceptionally(new CancellationException("Builder deactivated")));
        freshWaiters.clear(); freshAfterCurrent = freshScanActive = false;
        scans.reset(); reportedStatus = null; reportedFailure = BuilderFailureDedupe.State.empty(); activeBoundsContext = null;
    }

    private void startRuntime(long generation) {
        legacyMigrationWarning = "";
        io = Executors.newFixedThreadPool(2, task -> { Thread thread = new Thread(task, "builder-io"); thread.setDaemon(true); return thread; });
        Path root = mc.runDirectory.toPath().resolve("kru-builder");
        CompletableFuture.supplyAsync(() -> {
            try {
                projectStore = new ProjectStore(root.resolve("projects"));
                conversationStore = new ConversationArchiveStore(root.resolve("conversations"));
                try {
                    LegacyProjectMigration.migrate(projectStore, conversationStore, PROJECT, System.currentTimeMillis());
                } catch (IOException migrationFailure) {
                    legacyMigrationWarning = migrationFailure.getMessage() == null ? "Legacy Builder plan could not be imported" : migrationFailure.getMessage();
                }
                return new AttachmentPolicy(root.resolve("attachments"));
            } catch (IOException failure) { throw new CompletionException(failure); }
        }, io).whenComplete((policy, failure) -> mc.execute(() -> {
            if (generation != runtimeGeneration || !isActive()) {
                if (policy != null) CompletableFuture.runAsync(policy::close);
                return;
            }
            if (failure != null) {
                warning("Builder attachment storage unavailable: %s", failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage());
                return;
            }
            initializeRuntime(root, policy, generation);
        }));
    }

    private void initializeRuntime(Path root, AttachmentPolicy policy, long generation) {
        attachmentPolicy = policy;
        activeConversation = legacyMigrationWarning.isBlank() ? PROJECT : UUID.randomUUID();
        worldCapture = new WorldCapture(mc, attachmentPolicy, io);
        try {
            bridgeServer = new BuilderBridgeServer(bridgePairing);
            bridgeServer.start(new BuilderBridgeDataSource(mc, scans::snapshot, scans::bounds,
                () -> bridgeWorldGeneration, this::bridgeWorldIdentity, worldCapture, io, root));
        } catch (IOException failure) {
            bridgeServer = null;
            warning("Builder local bridge could not start: %s", failure.getMessage());
        }
        MinecraftPlanRules rules = new MinecraftPlanRules();
        var catalogTools = new BuilderDynamicTools(new ClientBlockCatalogTools(mc,
            () -> isActive() && runtimeGeneration == generation));
        agent = new CodexProcess(root.resolve("codex-profile"), resolveExecutable(executable.get()), rules, catalogTools);
        controller = new BuilderController(activeConversation, executable.get(), this::freshSnapshot, () -> projectSession,
            agent, worldCapture::capture, attachmentPolicy, conversationStore, mc::execute, io,
            this::activateConversation, this::restoreProject);
        if (!legacyMigrationWarning.isBlank()) controller.showNotice("Legacy plan migration warning: " + legacyMigrationWarning);
    }

    private void activateConversation(UUID id) {
        if (Objects.equals(activeConversation, id)) return;
        activeConversation = Objects.requireNonNull(id);
        WorldSnapshot current = scans.snapshot().orElse(null);
        projectSession = current == null ? null : new ProjectSession(activeConversation, current, new MinecraftPlanRules());
        renderedPlan = null; blueprintRenderer = null;
    }

    private String resolveExecutable(String value) {
        return CodexExecutable.resolve(mc.runDirectory.toPath(), value,
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"));
    }

    private void onExecutableChanged(String value) {
        if (controller == null || io == null || io.isShutdown()) return;
        MinecraftPlanRules rules = new MinecraftPlanRules();
        long generation = runtimeGeneration;
        var catalogTools = new BuilderDynamicTools(new ClientBlockCatalogTools(mc,
            () -> isActive() && runtimeGeneration == generation));
        AgentClient replacement = new CodexProcess(mc.runDirectory.toPath().resolve("kru-builder/codex-profile"), resolveExecutable(value), rules, catalogTools);
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
        if (scans.bounds().isEmpty()) return CompletableFuture.failedFuture(new IllegalStateException("Set valid numeric bounds before scanning"));
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
        if (projectSession == null) {
            try { projectSession = new ProjectSession(activeConversation, snapshot, new MinecraftPlanRules()); }
            catch (IllegalArgumentException invalidSnapshot) {
                failFreshWaiters(new IllegalStateException("Selection contains unknown or unloaded blocks; choose a fully loaded region", invalidSnapshot));
                return;
            }
        }
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
        bridgeWorldGeneration++;
        if (controller != null) controller.worldChanged();
        invalidateSelectionState(reason);
    }

    private void invalidateSelection(String reason) {
        if (controller != null) controller.selectionChanged();
        invalidateSelectionState(reason);
    }

    private void invalidateSelectionState(String reason) {
        bridgeWorldGeneration++;
        projectSession = null;
        renderedPlan = null; renderedSelection = null; blueprintRenderer = null;
        failFreshWaiters(new CancellationException(reason));
    }

    @EventHandler private void onJoined(GameJoinedEvent event) { syncContext(); }
    @EventHandler private void onLeft(GameLeftEvent event) { invalidateWorldSession("World disconnected"); scans.reset(); boundWorld = boundConnection = boundPlayer = null; activeBoundsContext = null; }

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
        mc.setScreen(new BuilderScreen(GuiThemes.get(), controller, () -> boundsWidget(GuiThemes.get()),
            renderOverlay::get, () -> renderOverlay.set(!renderOverlay.get()),
            () -> bridgePairing == null ? null : bridgePairing.pending(),
            () -> bridgePairing != null && bridgePairing.state() == BridgePairing.State.APPROVED,
            this::approveBridgePairing, this::rejectBridgePairing, this::revokeBridgePairing));
    }

    private void approveBridgePairing() {
        if (bridgePairing == null) return;
        BridgePairing.Challenge pending = bridgePairing.pending();
        if (pending != null && bridgePairing.approve(pending.code())) info("Builder MCP paired for this session.");
    }
    private void rejectBridgePairing() {
        if (bridgePairing == null) return;
        BridgePairing.Challenge pending = bridgePairing.pending();
        if (pending != null) bridgePairing.reject(pending.code());
    }
    private void revokeBridgePairing() { if (bridgePairing != null) bridgePairing.revoke(); }

    private String bridgeWorldIdentity() {
        if (mc.world == null) return "";
        String server = mc.isInSingleplayer() ? "singleplayer" : Optional.ofNullable(mc.getCurrentServerEntry())
            .map(entry -> entry.address).orElse("unknown-server");
        return server + "|" + mc.world.getRegistryKey().getValue();
    }

    @EventHandler private void onTick(TickEvent.Post event) {
        syncContext(); scans.tick(); tickFreshScan();
        if (bridgePairing != null) {
            BridgePairing.State pairingState = bridgePairing.state();
            if (pairingState == BridgePairing.State.WAITING && reportedPairingState != pairingState) {
                BridgePairing.Challenge challenge = bridgePairing.pending();
                if (challenge != null) info("Builder MCP pairing requested (%s). Open Builder Settings to allow or reject.", challenge.code());
            }
            if (pairingState == BridgePairing.State.NONE && reportedPairingState == BridgePairing.State.APPROVED)
                info("Builder MCP pairing revoked.");
            reportedPairingState = pairingState;
        }
        if (controller != null) scans.snapshot().ifPresent(controller::observeSnapshot);
        reportFailure();
        if (projectSession == null && !freshScanActive && !freshAfterCurrent)
            scans.snapshot().filter(snapshot -> snapshot.unknown().isEmpty())
                .ifPresent(snapshot -> projectSession = new ProjectSession(activeConversation, snapshot, new MinecraftPlanRules()));
        report();
    }

    @EventHandler private void onRender(Render3DEvent event) {
        syncContext();
        boolean drawOverlay = BuilderOverlayVisibility.shouldRender(renderOverlay.get(), WorldCapture.suppressPreviewThisFrame());
        if (drawOverlay) scans.bounds().ifPresent(bounds -> event.renderer.box(
            bounds.min().x(), bounds.min().y(), bounds.min().z(),
            (double) bounds.max().x() + 1, (double) bounds.max().y() + 1, (double) bounds.max().z() + 1,
            FILL, OUTLINE, ShapeMode.Both, 0));
        if (controller != null && drawOverlay) {
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

    private void reportFailure() {
        BuilderUiState state = controller == null ? null : controller.state();
        BuilderFailureDedupe.Transition transition = BuilderFailureDedupe.advance(reportedFailure,
            state != null && state.phase() == BuilderUiState.Phase.FAILED,
            state == null ? null : state.status());
        reportedFailure = transition.state();
        if (transition.report() != null && mc.player != null) info("Builder request failed: %s", transition.report());
    }
    @Override public String getInfoString() { syncContext(); return scans.status(); }
    @Override public WWidget getWidget(GuiTheme theme) {
        syncContext();
        var list = theme.verticalList();
        list.add(theme.button("Open Builder conversation")).expandX().widget().action = this::openBuilder;
        return list;
    }

    public WWidget boundsWidget(GuiTheme theme) {
        syncContext();
        BoundsContext[] editorContext = {activeBoundsContext};
        Optional<Bounds> initial = scans.bounds();
        String[] values = coordinateValues(initial.orElse(null));
        WTextBox[] fields = new WTextBox[6];
        String[] labels = {"X1", "Y1", "Z1", "X2", "Y2", "Z2"};
        var editor = theme.verticalList();
        editor.add(theme.label("Selection bounds · max 32,768 cells")).expandX();
        WLabel dimensions = editor.add(theme.label(boundsSummary(initial.orElse(null)))).expandX().widget();
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            WHorizontalList row = theme.horizontalList();
            for (int column = 0; column < 3; column++) {
                int index = rowIndex * 3 + column;
                row.add(theme.label(labels[index]));
                fields[index] = theme.textBox(values[index], labels[index]);
                fields[index].minWidth = 80;
                row.add(fields[index]);
            }
            editor.add(row).expandX();
        }
        WLabel feedback = editor.add(theme.label("")).expandX().widget();
        WHorizontalList actions = theme.horizontalList();
        actions.add(theme.button("Apply")).widget().action = () -> {
            syncContext();
            if (!Objects.equals(editorContext[0], activeBoundsContext)) {
                editorContext[0] = activeBoundsContext;
                String[] refreshed = coordinateValues(scans.bounds().orElse(null));
                for (int i = 0; i < fields.length; i++) fields[i].set(refreshed[i]);
                feedback.set("Context changed; review bounds and apply again.");
                return;
            }
            if (activeBoundsContext == null) {
                feedback.set("Join a world before applying bounds.");
                return;
            }
            Optional<Bounds> parsed = BoundsInput.parse(fields[0].get(), fields[1].get(), fields[2].get(),
                fields[3].get(), fields[4].get(), fields[5].get());
            if (parsed.isEmpty()) {
                feedback.set("Use six integers; maximum is 32,768 cells.");
                return;
            }
            try {
                boundsStore.save(activeBoundsContext, parsed.orElseThrow());
                setBounds(parsed.orElseThrow());
                invalidateSelection("Bounds changed");
                feedback.set("Applied. Next send scans this area.");
                dimensions.set(boundsSummary(parsed.orElseThrow()));
                report();
            } catch (IOException failure) {
                feedback.set("Save failed; check bounds storage.");
            }
        };
        actions.add(theme.button("Clear")).widget().action = () -> {
            syncContext();
            if (!Objects.equals(editorContext[0], activeBoundsContext)) {
                editorContext[0] = activeBoundsContext;
                String[] refreshed = coordinateValues(scans.bounds().orElse(null));
                for (int i = 0; i < fields.length; i++) fields[i].set(refreshed[i]);
                feedback.set("Context changed; review bounds first.");
                return;
            }
            try {
                if (activeBoundsContext != null) boundsStore.clear(activeBoundsContext);
                scans.clearSelection();
                invalidateSelection("Bounds cleared");
                for (WTextBox field : fields) field.set("");
                dimensions.set("No bounds selected.");
                feedback.set(activeBoundsContext == null ? "Current selection cleared; no world bounds were saved." : "Saved bounds cleared.");
                report();
            } catch (IOException failure) {
                feedback.set("Clear failed; check bounds storage.");
            }
        };
        editor.add(actions).expandX();
        return editor;
    }

    private static String[] coordinateValues(Bounds bounds) {
        if (bounds == null) return new String[]{"", "", "", "", "", ""};
        return new String[]{Integer.toString(bounds.min().x()), Integer.toString(bounds.min().y()), Integer.toString(bounds.min().z()),
            Integer.toString(bounds.max().x()), Integer.toString(bounds.max().y()), Integer.toString(bounds.max().z())};
    }

    private static String boundsSummary(Bounds bounds) {
        if (bounds == null) return "No bounds selected.";
        long width = (long) bounds.max().x() - bounds.min().x() + 1;
        long height = (long) bounds.max().y() - bounds.min().y() + 1;
        long depth = (long) bounds.max().z() - bounds.min().z() + 1;
        return "Dimensions " + width + " × " + height + " × " + depth + " · " + bounds.volume() + " cells";
    }

    private void ensureHud() {
        for (HudElement element : Hud.get()) if (element.info.name.equals(BuilderHud.INFO.name)) return;
        Hud.get().add(BuilderHud.INFO, 8, 140, meteordevelopment.meteorclient.systems.hud.XAnchor.Left,
            meteordevelopment.meteorclient.systems.hud.YAnchor.Top);
    }
}
