package dev.krul.cobblemon.discovery;

/** Pure filter contract shared by the Discovery feed, rendering, and Auto RTP. */
public final class DiscoveryObservationFilter {
    private DiscoveryObservationFilter() {}

    public static boolean matches(PokemonObservation observation, String targetSpecies,
                                  DiscoverySpeciesClassFilter classFilter,
                                  boolean shinyOnly, boolean radiantOnly,
                                  DiscoveryMinimumRarity minimumRarity,
                                  boolean unknownRarityMatches) {
        DiscoveryAppearanceFilter appearance = shinyOnly && radiantOnly
            ? DiscoveryAppearanceFilter.SHINY_OR_RADIANT
            : shinyOnly ? DiscoveryAppearanceFilter.SHINY
            : radiantOnly ? DiscoveryAppearanceFilter.RADIANT
            : DiscoveryAppearanceFilter.ANY;
        return matches(observation, targetSpecies, classFilter, appearance, minimumRarity, unknownRarityMatches);
    }

    public static boolean matches(PokemonObservation observation, String targetSpecies,
                                  DiscoverySpeciesClassFilter classFilter,
                                  DiscoveryAppearanceFilter appearance,
                                  DiscoveryMinimumRarity minimumRarity,
                                  boolean unknownRarityMatches) {
        if (observation == null) return false;
        if (!DiscoveryTarget.matches(targetSpecies, observation.species())) return false;
        if (classFilter == null || !classFilter.matches(observation.speciesClasses())) return false;
        if (appearance == null || !appearance.matches(observation)) return false;
        SpawnRarity minimum = minimumRarity.spawnRarity();
        if (minimum == SpawnRarity.UNKNOWN) return true;
        if (observation.spawnRarity() != SpawnRarity.UNKNOWN) {
            return observation.spawnRarity().atLeast(minimum);
        }
        return unknownRarityMatches;
    }
}
