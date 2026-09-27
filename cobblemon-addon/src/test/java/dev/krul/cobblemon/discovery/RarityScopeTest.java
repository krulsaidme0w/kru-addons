package dev.krul.cobblemon.discovery;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RarityScopeTest {
    @Test void speciesNamespacesDoNotShareRarityEvidence() {
        var index = DiscoveryRarityIndex.builder().add("cobblemon:bird", SpawnRarity.RARE).build();
        assertEquals(SpawnRarity.UNKNOWN, index.classify("custom:bird"));
        var evidence = new DiscoveryRarityEvidence();
        evidence.observeServer("cobblemon:bird", SpawnRarity.ULTRA_RARE);
        assertEquals(SpawnRarity.UNKNOWN, evidence.resolve("custom:bird", index).rarity());
    }

    @Test void localSpawnDefinitionsRemainExplicitlyLocalReferenceData() {
        var index = DiscoveryRarityIndex.builder().add("cobblemon:bird", SpawnRarity.RARE).build();
        var result = new DiscoveryRarityEvidence().resolve("cobblemon:bird", index);
        assertEquals(DiscoveryRarityEvidence.Source.LOCAL, result.source());
        assertTrue(result.serverBuckets().isEmpty());
    }
}
