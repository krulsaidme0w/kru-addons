package dev.krul.builder.context;

import dev.krul.builder.model.Cell;
import dev.krul.builder.model.StateKey;
import java.util.Optional;

@FunctionalInterface
public interface WorldReader {
    /** Read an absolute world position; empty means unavailable, never inferred air. */
    Optional<StateKey> read(Cell cell);
}
