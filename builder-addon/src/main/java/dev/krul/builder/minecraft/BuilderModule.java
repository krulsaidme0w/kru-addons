package dev.krul.builder.minecraft;

import dev.krul.builder.BuilderCategory;
import dev.krul.builder.context.ScanSession;
import dev.krul.builder.model.Cell;
import dev.krul.builder.model.WorldSnapshot;
import java.util.Optional;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/** Read-only selection and explicitly requested scans; no block interaction or packet sending. */
public final class BuilderModule extends Module {
    private static final Color OUTLINE = new Color(80, 190, 255, 220);
    private static final Color FILL = new Color(80, 190, 255, 20);
    private final ScanSession scans = new ScanSession();
    private WLabel statusLabel;
    private String reportedStatus;

    public BuilderModule() {
        super(BuilderCategory.BUILDER, "builder", "Select and inspect bounded client-visible regions without changing the world.");
        // Ensures activation/deactivation and disconnect resets also run outside a loaded world.
        runInMainMenu = true;
        addAction("mark-first", "Mark the looked-at block as the first corner.", () -> mark(true));
        addAction("mark-second", "Mark the looked-at block as the second corner.", () -> mark(false));
        addAction("clear-selection", "Forget both corners and all scan evidence.", this::clearSelection);
        addAction("scan-selection", "Request a fresh bounded scan; keep the previous result until complete.", this::requestScan);
    }

    private void addAction(String name, String description, Runnable action) {
        settings.getDefaultGroup().add(new KeybindSetting.Builder().name(name).description(description)
            .action(() -> {
                // Keybind settings are globally subscribed by Meteor. Never act through another screen.
                if (isActive() && mc.isOnThread() && mc.currentScreen == null) action.run();
            }).build());
    }

    private void syncContext() {
        if (!mc.isOnThread()) throw new IllegalStateException("Builder context requires the client thread");
        scans.bind(mc.world, mc.getNetworkHandler(), mc.player);
    }

    @Override public void onActivate() { scans.reset(); syncContext(); report(); }
    @Override public void onDeactivate() { scans.reset(); reportedStatus = null; }
    @EventHandler private void onJoined(GameJoinedEvent event) { scans.reset(); reportedStatus = null; }
    @EventHandler private void onLeft(GameLeftEvent event) { scans.reset(); reportedStatus = null; }

    private void mark(boolean first) {
        if (!isActive()) { warning("Enable Builder before marking a selection."); return; }
        syncContext();
        if (mc.world == null || mc.player == null || mc.getCameraEntity() == null) { report(); return; }
        var hit = mc.getCameraEntity().raycast(mc.player.getBlockInteractionRange(), 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult block)) {
            warning("Look at a block to mark a selection corner.");
            return;
        }
        var pos = block.getBlockPos();
        var cell = new Cell(pos.getX(), pos.getY(), pos.getZ());
        if (first) scans.setFirst(cell); else scans.setSecond(cell);
        info("%s corner: %d, %d, %d", first ? "First" : "Second", cell.x(), cell.y(), cell.z());
        requestScan();
    }

    private void clearSelection() {
        syncContext();
        scans.clearSelection();
        report();
    }

    private void requestScan() {
        if (!isActive()) { warning("Enable Builder before scanning."); return; }
        syncContext();
        if (mc.world != null) scans.requestScan(new ClientWorldReader(mc, mc.world));
        report();
    }

    @EventHandler private void onTick(TickEvent.Post event) {
        syncContext();
        scans.tick();
        report();
    }

    @EventHandler private void onRender(Render3DEvent event) {
        syncContext();
        if (!WorldCapture.suppressPreviewThisFrame()) scans.bounds().ifPresent(bounds -> event.renderer.box(
            bounds.min().x(), bounds.min().y(), bounds.min().z(),
            (double) bounds.max().x() + 1, (double) bounds.max().y() + 1, (double) bounds.max().z() + 1,
            FILL, OUTLINE, ShapeMode.Both, 0));
    }

    /** The last complete pass, possibly retained during a newer pass; inspect its revision. */
    public Optional<WorldSnapshot> snapshot() {
        syncContext();
        return isActive() ? scans.snapshot() : Optional.empty();
    }

    private void report() {
        String status = scans.status();
        if (statusLabel != null) statusLabel.set(status);
        if (mc.player != null && !status.equals(reportedStatus)) {
            info("%s", status);
            reportedStatus = status;
        }
    }

    @Override public String getInfoString() {
        syncContext();
        return scans.status();
    }

    @Override public WWidget getWidget(GuiTheme theme) {
        syncContext();
        var list = theme.verticalList();
        list.add(theme.label("Read only. Mark two corners; up to 32,768 cells, 2,048 reads/tick."));
        list.add(theme.label("Use the optional keybinds in-world, or mark your current view below."));
        statusLabel = list.add(theme.label(scans.status())).widget();
        list.add(theme.button("Mark first looked-at block")).expandX().widget().action = () -> mark(true);
        list.add(theme.button("Mark second looked-at block")).expandX().widget().action = () -> mark(false);
        list.add(theme.button("Clear selection")).expandX().widget().action = this::clearSelection;
        list.add(theme.button("Scan selection again")).expandX().widget().action = this::requestScan;
        return list;
    }
}
