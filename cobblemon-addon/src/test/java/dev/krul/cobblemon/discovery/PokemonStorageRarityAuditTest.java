package dev.krul.cobblemon.discovery;

import dev.krul.cobblemon.pokemon.CobblemonPokemonData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PokemonStorageRarityAuditTest {
    @Test
    void resolvesEveryStorageEntryIndependentlyAndKeepsSourceEvidence() {
        var evidence = new DiscoveryRarityEvidence();
        evidence.observeServer("cobblemon:ralts", SpawnRarity.RARE);
        var local = DiscoveryRarityIndex.builder().add("cobblemon:eevee", SpawnRarity.UNCOMMON).build();
        var entries = List.of(
            new CobblemonPokemonData("cobblemon:ralts", false, false, Set.of("day"), Map.of("form", "Normal")),
            new CobblemonPokemonData("cobblemon:eevee", false, false, Set.of(), Map.of()),
            new CobblemonPokemonData("cobblemon:missingno", null, null, Set.of(), Map.of())
        );

        var result = PokemonStorageRarityAudit.resolve(entries, evidence, local);

        assertEquals(3, result.size());
        assertEquals(SpawnRarity.RARE, result.get(0).resolution().rarity());
        assertEquals(DiscoveryRarityEvidence.Source.SERVER, result.get(0).resolution().source());
        assertEquals(SpawnRarity.UNCOMMON, result.get(1).resolution().rarity());
        assertEquals(DiscoveryRarityEvidence.Source.LOCAL, result.get(1).resolution().source());
        assertEquals(SpawnRarity.UNKNOWN, result.get(2).resolution().rarity());
        assertEquals(DiscoveryRarityEvidence.Source.UNKNOWN, result.get(2).resolution().source());
    }
}
