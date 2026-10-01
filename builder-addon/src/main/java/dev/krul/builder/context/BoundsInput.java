package dev.krul.builder.context;

import dev.krul.builder.model.Bounds;
import dev.krul.builder.model.Cell;

import java.util.Optional;

/** Parses and validates the six numeric coordinates shown in Builder settings. */
public final class BoundsInput {
    private BoundsInput() {}

    public static Optional<Bounds> parse(String x1, String y1, String z1, String x2, String y2, String z2) {
        try {
            Cell first = new Cell(Integer.parseInt(x1.trim()), Integer.parseInt(y1.trim()), Integer.parseInt(z1.trim()));
            Cell second = new Cell(Integer.parseInt(x2.trim()), Integer.parseInt(y2.trim()), Integer.parseInt(z2.trim()));
            Bounds bounds = new Bounds(new Cell(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()), Math.min(first.z(), second.z())),
                new Cell(Math.max(first.x(), second.x()), Math.max(first.y(), second.y()), Math.max(first.z(), second.z())));
            if (bounds.volume() > ScanCursor.MAX_CELLS) return Optional.empty();
            return Optional.of(bounds);
        } catch (NullPointerException | NumberFormatException | ArithmeticException invalid) {
            return Optional.empty();
        }
    }
}
