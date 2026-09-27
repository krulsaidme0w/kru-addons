package dev.krul.builder.context;

import dev.krul.builder.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScanSessionTest {
    private final Object world = new Object(), connection = new Object(), player = new Object();
    private static final StateKey STONE = new StateKey("minecraft:stone", Map.of());

    private ScanSession selected() {
        var session = new ScanSession();
        session.bind(world, connection, player);
        session.setFirst(new Cell(0,64,0));
        session.setSecond(new Cell(2048,64,0));
        return session;
    }

    @Test void keepsLastCompleteSnapshotUntilReplacementPassCompletes() {
        var session = selected();
        session.requestScan(p -> Optional.of(STONE));
        session.tick();
        assertTrue(session.snapshot().isEmpty());
        session.tick();
        var first = session.snapshot().orElseThrow();
        session.requestScan(p -> Optional.empty());
        session.tick();
        assertSame(first, session.snapshot().orElseThrow());
        assertTrue(session.status().contains("Scanning"));
        session.tick();
        var second = session.snapshot().orElseThrow();
        assertEquals(first.session(), second.session());
        assertTrue(second.revision() > first.revision());
        assertEquals(2049, second.unknown().size());
        assertTrue(session.status().contains("unknown=2049"));
        session.tick();
        assertSame(second, session.snapshot().orElseThrow());
    }

    @Test void worldConnectionAndPlayerIdentityChangesAbandonCursorAndEvidence() {
        for (int change = 0; change < 3; change++) {
            var session = selected();
            session.requestScan(p -> Optional.of(STONE));
            session.tick(); session.tick();
            var old = session.snapshot().orElseThrow();
            var reads = new ArrayList<Cell>();
            session.requestScan(p -> { reads.add(p); return Optional.of(STONE); });
            session.tick();
            session.bind(change == 0 ? new Object() : world,
                change == 1 ? new Object() : connection, change == 2 ? new Object() : player);
            session.tick();
            assertEquals(2048, reads.size());
            assertTrue(session.snapshot().isEmpty());
            assertTrue(session.bounds().isEmpty());
            session.setFirst(new Cell(3,4,5)); session.setSecond(new Cell(3,4,5));
            session.requestScan(p -> Optional.of(STONE)); session.tick();
            assertNotEquals(old.session(), session.snapshot().orElseThrow().session());
        }
    }

    @Test void unchangedIdentityPreservesCursorButExplicitResetDiscardsIt() {
        var session = selected();
        session.requestScan(p -> Optional.of(STONE)); session.tick();
        session.bind(world, connection, player);
        session.tick();
        assertEquals(2049, session.snapshot().orElseThrow().known().size());
        session.reset();
        assertTrue(session.snapshot().isEmpty());
        assertTrue(session.bounds().isEmpty());
    }

    @Test void selectionChangesAndDisconnectDiscardPendingWork() {
        var session = selected();
        session.requestScan(p -> Optional.of(STONE)); session.tick();
        session.setSecond(new Cell(0,64,0)); session.tick();
        assertTrue(session.snapshot().isEmpty());
        session.requestScan(p -> Optional.of(STONE)); session.tick();
        assertEquals(1, session.snapshot().orElseThrow().known().size());
        session.clearSelection();
        assertTrue(session.snapshot().isEmpty());
        assertTrue(session.bounds().isEmpty());
        session.bind(null, null, null);
        session.requestScan(p -> { fail("Disconnected scan must not read"); return Optional.empty(); });
        session.tick();
        assertTrue(session.snapshot().isEmpty());
        assertTrue(session.status().contains("No world"));
    }

    @Test void reportsIncompleteOversizeAndOverflowWithoutReading() {
        var session = new ScanSession();
        session.bind(world, connection, player);
        WorldReader never = p -> { fail("Invalid selection must not read"); return Optional.empty(); };
        session.requestScan(never);
        assertTrue(session.status().contains("Incomplete"));
        session.setFirst(new Cell(0,0,0)); session.setSecond(new Cell(32768,0,0));
        session.requestScan(never); session.tick();
        assertTrue(session.status().contains("Oversize"));
        session.setFirst(new Cell(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE));
        session.setSecond(new Cell(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE));
        session.requestScan(never); session.tick();
        assertTrue(session.status().contains("Oversize"));
    }
}
