package dev.krul.builder.model;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable evidence. Neither unknown nor omitted cells prove air or completion. */
public record WorldSnapshot(UUID session, long revision, Bounds bounds,
                            Map<Cell, StateKey> known, Set<Cell> unknown) {
    public WorldSnapshot {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(bounds, "bounds");
        known = Map.copyOf(known);
        unknown = Set.copyOf(unknown);
        for (Cell cell : known.keySet()) {
            if (!bounds.contains(cell)) {
                throw new IllegalArgumentException("Known cell lies outside snapshot bounds: " + cell);
            }
            if (unknown.contains(cell)) {
                throw new IllegalArgumentException("Cell cannot be both known and unknown: " + cell);
            }
        }
        for (Cell cell : unknown) {
            if (!bounds.contains(cell)) {
                throw new IllegalArgumentException("Unknown cell lies outside snapshot bounds: " + cell);
            }
        }
    }
}
