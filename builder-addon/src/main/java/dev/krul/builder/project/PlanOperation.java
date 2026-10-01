package dev.krul.builder.project;

import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import java.util.Objects;

/** One exact state transition relative to the selected-bounds origin. */
public record PlanOperation(Kind kind, Cell position, StateKey expected, String target) {
    public enum Kind { PLACE, REPLACE, REMOVE }
    public PlanOperation {
        Objects.requireNonNull(kind); Objects.requireNonNull(position);
        switch (kind) {
            case PLACE -> { if (expected != null || target == null || target.isBlank()) throw new IllegalArgumentException("PLACE requires a target palette key and no expected state"); }
            case REPLACE -> { if (expected == null || target == null || target.isBlank()) throw new IllegalArgumentException("REPLACE requires an exact expected state and target palette key"); }
            case REMOVE -> { if (expected == null || target != null) throw new IllegalArgumentException("REMOVE requires an exact expected state and no target"); }
        }
    }
}
