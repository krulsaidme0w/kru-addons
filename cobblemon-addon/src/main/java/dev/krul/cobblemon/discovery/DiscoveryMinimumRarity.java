package dev.krul.cobblemon.discovery;

/** User-facing minimum-rarity choices; UNKNOWN remains an internal fallback only. */
public enum DiscoveryMinimumRarity {
    ALL(SpawnRarity.UNKNOWN),
    COMMON(SpawnRarity.COMMON),
    UNCOMMON(SpawnRarity.UNCOMMON),
    RARE(SpawnRarity.RARE),
    ULTRA_RARE(SpawnRarity.ULTRA_RARE),
    LEGENDARY(SpawnRarity.LEGENDARY);

    private final SpawnRarity spawnRarity;

    DiscoveryMinimumRarity(SpawnRarity spawnRarity) {
        this.spawnRarity = spawnRarity;
    }

    public SpawnRarity spawnRarity() {
        return spawnRarity;
    }
}

