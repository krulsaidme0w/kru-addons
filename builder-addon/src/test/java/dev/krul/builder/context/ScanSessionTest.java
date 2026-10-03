package dev.krul.builder.context;

import dev.krul.builder.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

    @Test void automaticScanStartsOnceAndStableTicksDoNotRescan() throws Exception {
        var session = new ScanSession();
        session.bind(world, connection, player);
        session.setFirst(new Cell(4, 64, -3));
        session.setSecond(new Cell(4, 64, -3));
        assertTrue(ensureScan(session, p -> Optional.of(STONE)));
        assertFalse(ensureScan(session, p -> { fail("An active scan must not be restarted"); return Optional.empty(); }));
        session.tick();
        assertEquals(1, session.snapshot().orElseThrow().known().size());
        assertFalse(ensureScan(session, p -> { fail("A completed snapshot must not be rescanned on stable ticks"); return Optional.empty(); }));
    }

    @Test void automaticScanWaitsForReadyWorldAndCompleteBounds() throws Exception {
        var session = new ScanSession();
        assertFalse(ensureScan(session, p -> { fail("No world must not be scanned"); return Optional.empty(); }));
        session.bind(world, connection, player);
        session.setFirst(new Cell(4, 64, -3));
        assertFalse(ensureScan(session, p -> { fail("Incomplete bounds must not be scanned"); return Optional.empty(); }));
    }

    @Test void automaticScanWaitsUntilEverySelectedChunkIsReady() throws Exception {
        var session = new ScanSession();
        session.bind(world, connection, player);
        session.setFirst(new Cell(-1, 64, -1));
        session.setSecond(new Cell(-1, 64, -1));
        var reader = new ReadinessReader(false, true);

        assertFalse(ensureScan(session, reader));
        assertEquals(0, reader.reads);
        assertTrue(session.status().contains("Waiting for selected chunks"));

        reader.chunksReady = true;
        assertTrue(ensureScan(session, reader));
        session.tick();
        assertEquals(1, session.snapshot().orElseThrow().known().size());
    }

    @Test void automaticallyRetriesAReadThatBecameUnknownDuringScan() throws Exception {
        var session = new ScanSession();
        session.bind(world, connection, player);
        session.setFirst(new Cell(4, 64, -3));
        session.setSecond(new Cell(4, 64, -3));
        var reader = new ReadinessReader(true, false);

        assertTrue(ensureScan(session, reader));
        session.tick();
        assertTrue(session.snapshot().isEmpty(), "An all-unknown pass is not a complete world snapshot");
        assertTrue(session.status().contains("retry"));

        reader.readable = true;
        for (int i = 0; i < 20; i++) {
            assertFalse(ensureScan(session, reader));
            session.tick();
        }
        assertTrue(ensureScan(session, reader));
        session.tick();
        assertEquals(1, session.snapshot().orElseThrow().known().size());
    }

    private static final class ReadinessReader implements WorldReader {
        private boolean chunksReady;
        private boolean readable;
        private int reads;

        private ReadinessReader(boolean chunksReady, boolean readable) {
            this.chunksReady = chunksReady;
            this.readable = readable;
        }

        @Override public boolean isReady(Bounds bounds) { return chunksReady; }
        @Override public Optional<StateKey> read(Cell cell) {
            reads++;
            return readable ? Optional.of(STONE) : Optional.empty();
        }
    }

    private static boolean ensureScan(ScanSession session, WorldReader reader) throws Exception {
        Method method = assertDoesNotThrow(() -> ScanSession.class.getMethod("ensureScan", WorldReader.class));
        try { return (boolean) method.invoke(session, reader); }
        catch (InvocationTargetException failure) { throw new AssertionError(failure.getCause()); }
    }

    @Test void keepsLastCompleteSnapshotUntilReplacementPassCompletes() {
        var session = selected();
        session.requestScan(p -> Optional.of(STONE));
        session.tick();
        assertTrue(session.snapshot().isEmpty());
        session.tick();
        var first = session.snapshot().orElseThrow();
        boolean[] readable = {false};
        WorldReader retryingReader = p -> readable[0] ? Optional.of(STONE) : Optional.empty();
        session.requestScan(retryingReader);
        session.tick();
        assertSame(first, session.snapshot().orElseThrow());
        assertTrue(session.status().contains("Scanning"));
        session.tick();
        assertTrue(session.snapshot().isEmpty(), "An incomplete replacement must not be published as a complete snapshot");
        assertTrue(session.status().contains("retry"));
        readable[0] = true;
        for (int i = 0; i < 20; i++) {
            assertFalse(session.ensureScan(retryingReader));
            session.tick();
        }
        assertTrue(session.ensureScan(retryingReader));
        session.tick(); session.tick();
        var second = session.snapshot().orElseThrow();
        assertEquals(first.session(), second.session());
        assertTrue(second.revision() > first.revision());
        assertTrue(second.unknown().isEmpty());
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
