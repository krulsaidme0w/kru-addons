package dev.krul.cobblemon.pcui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import java.util.List;

/** Live adapter selection, with no saved screens, slots, or server-name heuristics. */
final class MenuAccess {
    record Current(UiActionScope scope, Screen screen, MenuAdapter adapter) {}
    private final List<MenuAdapter> adapters = List.of(new PcMenuAdapter(), new ContainerMenuAdapter());
    Current current() {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.getNetworkHandler() == null || client.currentScreen == null) return null;
        for (var adapter : adapters) {
            Object target = adapter.target(client, client.currentScreen);
            if (target != null) return new Current(new UiActionScope(client.world, client.getNetworkHandler(), client.currentScreen, target),
                client.currentScreen, adapter);
        }
        return null;
    }
}
