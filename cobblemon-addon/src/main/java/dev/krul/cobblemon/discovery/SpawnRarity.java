package dev.krul.cobblemon.discovery;

/** Contextual server/local spawn bucket, not an intrinsic species class. */
public enum SpawnRarity {
    UNKNOWN(0),
    COMMON(1),
    UNCOMMON(2),
    RARE(3),
    ULTRA_RARE(4),
    LEGENDARY(5);

    private final int level;

    SpawnRarity(int level) {
        this.level = level;
    }

    public boolean atLeast(SpawnRarity minimum) {
        return this.level >= minimum.level;
    }
}

