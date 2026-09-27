package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GuiPacketBufferTest {
    private final Object connection = new Object(), world = new Object();
    private GuiPacketBuffer<String> buffer(int capacity) {
        var b = new GuiPacketBuffer<String>(capacity);
        b.context(connection, world, true);
        return b;
    }

    @Test void delayRetainsEveryClickUntilExplicitFlushAndFlushDoesNotDuplicate() {
        var b = buffer(4);
        b.setDelayEnabled(true);
        assertEquals(GuiPacketBuffer.Disposition.QUEUED, b.intercept("a"));
        b.context(connection, world, true);
        assertEquals(GuiPacketBuffer.Disposition.QUEUED, b.intercept("b"));
        var sent = new ArrayList<String>();
        assertEquals(2, b.queued());
        b.flush(sent::add);
        b.flush(sent::add);
        assertEquals(List.of("a", "b"), sent);
        assertTrue(b.delayEnabled());
    }

    @Test void sendOffDropsNewPacketsButExplicitFlushStillSendsPreviouslyQueuedOnes() {
        var b = buffer(4);
        b.setDelayEnabled(true);
        b.intercept("queued");
        b.setSendEnabled(false);
        assertEquals(GuiPacketBuffer.Disposition.BLOCKED, b.intercept("blocked"));
        var sent = new ArrayList<String>();
        b.flush(sent::add);
        assertEquals(List.of("queued"), sent);
        b.setSendEnabled(true);
        b.setDelayEnabled(false);
        assertEquals(GuiPacketBuffer.Disposition.PASS, b.intercept("normal"));
    }

    @Test void overflowRejectsNewestWithoutEvictingOrReorderingExistingPackets() {
        var b = buffer(2);
        b.setDelayEnabled(true);
        b.intercept("a"); b.intercept("b");
        assertEquals(GuiPacketBuffer.Disposition.FULL, b.intercept("c"));
        var sent = new ArrayList<String>();
        b.flush(sent::add);
        assertEquals(List.of("a", "b"), sent);
    }

    @Test void connectionWorldDisableAndResetDiscardStalePacketsAndRestoreDefaults() {
        for (int change = 0; change < 4; change++) {
            var b = buffer(2);
            b.setDelayEnabled(true); b.intercept("stale"); b.setSendEnabled(false);
            switch (change) {
                case 0 -> b.context(new Object(), world, true);
                case 1 -> b.context(connection, new Object(), true);
                case 2 -> b.context(connection, world, false);
                case 3 -> b.reset();
            }
            var sent = new ArrayList<String>(); b.flush(sent::add);
            assertTrue(sent.isEmpty());
            assertTrue(b.sendEnabled()); assertFalse(b.delayEnabled());
        }
    }

    @Test void clearDiscardsWithoutChangingToggles() {
        var b = buffer(2); b.setDelayEnabled(true); b.intercept("a"); b.clear();
        assertEquals(0, b.queued()); assertTrue(b.delayEnabled());
    }

    @Test void failedDispatchIsNeverRetriedAndRemainingPacketsStayQueued() {
        var b = buffer(2); b.setDelayEnabled(true); b.intercept("a"); b.intercept("b");
        assertThrows(IllegalStateException.class, () -> b.flush(p -> { throw new IllegalStateException(); }));
        var sent = new ArrayList<String>(); b.flush(sent::add);
        assertEquals(List.of("b"), sent);
    }

    @Test void contextChangeDuringFlushDiscardsRemainingPackets() {
        var b = buffer(2); b.setDelayEnabled(true); b.intercept("a"); b.intercept("b");
        var sent = new ArrayList<String>();
        b.flush(p -> { sent.add(p); b.context(new Object(), world, true); });
        assertEquals(List.of("a"), sent);
    }
}
