package dev.krul.builder.context;

import dev.krul.builder.model.*;
import java.util.*;

public final class Selection {
    private Cell first;
    private Cell second;

    public void setFirst(Cell cell) { first = Objects.requireNonNull(cell); }
    public void setSecond(Cell cell) { second = Objects.requireNonNull(cell); }
    public void clear() { first = null; second = null; }

    public Optional<Bounds> bounds() {
        if (first == null || second == null) return Optional.empty();
        return Optional.of(new Bounds(
            new Cell(Math.min(first.x(), second.x()), Math.min(first.y(), second.y()), Math.min(first.z(), second.z())),
            new Cell(Math.max(first.x(), second.x()), Math.max(first.y(), second.y()), Math.max(first.z(), second.z()))));
    }
}
