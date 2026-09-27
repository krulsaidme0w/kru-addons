package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiActionScopeTest {
    @Test void anyReplacementInvalidatesDisplayedActionTarget() {
        Object world = new Object(), connection = new Object(), screen = new Object(), handler = new Object();
        var scope = new UiActionScope(world, connection, screen, handler);
        assertTrue(scope.matches(world, connection, screen, handler));
        assertFalse(scope.matches(new Object(), connection, screen, handler));
        assertFalse(scope.matches(world, new Object(), screen, handler));
        assertFalse(scope.matches(world, connection, new Object(), handler));
        assertFalse(scope.matches(world, connection, screen, new Object()));
        assertFalse(scope.matches(world, connection, null, handler));
    }
    @Test void keysDistinguishIdentityEvenWhenObjectsCompareEqual() {
        Object world = new Object(), connection = new Object(), screen = new Object();
        var a = new UiActionScope(world, connection, screen, new String("handler"));
        var b = new UiActionScope(world, connection, screen, new String("handler"));
        assertNotEquals(a, b);
        assertNotEquals(a, new UiActionScope(null, connection, screen, null));
    }
    @Test void staleActionsNeverRunButCurrentActionRunsOnce() {
        Object world = new Object(), connection = new Object(), screen = new Object(), handler = new Object();
        var scope = new UiActionScope(world, connection, screen, handler);
        int[] calls = {0};
        assertFalse(scope.runIfCurrent(null, () -> calls[0]++));
        assertFalse(scope.runIfCurrent(new UiActionScope(world, connection, new Object(), handler), () -> calls[0]++));
        assertEquals(0, calls[0]);
        assertTrue(scope.runIfCurrent(new UiActionScope(world, connection, screen, handler), () -> calls[0]++));
        assertEquals(1, calls[0]);
    }
}
