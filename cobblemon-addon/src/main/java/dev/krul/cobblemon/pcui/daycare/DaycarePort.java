package dev.krul.cobblemon.pcui.daycare;

import java.util.*;

/** Immutable observations at the native GUI/storage boundary. No inferred card UUIDs. */
interface DaycarePort {
    enum Kind { ROOT, PICKER, CONFIRM, GAP, UNRELATED }
    enum Role { EMPTY, OCCUPIED, UNKNOWN }
    enum ConfirmationReadiness { READY, WAITING, UNAVAILABLE }
    record Projection(String species, Set<String> aspects) {
        public Projection { aspects = Set.copyOf(aspects); }
    }
    record Pokemon(UUID uuid, String species, Set<String> aspects, int level) {
        public Pokemon { aspects = Set.copyOf(aspects); }
        Projection projection() { return new Projection(species, aspects); }
    }
    record Storage(List<Pokemon> party, List<Pokemon> pc, boolean capacity, Object partyIdentity, Object pcIdentity) {
        public Storage { party = List.copyOf(party); pc = List.copyOf(pc); }
        Storage(List<Pokemon> party, List<Pokemon> pc, boolean capacity) { this(party, pc, capacity, null, null); }
        Map<UUID, Pokemon> all() {
            var result = new LinkedHashMap<UUID, Pokemon>();
            for (Pokemon p : party) if (result.put(p.uuid(), p) != null) return null;
            for (Pokemon p : pc) if (result.put(p.uuid(), p) != null) return null;
            return Map.copyOf(result);
        }
    }
    record Frame(Object screen, Object handler, Kind kind, int pen, Role left,
                 Projection card, String rightFingerprint, boolean live, Storage storage, Object transitionIdentity) {
        Frame(Object screen, Object handler, Kind kind, int pen, Role left, Projection card,
              String rightFingerprint, boolean live, Storage storage) {
            this(screen, handler, kind, pen, left, card, rightFingerprint, live, storage, screen);
        }
    }
    Frame read(Object retainedRoot);
    default Frame inspect(Object retainedRoot) { return read(retainedRoot); }
    void openLeft(Object root, boolean insert);
    boolean select(Object screen, Pokemon expected, Storage baseline);
    /** Read-only: WAITING guarantees that no confirmation dispatch was attempted. */
    ConfirmationReadiness confirmationReadiness(Object screen, boolean insert);
    boolean confirm(Object screen, boolean insert);
}
