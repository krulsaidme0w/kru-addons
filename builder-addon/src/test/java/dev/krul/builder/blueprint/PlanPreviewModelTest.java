package dev.krul.builder.blueprint;

import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import dev.krul.builder.project.BuildPlan;
import dev.krul.builder.project.BuildStage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanPreviewModelTest {
    @Test void combinesOrderedStagesAndKeepsAbsoluteAndRelativeCells() {
        UUID project = UUID.randomUUID();
        var first = new BuildStage("base", "Base", Set.of(), Map.of(new Cell(0, 0, 0), stone()));
        var roof = new BuildStage("roof", "Roof", Set.of("base"), Map.of(new Cell(2, 3, 4), new StateKey("minecraft:glass", Map.of())));
        var plan = new BuildPlan(project, 1, 1, List.of(first, roof));
        var projection = PlanPreviewModel.project(plan, new Bounds(new Cell(-20, 60, 100), new Cell(-10, 70, 110)));
        assertEquals(2, projection.cells().size());
        assertEquals(new Cell(-20, 60, 100), projection.cells().getFirst().worldPosition());
        assertEquals(new Cell(2, 3, 4), projection.cells().getLast().relativePosition());
        assertEquals("roof", projection.cells().getLast().stageId());
    }

    @Test void laterStagesReplaceEarlierAssignmentAtSameCell() {
        UUID project = UUID.randomUUID();
        var plan = new BuildPlan(project, 1, 1, List.of(
            new BuildStage("base", "Base", Set.of(), Map.of(new Cell(0, 0, 0), stone())),
            new BuildStage("finish", "Finish", Set.of("base"), Map.of(new Cell(0, 0, 0), new StateKey("minecraft:oak_planks", Map.of())))));
        var result = PlanPreviewModel.project(plan, new Bounds(new Cell(0, 0, 0), new Cell(2, 2, 2)));
        assertEquals(1, result.cells().size());
        assertEquals("minecraft:oak_planks", result.cells().getFirst().state().id());
    }

    @Test void rejectsOffBoundsAndOversizedPlanRatherThanClippingIt() {
        UUID project = UUID.randomUUID();
        var out = new BuildPlan(project, 1, 1, List.of(new BuildStage("base", "Base", Set.of(), Map.of(new Cell(3, 0, 0), stone()))));
        assertThrows(IllegalArgumentException.class, () -> PlanPreviewModel.project(out, new Bounds(new Cell(0, 0, 0), new Cell(2, 2, 2))));
        var many = new java.util.HashMap<Cell, StateKey>();
        for (int i = 0; i <= dev.krul.builder.project.PlanValidator.MAX_TARGETS; i++) many.put(new Cell(i, 0, 0), stone());
        var huge = new BuildPlan(project, 1, 1, List.of(new BuildStage("base", "Base", Set.of(), many)));
        assertThrows(IllegalArgumentException.class, () -> PlanPreviewModel.project(huge,
            new Bounds(new Cell(0, 0, 0), new Cell(dev.krul.builder.project.PlanValidator.MAX_TARGETS, 0, 0))));
    }

    private static StateKey stone() { return new StateKey("minecraft:stone", Map.of()); }
}
