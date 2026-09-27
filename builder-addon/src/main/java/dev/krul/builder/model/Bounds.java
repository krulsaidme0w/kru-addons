package dev.krul.builder.model;

import java.util.Objects;

/** Inclusive, ordered endpoints. Volumes that cannot fit in a long are rejected. */
public record Bounds(Cell min, Cell max) {
    public Bounds {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("Bounds min must not exceed max on any axis");
        }
    }

    public long volume() {
        long x = (long) max.x() - min.x() + 1;
        long y = (long) max.y() - min.y() + 1;
        long z = (long) max.z() - min.z() + 1;
        return Math.multiplyExact(Math.multiplyExact(x, y), z);
    }

    public boolean contains(Cell cell) {
        Objects.requireNonNull(cell, "cell");
        return cell.x() >= min.x() && cell.x() <= max.x()
            && cell.y() >= min.y() && cell.y() <= max.y()
            && cell.z() >= min.z() && cell.z() <= max.z();
    }
}
