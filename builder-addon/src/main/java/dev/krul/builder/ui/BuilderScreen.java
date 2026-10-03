package dev.krul.builder.ui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.utils.Utils;

import java.util.Objects;
import java.util.function.Supplier;

/** Selection, scan status and local MCP bridge status. */
public final class BuilderScreen extends WindowScreen {
    private final Supplier<WWidget> boundsEditor;
    private final Runnable updateBounds;
    private final Supplier<String> scanStatus;
    private final Supplier<String> bridgeStatus;
    private WLabel status, bridge;

    public BuilderScreen(GuiTheme theme, Supplier<WWidget> boundsEditor, Runnable updateBounds,
                         Supplier<String> scanStatus, Supplier<String> bridgeStatus) {
        super(theme, "Builder");
        this.boundsEditor = Objects.requireNonNull(boundsEditor); this.updateBounds = Objects.requireNonNull(updateBounds);
        this.scanStatus = Objects.requireNonNull(scanStatus); this.bridgeStatus = Objects.requireNonNull(bridgeStatus);
    }

    @Override public void initWidgets() {
        add(boundsEditor.get()).expandX();
        status = add(theme.label(scanStatus.get())).expandX().widget();
        bridge = add(theme.label(bridgeStatus.get())).expandX().widget();
    }

    @Override public void tick() {
        super.tick();
        updateBounds.run();
        if (status != null) status.set(scanStatus.get());
        if (bridge != null) bridge.set(bridgeStatus.get());
        window.view.maxHeight = Math.max(theme.scale(100), Utils.getWindowHeight() - theme.scale(72));
    }
}
