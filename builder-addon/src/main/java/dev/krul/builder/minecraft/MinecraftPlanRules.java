package dev.krul.builder.minecraft;

import dev.krul.builder.model.*;
import dev.krul.builder.project.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import static dev.krul.builder.project.PlanValidator.Code.*;

/** Registry boundary only; does not access a world, send commands, or place/remove blocks. */
public final class MinecraftPlanRules implements PlanValidator.StateRules {
    // Exact vanilla classes with known placement quantity. Other blocks keep honest block totals.
    private static final Set<Class<?>> UNIT_BLOCKS = Set.of(Block.class, PillarBlock.class,
        StairsBlock.class, SlabBlock.class, DoorBlock.class, BedBlock.class);

    @Override public boolean test(StateKey state) { return resolve(state).isPresent(); }

    private Optional<BlockState> resolve(StateKey key) {
        var id = Identifier.of(key.id());
        // A defaulted registry's get alone would silently turn an unknown ID into air.
        if (!Registries.BLOCK.containsId(id)) return Optional.empty();
        var block = Registries.BLOCK.get(id);
        var properties = block.getStateManager().getProperties();
        if (properties.size() != key.properties().size()) return Optional.empty();
        var state = block.getDefaultState();
        for (var property : properties) {
            var value = key.properties().get(property.getName());
            if (value == null) return Optional.empty();
            var next = withValue(state, property, value);
            if (next.isEmpty()) return Optional.empty();
            state = next.orElseThrow();
        }
        return Optional.of(state);
    }

    private static <T extends Comparable<T>> Optional<BlockState> withValue(BlockState state, Property<T> property, String value) {
        return property.parse(value).filter(parsed -> property.name(parsed).equals(value))
            .map(parsed -> state.with(property, parsed));
    }

    @Override public List<PlanValidator.Diagnostic> validateTargets(Map<Cell, StateKey> targets) {
        var errors = new ArrayList<PlanValidator.Diagnostic>();
        var resolved = new HashMap<Cell, BlockState>();
        targets.forEach((cell, key) -> resolve(key).ifPresentOrElse(state -> resolved.put(cell, state),
            () -> errors.add(new PlanValidator.Diagnostic(UNKNOWN_STATE, cell + ": " + key))));
        resolved.forEach((cell, state) -> {
            // StateKey has no NBT contract. Beds are the explicit state-only paired exception;
            // containers/signs/commands and unrecognized modded block entities are unsupported.
            boolean vanillaBed = state.getBlock().getClass() == BedBlock.class
                && targets.get(cell).id().startsWith("minecraft:");
            if (state.hasBlockEntity() && !vanillaBed) {
                errors.add(new PlanValidator.Diagnostic(UNSUPPORTED_NBT,
                    cell + ": block entity requirements are not represented by full block states"));
            }
            if (state.getBlock() instanceof DoorBlock) {
                boolean lower = state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
                var other = offset(cell, 0, lower ? 1 : -1, 0);
                var expected = state.with(DoorBlock.HALF, lower ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
                if (!expected.equals(resolved.get(other))) errors.add(pairError(cell, "door"));
            } else if (state.getBlock() instanceof BedBlock) {
                boolean foot = state.get(BedBlock.PART) == BedPart.FOOT;
                var direction = state.get(BedBlock.FACING);
                int sign = foot ? 1 : -1;
                var other = offset(cell, sign * direction.getOffsetX(), 0, sign * direction.getOffsetZ());
                var expected = state.with(BedBlock.PART, foot ? BedPart.HEAD : BedPart.FOOT);
                if (!expected.equals(resolved.get(other))) errors.add(pairError(cell, "bed"));
            }
        });
        return List.copyOf(errors);
    }

    private static Cell offset(Cell cell, int x, int y, int z) {
        try { return new Cell(Math.addExact(cell.x(), x), Math.addExact(cell.y(), y), Math.addExact(cell.z(), z)); }
        catch (ArithmeticException overflow) { return null; } // No representable partner.
    }

    private static PlanValidator.Diagnostic pairError(Cell cell, String kind) {
        return new PlanValidator.Diagnostic(INCOMPLETE_PAIR, cell + ": missing or mismatched " + kind + " partner");
    }

    /** Placement counts for the whole plan, not inventory deficits or survival acquisition costs. */
    public record Materials(Map<String, Integer> placementItems, Map<StateKey, Integer> unmappedBlocks, int manualRemovals) {
        public Materials {
            placementItems = Map.copyOf(placementItems);
            unmappedBlocks = Map.copyOf(unmappedBlocks);
        }
    }

    public Materials materials(BuildPlan plan, Bounds bounds) {
        var errors = PlanValidator.validate(plan, bounds, this);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        var targets = new HashMap<Cell, StateKey>();
        for (var stage : plan.stages()) targets.putAll(stage.targets());
        var items = new TreeMap<String, Integer>();
        var blocks = new HashMap<StateKey, Integer>();
        int removals = 0;
        for (var key : targets.values()) {
            if (Progress.isAir(key)) { removals++; continue; }
            var state = resolve(key).orElseThrow();
            var block = state.getBlock();
            var item = block.asItem();
            if (!key.id().startsWith("minecraft:") || !UNIT_BLOCKS.contains(block.getClass())
                || !(item instanceof BlockItem blockItem) || blockItem.getBlock() != block) {
                blocks.merge(key, 1, Integer::sum);
                continue;
            }
            if (block instanceof DoorBlock && state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) continue;
            if (block instanceof BedBlock && state.get(BedBlock.PART) == BedPart.HEAD) continue;
            int quantity = block instanceof SlabBlock && state.get(SlabBlock.TYPE) == SlabType.DOUBLE ? 2 : 1;
            items.merge(Registries.ITEM.getId(item).toString(), quantity, Integer::sum);
        }
        return new Materials(items, blocks, removals);
    }
}
