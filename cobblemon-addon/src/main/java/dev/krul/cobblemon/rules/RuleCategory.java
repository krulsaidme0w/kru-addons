package dev.krul.cobblemon.rules;

import java.util.List;

/** Immutable visual settings; interpretation belongs to consumers. */
public record RuleCategory(String id, String name, boolean enabled, List<Integer> color,
                           boolean boxes, boolean tracers, boolean nametags, boolean arrows,
                           double maxDistance) {
    public RuleCategory {
        if (id == null || id.isBlank() || id.length() > 64 || name == null || name.isBlank() || name.length() > 128)
            throw new IllegalArgumentException("Invalid category id/name");
        color = List.copyOf(color);
        if (color.size() != 4 || color.stream().anyMatch(n -> n < 0 || n > 255))
            throw new IllegalArgumentException("Expected four RGBA channels in 0–255");
        if (!Double.isFinite(maxDistance) || maxDistance < 0 || maxDistance > 9007199254740991d)
            throw new IllegalArgumentException("Invalid maxDistance");
    }
}
