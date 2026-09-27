package dev.krul.cobblemon.discovery;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CobblemonSpawnMapPacketReaderTest {
    public record Species(String id) { public String getResourceIdentifier() { return id; } }
    public record Renderable(Species species) { public Species getSpecies() { return species; } }
    public record SpawnData(Renderable renderable) { public Renderable getRenderable() { return renderable; } }
    public record Payload(String bucket, List<?> entries) {
        public String getId() { return "cobblenav:spawn_map"; }
        public String getBucketName() { return bucket; }
        public List<?> getSpawnDataList() { return entries; }
    }
    public static class BrokenEntry {
        public Object getRenderable() { throw new NoClassDefFoundError("optional dependency"); }
    }
    public static class LegacyPayload {
        public String getId() { return "cobblenav:spawn_map"; }
        public WeightedBucket getWeightedBucket() { return new WeightedBucket(); }
        public List<?> getSpawnDataList() { return List.of(); }
    }

    @org.junit.jupiter.api.Test
    void readsEntryLocalBucketsAndPokemonRenderables() {
        var payload = new EntryBucketPayload("", java.util.List.of(new Entry("rare", new Pokemon("cobblemon:cherubi"))));
        var result = CobblemonSpawnMapPacketReader.readPayload(payload);
        org.junit.jupiter.api.Assertions.assertEquals(
            java.util.List.of(new CobblemonSpawnMapPacketReader.SpawnRarityEntry("cobblemon:cherubi", SpawnRarity.RARE)),
            result.entries());
    }

    public record EntryBucketPayload(String bucketName, java.util.List<Entry> spawnDataList) {
        public String getId() { return "cobblenav:spawn_map"; }
        public String getBucketName() { return bucketName; }
        public java.util.List<Entry> getSpawnDataList() { return spawnDataList; }
    }
    public record Entry(String bucket, Pokemon pokemon) {
        public String getBucket() { return bucket; }
        public Pokemon getPokemon() { return pokemon; }
    }
    public record Pokemon(Species species) {
        public Pokemon(String id) { this(new Species(id)); }
        public Species getSpecies() { return species; }
    }
    public static class WeightedBucket {
        public String getName() { return "rare"; }
        public float getChance() { return 1f; }
    }

    @Test void nativeShapeUsesPacketBucketForRenderableSpecies() {
        var result = CobblemonSpawnMapPacketReader.readPayload(new Payload("ultra-rare", List.of(
            new SpawnData(new Renderable(new Species("cobblemon:ralts"))))));
        assertTrue(result.found());
        assertEquals("ultra-rare", result.bucket());
        assertEquals(List.of(new CobblemonSpawnMapPacketReader.SpawnRarityEntry("cobblemon:ralts", SpawnRarity.ULTRA_RARE)), result.entries());
        assertTrue(Float.isNaN(result.chance()), "Packet supplies no overall chance; zero would falsely prove impossibility");
    }

    @Test void badOptionalEntryDoesNotDiscardOtherSpecies() {
        var result = CobblemonSpawnMapPacketReader.readPayload(new Payload("rare", List.of(
            new BrokenEntry(), new SpawnData(new Renderable(new Species("custom:bird"))))));
        assertEquals(List.of(new CobblemonSpawnMapPacketReader.SpawnRarityEntry("custom:bird", SpawnRarity.RARE)), result.entries());
    }

    @Test void unrelatedOrLegacyPayloadDoesNotSupplyRarity() {
        assertFalse(CobblemonSpawnMapPacketReader.readPayload(null).found());
        assertFalse(CobblemonSpawnMapPacketReader.readPayload(new Object()).found());
        assertFalse(CobblemonSpawnMapPacketReader.readPayload(new LegacyPayload()).found());
        assertTrue(CobblemonSpawnMapPacketReader.readPayload(new Payload("unknown-bucket", List.of(
            new SpawnData(new Renderable(new Species("cobblemon:ralts")))))).entries().isEmpty());
    }

    @Test void spawnMapDefensivelyCopiesEntries() {
        var entries = new java.util.ArrayList<CobblemonSpawnMapPacketReader.SpawnRarityEntry>();
        var result = new CobblemonSpawnMapPacketReader.SpawnMap("rare", Float.NaN, entries);
        entries.add(new CobblemonSpawnMapPacketReader.SpawnRarityEntry("custom:bird", SpawnRarity.RARE));
        assertTrue(result.entries().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.entries().clear());
    }
}
