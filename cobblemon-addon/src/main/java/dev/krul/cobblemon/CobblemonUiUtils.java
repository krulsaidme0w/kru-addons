package dev.krul.cobblemon;

import dev.krul.cobblemon.gui.overlay.OverlayModule;
import dev.krul.cobblemon.pcui.UiToolsContent;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import org.lwjgl.glfw.GLFW;

public final class CobblemonUiUtils extends OverlayModule {
    public final Setting<Keybind> loadGuiKey;
    public CobblemonUiUtils() {
        this(new UiToolsContent());
    }
    private CobblemonUiUtils(UiToolsContent content) {
        super("cobblemon-ui-utils", "Contextual commands and menu utilities for inventory, server containers and Cobblemon PC.",
            content, -1, 75);
        loadGuiKey = settings.getDefaultGroup().add(new KeybindSetting.Builder().name("load-saved-gui")
            .description("Restore the saved client window while playing. Does not reopen its server session.")
            .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_V)).build());
        content.configure(this::isActive, loadGuiKey::get);
    }
}
