package dev.krul.builder.context;

import dev.krul.builder.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScanCursorTest {
    private static final StateKey AIR = new StateKey("minecraft:air", Map.of());

    @Test void publishesOnlyAfterEveryAbsoluteCellIsReadOnce() {
        var bounds = new Bounds(new Cell(-2, 64, 7), new Cell(-1, 65, 8));
        var reads = new ArrayList<Cell>();
        var session = UUID.randomUUID();
        var scan = new ScanCursor(session, 9, bounds, cell -> {
            assertTrue(bounds.contains(cell), "Reader must never receive out-of-bounds positions");
            reads.add(cell);
            return cell.equals(new Cell(-1, 65, 8)) ? Optional.empty() : Optional.of(AIR);
        });
        assertTrue(scan.step(3).isEmpty());
        assertEquals(3, reads.size());
        assertTrue(scan.step(4).isEmpty());
        assertEquals(7, reads.size());
        var result = scan.step(3).orElseThrow();
        assertEquals(8, reads.size());
        assertEquals(8, new HashSet<>(reads).size());
        assertEquals(session, result.session());
        assertEquals(9, result.revision());
        assertEquals(bounds, result.bounds());
        assertEquals(Set.of(new Cell(-1, 65, 8)), result.unknown());
        assertEquals(Set.of(new Cell(-2,64,7), new Cell(-1,64,7),
            new Cell(-2,65,7), new Cell(-1,65,7), new Cell(-2,64,8),
            new Cell(-1,64,8), new Cell(-2,65,8)), result.known().keySet());
        assertTrue(result.known().values().stream().allMatch(AIR::equals));
        assertSame(result, scan.step(100).orElseThrow());
        assertEquals(8, reads.size(), "Completed scan must not read again");
    }

    @Test void preservesAllUnavailableCells() {
        var scan = new ScanCursor(UUID.randomUUID(), 1,
            new Bounds(new Cell(0,0,0), new Cell(1,0,0)), p -> Optional.empty());
        assertTrue(scan.step(1).isEmpty());
        var result = scan.step(1).orElseThrow();
        assertEquals(Set.of(new Cell(0,0,0), new Cell(1,0,0)), result.unknown());
        assertTrue(result.known().isEmpty());
    }

    @Test void capsEvenAnOversizedBudgetAt2048Reads() {
        var reads = new ArrayList<Cell>();
        var scan = new ScanCursor(UUID.randomUUID(), 1,
            new Bounds(new Cell(0,0,0), new Cell(2048,0,0)), p -> {
                reads.add(p); return Optional.of(AIR);
            });
        assertTrue(scan.step(Integer.MAX_VALUE).isEmpty());
        assertEquals(2048, reads.size());
        assertEquals(2049, scan.step(1).orElseThrow().known().size());
    }

    @Test void rejectsNegativeBudgetsAndZeroDoesNotAdvance() {
        var reads = new ArrayList<Cell>();
        var scan = new ScanCursor(UUID.randomUUID(), 1,
            new Bounds(new Cell(0,0,0), new Cell(0,0,0)), p -> {
                reads.add(p); return Optional.of(AIR);
            });
        assertThrows(IllegalArgumentException.class, () -> scan.step(-1));
        assertTrue(scan.step(0).isEmpty());
        assertTrue(reads.isEmpty());
        assertEquals(1, scan.step(1).orElseThrow().known().size());
    }

    @Test void rejectsOversizeAndOverflowingVolumesBeforeReading() {
        WorldReader never = p -> { fail("Rejected selection must not read"); return Optional.empty(); };
        assertThrows(IllegalArgumentException.class, () -> new ScanCursor(UUID.randomUUID(), 1,
            new Bounds(new Cell(0,0,0), new Cell(32768,0,0)), never));
        assertThrows(IllegalArgumentException.class, () -> new ScanCursor(UUID.randomUUID(), 1,
            new Bounds(new Cell(Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE),
                new Cell(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE)), never));
    }

    @Test void acceptsCapAndDoesNotWrapAtIntegerCoordinateLimits() {
        var scan = new ScanCursor(UUID.randomUUID(), 1,
            new Bounds(new Cell(Integer.MAX_VALUE-32767, Integer.MIN_VALUE, Integer.MAX_VALUE),
                new Cell(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE)), p -> Optional.of(AIR));
        for (int i = 0; i < 15; i++) assertTrue(scan.step(2048).isEmpty());
        var result = scan.step(2048).orElseThrow();
        assertEquals(32768, result.known().size());
        assertTrue(result.known().containsKey(new Cell(Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE)));
    }
}
