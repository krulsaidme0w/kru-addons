package dev.krul.builder.context;

import dev.krul.builder.model.Cell;
import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.StateKey;
import java.util.Optional;

@FunctionalInterface
public interface WorldReader {
    /** True only when every cell in this selection can be read without requesting chunk loads. */
    default boolean isReady(Bounds bounds) { return true; }

    /** Read an absolute world position; empty means unavailable, never inferred air. */
    Optional<StateKey> read(Cell cell);
}
