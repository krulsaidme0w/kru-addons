package dev.krul.cobblemon.pcorganizer;

import java.util.*;

/** Plans the physical PC box layout before any Pokémon-level sorting is considered. */
public final class PcOrganizerLayoutPlanner {
    private static final List<String> FIXED = List.of("MAIN", "SPECIAL", "SHINIES", "UTILITY", "TRADE", "BREEDING");
    private PcOrganizerLayoutPlanner() {}

    public record Plan(List<Integer> sourceOrder, List<String> currentNames, List<String> targetNames) {
        public Plan { sourceOrder = List.copyOf(sourceOrder); currentNames = List.copyOf(currentNames); targetNames = List.copyOf(targetNames); }
        public boolean requiresReorder() { return !sourceOrder.equals(indexes(currentNames.size())); }
    }

    public static Plan plan(List<String> names) {
        if (names == null || names.isEmpty()) throw new IllegalArgumentException("no-boxes");
        List<String> normalized = names.stream().map(PcOrganizerLayoutPlanner::normalize).toList();
        List<Integer> order = new ArrayList<>(Collections.nCopies(names.size(), -1));
        Set<Integer> used = new HashSet<>();
        for (int role = 0; role < FIXED.size(); role++) {
            String wanted = FIXED.get(role);
            int found = -1;
            for (int i = 0; i < normalized.size(); i++) if (wanted.equals(normalized.get(i))) {
                if (found >= 0) throw new IllegalArgumentException("duplicate-fixed-box:" + wanted);
                found = i;
            }
            if (found < 0) continue;
            if (role >= names.size()) throw new IllegalArgumentException("box-" + (role + 1) + "-required-for-" + wanted);
            order.set(role, found); used.add(found);
        }
        int destination = 0;
        for (int i = 0; i < names.size(); i++) if (!used.contains(i)) {
            while (order.get(destination) >= 0) destination++;
            order.set(destination, i);
        }
        List<String> target = order.stream().map(names::get).toList();
        return new Plan(order, names, target);
    }
    public static String normalize(String value) {
        String name = value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return name.equals("UTILITIES") ? "UTILITY" : name;
    }
    private static List<Integer> indexes(int count) { List<Integer> out = new ArrayList<>(); for (int i = 0; i < count; i++) out.add(i); return out; }
}
