package dev.krul.builder.project;

import dev.krul.builder.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanPreconditionValidatorTest {
    private final StateKey air = new StateKey("minecraft:air", Map.of());
    private final StateKey stone = new StateKey("minecraft:stone", Map.of());
    private final StateKey planks = new StateKey("minecraft:oak_planks", Map.of());
    private final Bounds bounds = new Bounds(new Cell(10, 64, 20), new Cell(11, 64, 20));
    private WorldSnapshot snapshot(StateKey first, StateKey second) {
        return new WorldSnapshot(UUID.randomUUID(), 4, bounds, Map.of(new Cell(10,64,20), first, new Cell(11,64,20), second), Set.of());
    }
    private PlanContract plan(PlanOperation... operations) {
        return new PlanContract(UUID.randomUUID(), "test", 1, Map.of("wood", planks),
            List.of(new PlanContract.Stage("s", "Stage", List.of(), List.of(operations))));
    }

    @Test void placeRequiresKnownAirAndBuildsRelativePreviewPlan() {
        var valid = new PlanPreconditionValidator(s -> true, s -> s.equals(air), s -> false)
            .validate(plan(new PlanOperation(PlanOperation.Kind.PLACE, new Cell(0,0,0), null, "wood")), snapshot(air, stone), UUID.randomUUID());
        assertTrue(valid.valid(), valid.diagnostics().toString());
        assertEquals(planks, valid.previewPlan().orElseThrow().stages().getFirst().targets().get(new Cell(0,0,0)));
        var invalid = new PlanPreconditionValidator(s -> true, s -> s.equals(air), s -> false)
            .validate(plan(new PlanOperation(PlanOperation.Kind.PLACE, new Cell(1,0,0), null, "wood")), snapshot(air, stone), UUID.randomUUID());
        assertFalse(invalid.valid()); assertTrue(invalid.diagnostics().getFirst().message().contains("air"));
    }

    @Test void replaceAndRemoveRequireExactCurrentStateAndComposeAcrossStages() {
        var validator = new PlanPreconditionValidator(s -> true, s -> s.equals(air), s -> false);
        var correct = plan(
            new PlanOperation(PlanOperation.Kind.REPLACE, new Cell(0,0,0), stone, "wood"),
            new PlanOperation(PlanOperation.Kind.REMOVE, new Cell(0,0,0), planks, null));
        var staged = new PlanContract(correct.id(), correct.title(), correct.revision(), correct.palette(), List.of(
            new PlanContract.Stage("first", "Replace", List.of(), List.of(correct.stages().getFirst().operations().getFirst())),
            new PlanContract.Stage("second", "Remove", List.of("first"), List.of(correct.stages().getFirst().operations().get(1)))));
        assertTrue(validator.validate(staged, snapshot(stone, air), UUID.randomUUID()).valid());
        var wrong = plan(new PlanOperation(PlanOperation.Kind.REMOVE, new Cell(0,0,0), planks, null));
        assertFalse(validator.validate(wrong, snapshot(stone, air), UUID.randomUUID()).valid());
    }

    @Test void rejectsUnknownOutOfBoundsInvalidStatesAndBlockEntities() {
        var validator = new PlanPreconditionValidator(s -> true, s -> s.equals(air), s -> false);
        assertFalse(validator.validate(plan(new PlanOperation(PlanOperation.Kind.PLACE, new Cell(5,0,0), null, "wood")), snapshot(air, air), UUID.randomUUID()).valid());
        var unknown = new WorldSnapshot(UUID.randomUUID(), 4, bounds, Map.of(new Cell(10,64,20), air), Set.of(new Cell(11,64,20)));
        assertFalse(validator.validate(plan(new PlanOperation(PlanOperation.Kind.PLACE, new Cell(1,0,0), null, "wood")), unknown, UUID.randomUUID()).valid());
        var unknownState = new PlanPreconditionValidator(s -> !s.equals(planks), s -> s.equals(air), s -> false)
            .validate(plan(new PlanOperation(PlanOperation.Kind.PLACE, new Cell(0,0,0), null, "wood")), snapshot(air, air), UUID.randomUUID());
        assertTrue(unknownState.diagnostics().stream().anyMatch(d -> d.code().equals("UNKNOWN_STATE")));
        var targetEntity = new PlanPreconditionValidator(s -> true, s -> s.equals(air), s -> s.equals(planks))
            .validate(plan(new PlanOperation(PlanOperation.Kind.PLACE, new Cell(0,0,0), null, "wood")), snapshot(air, air), UUID.randomUUID());
        assertTrue(targetEntity.diagnostics().stream().anyMatch(d -> d.code().equals("UNSUPPORTED_BLOCK_ENTITY")));
        var existingEntity = new PlanPreconditionValidator(s -> true, s -> s.equals(air), s -> s.equals(stone))
            .validate(plan(new PlanOperation(PlanOperation.Kind.REPLACE, new Cell(0,0,0), stone, "wood")), snapshot(stone, air), UUID.randomUUID());
        assertTrue(existingEntity.diagnostics().stream().anyMatch(d -> d.code().equals("UNSUPPORTED_BLOCK_ENTITY")));
    }
}
