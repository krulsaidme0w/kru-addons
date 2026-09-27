package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiToolsStateTest {
    @Test void chatAndCommandsKeepTheirTransportDistinction() {
        var sent = new java.util.ArrayList<String>();
        var state = new UiToolsState(value -> { sent.add(value); return true; });
        state.line.focus(); state.line.insert("/gts"); state.line.submit();
        state.line.insert("hello world"); state.line.submit();
        assertEquals(java.util.List.of("/gts", "hello world"), sent);
    }
    @Test void menuChangeKeepsUnsentDraftButBlursAndConnectionChangeClearsIt() {
        var state = new UiToolsState(command -> { fail("context changes cannot send commands"); return false; });
        Object world = new Object(), connection = new Object();
        state.update(new UiActionScope(world, connection, new Object(), new Object()));
        state.line.focus();
        state.line.insert("/daycare");
        state.update(new UiActionScope(world, connection, new Object(), new Object()));
        assertEquals("/daycare", state.line.text());
        assertFalse(state.line.focused());
        state.update(new UiActionScope(world, new Object(), new Object(), new Object()));
        assertEquals("", state.line.text());
    }
    @Test void unchangedContextDoesNotStealEditorFocus() {
        var state = new UiToolsState(command -> false);
        var scope = new UiActionScope(new Object(), new Object(), new Object(), new Object());
        state.update(scope);
        state.line.focus();
        state.update(new UiActionScope(scope.world(), scope.connection(), scope.screen(), scope.target()));
        assertTrue(state.line.focused());
        state.reset();
        assertNull(state.scope());
        assertFalse(state.line.focused());
    }
}
