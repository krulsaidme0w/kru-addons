package dev.krul.cobblemon.pcui;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;

/** Exact pinned implementations only: arbitrary subclasses can add network teardown. */
final class ContainerClosePolicy {
    private ContainerClosePolicy() {}
    static boolean allows(Class<?> screen, Class<?> handler, Class<?> inventory) {
        return screen == InventoryScreen.class && handler == PlayerScreenHandler.class
            || screen == GenericContainerScreen.class && handler == GenericContainerScreenHandler.class && inventory == SimpleInventory.class;
    }
    static boolean removeIfAllowed(Class<?> screen, Class<?> handler, Class<?> inventory, Runnable remove) {
        if (!allows(screen, handler, inventory)) return false;
        remove.run();
        return true;
    }
}
