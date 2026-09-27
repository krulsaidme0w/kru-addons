package dev.krul.cobblemon.discovery;

import java.util.Locale;

public final class RarityClassifier {
    private RarityClassifier() {
    }

    public static SpawnRarity classify(String value) {
        if (value == null || value.isBlank()) return SpawnRarity.UNKNOWN;

        return switch (value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-')) {
            case "common", "very-common", "verycommon" -> SpawnRarity.COMMON;
            case "uncommon", "less-common", "lesscommon" -> SpawnRarity.UNCOMMON;
            case "rare" -> SpawnRarity.RARE;
            case "ultra-rare", "ultrarare", "ultra" -> SpawnRarity.ULTRA_RARE;
            case "legendary", "legend", "mythical", "mythic" -> SpawnRarity.LEGENDARY;
            default -> SpawnRarity.UNKNOWN;
        };
    }
}

