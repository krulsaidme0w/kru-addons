package dev.krul.builder.bridge;

import dev.krul.builder.model.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BridgeSnapshotPageTest {
    @Test void emitsExactAbsoluteAndRelativeStatesAndPreservesUnknownCells() {
        var bounds = new Bounds(new Cell(4, 8, -2), new Cell(5, 8, -2));
        var known = Map.of(new Cell(4, 8, -2), new StateKey("minecraft:oak_planks", Map.of("axis", "x")));
        var snapshot = new WorldSnapshot(UUID.randomUUID(), 7, bounds, known, Set.of(new Cell(5, 8, -2)));
        String json = BridgeSnapshotPage.encode(snapshot, bounds, 0, 2);
        assertTrue(json.contains("minecraft:oak_planks"));
        assertTrue(json.contains("\"relative\":{\"x\":0,\"y\":0,\"z\":0}"));
        assertTrue(json.contains("\"unknown\":true"));
        assertTrue(json.contains("\"known\":1"));
        assertTrue(json.contains("\"unknown\":1"));
    }

    @Test void rejectsOutOfSelectionAndStaleOrOversizedPages() {
        var bounds = new Bounds(new Cell(0, 0, 0), new Cell(1, 0, 0));
        var snapshot = new WorldSnapshot(UUID.randomUUID(), 1, bounds, Map.of(), Set.of(new Cell(0,0,0), new Cell(1,0,0)));
        assertThrows(IllegalArgumentException.class, () -> BridgeSnapshotPage.encode(snapshot,
            new Bounds(new Cell(-1, 0, 0), new Cell(0, 0, 0)), 0, 10));
        assertThrows(IllegalArgumentException.class, () -> BridgeSnapshotPage.encode(snapshot, bounds, 1, 20));
        assertThrows(IllegalArgumentException.class, () -> BridgeSnapshotPage.encode(snapshot, bounds, 0, 1025));
    }
}
