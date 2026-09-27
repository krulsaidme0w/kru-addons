package dev.krul.cobblemon.pcui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** PCGUI is a plain Screen: vanilla sync IDs and HandledScreen.close do not apply. */
final class PcMenuAdapter implements MenuAdapter {
    private static final String PC_GUI = "com.cobblemon.mod.common.client.gui.pc.PCGUI";
    public Object target(MinecraftClient client, Screen screen) {
        if (screen == null || !screen.getClass().getName().equals(PC_GUI)) return null;
        return PcReflection.get(screen, "getPc");
    }
    public String describe(Screen screen) { return "Cobblemon PC"; }
    public boolean canCloseLocal(Screen screen) {
        try {
            return screen.getClass().getName().equals(PC_GUI)
                && screen.getClass().getMethod("closeNormally", boolean.class).getReturnType() == void.class;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return false; }
    }
    public boolean close(Screen screen, boolean local) {
        var client = MinecraftClient.getInstance();
        if (client.currentScreen != screen || target(client, screen) == null || !canCloseLocal(screen)) return false;
        try {
            return PcCloseAction.invoke(screen, client.currentScreen, local,
                unlink -> screen.getClass().getMethod("closeNormally", boolean.class).invoke(screen, unlink));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return false; }
    }
    public boolean endSession(Screen screen) {
        var client = MinecraftClient.getInstance();
        if (target(client, screen) == null || !canCloseLocal(screen)) return false;
        if (client.currentScreen != screen && client.currentScreen != null
            && !dev.krul.cobblemon.gui.overlay.OverlayHost.isInteractionScreen(client.currentScreen)) return false;
        try {
            screen.getClass().getMethod("closeNormally", boolean.class).invoke(screen, true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return false; }
    }
}
