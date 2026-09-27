package dev.krul.cobblemon.dungeon;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Allocation-free samples; a bounded summary replaces per-frame performance logging. */
public final class DungeonPerformance {
    public enum Phase { TICK, SELECT, RENDER_2D, RENDER_3D }
    private final long[] counts = new long[Phase.values().length];
    private final long[] totals = new long[counts.length];
    private final long[] maxima = new long[counts.length];
    private long started;
    private boolean running;

    public void record(Phase phase, long nanos) {
        int i = phase.ordinal();
        counts[i]++;
        totals[i] += Math.max(0, nanos);
        maxima[i] = Math.max(maxima[i], nanos);
    }

    public Optional<String> report(long now) {
        if (!running) { running = true; started = now; return Optional.empty(); }
        if (now - started < 5_000_000_000L) return Optional.empty();
        var line = new StringBuilder("DUNGEON_PERF windowMs=").append((now - started) / 1_000_000);
        for (Phase phase : Phase.values()) {
            int i = phase.ordinal();
            line.append(String.format(Locale.ROOT, " %s_count=%d %s_totalMs=%.3f %s_maxMs=%.3f",
                phase, counts[i], phase, totals[i] / 1_000_000.0, phase, maxima[i] / 1_000_000.0));
        }
        clear();
        running = true;
        started = now;
        return Optional.of(line.toString());
    }

    public void clear() {
        Arrays.fill(counts, 0);
        Arrays.fill(totals, 0);
        Arrays.fill(maxima, 0);
        running = false;
    }
}

