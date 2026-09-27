package dev.krul.builder.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldSnapshotTest {
    private final UUID session = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final Cell origin = new Cell(0, 0, 0);
    private final Cell end = new Cell(1, 0, 0);
    private final Bounds bounds = new Bounds(origin, end);
    private final StateKey air = new StateKey("minecraft:air", Map.of());

    @Test
    void copiesKnownAndUnknownWithoutConvertingUnknownToAir() {
        var known = new HashMap<>(Map.of(origin, air));
        var unknown = new HashSet<>(Set.of(end));
        var snapshot = new WorldSnapshot(session, 7, bounds, known, unknown);
        known.clear();
        unknown.clear();
        assertEquals(Map.of(new Cell(0, 0, 0), air), snapshot.known());
        assertEquals(Set.of(new Cell(1, 0, 0)), snapshot.unknown());
        assertFalse(snapshot.known().containsKey(end));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.known().put(end, air));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.unknown().add(origin));
        assertEquals(session, snapshot.session());
        assertEquals(7, snapshot.revision());
        assertEquals(bounds, snapshot.bounds());
    }

    @Test
    void rejectsConflictingKnownAndUnknownEvidence() {
        assertThrows(IllegalArgumentException.class,
            () -> new WorldSnapshot(session, 1, bounds, Map.of(origin, air), Set.of(origin)));
    }

    @Test
    void rejectsOutOfBoundsKnownAndUnknownCells() {
        var outside = new Cell(2, 0, 0);
        assertThrows(IllegalArgumentException.class,
            () -> new WorldSnapshot(session, 1, bounds, Map.of(outside, air), Set.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new WorldSnapshot(session, 1, bounds, Map.of(), Set.of(outside)));
    }

    @Test
    void omittedCellsDoNotBecomeKnownAir() {
        var snapshot = new WorldSnapshot(session, 0, bounds, Map.of(), Set.of());
        assertTrue(snapshot.known().isEmpty());
        assertTrue(snapshot.unknown().isEmpty());
    }

    @Test
    void rejectsMissingSnapshotComponentsOrNullEvidence() {
        assertThrows(NullPointerException.class, () -> new WorldSnapshot(null, 0, bounds, Map.of(), Set.of()));
        assertThrows(NullPointerException.class, () -> new WorldSnapshot(session, 0, null, Map.of(), Set.of()));
        assertThrows(NullPointerException.class, () -> new WorldSnapshot(session, 0, bounds, null, Set.of()));
        assertThrows(NullPointerException.class, () -> new WorldSnapshot(session, 0, bounds, Map.of(), null));
        var badKnown = new HashMap<Cell, StateKey>();
        badKnown.put(origin, null);
        assertThrows(NullPointerException.class, () -> new WorldSnapshot(session, 0, bounds, badKnown, Set.of()));
        badKnown.clear();
        badKnown.put(null, air);
        assertThrows(NullPointerException.class, () -> new WorldSnapshot(session, 0, bounds, badKnown, Set.of()));
        var badUnknown = new HashSet<Cell>();
        badUnknown.add(null);
        assertThrows(NullPointerException.class, () -> new WorldSnapshot(session, 0, bounds, Map.of(), badUnknown));
    }
}
