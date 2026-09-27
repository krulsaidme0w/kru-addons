package dev.krul.cobblemon.pcui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/** Default read-only/container session support. No server-specific menu actions. */
final class ContainerMenuAdapter implements MenuAdapter {
    @Override public Object target(MinecraftClient client, Screen screen) {
        if (!(screen instanceof HandledScreen<?> handled) || client.player == null) return null;
        var handler = handled.getScreenHandler();
        return client.player.currentScreenHandler == handler ? handler : null;
    }

    @Override public String describe(Screen screen) {
        var handled = (HandledScreen<?>) screen;
        return "Container | " + handled.getScreenHandler().slots.size() + " slots";
    }

    @Override public boolean canCloseLocal(Screen screen) {
        return screen instanceof HandledScreen<?> handled
            && ContainerClosePolicy.allows(screen.getClass(), handled.getScreenHandler().getClass(), inventoryClass(handled));
    }

    private static Class<?> inventoryClass(HandledScreen<?> screen) {
        return screen.getScreenHandler() instanceof net.minecraft.screen.GenericContainerScreenHandler handler
            ? handler.getInventory().getClass() : null;
    }

    @Override public boolean close(Screen screen, boolean local) {
        var client = MinecraftClient.getInstance();
        if (client.currentScreen != screen || target(client, screen) == null) return false;
        if (local) {
            var handled = (HandledScreen<?>) screen;
            return ContainerClosePolicy.removeIfAllowed(screen.getClass(), handled.getScreenHandler().getClass(), inventoryClass(handled),
                () -> client.setScreen(null));
        }
        screen.close();
        return true;
    }

    @Override public boolean endSession(Screen screen) {
        var client = MinecraftClient.getInstance();
        if (target(client, screen) == null) return false;
        if (client.currentScreen == screen) screen.close();
        else if (client.currentScreen == null || dev.krul.cobblemon.gui.overlay.OverlayHost.isInteractionScreen(client.currentScreen))
            client.player.closeHandledScreen();
        else return false;
        return true;
    }
}
