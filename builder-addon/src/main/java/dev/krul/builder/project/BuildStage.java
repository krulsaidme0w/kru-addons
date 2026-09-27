package dev.krul.builder.project;

import dev.krul.builder.model.*;
import java.util.*;

/** Target coordinates are relative to the selected bounds.min, including air removals. */
public record BuildStage(String id, String title, Set<String> after, Map<Cell, StateKey> targets) {
    public BuildStage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        after = Set.copyOf(after);
        targets = Map.copyOf(targets);
    }
}
