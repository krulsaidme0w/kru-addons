package dev.krul.cobblemon.pcorganizer;

import dev.krul.cobblemon.species.PokemonTypeOrder;
import java.util.*;

/** Pure whole-collection destination plan, after the named-role layout projection. */
public final class PcCollectionPlanner {
    private static final int SLOTS = 30;
    private static final List<String> ANCHORS = List.of("MAIN", "SPECIAL", "SHINIES", "UTILITY", "TRADE", "BREEDING");
    private static final Set<String> MANUAL = Set.of("MAIN", "UTILITY", "TRADE", "BREEDING");
    private static final Set<String> SPECIAL = Set.of("legendary", "mythical", "ultra_beast", "paradox", "restricted");
    private static final Comparator<PcOrganizerPokemon> CANDIDATE_ORDER = Comparator
        .comparing(PcOrganizerPokemon::normalizedSpecies)
        .thenComparing(p -> p.form().toLowerCase(Locale.ROOT))
        .thenComparing(Comparator.comparingInt(PcOrganizerPokemon::ivTotal).reversed())
        .thenComparing(PcOrganizerPokemon::uuid);

    private PcCollectionPlanner() {}

    public record Plan(PcOrganizerSnapshot result, Set<Integer> protectedBoxes, List<Group> groups) {
        public Plan {
            Objects.requireNonNull(result, "result");
            protectedBoxes = Set.copyOf(protectedBoxes);
            groups = List.copyOf(groups);
        }
    }

    public record Group(String key, String label, List<Integer> boxes, int count) {
        public Group { boxes = List.copyOf(boxes); }
    }

    public static Plan plan(PcOrganizerSnapshot projected, Set<Integer> explicitPinned) {
        Objects.requireNonNull(projected, "projected");
        Objects.requireNonNull(explicitPinned, "explicitPinned");
        if (projected.boxCount() < ANCHORS.size()) throw new IllegalArgumentException("Collection requires at least 6 boxes");
        Set<Integer> protectedBoxes = new HashSet<>(explicitPinned);
        for (int box : explicitPinned) {
            if (box < 0 || box >= projected.boxCount()) throw new IllegalArgumentException("Pinned box out of range: " + box);
            if (box < ANCHORS.size() && !ANCHORS.get(box).equals(projected.boxNames().get(box)))
                throw new IllegalArgumentException("Pinned box conflicts with required anchor rename: " + ANCHORS.get(box));
        }
        for (int box = 0; box < projected.boxCount(); box++) {
            if (MANUAL.contains(manualRole(projected.boxNames().get(box)))) protectedBoxes.add(box);
        }

        Map<String, List<PcOrganizerPokemon>> candidates = new HashMap<>();
        Map<String, List<PcOrganizerPokemon>> manualCandidates = new HashMap<>();
        List<PcOrganizerPokemon> result = new ArrayList<>();
        for (var pokemon : projected.pokemon()) {
            if (protectedBoxes.contains(pokemon.box())) {
                String role = manualRole(projected.boxNames().get(pokemon.box()));
                if (!explicitPinned.contains(pokemon.box()) && Set.of("TRADE", "BREEDING").contains(role))
                    manualCandidates.computeIfAbsent(role, ignored -> new ArrayList<>()).add(pokemon);
                else result.add(pokemon);
            }
            else candidates.computeIfAbsent(classify(pokemon), ignored -> new ArrayList<>()).add(pokemon);
        }
        candidates.values().forEach(values -> values.sort(CANDIDATE_ORDER));
        // These boxes are routing boundaries, not frozen ordering boundaries.
        for (String role : List.of("TRADE", "BREEDING")) {
            List<Integer> boxes = new ArrayList<>();
            for (int box = 0; box < projected.boxCount(); box++)
                if (!explicitPinned.contains(box) && role.equals(manualRole(projected.boxNames().get(box)))) boxes.add(box);
            List<PcOrganizerPokemon> values = manualCandidates.getOrDefault(role, List.of()).stream().sorted(CANDIDATE_ORDER).toList();
            int index = 0;
            for (int box : boxes) for (int slot = 0; slot < SLOTS && index < values.size(); slot++)
                result.add(at(values.get(index++), box, slot));
        }

        // Fixed anchors always exist. Pinned named collection boxes appear in their
        // group, but none of their slots count toward capacity for movable candidates.
        Map<String, List<Integer>> assigned = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (int box = 0; box < ANCHORS.size(); box++) {
            assigned.put(ANCHORS.get(box), new ArrayList<>(List.of(box)));
            labels.put(ANCHORS.get(box), ANCHORS.get(box));
        }
        Set<String> types = new TreeSet<>(Comparator.comparingInt(PokemonTypeOrder::rank).thenComparing(Comparator.naturalOrder()));
        types.addAll(candidates.keySet()); types.removeAll(ANCHORS);
        for (int box : new TreeSet<>(explicitPinned)) {
            if (box < ANCHORS.size()) continue;
            String key = collectionKey(projected.boxNames().get(box));
            if (key != null && !ANCHORS.contains(key)) types.add(key);
        }
        for (String key : types) {
            assigned.put(key, new ArrayList<>()); labels.put(key, key);
        }
        for (int box : new TreeSet<>(protectedBoxes)) {
            if (box < ANCHORS.size()) continue;
            String name = projected.boxNames().get(box);
            String key = collectionKey(name);
            if (key == null) {
                String normalized = PcOrganizerLayoutPlanner.normalize(name);
                key = MANUAL.contains(normalized) ? normalized : "PINNED:" + box;
            }
            assigned.computeIfAbsent(key, ignored -> new ArrayList<>()).add(box);
            labels.putIfAbsent(key, name);
        }

        List<String> dynamic = new ArrayList<>(List.of("SPECIAL", "SHINIES"));
        dynamic.addAll(types);
        Set<Integer> free = new TreeSet<>();
        for (int box = ANCHORS.size(); box < projected.boxCount(); box++) if (!protectedBoxes.contains(box)) free.add(box);
        Map<String, Integer> needed = new LinkedHashMap<>();
        int totalNeeded = 0;
        for (String key : dynamic) {
            int capacity = (int) assigned.get(key).stream().filter(box -> !protectedBoxes.contains(box)).count();
            int count = candidates.getOrDefault(key, List.of()).size();
            int missing = Math.max(0, (count + SLOTS - 1) / SLOTS - capacity);
            needed.put(key, missing); totalNeeded += missing;
        }
        if (totalNeeded > free.size()) throw new IllegalArgumentException("Insufficient collection box capacity: need " + totalNeeded + " additional boxes, available " + free.size());

        // Reserve each group's existing minimum before taking any unassigned boxes.
        // Stale surplus belongs to the shared pool, so it cannot starve another group.
        for (String key : dynamic) {
            for (int box : new ArrayList<>(free)) {
                if (needed.get(key) == 0) break;
                if (key.equals(collectionKey(projected.boxNames().get(box)))) {
                    assigned.get(key).add(box); free.remove(box);
                    needed.put(key, needed.get(key) - 1);
                }
            }
        }
        for (String key : dynamic) {
            while (needed.get(key) > 0) {
                int box = free.iterator().next(); free.remove(box);
                assigned.get(key).add(box); needed.put(key, needed.get(key) - 1);
            }
            assigned.get(key).sort(Comparator.naturalOrder());
            List<Integer> destinations = assigned.get(key).stream().filter(box -> !protectedBoxes.contains(box)).toList();
            List<PcOrganizerPokemon> values = candidates.getOrDefault(key, List.of());
            for (int i = 0; i < values.size(); i++) result.add(at(values.get(i), destinations.get(i / SLOTS), i % SLOTS));
        }

        List<String> names = new ArrayList<>(projected.boxNames());
        for (int box = 0; box < ANCHORS.size(); box++) names.set(box, ANCHORS.get(box));
        for (String key : dynamic) {
            List<Integer> boxes = assigned.get(key);
            for (int i = 0; i < boxes.size(); i++) {
                int box = boxes.get(i);
                if (box < ANCHORS.size() || explicitPinned.contains(box)) continue;
                String name = boxes.size() == 1 ? key : key + " " + (i + 1) + "/" + boxes.size();
                if (name.length() > 19) throw new IllegalArgumentException("Collection box name exceeds 19 characters: " + name);
                names.set(box, name);
            }
        }
        int spare = 1;
        for (int box : free) names.set(box, "SPARE " + spare++);
        result.sort(Comparator.comparingInt(PcOrganizerPokemon::box).thenComparingInt(PcOrganizerPokemon::slot));
        var snapshot = new PcOrganizerSnapshot(projected.storeUuid(), projected.boxCount(), names, result, projected.currentBox());
        Set<UUID> originalIds = new HashSet<>(), resultIds = new HashSet<>();
        projected.pokemon().forEach(p -> originalIds.add(p.uuid()));
        result.forEach(p -> resultIds.add(p.uuid()));
        if (!originalIds.equals(resultIds)) throw new IllegalStateException("Collection plan lost Pokemon identities");
        int[] counts = new int[projected.boxCount()];
        result.forEach(p -> counts[p.box()]++);
        List<Group> groups = new ArrayList<>();
        assigned.forEach((key, boxes) -> groups.add(new Group(key, labels.get(key), boxes, boxes.stream().mapToInt(box -> counts[box]).sum())));
        return new Plan(snapshot, protectedBoxes, groups);
    }

    private static String classify(PcOrganizerPokemon pokemon) {
        if (pokemon.shiny() || pokemon.radiant()) return "SHINIES";
        if (pokemon.rarity().isBlank()) throw new IllegalArgumentException("Unknown rarity for Pokemon " + pokemon.uuid());
        for (String token : pokemon.rarity().toLowerCase(Locale.ROOT).split(",")) if (SPECIAL.contains(token.strip())) return "SPECIAL";
        String primary = pokemon.type().split("/", -1)[0].strip().toUpperCase(Locale.ROOT);
        return PokemonTypeOrder.rank(primary) == Integer.MAX_VALUE ? "UNCLASSIFIED" : primary;
    }

    private static String collectionKey(String name) {
        String key = PcOrganizerLayoutPlanner.normalize(name).replaceFirst(" [1-9][0-9]*(?:/[1-9][0-9]*)?$", "");
        return key.equals("SPECIAL") || key.equals("SHINIES") || key.equals("UNCLASSIFIED")
            || PokemonTypeOrder.rank(key) != Integer.MAX_VALUE ? key : null;
    }

    private static String manualRole(String name) {
        return PcOrganizerLayoutPlanner.normalize(name).replaceFirst(" [1-9][0-9]*(?:/[1-9][0-9]*)?$", "");
    }

    private static PcOrganizerPokemon at(PcOrganizerPokemon p, int box, int slot) {
        return new PcOrganizerPokemon(p.uuid(), box, slot, p.species(), p.type(), p.form(), p.shiny(), p.radiant(),
            p.rarity(), p.ability(), p.heldItem(), p.level(), p.ivTotal(), p.perfectIvs());
    }
}
