package dev.krul.cobblemon.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class DiscoveryDomainTest {
    @Test
    void mapsKnownRarityLabelsAndUnknownValues() {
        assertEquals(SpawnRarity.COMMON, RarityClassifier.classify("common"));
        assertEquals(SpawnRarity.UNCOMMON, RarityClassifier.classify("Uncommon"));
        assertEquals(SpawnRarity.RARE, RarityClassifier.classify("rare"));
        assertEquals(SpawnRarity.ULTRA_RARE, RarityClassifier.classify("ultra-rare"));
        assertEquals(SpawnRarity.LEGENDARY, RarityClassifier.classify("legendary"));
        assertEquals(SpawnRarity.UNKNOWN, RarityClassifier.classify("legendary-ish"));
        assertEquals(SpawnRarity.UNKNOWN, RarityClassifier.classify(null));
    }

    @Test
    void rarityIndexUsesLowestBucketWhenSpeciesHasMultipleSpawnBuckets() {
        DiscoveryRarityIndex index = DiscoveryRarityIndex.builder()
            .add("cobblemon:pikachu", SpawnRarity.RARE)
            .add("cobblemon:pikachu", SpawnRarity.COMMON)
            .build();

        assertEquals(SpawnRarity.COMMON, index.classify("cobblemon:pikachu"));
        assertTrue(index.matches("cobblemon:pikachu", SpawnRarity.RARE));
        assertEquals(SpawnRarity.UNKNOWN, index.classify("cobblemon:missingno"));
    }



    @Test
    void rarityEvidencePrefersServerAndFallsBackToLocal() {
        DiscoveryRarityEvidence evidence = new DiscoveryRarityEvidence();
        DiscoveryRarityIndex local = DiscoveryRarityIndex.builder()
            .add("cobblemon:ralts", SpawnRarity.RARE)
            .build();
        evidence.observeServer("COBBLEMON:RALTS", SpawnRarity.ULTRA_RARE);
        evidence.observeServer("ralts", SpawnRarity.COMMON);

        var resolved = evidence.resolve("cobblemon:ralts", local);
        assertEquals(SpawnRarity.COMMON, resolved.rarity());
        assertEquals(DiscoveryRarityEvidence.Source.SERVER, resolved.source());
        assertEquals(Set.of(SpawnRarity.COMMON, SpawnRarity.ULTRA_RARE), resolved.serverBuckets());
        assertEquals(Set.of(SpawnRarity.RARE), resolved.localBuckets());

        var fallback = evidence.resolve("cobblemon:eevee", local);
        assertEquals(SpawnRarity.UNKNOWN, fallback.rarity());
        assertEquals(DiscoveryRarityEvidence.Source.UNKNOWN, fallback.source());
    }

    @Test
    void duplicateObservationDoesNotAdvanceRevisionAndResetCreatesNewSession() {
        DiscoveryRarityEvidence evidence = new DiscoveryRarityEvidence();
        assertTrue(evidence.observeServer("cobblemon:ralts", SpawnRarity.RARE));
        long revision = evidence.revision();
        assertFalse(evidence.observeServer("ralts", SpawnRarity.RARE));
        assertEquals(revision, evidence.revision());
        evidence.reset();
        assertFalse(evidence.containsServer("ralts"));
        assertTrue(evidence.revision() > revision);
    }

    @Test
    void spawnMapReaderUsesPacketBucketAndServerSpecies() {
        var payload = new FakeSpawnMapPayload(
            "ultra-rare",
            List.of(new FakeSpawnData(new FakeRenderable(new FakeSpecies("cobblemon:ralts"))))
        );

        CobblemonSpawnMapPacketReader.SpawnMap result = CobblemonSpawnMapPacketReader.readPayload(payload);

        assertTrue(result.found());
        assertEquals("ultra-rare", result.bucket());
        assertTrue(Float.isNaN(result.chance()));
        assertEquals(List.of(new CobblemonSpawnMapPacketReader.SpawnRarityEntry("cobblemon:ralts", SpawnRarity.ULTRA_RARE)), result.entries());
    }

    public record FakeSpawnMapPayload(String bucketName, List<FakeSpawnData> spawnDataList) {
        public String getId() { return "cobblenav:spawn_map"; }
        public String getBucketName() { return bucketName; }
        public List<FakeSpawnData> getSpawnDataList() { return spawnDataList; }
    }

    public record FakeSpawnData(FakeRenderable renderable) {
        public FakeRenderable getRenderable() { return renderable; }
    }
    public record FakeRenderable(FakeSpecies species) {
        public FakeSpecies getSpecies() { return species; }
    }
    public record FakeSpecies(String resourceIdentifier) {
        public String getResourceIdentifier() { return resourceIdentifier; }
    }

    @Test
    void keepsSpeciesAndRarityExplicit() {
        PokemonObservation observation = PokemonObservation.builder(7, "cobblemon:pikachu")
            .spawnRarity(SpawnRarity.UNKNOWN)
            .position(1, 2, 3)
            .seenAt(100)
            .build();

        assertEquals("cobblemon:pikachu", observation.species());
        assertNull(observation.shiny());
        assertNull(observation.radiant());
        assertEquals(SpawnRarity.UNKNOWN, observation.spawnRarity());
    }

    @Test
    void deduplicatesByEntityAndReturnsNewestFirstAboveMinimumRarity() {
        DiscoveryIndex index = new DiscoveryIndex(3, 1_000);
        index.observe(observation(1, SpawnRarity.COMMON, 10));
        index.observe(observation(2, SpawnRarity.RARE, 20));
        index.observe(observation(1, SpawnRarity.ULTRA_RARE, 30));

        List<PokemonObservation> result = index.recent(30, SpawnRarity.ULTRA_RARE, 10);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).entityId());
        assertEquals(SpawnRarity.ULTRA_RARE, result.get(0).spawnRarity());
    }

    @Test
    void expiresOldObservationsAndHonorsLimit() {
        DiscoveryIndex index = new DiscoveryIndex(2, 10);
        index.observe(observation(1, SpawnRarity.COMMON, 1));
        index.observe(observation(2, SpawnRarity.COMMON, 9));
        index.observe(observation(3, SpawnRarity.COMMON, 10));

        List<PokemonObservation> result = index.recent(10, SpawnRarity.UNKNOWN, 1);

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).entityId());
        assertEquals(2, index.size());
    }

    @Test
    void observeReportsOnlyNewOrChangedPokemon() {
        DiscoveryIndex index = new DiscoveryIndex(8, 1_000);
        PokemonObservation first = PokemonObservation.builder(42, "cobblemon:eevee")
            .spawnRarity(SpawnRarity.COMMON).seenAt(1).build();

        assertTrue(index.observe(first));
        assertFalse(index.observe(first));
        assertFalse(index.observe(PokemonObservation.builder(42, "cobblemon:eevee")
            .spawnRarity(SpawnRarity.COMMON).position(9, 9, 9).seenAt(99).build()));
        assertTrue(index.observe(first.withSpawnRarity(SpawnRarity.RARE)));
    }

    @Test
    void renderSelectionIncludesEveryLoadedPokemonNearestFirst() {
        List<PokemonObservation> observations = List.of(
            positionedObservation(12, 2, 0, 0),
            positionedObservation(1, 0, 0, 0),
            positionedObservation(7, -2, 0, 0),
            positionedObservation(99, 1, 0, 0));

        List<Integer> selected = DiscoveryRenderSelector.select(
                observations, Set.of(7, 12, 99), 0, 0, 0)
            .stream().map(PokemonObservation::entityId).toList();

        assertEquals(List.of(99, 7, 12), selected);
    }


    private static PokemonObservation observation(int id, SpawnRarity rarity, long seenAt) {
        return PokemonObservation.builder(id, "cobblemon:test")
            .spawnRarity(rarity)
            .seenAt(seenAt)
            .build();
    }

    private static PokemonObservation positionedObservation(int id, double x, double y, double z) {
        return PokemonObservation.builder(id, "cobblemon:test")
            .position(x, y, z)
            .seenAt(10)
            .build();
    }
}
