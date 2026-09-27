package dev.krul.cobblemon.battle;

import java.util.Objects;

public record BattleMove(String id, String name, PokemonType type,
                         MoveCategory category, int power) {
    public BattleMove {
        id = normalize(id, "unknown");
        name = normalize(name, id);
        category = category == null ? MoveCategory.UNKNOWN : category;
        if (power < 0) power = 0;
    }

    public boolean damaging() {
        return type != null && category != MoveCategory.STATUS
            && category != MoveCategory.UNKNOWN && power > 0;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNull(fallback) : value.trim();
    }
}

