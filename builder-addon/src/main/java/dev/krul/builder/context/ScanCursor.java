package dev.krul.builder.context;

import dev.krul.builder.model.*;
import java.util.*;

/** A bounded pass in absolute world coordinates; only complete partitions are published. */
public final class ScanCursor {
    public static final int MAX_READS_PER_TICK = 2048;
    public static final int MAX_CELLS = 32768;

    private final UUID session;
    private final long revision;
    private final Bounds bounds;
    private final WorldReader reader;
    private final long volume;
    private final long width;
    private final long height;
    private final Map<Cell, StateKey> known = new HashMap<>();
    private final Set<Cell> unknown = new HashSet<>();
    private long next;
    private WorldSnapshot complete;

    public ScanCursor(UUID session, long revision, Bounds bounds, WorldReader reader) {
        this.session = Objects.requireNonNull(session);
        this.revision = revision;
        this.bounds = Objects.requireNonNull(bounds);
        this.reader = Objects.requireNonNull(reader);
        try {
            volume = bounds.volume();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Selection exceeds " + MAX_CELLS + " cells", overflow);
        }
        if (volume > MAX_CELLS) throw new IllegalArgumentException("Selection exceeds " + MAX_CELLS + " cells");
        width = (long) bounds.max().x() - bounds.min().x() + 1;
        height = (long) bounds.max().y() - bounds.min().y() + 1;
    }

    public Optional<WorldSnapshot> step(int budget) {
        if (budget < 0) throw new IllegalArgumentException("Read budget must not be negative");
        if (complete != null) return Optional.of(complete);
        long end = Math.min(volume, next + Math.min(budget, MAX_READS_PER_TICK));
        while (next < end) {
            var cell = new Cell((int) (bounds.min().x() + next % width),
                (int) (bounds.min().y() + next / width % height),
                (int) (bounds.min().z() + next / (width * height)));
            var state = reader.read(cell);
            if (state.isPresent()) known.put(cell, state.orElseThrow());
            else unknown.add(cell);
            next++;
        }
        if (next == volume) complete = new WorldSnapshot(session, revision, bounds, known, unknown);
        return Optional.ofNullable(complete);
    }
}
