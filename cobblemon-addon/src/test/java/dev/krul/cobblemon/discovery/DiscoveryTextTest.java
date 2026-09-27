package dev.krul.cobblemon.discovery;

import dev.krul.cobblemon.species.SpeciesClass;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryTextTest {
    @Test
    void rendersOnlySpeciesWhenRarityIsUnknown() {
        PokemonObservation observation = PokemonObservation.builder(1, "cobblemon:pikachu")
            .spawnRarity(SpawnRarity.UNKNOWN)
            .build();

        assertEquals(List.of("cobblemon:pikachu"), DiscoveryText.lines(observation, true));
    }

    @Test
    void rendersSpeciesAndRarity() {
        PokemonObservation observation = PokemonObservation.builder(1, "cobblemon:pikachu")
            .spawnRarity(SpawnRarity.RARE)
            .build();

        assertEquals(List.of("cobblemon:pikachu [spawn: rare]"), DiscoveryText.lines(observation, true));
    }

    @Test
    void rendersShinyAndRadiantFlags() {
        PokemonObservation observation = PokemonObservation.builder(1, "cobblemon:pikachu")
            .shiny(true)
            .radiant(true)
            .spawnRarity(SpawnRarity.RARE)
            .build();

        assertEquals(List.of("cobblemon:pikachu [shiny] [radiant] [spawn: rare]"), DiscoveryText.lines(observation, true));
    }

    @Test
    void rendersIntrinsicClassSeparatelyFromCommonSpawnBucket() {
        PokemonObservation observation = PokemonObservation.builder(1, "cobblemon:cobalion")
            .spawnRarity(SpawnRarity.COMMON)
            .speciesClasses(Set.of(SpeciesClass.LEGENDARY))
            .speciesLabels(Set.of("legendary", "gen5"))
            .build();

        assertEquals(List.of("cobblemon:cobalion [legendary] [spawn: common]"),
            DiscoveryText.lines(observation, true));
    }
}

