package dev.krul.cobblemon.pcorganizer;

import java.util.*;

/** Explicit storage protection. Names/roles are advisory until the user selects their exact box. */
public record PcOrganizerBoxPolicy(int boxCount, Set<Integer> explicitProtected, Map<PcOrganizerBoxRole, Integer> roles) {
    public PcOrganizerBoxPolicy {
        if (boxCount < 1) throw new IllegalArgumentException("boxCount must be positive");
        explicitProtected = immutableIndexes(explicitProtected, boxCount, "protected box");
        roles = roles == null ? Map.of() : Map.copyOf(roles);
        for (var entry : roles.entrySet()) {
            if (entry.getKey() == null) throw new IllegalArgumentException("box role must not be null");
            checkIndex(entry.getValue(), boxCount, "role box");
        }
    }

    public Set<Integer> protectedIndexes() {
        Set<Integer> result = new HashSet<>(explicitProtected);
        result.addAll(roles.values());
        return Set.copyOf(result);
    }

    public boolean isProtected(int box) { return protectedIndexes().contains(box); }

    public static PcOrganizerBoxPolicy forSnapshot(PcOrganizerSnapshot snapshot, Set<Integer> explicit, Integer currentBox) {
        Set<Integer> explicitIndexes = new HashSet<>(explicit);
        Map<PcOrganizerBoxRole, Integer> roles = new EnumMap<>(PcOrganizerBoxRole.class);
        for (int i = 0; i < snapshot.boxCount(); i++) {
            String name = PcOrganizerLayoutPlanner.normalize(snapshot.boxNames().get(i));
            for (var role : PcOrganizerBoxRole.values()) if (role.name().equals(name)) {
                if (roles.putIfAbsent(role, i) != null) explicitIndexes.add(i);
            }
            if (currentBox != null && currentBox != i) explicitIndexes.add(i);
        }
        if (currentBox != null) checkIndex(currentBox, snapshot.boxCount(), "current box");
        return new PcOrganizerBoxPolicy(snapshot.boxCount(), explicitIndexes, roles);
    }

    private static Set<Integer> immutableIndexes(Set<Integer> values, int count, String label) {
        Set<Integer> result = new HashSet<>();
        if (values != null) for (Integer value : values) {
            if (value == null) throw new IllegalArgumentException(label + " must not be null");
            checkIndex(value, count, label);
            result.add(value);
        }
        return Set.copyOf(result);
    }

    private static void checkIndex(int value, int count, String label) {
        if (value < 0 || value >= count) throw new IllegalArgumentException(label + " out of range: " + value);
    }
}
