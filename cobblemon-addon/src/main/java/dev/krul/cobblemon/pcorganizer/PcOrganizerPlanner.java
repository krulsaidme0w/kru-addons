package dev.krul.cobblemon.pcorganizer;

import java.util.*;

/** Pure deterministic planner: matching Pokémon are permuted only through their existing unprotected slots. */
public final class PcOrganizerPlanner {
    private PcOrganizerPlanner() {}

    public static PcOrganizerPlan plan(PcOrganizerSnapshot snapshot, PcOrganizerSettings settings) {
        return plan(snapshot, settings, true);
    }
    public static PcOrganizerPlan plan(PcOrganizerSnapshot snapshot, PcOrganizerSettings settings, boolean routeNamedTypes) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(settings, "settings");
        Set<Integer> protectedBoxes = settings.boxPolicy().protectedIndexes();
        List<PcOrganizerPokemon> protectedPokemon = snapshot.pokemon().stream().filter(p -> protectedBoxes.contains(p.box())).toList();
        List<PcOrganizerPokemon> selected = snapshot.pokemon().stream()
            .filter(p -> !protectedBoxes.contains(p.box()))
            .filter(p -> matches(p, settings.filter()))
            .sorted(comparator(settings))
            .toList();
        if (settings.primary() == PcOrganizerSortKey.TYPE && selected.stream().anyMatch(p -> p.type().isBlank()))
            throw new IllegalArgumentException("Pokemon type unavailable; cannot sort by type");
        Map<String, List<Integer>> typeBoxes = new LinkedHashMap<>();
        List<Integer> ordinaryBoxes = new ArrayList<>();
        for (int box = 0; box < snapshot.boxCount(); box++) {
            String name = PcOrganizerLayoutPlanner.normalize(snapshot.boxNames().get(box)).toLowerCase(Locale.ROOT);
            String type = name.replaceFirst(" [0-9]+$", "");
            if (!protectedBoxes.contains(box) && dev.krul.cobblemon.species.PokemonTypeOrder.rank(type) != Integer.MAX_VALUE)
                typeBoxes.computeIfAbsent(type, ignored -> new ArrayList<>()).add(box);
            else if (!protectedBoxes.contains(box)) ordinaryBoxes.add(box);
        }
        if (routeNamedTypes && !typeBoxes.isEmpty()) return namedTypes(snapshot, selected, protectedPokemon.size(), typeBoxes, ordinaryBoxes);
        List<PcOrganizerPokemon> positions = snapshot.pokemon().stream()
            .filter(p -> !protectedBoxes.contains(p.box()))
            .filter(p -> matches(p, settings.filter()))
            .sorted(Comparator.comparingInt(PcOrganizerPokemon::box).thenComparingInt(PcOrganizerPokemon::slot))
            .toList();
        List<PcOrganizerPlan.Move> moves = new ArrayList<>();
        Set<Integer> touched = new HashSet<>();
        for (int i = 0; i < positions.size(); i++) {
            PcOrganizerPokemon from = positions.get(i), to = selected.get(i);
            if (!from.uuid().equals(to.uuid())) {
                moves.add(new PcOrganizerPlan.Move(to, to.box(), to.slot(), from.box(), from.slot()));
                touched.add(from.box()); touched.add(to.box());
            }
        }
        return new PcOrganizerPlan(moves, selected.size(), protectedPokemon.size(), touched);
    }

    private static PcOrganizerPlan namedTypes(PcOrganizerSnapshot snapshot, List<PcOrganizerPokemon> selected,
                                               int protectedCount, Map<String, List<Integer>> typeBoxes, List<Integer> ordinaryBoxes) {
        Map<Integer, PcOrganizerPokemon> occupied = new HashMap<>();
        Set<UUID> selectedIds = new HashSet<>();
        selected.forEach(p -> selectedIds.add(p.uuid()));
        snapshot.pokemon().forEach(p -> occupied.put(p.box() * 30 + p.slot(), p));
        Map<String, List<PcOrganizerPokemon>> groups = new LinkedHashMap<>();
        for (var p : selected) {
            String primary = p.type().split("/")[0].toLowerCase(Locale.ROOT);
            if (!typeBoxes.containsKey(primary)) {
                if (ordinaryBoxes.isEmpty()) throw new IllegalArgumentException("No ordinary or " + primary + " box available for " + p.species());
                primary = "ordinary boxes";
            }
            groups.computeIfAbsent(primary, ignored -> new ArrayList<>()).add(p);
        }
        List<PcOrganizerPlan.Move> moves = new ArrayList<>();
        Set<Integer> touched = new HashSet<>();
        for (var group : groups.entrySet()) {
            List<Integer> slots = new ArrayList<>();
            for (int box : typeBoxes.getOrDefault(group.getKey(), ordinaryBoxes)) for (int slot = 0; slot < 30; slot++) {
                int key = box * 30 + slot;
                var occupant = occupied.get(key);
                if (occupant == null || selectedIds.contains(occupant.uuid())) slots.add(key);
            }
            if (slots.size() < group.getValue().size()) throw new IllegalArgumentException("Type box full: " + group.getKey());
            for (int i = 0; i < group.getValue().size(); i++) {
                var p = group.getValue().get(i);
                int key = slots.get(i);
                if (key != p.box() * 30 + p.slot()) {
                    moves.add(new PcOrganizerPlan.Move(p, p.box(), p.slot(), key / 30, key % 30));
                    touched.add(p.box()); touched.add(key / 30);
                }
            }
        }
        return new PcOrganizerPlan(moves, selected.size(), protectedCount, touched);
    }

    private static boolean matches(PcOrganizerPokemon p, PcOrganizerFilter f) {
        return (f.species().isBlank() || dev.krul.cobblemon.discovery.DiscoveryTarget.matches(f.species(), p.species()))
            && (f.type().isBlank() || Arrays.stream(p.type().split("/")).anyMatch(t -> t.equalsIgnoreCase(f.type())))
            && (f.form().isBlank() || p.form().equalsIgnoreCase(f.form()))
            && (!f.shiny() || p.shiny()) && (!f.radiant() || p.radiant())
            && (f.rarity().isBlank() || p.rarity().equalsIgnoreCase(f.rarity()))
            && (f.ivPreset().isBlank() || f.ivPreset().equals("perfect") && p.perfectIvs() >= 6)
            && (f.ability().isBlank() || p.ability().equalsIgnoreCase(f.ability()))
            && (f.heldItem().isBlank() || p.heldItem().equalsIgnoreCase(f.heldItem()));
    }

    private static Comparator<PcOrganizerPokemon> comparator(PcOrganizerSettings settings) {
        Comparator<PcOrganizerPokemon> comparator = Comparator.comparing(PcOrganizerPokemon::normalizedSpecies)
            .thenComparingInt(PcOrganizerPokemon::box).thenComparingInt(PcOrganizerPokemon::slot);
        for (PcOrganizerSortKey key : List.of(settings.tertiary(), settings.secondary(), settings.primary())) {
            Comparator<PcOrganizerPokemon> current = switch (key) {
                case SPECIES -> Comparator.comparing(PcOrganizerPokemon::normalizedSpecies);
                case TYPE -> Comparator.comparing(p -> p.type().toLowerCase(Locale.ROOT));
                case LEVEL -> Comparator.comparingInt(PcOrganizerPokemon::level);
                case IV_TOTAL -> Comparator.comparingInt(PcOrganizerPokemon::ivTotal);
                case ORIGINAL_SLOT -> Comparator.comparingInt(PcOrganizerPokemon::box).thenComparingInt(PcOrganizerPokemon::slot);
            };
            comparator = current.thenComparing(comparator);
        }
        return comparator;
    }
}
