package dev.krul.cobblemon.gui.overlay;

import dev.krul.cobblemon.CobblemonAddon;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;

/** Meteor owns configuration/lifecycle; OverlayHost owns presentation and input. */
public abstract class OverlayModule extends Module {
    public final OverlayContent content;
    public final Setting<Keybind> overlayKey;
    public final Setting<Keybind> interactKey;
    public final Setting<Double> panelScale;
    public final Setting<Integer> panelX, panelY;
    public final Setting<Boolean> collapsed;
    boolean visible;
    private final ContextVisibility contextVisibility = new ContextVisibility();
    double scrollX, scrollY;

    protected OverlayModule(String name, String description, OverlayContent content, int x, int y) {
        super(CobblemonAddon.CATEGORY, name, description);
        this.content = content;
        autoSubscribe = false;
        // The host owns world-bound data cleanup and forbids rendering outside a world.
        // Meteor must not replay manual show/hide callbacks on join/leave/reconfiguration.
        runInMainMenu = true;
        var controls = settings.createGroup("Overlay");
        overlayKey = controls.add(new KeybindSetting.Builder().name("show-hide-key")
            .description("Show/hide this panel, including inside PC/battle. Does not fire while typing.")
            .defaultValue(Keybind.none()).build());
        interactKey = controls.add(new KeybindSetting.Builder().name("interact-key")
            .description("Release the cursor for overlays while in the world. Escape returns to play.")
            .defaultValue(Keybind.none()).build());
        panelScale = controls.add(new DoubleSetting.Builder().name("panel-scale").description("Uniform window scale.")
            .defaultValue(content.defaultScale()).range(.5, 2).sliderRange(.5, 2).build());
        collapsed = controls.add(new BoolSetting.Builder().name("collapsed").description("Keep only the title bar.")
            .defaultValue(false).build());
        var layout = settings.createGroup("Position");
        panelX = layout.add(new IntSetting.Builder().name("panel-x").description("Saved GUI pixel position; clamped to viewport.")
            .defaultValue(x).range(-1, 10000).sliderRange(0, 1000).build());
        panelY = layout.add(new IntSetting.Builder().name("panel-y").description("Saved GUI pixel position.")
            .defaultValue(y).range(0, 10000).sliderRange(0, 700).build());
        OverlayHost.register(this);
    }
    @Override public void onActivate() {
        resetContext(); visible = true; refreshVisibility();
        if (visible) OverlayHost.front(this);
    }
    @Override public void onDeactivate() { resetContext(); visible = false; content.reset(); OverlayHost.closed(this); }
    void resetContext() { contextVisibility.reset(); content.blur(); scrollX = scrollY = 0; }
    void refreshVisibility() {
        if (!content.contextual()) return;
        if (contextVisibility.update(content.contextKey())) { content.blur(); scrollX = scrollY = 0; }
        visible = contextVisibility.visible();
        if (!visible) OverlayHost.closed(this);
    }
    void hidePanel() {
        visible = false;
        contextVisibility.hide();
        OverlayHost.closed(this);
    }
    public void showPanel() {
        if (!isActive()) toggle();
        refreshVisibility();
        if (content.contextual()) { contextVisibility.show(); visible = contextVisibility.visible(); }
        else visible = true;
        if (visible) OverlayHost.front(this);
    }
    void togglePanel() {
        if (!isActive()) { showPanel(); return; }
        refreshVisibility();
        if (visible) hidePanel();
        else showPanel();
    }
    @Override public String getInfoString() { return visible ? "Shown" : "Hidden"; }
    @Override public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        list.add(theme.label("Overlay: " + overlayKey.get() + " | Cursor: " + interactKey.get()));
        var open = list.add(theme.button("Show overlay")).expandX().widget();
        open.action = this::showPanel;
        var reset = list.add(theme.button("Reset position / scale")).expandX().widget();
        reset.action = () -> { panelX.reset(); panelY.reset(); panelScale.reset(); collapsed.set(false); };
        return list;
    }
}
