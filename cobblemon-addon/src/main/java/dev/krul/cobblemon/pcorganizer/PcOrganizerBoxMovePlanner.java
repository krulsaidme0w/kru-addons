package dev.krul.cobblemon.pcorganizer;

import java.util.*;

/** Converts the named-box order into one-at-a-time moves through a real empty scratch slot. */
public final class PcOrganizerBoxMovePlanner {
    public record Rename(int box, String name) {}
    public record Plan(List<Integer> sourceOrder, List<PcOrganizerPlan.Move> moves, List<Rename> renames) {
        public Plan { sourceOrder = List.copyOf(sourceOrder); moves = List.copyOf(moves); renames = List.copyOf(renames); }
    }
    private PcOrganizerBoxMovePlanner() {}

    public static Plan plan(PcOrganizerSnapshot snapshot) {
        return plan(snapshot, Set.of());
    }
    public static Plan plan(PcOrganizerSnapshot snapshot, Set<Integer> pinned) {
        var layout = PcOrganizerLayoutPlanner.plan(snapshot.boxNames());
        for (int box : pinned) if (box < 0 || box >= snapshot.boxCount() || layout.sourceOrder().get(box) != box)
            throw new IllegalArgumentException("Protected box " + (box + 1) + " conflicts with box order; change protection or use current-box sort");
        Map<Integer, UUID> current = new HashMap<>();
        Map<UUID, PcOrganizerPokemon> byUuid = new HashMap<>();
        for (var p : snapshot.pokemon()) { current.put(key(p.box(), p.slot()), p.uuid()); byUuid.put(p.uuid(), p); }
        Map<Integer, UUID> target = new HashMap<>();
        for (int destination = 0; destination < layout.sourceOrder().size(); destination++) {
            int source = layout.sourceOrder().get(destination);
            for (int slot = 0; slot < 30; slot++) {
                UUID id = current.get(key(source, slot));
                if (id != null) target.put(key(destination, slot), id);
            }
        }
        List<PcOrganizerPlan.Move> moves = new ArrayList<>();
        var ordered = target.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        while (!current.equals(target)) {
            // Complete an open chain before breaking another cycle. Scratch must be empty each time.
            var entry = ordered.stream().filter(e -> !current.containsKey(e.getKey())).findFirst()
                .orElseGet(() -> ordered.stream().filter(e -> !Objects.equals(current.get(e.getKey()), e.getValue())).findFirst().orElseThrow());
            int destination = entry.getKey(); UUID wanted = entry.getValue();
            if (Objects.equals(current.get(destination), wanted)) continue;
            UUID occupant = current.get(destination);
            if (occupant != null) {
                int scratch = findScratch(current, target, snapshot.boxCount(), pinned);
                moves.add(move(byUuid.get(occupant), destination, scratch));
                current.remove(destination); current.put(scratch, occupant);
            }
            int source = current.entrySet().stream().filter(e -> wanted.equals(e.getValue())).mapToInt(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("layout-uuid-missing"));
            moves.add(move(byUuid.get(wanted), source, destination));
            current.remove(source); current.put(destination, wanted);
        }
        List<Rename> renames = new ArrayList<>();
        for (int i = 0; i < layout.targetNames().size(); i++) if (!snapshot.boxNames().get(i).equals(layout.targetNames().get(i)))
            renames.add(new Rename(i, layout.targetNames().get(i)));
        return new Plan(layout.sourceOrder(), moves, renames);
    }
    private static PcOrganizerPlan.Move move(PcOrganizerPokemon pokemon, int from, int to) {
        return new PcOrganizerPlan.Move(pokemon, from / 30, from % 30, to / 30, to % 30);
    }
    private static int findScratch(Map<Integer, UUID> current, Map<Integer, UUID> target, int boxes, Set<Integer> pinned) {
        for (int i = 0; i < boxes * 30; i++) if (!pinned.contains(i / 30) && !current.containsKey(i) && !target.containsKey(i)) return i;
        throw new IllegalArgumentException("layout-scratch-unavailable");
    }
    private static int key(int box, int slot) { return box * 30 + slot; }
}
