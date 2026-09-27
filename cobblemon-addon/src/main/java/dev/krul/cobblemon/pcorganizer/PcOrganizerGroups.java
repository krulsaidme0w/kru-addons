package dev.krul.cobblemon.pcorganizer;

import java.util.*;

/** Group navigation derives from real or previewed box names, never separate virtual storage. */
final class PcOrganizerGroups {
    record Group(String label, List<Integer> boxes, int count) {
        Group { boxes = List.copyOf(boxes); }
        int nextBox(int current) { return boxes.get((boxes.indexOf(current) + 1) % boxes.size()); }
    }
    static List<Group> from(PcOrganizerSnapshot snapshot) {
        if (snapshot == null) return List.of();
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int box = 0; box < snapshot.boxCount(); box++) {
            String name = PcOrganizerLayoutPlanner.normalize(snapshot.boxNames().get(box));
            String base = name.replaceFirst(" [0-9]+(?:/[0-9]+)?$", "");
            boolean generated = Set.of("MAIN", "SPECIAL", "SHINIES", "UTILITY", "TRADE", "BREEDING", "SPARE", "UNCLASSIFIED").contains(base)
                || dev.krul.cobblemon.species.PokemonTypeOrder.rank(base.toLowerCase(Locale.ROOT)) != Integer.MAX_VALUE;
            groups.computeIfAbsent(generated ? base : name, ignored -> new ArrayList<>()).add(box);
        }
        return groups.entrySet().stream().map(e -> new Group(e.getKey(), e.getValue(),
            (int) snapshot.pokemon().stream().filter(p -> e.getValue().contains(p.box())).count())).toList();
    }
    static int at(double x, double y, int count) {
        if (x < 8 || y < 238) return -1;
        int column = (int) ((x - 8) / 106), row = (int) ((y - 238) / 24);
        if (column >= 3 || x >= 8 + column * 106 + 102 || y >= 238 + row * 24 + 21) return -1;
        int index = row * 3 + column;
        return index < count ? index : -1;
    }
    static int detailsY(int count) { return 238 + ((count + 2) / 3) * 24 + 8; }
}
