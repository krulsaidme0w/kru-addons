package dev.krul.builder.project;

import dev.krul.builder.model.StateKey;
import java.util.*;

/** Versioned staged plan contract; validation against a snapshot is a separate operation. */
public record PlanContract(UUID id, String title, long revision, Map<String, StateKey> palette, List<Stage> stages) {
    public record Stage(String id, String title, List<String> after, List<PlanOperation> operations) {
        public Stage {
            id = text(id, "stage id", 128); title = text(title, "stage title", 256);
            after = List.copyOf(after); operations = List.copyOf(operations);
            if (new HashSet<>(after).size() != after.size()) throw new IllegalArgumentException("Duplicate stage dependency");
        }
    }
    public PlanContract {
        Objects.requireNonNull(id); title = text(title, "plan title", 256);
        if (revision < 1) throw new IllegalArgumentException("Plan revision must be positive");
        var sorted = new TreeMap<String, StateKey>();
        Objects.requireNonNull(palette).forEach((key, state) -> sorted.put(text(key, "palette key", 128), Objects.requireNonNull(state)));
        palette = Collections.unmodifiableMap(sorted); stages = List.copyOf(stages);
        if (palette.isEmpty() || stages.isEmpty() || stages.size() > PlanValidator.MAX_TARGETS)
            throw new IllegalArgumentException("Plan requires a palette and bounded stages");
    }
    private static String text(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("Invalid " + name);
        return value;
    }
}
