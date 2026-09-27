package dev.krul.builder.blueprint;

import dev.krul.builder.model.*;
import dev.krul.builder.project.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintDiffTest {
    private static final StateKey AIR = new StateKey("minecraft:air", Map.of());
    private static final StateKey STONE = new StateKey("minecraft:stone", Map.of());
    private static final Bounds BOUNDS = new Bounds(new Cell(10, 64, -5), new Cell(13, 65, -5));
    private static BuildPlan plan(BuildStage... stages) { return new BuildPlan(UUID.randomUUID(), 1, 1, List.of(stages)); }
    private static BuildStage stage(String id, Map<Cell, StateKey> targets) { return new BuildStage(id, id, Set.of(), targets); }
    private static WorldSnapshot world(Map<Cell, StateKey> known, Set<Cell> unknown) {
        return new WorldSnapshot(UUID.randomUUID(), 2, BOUNDS, known, unknown);
    }

    @Test void fullPropertiesDistinguishReplacementFromCompletion() {
        var east = new StateKey("minecraft:oak_stairs", Map.of("facing", "east", "half", "bottom", "shape", "straight", "waterlogged", "false"));
        var west = new StateKey(east.id(), Map.of("facing", "west", "half", "bottom", "shape", "straight", "waterlogged", "false"));
        assertNotEquals(east, west);
        var plan = plan(stage("stairs", Map.of(new Cell(0, 0, 0), east)));
        assertEquals(new Progress(0, 0, 1, 0), BlueprintDiff.compare(plan, world(Map.of(BOUNDS.min(), west), Set.of())));
        assertEquals(new Progress(1, 0, 0, 0), BlueprintDiff.compare(plan, world(Map.of(BOUNDS.min(), east), Set.of())));
    }

    @Test void unknownAndOmittedCellsNeverBecomeAirOrCompleted() {
        var plan = plan(stage("a", Map.of(new Cell(0, 0, 0), AIR, new Cell(1, 0, 0), STONE)));
        var snapshot = world(Map.of(), Set.of(BOUNDS.min()));
        assertEquals(new Progress(0, 0, 0, 2), BlueprintDiff.compare(plan, snapshot));
        assertTrue(BlueprintDiff.cells(plan, snapshot, BlueprintDiff.Selection.ALL).stream()
            .allMatch(cell -> cell.kind() == BlueprintDiff.Kind.UNKNOWN));
    }

    @Test void airRemovalReplacementPlacementAndMatchStayDistinct() {
        var plan = plan(stage("a", Map.of(new Cell(0, 0, 0), AIR, new Cell(1, 0, 0), STONE,
            new Cell(2, 0, 0), STONE, new Cell(3, 0, 0), AIR)));
        var snapshot = world(Map.of(BOUNDS.min(), STONE, new Cell(11, 64, -5), AIR,
            new Cell(12, 64, -5), STONE, new Cell(13, 64, -5), AIR), Set.of());
        assertEquals(new Progress(2, 1, 1, 0), BlueprintDiff.compare(plan, snapshot));
        var cells = BlueprintDiff.cells(plan, snapshot, BlueprintDiff.Selection.ALL);
        assertEquals(List.of(BlueprintDiff.Kind.REMOVE, BlueprintDiff.Kind.PLACE, BlueprintDiff.Kind.MATCHING,
            BlueprintDiff.Kind.MATCHING), cells.stream().map(BlueprintDiff.Difference::kind).toList());
        assertEquals(BOUNDS.min(), cells.getFirst().absolute());
        assertThrows(UnsupportedOperationException.class, () -> cells.clear());
    }

    @Test void stageAndRelativeLayerIntersectWithoutChangingOverallProgressOrCountingDuplicates() {
        var low = new Cell(0, 0, 0); var high = new Cell(0, 1, 0);
        var plan = plan(stage("base", Map.of(low, STONE)), stage("roof", Map.of(low, STONE, high, STONE)));
        var snapshot = world(Map.of(BOUNDS.min(), STONE, new Cell(10, 65, -5), AIR), Set.of());
        assertEquals(new Progress(1, 1, 0, 0), BlueprintDiff.compare(plan, snapshot));
        var selected = BlueprintDiff.cells(plan, snapshot, new BlueprintDiff.Selection("roof", 1));
        assertEquals(1, selected.size());
        assertEquals(new Cell(10, 65, -5), selected.getFirst().absolute());
        assertEquals(BlueprintDiff.Kind.PLACE, selected.getFirst().kind());
        assertTrue(BlueprintDiff.cells(plan, snapshot, new BlueprintDiff.Selection("base", 1)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> BlueprintDiff.cells(plan, snapshot, new BlueprintDiff.Selection("missing", null)));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintDiff.Selection(null, -1));
    }

    @Test void unvalidatedOversizedOrConflictingInputIsRejectedBeforeDiffing() {
        var many = new HashMap<Cell, StateKey>();
        for (int x = 0; x <= PlanValidator.MAX_TARGETS; x++) many.put(new Cell(x, 0, 0), STONE);
        assertThrows(IllegalArgumentException.class, () -> BlueprintDiff.compare(plan(stage("big", many)), world(Map.of(), Set.of())));
        var conflict = plan(stage("a", Map.of(new Cell(0, 0, 0), STONE)), stage("b", Map.of(new Cell(0, 0, 0), AIR)));
        assertThrows(IllegalArgumentException.class, () -> BlueprintDiff.compare(conflict, world(Map.of(), Set.of())));
    }
}
