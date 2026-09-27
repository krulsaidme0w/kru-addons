package dev.krul.cobblemon.discovery;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DiscoveryRarityBoundsTest {
    @Test void evidenceEvictsOldSpeciesAndClearsOnWorldIdentityChange() {
        var evidence = new DiscoveryRarityEvidence(2); Object world = new Object();
        evidence.bindWorld(world);
        evidence.observeServer("ralts", SpawnRarity.RARE);
        evidence.observeServer("eevee", SpawnRarity.RARE);
        evidence.observeServer("pikachu", SpawnRarity.COMMON);
        assertFalse(evidence.containsServer("ralts")); assertTrue(evidence.containsServer("pikachu"));
        evidence.bindWorld(world); assertTrue(evidence.containsServer("eevee"));
        evidence.bindWorld(new Object()); assertFalse(evidence.containsServer("eevee"));
    }
}
