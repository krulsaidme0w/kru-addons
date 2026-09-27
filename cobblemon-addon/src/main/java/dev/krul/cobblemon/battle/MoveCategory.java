package dev.krul.cobblemon.battle;

import java.util.Locale;

public enum MoveCategory {
    PHYSICAL, SPECIAL, STATUS, UNKNOWN;

    public static MoveCategory fromId(String id) {
        if (id == null) return UNKNOWN;
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}

