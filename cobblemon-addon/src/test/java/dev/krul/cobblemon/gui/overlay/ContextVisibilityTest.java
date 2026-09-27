package dev.krul.cobblemon.gui.overlay;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContextVisibilityTest {
    @Test void dismissedPanelStaysDismissedUntilNewContext() {
        var visibility = new ContextVisibility();
        visibility.update("battle-1");
        assertTrue(visibility.visible());
        visibility.hide();
        visibility.update(new String("battle-1"));
        assertFalse(visibility.visible());
        visibility.update("battle-2");
        assertTrue(visibility.visible());
    }

    @Test void absentContextCannotBeShownManuallyAndReentryShowsIt() {
        var visibility = new ContextVisibility();
        visibility.update("pc");
        visibility.update(null);
        assertFalse(visibility.visible(), "Ending a context must hide it before any manual action");
        visibility.show();
        assertFalse(visibility.visible());
        visibility.update("pc");
        assertTrue(visibility.visible());
    }

    @Test void resetDropsOldDismissalAndToggleCanRestoreWithinSameContext() {
        var visibility = new ContextVisibility();
        visibility.update("menu");
        visibility.hide();
        visibility.show();
        assertTrue(visibility.visible());
        visibility.hide();
        visibility.reset();
        assertFalse(visibility.visible());
        visibility.update("menu");
        assertTrue(visibility.visible());
    }
}
