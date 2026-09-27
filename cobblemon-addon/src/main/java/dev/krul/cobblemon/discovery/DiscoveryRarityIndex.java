package dev.krul.cobblemon.discovery;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DiscoveryRarityIndex {
    private final Map<String, Set<SpawnRarity>> values;

    public DiscoveryRarityIndex(Map<String, Set<SpawnRarity>> values) {
        Map<String, Set<SpawnRarity>> copy = new HashMap<>();
        values.forEach((species, rarities) -> copy.put(species, Set.copyOf(rarities)));
        this.values = Map.copyOf(copy);
    }

    public SpawnRarity classify(String species) {
        if (species == null || species.isBlank()) return SpawnRarity.UNKNOWN;
        String normalized = normalize(species);
        Set<SpawnRarity> rarities = values.get(normalized);
        if (rarities == null || rarities.isEmpty()) return SpawnRarity.UNKNOWN;
        return rarities.stream().min(java.util.Comparator.comparingInt(Enum::ordinal)).orElse(SpawnRarity.UNKNOWN);
    }

    public boolean matches(String species, SpawnRarity minimum) {
        if (minimum == null || minimum == SpawnRarity.UNKNOWN) return values.containsKey(normalize(species));
        Set<SpawnRarity> rarities = values.get(normalize(species));
        return rarities != null && rarities.stream().anyMatch(rarity -> rarity.atLeast(minimum));
    }

    public int size() {
        return values.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Set<SpawnRarity>> values = new HashMap<>();

        public Builder add(String species, SpawnRarity rarity) {
            if (species == null || species.isBlank() || rarity == null) return this;
            if (rarity == SpawnRarity.UNKNOWN) return this;
            values.computeIfAbsent(normalize(species), ignored -> EnumSet.noneOf(SpawnRarity.class)).add(rarity);
            return this;
        }

        public DiscoveryRarityIndex build() {
            return new DiscoveryRarityIndex(values);
        }

    }

    private static String normalize(String species) {
        if (species == null) return "";
        String normalized = species.toLowerCase(Locale.ROOT).trim();
        return normalized.isBlank() || normalized.contains(":") ? normalized : "cobblemon:" + normalized;
    }

    public Set<SpawnRarity> rarities(String species) {
        Set<SpawnRarity> rarities = values.get(normalize(species));
        return rarities == null ? Set.of() : rarities;
    }

    public static DiscoveryRarityIndex empty() {
        return new DiscoveryRarityIndex(Map.of());
    }

}
