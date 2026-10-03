package dev.krul.builder.context;

import dev.krul.builder.model.Bounds;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Debounces a six-coordinate UI draft before applying it to the active selection. */
public final class BoundsAutoApply {
    public static final long DEBOUNCE_MILLIS = 350;

    private String[] lastValues;
    private long lastChangeMillis;

    public Optional<Bounds> update(String[] coordinates, long nowMillis, Bounds current) {
        Objects.requireNonNull(coordinates);
        if (coordinates.length != 6) throw new IllegalArgumentException("Exactly six coordinates are required");
        String[] values = Arrays.copyOf(coordinates, coordinates.length);
        if (lastValues == null || !Arrays.equals(lastValues, values) || nowMillis < lastChangeMillis) {
            lastValues = values;
            lastChangeMillis = nowMillis;
            return Optional.empty();
        }
        if (nowMillis - lastChangeMillis < DEBOUNCE_MILLIS) return Optional.empty();
        Optional<Bounds> parsed = BoundsInput.parse(values[0], values[1], values[2], values[3], values[4], values[5]);
        if (parsed.isEmpty() || parsed.orElseThrow().equals(current)) return Optional.empty();
        return parsed;
    }

    public void reset() { lastValues = null; lastChangeMillis = 0; }
}
