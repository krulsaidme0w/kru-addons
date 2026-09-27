package dev.krul.cobblemon.dungeon;

import net.minecraft.util.math.BlockPos;

import java.util.Map;

/** Immutable, safe-to-log evidence collected by Dungeon Discovery. */
public record DungeonObservation(
    Kind kind,
    String dimension,
    String identity,
    String registryId,
    String className,
    String name,
    BlockPos position,
    Map<String, String> facts,
    long seenAt
) {
    private static final int MAX_LOG_FIELD_LENGTH = 128;

    public DungeonObservation {
        dimension = bounded(dimension);
        identity = bounded(identity);
        registryId = bounded(registryId);
        className = bounded(className);
        name = bounded(name);
        facts = facts == null ? Map.of() : Map.copyOf(facts);
    }

    private static String bounded(String value) {
        if (value == null) return "";
        return value.length() <= MAX_LOG_FIELD_LENGTH ? value : value.substring(0, MAX_LOG_FIELD_LENGTH);
    }

    public enum Kind {
        CHUNK,
        BLOCK_ENTITY,
        ENTITY,
        POKEMON,
        CROSSHAIR,
        BLOCK
    }
}

