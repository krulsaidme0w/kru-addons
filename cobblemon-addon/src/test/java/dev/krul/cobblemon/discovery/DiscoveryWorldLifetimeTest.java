package dev.krul.cobblemon.discovery;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveryWorldLifetimeTest {
    @Test void releasingWorldDropsTheStrongReferenceAsWellAsObservations() throws Exception {
        var index = new DiscoveryIndex(128, 300_000);
        Object world = new Object();
        index.bindWorld(world);
        index.observe(PokemonObservation.builder(7, "cobblemon:ralts").seenAt(100).build());
        var worldField = DiscoveryIndex.class.getDeclaredField("world");
        worldField.setAccessible(true);
        assertSame(world, worldField.get(index));

        index.bindWorld(null);

        assertNull(worldField.get(index), "Clearing observations alone must not retain the old world");
        assertEquals(0, index.size());
    }
    @Test void newWorldClearsHudAndRenderObservationsBeforeAnotherScan() {
        var index = new DiscoveryIndex(128, 300_000);
        index.bindWorld(new Object());
        index.observe(PokemonObservation.builder(7, "cobblemon:ralts").shiny(true).seenAt(100).build());

        index.bindWorld(new Object());

        assertTrue(index.recent(101, SpawnRarity.UNKNOWN, 8).isEmpty());
        assertTrue(DiscoveryRenderSelector.select(index.recent(101, SpawnRarity.UNKNOWN, 8),
            Set.of(7), 0, 0, 0).isEmpty(), "A reused entity ID cannot attach the previous world's label");
        index.observe(PokemonObservation.builder(7, "cobblemon:eevee").seenAt(102).build());
        assertEquals(List.of("cobblemon:eevee"), index.recent(102, SpawnRarity.UNKNOWN, 8)
            .stream().map(PokemonObservation::species).toList());
        assertNull(index.recent(102, SpawnRarity.UNKNOWN, 8).getFirst().shiny());
    }

    @Test void repeatedAccessWithinTheSameWorldRetainsObservations() {
        var index = new DiscoveryIndex(128, 300_000);
        Object world = new Object();
        index.bindWorld(world);
        index.observe(PokemonObservation.builder(7, "cobblemon:ralts").seenAt(100).build());
        index.bindWorld(world);
        assertEquals(1, index.recent(101, SpawnRarity.UNKNOWN, 8).size());
    }

    @Test void worldReferenceIdentityMattersEvenWhenObjectsCompareEqual() {
        var index = new DiscoveryIndex(128, 300_000);
        index.bindWorld(new String("same dimension"));
        index.observe(PokemonObservation.builder(7, "cobblemon:ralts").seenAt(100).build());
        index.bindWorld(new String("same dimension"));
        assertEquals(0, index.size());
    }

    @Test void disconnectClearsObservationsWithoutWaitingForExpiration() {
        var index = new DiscoveryIndex(128, 300_000);
        index.bindWorld(new Object());
        index.observe(PokemonObservation.builder(7, "cobblemon:ralts").seenAt(100).build());
        index.bindWorld(null);
        assertEquals(0, index.size());
        index.bindWorld(null);
        assertEquals(0, index.size());
    }
}
