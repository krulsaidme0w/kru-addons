package dev.krul.cobblemon.discovery;

import dev.krul.cobblemon.species.SpeciesClass;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryObservationFilterTest {
    @Test
    void speciesAndNormalFiltersMustAllMatch() {
        PokemonObservation tadbulb = observation("cobblemon:tadbulb", false, false,
            SpawnRarity.COMMON, Set.of());

        assertFalse(DiscoveryObservationFilter.matches(tadbulb, "tadbulb",
            DiscoverySpeciesClassFilter.ANY, false, false,
            DiscoveryMinimumRarity.ULTRA_RARE, false));
        assertTrue(DiscoveryObservationFilter.matches(tadbulb, "tadbulb",
            DiscoverySpeciesClassFilter.ANY, false, false,
            DiscoveryMinimumRarity.COMMON, false));
        assertFalse(DiscoveryObservationFilter.matches(tadbulb, "bellibolt",
            DiscoverySpeciesClassFilter.ANY, false, false,
            DiscoveryMinimumRarity.ALL, true));
    }

    @Test
    void enabledShinyAndRadiantFiltersMatchEitherSpecialAppearance() {
        PokemonObservation normal = observation("cobblemon:ralts", false, false,
            SpawnRarity.RARE, Set.of(SpeciesClass.LEGENDARY));
        PokemonObservation shiny = observation("cobblemon:ralts", true, false,
            SpawnRarity.RARE, Set.of(SpeciesClass.LEGENDARY));
        PokemonObservation radiant = observation("cobblemon:ralts", false, true,
            SpawnRarity.RARE, Set.of(SpeciesClass.LEGENDARY));

        assertFalse(matchesNormal(normal));
        assertTrue(matchesNormal(shiny));
        assertTrue(matchesNormal(radiant));
    }

    private static boolean matchesNormal(PokemonObservation observation) {
        return DiscoveryObservationFilter.matches(observation, "",
            DiscoverySpeciesClassFilter.LEGENDARY, true, true,
            DiscoveryMinimumRarity.RARE, false);
    }

    @Test void appearanceDoesNotBypassMinimumRarity() {
        assertFalse(matchesNormal(observation("ralts", true, false, SpawnRarity.COMMON, Set.of(SpeciesClass.LEGENDARY))));
        assertFalse(matchesNormal(observation("ralts", true, false, SpawnRarity.UNKNOWN, Set.of(SpeciesClass.LEGENDARY))));
    }

    private static PokemonObservation observation(String species, boolean shiny, boolean radiant,
                                                   SpawnRarity rarity, Set<SpeciesClass> classes) {
        return PokemonObservation.builder(species.hashCode(), species)
            .shiny(shiny).radiant(radiant).spawnRarity(rarity).speciesClasses(classes)
            .position(1, 2, 3).seenAt(System.currentTimeMillis()).build();
    }
}
