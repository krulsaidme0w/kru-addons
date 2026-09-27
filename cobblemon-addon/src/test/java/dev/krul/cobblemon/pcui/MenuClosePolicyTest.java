package dev.krul.cobblemon.pcui;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuClosePolicyTest {
    @Test void pcCloseMapsLocalToFalseNormalToTrueAndRejectsStaleScreen() throws Exception {
        Object screen = new Object();
        var unlink = new ArrayList<Boolean>();
        assertTrue(PcCloseAction.invoke(screen, screen, true, unlink::add));
        assertTrue(PcCloseAction.invoke(screen, screen, false, unlink::add));
        assertFalse(PcCloseAction.invoke(screen, new Object(), true, unlink::add));
        assertFalse(PcCloseAction.invoke(null, null, true, unlink::add));
        assertEquals(java.util.List.of(false, true), unlink);
    }

    @Test void onlyExactVerifiedScreenAndHandlerPairsAllowLocalRemoval() {
        int[] removals = {0};
        assertFalse(ContainerClosePolicy.removeIfAllowed(SideEffectScreen.class,
            net.minecraft.screen.GenericContainerScreenHandler.class, net.minecraft.inventory.SimpleInventory.class, () -> removals[0]++));
        assertEquals(0, removals[0]);
        assertTrue(ContainerClosePolicy.allows(net.minecraft.client.gui.screen.ingame.GenericContainerScreen.class,
            net.minecraft.screen.GenericContainerScreenHandler.class, net.minecraft.inventory.SimpleInventory.class));
        assertTrue(ContainerClosePolicy.allows(net.minecraft.client.gui.screen.ingame.InventoryScreen.class,
            net.minecraft.screen.PlayerScreenHandler.class, null));
        assertFalse(ContainerClosePolicy.allows(net.minecraft.client.gui.screen.ingame.GenericContainerScreen.class, Object.class, null));
        assertFalse(ContainerClosePolicy.allows(net.minecraft.client.gui.screen.ingame.GenericContainerScreen.class,
            net.minecraft.screen.GenericContainerScreenHandler.class, Object.class));
    }
    private static class SideEffectScreen extends net.minecraft.client.gui.screen.ingame.GenericContainerScreen {
        private SideEffectScreen() { super(null, null, null); }
        @Override public void removed() { throw new AssertionError("Unverified subclass teardown must not run"); }
    }
}
