package dev.krul.cobblemon.observation;

import java.util.*;

/** Immutable provider boundary. Detailed facts never enter rules in place of scalarFacts(). */
public record ObservationSnapshot(Identity identity, long seenAt, Map<String, FactValue> facts) {
    public ObservationSnapshot {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(facts, "facts");
        if (facts.size() > FactSchema.MAX_FACTS) throw new IllegalArgumentException("At most 64 facts per snapshot");
        facts.forEach(FactSchema::validate);
        facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }

    public enum Kind { ENTITY, BLOCK_ENTITY, BLOCK, ITEM, POKEMON }
    /** Caller creates fresh tokens on connection and world-instance replacement, respectively. */
    public record Scope(UUID connectionId, UUID worldId) {
        public Scope {
            Objects.requireNonNull(connectionId, "connectionId");
            Objects.requireNonNull(worldId, "worldId");
        }
    }
    public record Identity(Scope scope, Kind kind, String subjectId) {
        public Identity {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(kind, "kind");
            if (subjectId == null || subjectId.isBlank() || subjectId.length() > FactValue.MAX_STRING_LENGTH)
                throw new IllegalArgumentException("Expected nonempty identity of at most 256 characters");
        }
        public static Identity entity(Scope scope, UUID uuid) { return new Identity(scope, Kind.ENTITY, uuid.toString()); }
        public static Identity block(Scope scope, int x, int y, int z) { return new Identity(scope, Kind.BLOCK_ENTITY, x + "," + y + "," + z); }
    }
    /** Unavailable facts are absent, including unknown booleans/numbers; available false/zero survive. */
    public Map<String, Object> scalarFacts() {
        Map<String, Object> scalars = new LinkedHashMap<>();
        facts.forEach((key, fact) -> { if (fact.available()) scalars.put(key, fact.value()); });
        return Collections.unmodifiableMap(scalars);
    }
}
