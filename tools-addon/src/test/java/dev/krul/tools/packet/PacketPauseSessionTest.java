package dev.krul.tools.packet;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static dev.krul.tools.packet.PacketPauseSession.Result.*;
import static org.junit.jupiter.api.Assertions.*;

class PacketPauseSessionTest {
    private final Object connection = new Object();
    private final Object world = new Object();
    private final Object player = new Object();
    private PacketPauseSession<String> bound(int capacity) {
        var q = new PacketPauseSession<String>(capacity);
        q.bindPlayer(connection, world, player);
        return q;
    }
    @Test void onlySelectedPacketsAreHeldAndReleasedInOrder() {
        var q = bound(3);
        assertEquals(BYPASS, q.holdFrom(connection, "keepalive", false));
        assertEquals(HELD, q.holdFrom(connection, "first", true));
        assertEquals(HELD, q.holdFrom(connection, "second", true));
        List<String> sent = new ArrayList<>(); q.flushTo(connection, sent::add);
        assertEquals(List.of("first", "second"), sent);
        q.flushTo(connection, sent::add); assertEquals(2, sent.size());
    }
    @Test void disconnectedAndChangedWorldCannotReplayOldPackets() {
        var q = new PacketPauseSession<String>(3);
        assertEquals(BYPASS, q.holdFrom(connection, "offline", true));
        q.bindPlayer(connection, world, player); q.holdFrom(connection, "old", true);
        q.bindPlayer(connection, new Object(), player);
        List<String> sent = new ArrayList<>(); q.flushTo(connection, sent::add); assertTrue(sent.isEmpty());
        q.holdFrom(connection, "new", true); q.bindPlayer(null, null, null);
        q.flushTo(connection, sent::add); assertTrue(sent.isEmpty()); assertEquals(0, q.size());
    }
    @Test void sameSessionPreservesQueueButNewConnectionClearsIt() {
        var q = bound(3); q.holdFrom(connection, "first", true); q.bindPlayer(connection, world, player);
        List<String> sent = new ArrayList<>(); q.flushTo(connection, sent::add); assertEquals(List.of("first"), sent);
        q.holdFrom(connection, "stale", true); q.bindPlayer(new Object(), world, player);
        assertEquals(0, q.size());
    }
    @Test void capacityDoesNotSilentlyDropExistingQueue() {
        var q = bound(1);
        assertEquals(HELD, q.holdFrom(connection, "first", true)); assertEquals(FULL, q.holdFrom(connection, "second", true));
        List<String> sent = new ArrayList<>(); q.flushTo(connection, sent::add); assertEquals(List.of("first"), sent);
    }
    @Test void flushBypassesRecaptureAndResetsAfterSendFailure() {
        var q = bound(3); q.holdFrom(connection, "first", true); q.holdFrom(connection, "second", true);
        assertThrows(IllegalStateException.class, () -> q.flushTo(connection, packet -> {
            assertEquals(BYPASS, q.holdFrom(connection, packet, true));
            throw new IllegalStateException("closed channel");
        }));
        List<String> sent = new ArrayList<>(); q.flushTo(connection, sent::add); assertTrue(sent.isEmpty());
        assertEquals(HELD, q.holdFrom(connection, "fresh", true));
    }
    @Test void packetFromOldConnectionCannotEnterCurrentQueue() {
        var q = bound(3);
        assertEquals(BYPASS, q.holdFrom(new Object(), "stale", true));
        assertEquals(HELD, q.holdFrom(connection, "fresh", true));
        List<String> sent = new ArrayList<>(); q.flushTo(connection, sent::add); assertEquals(List.of("fresh"), sent);
    }
    @Test void respawnBlocksRefillUntilPlayerReplacementEvenInSameWorld() {
        var q = bound(3); q.holdFrom(connection, "before", true);
        q.respawn(connection); q.bindPlayer(connection, world, player);
        assertEquals(BYPASS, q.holdFrom(connection, "during", true));
        List<String> sent = new ArrayList<>(); q.flushTo(connection, sent::add); assertTrue(sent.isEmpty());
        q.bindPlayer(connection, world, new Object());
        assertEquals(HELD, q.holdFrom(connection, "after", true)); q.flushTo(connection, sent::add);
        assertEquals(List.of("after"), sent);
    }
    @Test void staleFlushCannotSendQueueThroughDifferentConnection() {
        var q = bound(3); q.holdFrom(connection, "fresh", true);
        List<String> sent = new ArrayList<>(); q.flushTo(new Object(), sent::add);
        assertTrue(sent.isEmpty()); q.flushTo(connection, sent::add); assertEquals(List.of("fresh"), sent);
    }
}
