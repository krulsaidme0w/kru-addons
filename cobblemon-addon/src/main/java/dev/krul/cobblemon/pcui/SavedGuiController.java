package dev.krul.cobblemon.pcui;

import java.util.function.Supplier;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;

/** Save/load client screen objects, matching UIUtils; no container-opening request is sent. */
final class SavedGuiController {
    private final SavedGuiState<Screen, ScreenHandler> saved = new SavedGuiState<>();
    private Supplier<Keybind> key = Keybind::none;
    private boolean held;
    private Screen restored;
    void configure(Supplier<Keybind> key) { this.key = key; }
    void save(Screen screen) {
        var mc = MinecraftClient.getInstance();
        saved.save(mc.world, mc.getNetworkHandler(), screen,
            screen instanceof HandledScreen<?> handled ? handled.getScreenHandler() : null);
    }
    boolean load() {
        var mc = MinecraftClient.getInstance();
        var entry = saved.load(mc.world, mc.getNetworkHandler());
        if (entry == null || mc.player == null) return false;
        UiPacketTools.shared().restoreLocally(() -> mc.setScreen(entry.screen()));
        if (entry.handler() != null) mc.player.currentScreenHandler = entry.handler();
        restored = entry.screen();
        return true;
    }
    boolean tick() {
        var mc = MinecraftClient.getInstance();
        saved.load(mc.world, mc.getNetworkHandler());
        Keybind binding = key.get();
        boolean pressed = binding.isSet() && binding.isPressed();
        boolean edge = pressed && !held;
        held = pressed;
        return edge && mc.isWindowFocused() && mc.currentScreen == null && load();
    }
    boolean isRestored(Screen screen) { return screen != null && screen == restored; }
    String keyName() { return key.get().toString(); }
    boolean available() {
        var mc = MinecraftClient.getInstance();
        return saved.load(mc.world, mc.getNetworkHandler()) != null;
    }
    void clear() { saved.clear(); held = false; restored = null; }
}
