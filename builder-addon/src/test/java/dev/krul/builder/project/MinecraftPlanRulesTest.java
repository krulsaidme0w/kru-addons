package dev.krul.builder.project;

import dev.krul.builder.minecraft.MinecraftPlanRules;
import dev.krul.builder.model.*;
import java.util.*;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static dev.krul.builder.project.PlanValidatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftPlanRulesTest {
    @BeforeAll static void bootstrap() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }
    private final MinecraftPlanRules rules = new MinecraftPlanRules();

    private static StateKey key(BlockState state) {
        var properties = new HashMap<String, String>();
        for (var property : state.getProperties()) properties.put(property.getName(), value(state, property));
        return new StateKey(Registries.BLOCK.getId(state.getBlock()).toString(), properties);
    }
    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return property.name(state.get(property));
    }
    private List<PlanValidator.Diagnostic> errors(Map<Cell, StateKey> targets) {
        return PlanValidator.diagnose(plan(stage("a", Set.of(), targets)), BOUNDS, rules);
    }
    private boolean has(Map<Cell, StateKey> targets, PlanValidator.Code code) {
        return errors(targets).stream().anyMatch(e -> e.code() == code);
    }

    @Test void registryRequiresEveryPropertyAndRejectsUnknownNamesValuesAndIds() {
        assertTrue(rules.test(STONE));
        assertFalse(rules.test(new StateKey("minecraft:not_a_real_block", Map.of())));
        var stairs = key(Blocks.OAK_STAIRS.getDefaultState());
        assertTrue(rules.test(stairs));
        assertFalse(rules.test(new StateKey(stairs.id(), Map.of())));
        var bad = new HashMap<>(stairs.properties()); bad.put("facing", "up");
        assertFalse(rules.test(new StateKey(stairs.id(), bad)));
        bad = new HashMap<>(stairs.properties()); bad.put("made_up", "true");
        assertFalse(rules.test(new StateKey(stairs.id(), bad)));
        assertTrue(rules.test(key(Blocks.WATER.getDefaultState())));
    }

    @Test void doorsRequireAdjacentOppositeHalfWithEveryOtherPropertyMatching() {
        var lower = Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        var upper = lower.with(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        var a = new Cell(0, 0, 0); var b = new Cell(0, 1, 0);
        assertTrue(errors(Map.of(a, key(lower), b, key(upper))).isEmpty());
        assertTrue(has(Map.of(a, key(lower)), PlanValidator.Code.INCOMPLETE_PAIR));
        assertTrue(has(Map.of(b, key(upper)), PlanValidator.Code.INCOMPLETE_PAIR));
        assertTrue(has(Map.of(a, key(lower), b, key(upper.with(DoorBlock.HINGE, DoorHinge.RIGHT))), PlanValidator.Code.INCOMPLETE_PAIR));
        assertTrue(has(Map.of(a, key(lower), b, key(Blocks.BIRCH_DOOR.getDefaultState().with(DoorBlock.HALF, DoubleBlockHalf.UPPER))), PlanValidator.Code.INCOMPLETE_PAIR));
    }

    @Test void bedsRequireCorrectFacingOffsetPartAndColorInAllDirections() {
        var foot = new Cell(1, 0, 1);
        var larger = new Bounds(new Cell(100, 64, -20), new Cell(103, 65, -17));
        for (var direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            var state = Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, direction).with(BedBlock.PART, BedPart.FOOT);
            var head = new Cell(foot.x() + direction.getOffsetX(), 0, foot.z() + direction.getOffsetZ());
            var paired = Map.of(foot, key(state), head, key(state.with(BedBlock.PART, BedPart.HEAD)));
            assertTrue(PlanValidator.validate(plan(stage("bed", Set.of(), paired)), larger, rules).isEmpty());
            assertTrue(has(Map.of(new Cell(0, 0, 0), key(state)), PlanValidator.Code.INCOMPLETE_PAIR));
        }
        var state = Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.EAST).with(BedBlock.PART, BedPart.FOOT);
        assertTrue(has(Map.of(new Cell(0, 0, 0), key(state), new Cell(1, 0, 0),
            key(Blocks.BLUE_BED.getDefaultState().with(BedBlock.FACING, Direction.EAST).with(BedBlock.PART, BedPart.HEAD))), PlanValidator.Code.INCOMPLETE_PAIR));
    }

    @Test void unsupportedBlockEntityRequirementsAreExplicitAndPreventProposal() {
        for (var block : List.of(Blocks.CHEST, Blocks.OAK_SIGN, Blocks.COMMAND_BLOCK, Blocks.SPAWNER)) {
            assertTrue(has(Map.of(new Cell(0, 0, 0), key(block.getDefaultState())), PlanValidator.Code.UNSUPPORTED_NBT));
        }
        var bounds = new Bounds(new Cell(100, 64, -20), new Cell(100, 64, -20));
        var session = new ProjectSession(PROJECT,
            new WorldSnapshot(UUID.randomUUID(), 7, bounds, Map.of(bounds.min(), AIR), Set.of()), rules);
        assertFalse(session.propose(plan(stage("chest", Set.of(), Map.of(new Cell(0, 0, 0), key(Blocks.CHEST.getDefaultState()))))).isEmpty());
        assertTrue(session.draft().isEmpty());
    }

    @Test void materialsCountPlacementItemsOncePerPairDoubleSlabsTwiceAndFallbackAsBlocks() {
        var door = Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        var bed = Blocks.RED_BED.getDefaultState().with(BedBlock.FACING, Direction.EAST).with(BedBlock.PART, BedPart.FOOT);
        var water = key(Blocks.WATER.getDefaultState());
        var cells = Map.of(new Cell(0, 0, 0), key(door), new Cell(0, 1, 0), key(door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER)),
            new Cell(1, 0, 0), key(bed), new Cell(2, 0, 0), key(bed.with(BedBlock.PART, BedPart.HEAD)),
            new Cell(3, 0, 0), key(Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.DOUBLE)),
            new Cell(0, 0, 1), water, new Cell(1, 0, 1), AIR, new Cell(2, 0, 1), STONE);
        var plan = plan(stage("a", Set.of(), cells), stage("b", Set.of("a"), Map.of(new Cell(2, 0, 1), STONE)));
        var materials = rules.materials(plan, BOUNDS);
        assertEquals(Map.of("minecraft:oak_door", 1, "minecraft:red_bed", 1, "minecraft:oak_slab", 2, "minecraft:stone", 1), materials.placementItems());
        assertEquals(Map.of(water, 1), materials.unmappedBlocks());
        assertEquals(1, materials.manualRemovals());
        assertThrows(UnsupportedOperationException.class, () -> materials.placementItems().clear());
        assertThrows(IllegalArgumentException.class, () -> rules.materials(plan(stage("a", Set.of(), Map.of(new Cell(0, 0, 0), key(door)))), BOUNDS));
    }
}
