package dev.krul.cobblemon.discovery;

import dev.krul.cobblemon.species.SpeciesClass;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoverySpeciesClassFilterTest {
    private final PokemonObservation cobalion = PokemonObservation.builder(1, "cobblemon:cobalion")
        .spawnRarity(SpawnRarity.COMMON)
        .speciesClasses(Set.of(SpeciesClass.LEGENDARY))
        .build();

    @Test
    void classAndSpawnFiltersAreIndependent() {
        assertTrue(DiscoveryObservationFilter.matches(cobalion, "",
            DiscoverySpeciesClassFilter.LEGENDARY, false, false, DiscoveryMinimumRarity.COMMON, false));
        assertFalse(DiscoveryObservationFilter.matches(cobalion, "",
            DiscoverySpeciesClassFilter.LEGENDARY, false, false, DiscoveryMinimumRarity.RARE, false));
    }

    @Test
    void anyPreservesExistingBehaviorAndSpecialUsesOnlySpecialClasses() {
        PokemonObservation fossil = PokemonObservation.builder(2, "cobblemon:omanyte")
            .speciesClasses(Set.of(SpeciesClass.FOSSIL))
            .build();

        assertTrue(DiscoveryObservationFilter.matches(fossil, "",
            DiscoverySpeciesClassFilter.ANY, false, false, DiscoveryMinimumRarity.ALL, false));
        assertFalse(DiscoveryObservationFilter.matches(fossil, "",
            DiscoverySpeciesClassFilter.SPECIAL, false, false, DiscoveryMinimumRarity.ALL, false));
        assertTrue(DiscoveryObservationFilter.matches(cobalion, "",
            DiscoverySpeciesClassFilter.SPECIAL, false, false, DiscoveryMinimumRarity.ALL, false));
    }
}
