package dev.krul.builder.project;

import dev.krul.builder.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanValidatorTest {
    static final UUID PROJECT = UUID.randomUUID();
    static final StateKey STONE = new StateKey("minecraft:stone", Map.of());
    static final StateKey AIR = new StateKey("minecraft:air", Map.of());
    static final Bounds BOUNDS = new Bounds(new Cell(100, 64, -20), new Cell(103, 65, -19));
    static BuildStage stage(String id, Set<String> after, Map<Cell, StateKey> targets) {
        return new BuildStage(id, id, after, targets);
    }
    static BuildPlan plan(BuildStage... stages) { return new BuildPlan(PROJECT, 1, 7, List.of(stages)); }
    private Set<PlanValidator.Code> codes(BuildPlan plan) {
        var codes = new HashSet<PlanValidator.Code>();
        PlanValidator.diagnose(plan, BOUNDS, STONE::equals).forEach(d -> codes.add(d.code()));
        return codes;
    }

    @Test void rejectsDuplicateIdsMissingDependenciesAndCycles() {
        assertTrue(codes(plan(stage("a", Set.of(), Map.of()), stage("a", Set.of(), Map.of())))
            .contains(PlanValidator.Code.DUPLICATE_STAGE));
        assertTrue(codes(plan(stage("a", Set.of("absent"), Map.of())))
            .contains(PlanValidator.Code.MISSING_DEPENDENCY));
        assertTrue(codes(plan(stage("a", Set.of("b"), Map.of()), stage("b", Set.of("a"), Map.of())))
            .contains(PlanValidator.Code.CYCLIC_DEPENDENCY));
        assertTrue(codes(plan(stage("a", Set.of("a"), Map.of())))
            .contains(PlanValidator.Code.CYCLIC_DEPENDENCY));
    }

    @Test void rejectsConflictingTargetsButAllowsRepeatedIdenticalTargets() {
        var cell = new Cell(0, 0, 0);
        assertTrue(codes(plan(stage("a", Set.of(), Map.of(cell, STONE)),
            stage("b", Set.of("a"), Map.of(cell, AIR)))).contains(PlanValidator.Code.CONFLICTING_TARGET));
        assertTrue(codes(plan(stage("a", Set.of(), Map.of(cell, STONE)),
            stage("b", Set.of("a"), Map.of(cell, STONE)))).isEmpty());
    }

    @Test void checksFullRegistryStateAndRelativeBoundsWithoutIntegerOverflow() {
        assertTrue(codes(plan(stage("a", Set.of(), Map.of(new Cell(0, 0, 0), AIR))))
            .contains(PlanValidator.Code.UNKNOWN_STATE));
        for (var cell : List.of(new Cell(-1, 0, 0), new Cell(4, 0, 0), new Cell(0, 2, 0),
            new Cell(0, 0, 2), new Cell(Integer.MAX_VALUE, 0, 0), BOUNDS.min())) {
            assertTrue(codes(plan(stage("a", Set.of(), Map.of(cell, STONE))))
                .contains(PlanValidator.Code.OUT_OF_BOUNDS), cell.toString());
        }
        assertTrue(codes(plan(stage("a", Set.of(), Map.of(new Cell(3, 1, 1), STONE)))).isEmpty());
        assertFalse(PlanValidator.validate(plan(stage("a", Set.of(), Map.of(new Cell(4, 0, 0), STONE))),
            BOUNDS, STONE::equals).isEmpty());
    }

    @Test void boundsTargetCountEvenForRepeatedCellsAcrossStages() {
        var cells = new HashMap<Cell, StateKey>();
        for (int x = 0; x < 32768; x++) cells.put(new Cell(x, 0, 0), STONE);
        var plan = plan(stage("a", Set.of(), cells), stage("b", Set.of("a"), Map.of(new Cell(0, 0, 0), STONE)));
        assertTrue(PlanValidator.diagnose(plan, new Bounds(new Cell(0, 0, 0), new Cell(32767, 0, 0)), s -> true)
            .stream().anyMatch(d -> d.code() == PlanValidator.Code.TOO_MANY_TARGETS));
    }

    @Test void immutableCopiesProtectTheReviewedPlan() {
        var after = new HashSet<String>();
        var cells = new HashMap<Cell, StateKey>(); cells.put(new Cell(0, 0, 0), STONE);
        var stage = stage("a", after, cells);
        var stages = new ArrayList<BuildStage>(); stages.add(stage);
        var plan = new BuildPlan(PROJECT, 1, 7, stages);
        after.add("missing"); cells.clear(); stages.clear();
        assertTrue(codes(plan).isEmpty());
        assertEquals(1, plan.stages().getFirst().targets().size());
        assertThrows(UnsupportedOperationException.class, () -> stage.targets().clear());
        assertThrows(UnsupportedOperationException.class, () -> stage.after().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.stages().clear());
    }
}
