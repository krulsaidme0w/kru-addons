package dev.krul.cobblemon.discovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryTargetTest {
    @Test
    void normalizesBareCobblemonSpeciesAndMatchesExactly() {
        assertEquals("cobblemon:tadbulb", DiscoveryTarget.normalize("  Tadbulb  "));
        assertEquals("cobblemon:tadbulb", DiscoveryTarget.normalize("COBBLEMON:TADBULB"));
        assertEquals("", DiscoveryTarget.normalize("  "));

        assertTrue(DiscoveryTarget.matches("tadbulb", "cobblemon:tadbulb"));
        assertFalse(DiscoveryTarget.matches("tadbulb", "cobblemon:bellibolt"));
        assertTrue(DiscoveryTarget.matches("", "cobblemon:bellibolt"));
    }

    @Test
    void nonBlankTargetEnablesExactNamedMode() {
        assertFalse(DiscoveryTarget.active("  "));
        assertTrue(DiscoveryTarget.active("tadbulb"));
        assertTrue(DiscoveryTarget.active("cobblemon:tadbulb"));
    }
}

