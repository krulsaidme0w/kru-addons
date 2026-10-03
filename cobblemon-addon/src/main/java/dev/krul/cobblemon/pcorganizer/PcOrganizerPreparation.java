package dev.krul.cobblemon.pcorganizer;

import java.util.*;

/** One preview covers box ordering and the subsequent ordinary sort. */
public record PcOrganizerPreparation(PcOrganizerSnapshot before, PcOrganizerSnapshot afterLayout,
                                     PcOrganizerSnapshot result, PcOrganizerBoxMovePlanner.Plan layout,
                                     PcOrganizerSettings sortSettings, PcOrganizerPlan sort) {
    public static PcOrganizerPreparation prepareCollection(PcOrganizerSnapshot before, Set<Integer> pinned) {
        var layout = PcOrganizerBoxMovePlanner.plan(before, pinned);
        var order = layout.sourceOrder();
        var projected = new PcOrganizerSnapshot(before.storeUuid(), before.boxCount(),
            order.stream().map(before.boxNames()::get).toList(), sorted(before.pokemon().stream()
                .map(p -> position(p, order.indexOf(p.box()), p.slot())).toList()), order.indexOf(before.currentBox()));
        Set<Integer> projectedPins = new HashSet<>();
        pinned.forEach(box -> projectedPins.add(order.indexOf(box)));
        var collection = PcCollectionPlanner.plan(projected, projectedPins);
        var collectionRoles = new EnumMap<PcOrganizerBoxRole, Integer>(PcOrganizerBoxRole.class);
        for (int box = 0; box < projected.boxCount(); box++) {
            String name = PcOrganizerLayoutPlanner.normalize(projected.boxNames().get(box))
                .replaceFirst(" [1-9][0-9]*(?:/[1-9][0-9]*)?$", "");
            if (name.equals("MAIN")) collectionRoles.putIfAbsent(PcOrganizerBoxRole.MAIN, box);
            else if (name.equals("UTILITY")) collectionRoles.putIfAbsent(PcOrganizerBoxRole.UTILITY, box);
            else if (name.equals("TRADE")) collectionRoles.putIfAbsent(PcOrganizerBoxRole.TRADE, box);
            else if (name.equals("BREEDING")) collectionRoles.putIfAbsent(PcOrganizerBoxRole.BREEDING, box);
        }
        var settings = new PcOrganizerSettings(PcOrganizerRunMode.APPLY, PcOrganizerFilter.empty(),
            PcOrganizerSortKey.SPECIES, PcOrganizerSortKey.IV_TOTAL, PcOrganizerSortKey.ORIGINAL_SLOT,
            new PcOrganizerBoxPolicy(before.boxCount(), projectedPins, collectionRoles));
        Map<UUID, PcOrganizerPokemon> original = new HashMap<>();
        projected.pokemon().forEach(p -> original.put(p.uuid(), p));
        var moves = new ArrayList<PcOrganizerPlan.Move>();
        Set<Integer> touched = new HashSet<>();
        for (var destination : collection.result().pokemon()) {
            var source = original.get(destination.uuid());
            if (source.box() == destination.box() && source.slot() == destination.slot()) continue;
            moves.add(new PcOrganizerPlan.Move(source, source.box(), source.slot(), destination.box(), destination.slot()));
            touched.add(source.box()); touched.add(destination.box());
        }
        int protectedCount = (int) projected.pokemon().stream().filter(p -> collection.protectedBoxes().contains(p.box())).count();
        return new PcOrganizerPreparation(before, projected, collection.result(), layout, settings,
            new PcOrganizerPlan(moves, projected.pokemon().size() - protectedCount, protectedCount, touched));
    }
    /** Positions finish before generated names are sent. */
    public PcOrganizerSnapshot afterSort() {
        return new PcOrganizerSnapshot(result.storeUuid(), result.boxCount(), afterLayout.boxNames(), result.pokemon(), result.currentBox());
    }
    public List<PcOrganizerBoxMovePlanner.Rename> renames() {
        var result = new ArrayList<PcOrganizerBoxMovePlanner.Rename>();
        for (int box = 0; box < this.result.boxCount(); box++)
            if (!afterLayout.boxNames().get(box).equals(this.result.boxNames().get(box)))
                result.add(new PcOrganizerBoxMovePlanner.Rename(box, this.result.boxNames().get(box)));
        return List.copyOf(result);
    }
    public static PcOrganizerPreparation prepare(PcOrganizerSnapshot before, PcOrganizerSettings settings,
                                                boolean reorder, Set<Integer> pinned) {
        var identity = java.util.stream.IntStream.range(0, before.boxCount()).boxed().toList();
        var layout = reorder ? PcOrganizerBoxMovePlanner.plan(before, pinned)
            : new PcOrganizerBoxMovePlanner.Plan(identity, List.of(), List.of());
        var order = layout.sourceOrder();
        var projected = new PcOrganizerSnapshot(before.storeUuid(), before.boxCount(),
            order.stream().map(before.boxNames()::get).toList(), sorted(before.pokemon().stream()
                .map(p -> position(p, order.indexOf(p.box()), p.slot())).toList()), order.indexOf(before.currentBox()));
        Set<Integer> excluded = new HashSet<>();
        settings.boxPolicy().protectedIndexes().forEach(box -> excluded.add(order.indexOf(box)));
        var sortSettings = new PcOrganizerSettings(PcOrganizerRunMode.APPLY, settings.filter(), settings.primary(),
            settings.secondary(), settings.tertiary(), PcOrganizerBoxPolicy.forSnapshot(projected, excluded, null));
        var sort = PcOrganizerPlanner.plan(projected, sortSettings, reorder);
        Map<UUID, PcOrganizerPlan.Move> destinations = new HashMap<>();
        sort.moves().forEach(move -> destinations.put(move.pokemon().uuid(), move));
        var result = new PcOrganizerSnapshot(projected.storeUuid(), projected.boxCount(), projected.boxNames(),
            sorted(projected.pokemon().stream().map(p -> {
                var move = destinations.get(p.uuid());
                return move == null ? p : position(p, move.toBox(), move.toSlot());
            }).toList()), projected.currentBox());
        return new PcOrganizerPreparation(before, projected, result, layout, sortSettings, sort);
    }
    static PcOrganizerPokemon position(PcOrganizerPokemon p, int box, int slot) {
        return new PcOrganizerPokemon(p.uuid(), box, slot, p.species(), p.type(), p.form(), p.shiny(), p.radiant(),
            p.rarity(), p.ability(), p.heldItem(), p.level(), p.ivTotal(), p.perfectIvs());
    }
    private static List<PcOrganizerPokemon> sorted(List<PcOrganizerPokemon> pokemon) {
        return pokemon.stream().sorted(Comparator.comparingInt(PcOrganizerPokemon::box).thenComparingInt(PcOrganizerPokemon::slot)).toList();
    }
    static boolean sameStorage(PcOrganizerSnapshot a, PcOrganizerSnapshot b) {
        return a != null && b != null && a.storeUuid().equals(b.storeUuid()) && a.boxNames().equals(b.boxNames())
            && sorted(a.pokemon()).equals(sorted(b.pokemon()));
    }
}
