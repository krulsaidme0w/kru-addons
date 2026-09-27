package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiMenuSessionTest {
    private final Object world = new Object(), connection = new Object(), screen = new Object(), target = new Object();
    private final UiActionScope scope = new UiActionScope(world, connection, screen, target);
    private final UiMenuSession session = new UiMenuSession();
    private UiActionScope open() { return session.observe(world, connection, screen, scope, true, false, 0); }

    @Test void onlyExplicitDetachKeepsLiveTargetWithoutScreen() {
        assertSame(scope, open());
        assertNull(session.observe(world, connection, null, null, true, false, 0));
        open();
        assertTrue(session.detach(scope));
        assertSame(scope, session.observe(world, connection, null, null, true, false, 0));
        assertTrue(session.detached());
    }
    @Test void detachedTargetLossOrServerClosureInvalidatesActions() {
        open(); session.detach(scope);
        assertNull(session.observe(world, connection, null, null, false, false, 0));
        open(); session.detach(scope);
        assertNull(session.observe(world, connection, null, null, true, false, 1));
    }
    @Test void exitSuppressesSameMenuUntilANewMenuArrives() {
        open(); session.exit(screen);
        assertNull(open());
        Object nextScreen = new Object();
        var next = new UiActionScope(world, connection, nextScreen, new Object());
        assertSame(next, session.observe(world, connection, nextScreen, next, true, false, 0));
    }
    @Test void replacementNeverAuthorizesOldScopeEvenWithSameTitleOrTarget() {
        open(); session.detach(scope);
        Object replacement = new Object();
        var next = new UiActionScope(world, connection, replacement, target);
        assertSame(next, session.observe(world, connection, replacement, next, true, false, 0));
        assertFalse(session.matches(scope));
        assertFalse(session.detached());
    }
    @Test void unrelatedScreensDisconnectAndWorldChangeInvalidate() {
        open(); session.detach(scope);
        assertNull(session.observe(world, connection, new Object(), null, true, false, 0));
        open(); session.detach(scope);
        assertNull(session.observe(new Object(), connection, null, null, true, false, 0));
        open(); session.detach(scope);
        assertNull(session.observe(world, new Object(), null, null, true, false, 0));
        open();
        assertNull(session.observe(null, null, null, null, false, false, 0));
    }
    @Test void onlyOwnedActionTransitionMayTemporarilyReplaceRootHandler() {
        open();
        assertSame(scope, session.observe(world, connection, new Object(), null, false, true, 0));
        assertNull(session.observe(world, connection, new Object(), null, false, false, 0));
    }
    @Test void revokedVisibleSessionDoesNotRearmFromOldFrame() {
        open();
        assertNull(session.observe(world, connection, screen, scope, true, false, 1));
        assertNull(session.observe(world, connection, screen, scope, true, false, 1));
    }
    @Test void serverRevisionDoesNotSuppressANewNativeMenu() {
        open();
        var newScreen = new Object();
        var next = new UiActionScope(world, connection, newScreen, new Object());
        assertSame(next, session.observe(world, connection, newScreen, next, false, false, 1));
        assertFalse(session.matches(scope));
    }
    @Test void oneClosureCanOnlyAccompanyANewProvenNonNullHandoff() {
        open();
        Object picker = new Object();
        assertSame(scope, session.observe(world, connection, picker, null, false, true, 1, true));
        assertNull(session.observe(world, connection, picker, null, false, true, 2, true));
    }
    @Test void ownedGapAndMultipleClosuresNeverKeepAuthorization() {
        open(); session.detach(scope);
        assertNull(session.observe(world, connection, null, null, false, true, 1, true));
        open();
        assertNull(session.observe(world, connection, new Object(), null, false, true, 2, true));
    }
    @Test void unapprovedClosureCannotBeHiddenByAnOwnedTransition() {
        open();
        assertNull(session.observe(world, connection, new Object(), null, false, true, 1, false));
    }
}
