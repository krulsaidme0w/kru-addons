package dev.krul.cobblemon.dungeon;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DungeonWorldProbeTest {
    @Test void runtimeCursorDoesNotPermanentlyStarveEntitiesBeyond8192() {
        var cursor = new DungeonWorldProbe.ScanCursor();
        var entities = java.util.stream.IntStream.range(0, 9000).boxed().toList();
        cursor.initializeBackingSlots(new AtomicReferenceArray<net.minecraft.world.chunk.Chunk>(0), entities, null);
        int consumed = 0;
        DungeonWorldProbe.ScanCursor.Work work;
        while ((work = cursor.nextWork()) != null) {
            assertEquals(consumed, work.value());
            consumed++;
        }
        assertEquals(9000, consumed);
    }
    @Test
    void budgetedEntityPassSurvivesBackingSourceMutation() {
        List<DungeonWorldProbe.EntityData> entities = new ArrayList<>();
        for (int id = 1; id <= 4; id++) {
            entities.add(new DungeonWorldProbe.EntityData("minecraft:zombie", "Zombie", id, "u" + id,
                id, 2, 3, "", Map.of()));
        }
        DungeonWorldProbe.ScanCursor cursor = new DungeonWorldProbe.ScanCursor();
        DungeonWorldProbe probe = new DungeonWorldProbe();

        DungeonWorldProbe.BudgetedResult first = probe.fromLoadedData("minecraft:overworld", 0,
            List.of(), List.of(), entities, List.of(), null, 1, cursor, 2);
        entities.clear();
        entities.add(new DungeonWorldProbe.EntityData("minecraft:skeleton", "Skeleton", 99, "replacement",
            99, 2, 3, "", Map.of()));

        DungeonWorldProbe.BudgetedResult second = probe.fromLoadedData("minecraft:overworld", 0,
            List.of(), List.of(), entities, List.of(), null, 2, cursor, 2);

        assertEquals(List.of("minecraft:overworld|entity|u3", "minecraft:overworld|entity|u4"),
            second.result().observations().stream().map(DungeonObservation::identity).toList());
        assertFalse(first.complete());
    }

    @Test
    void budgetedBlockEntityPassSurvivesBackingSourceMutation() {
        List<DungeonWorldProbe.BlockEntityData> blockEntities = new ArrayList<>();
        for (int x = 1; x <= 4; x++) {
            blockEntities.add(new DungeonWorldProbe.BlockEntityData("minecraft:chest", "Chest",
                new BlockPos(x, 2, 3), "", List.of()));
        }
        DungeonWorldProbe.ScanCursor cursor = new DungeonWorldProbe.ScanCursor();
        DungeonWorldProbe probe = new DungeonWorldProbe();

        DungeonWorldProbe.BudgetedResult first = probe.fromLoadedData("minecraft:overworld", 0,
            List.of(), blockEntities, List.of(), List.of(), null, 1, cursor, 2);
        blockEntities.clear();
        blockEntities.add(new DungeonWorldProbe.BlockEntityData("minecraft:barrel", "Barrel",
            new BlockPos(99, 2, 3), "", List.of()));

        DungeonWorldProbe.BudgetedResult second = probe.fromLoadedData("minecraft:overworld", 0,
            List.of(), blockEntities, List.of(), List.of(), null, 2, cursor, 2);

        assertEquals(List.of("minecraft:overworld|block-entity|minecraft:chest|3,2,3",
                "minecraft:overworld|block-entity|minecraft:chest|4,2,3"),
            second.result().observations().stream().map(DungeonObservation::identity).toList());
        assertFalse(first.complete());
    }

    @Test
    void blockEntityMetadataCacheSerializesOncePerLiveObjectAndAgainAfterReplacement() {
        DungeonWorldProbe.BlockEntityMetadataCache cache = new DungeonWorldProbe.BlockEntityMetadataCache(8);
        Object live = new Object();
        AtomicInteger serializations = new AtomicInteger();

        assertEquals(List.of("Items"), cache.read("minecraft:the_nether", "minecraft:chest",
            new BlockPos(1, 2, 3), live, () -> { serializations.incrementAndGet(); return List.of("Items"); }));
        assertEquals(List.of("Items"), cache.read("minecraft:the_nether", "minecraft:chest",
            new BlockPos(1, 2, 3), live, () -> { serializations.incrementAndGet(); return List.of("Items"); }));
        assertEquals(1, serializations.get());

        Object replacement = new Object();
        cache.read("minecraft:the_nether", "minecraft:chest", new BlockPos(1, 2, 3), replacement,
            () -> { serializations.incrementAndGet(); return List.of("CustomName"); });
        assertEquals(2, serializations.get());

        cache.clear();
        cache.read("minecraft:the_nether", "minecraft:chest", new BlockPos(1, 2, 3), live,
            () -> { serializations.incrementAndGet(); return List.of("Items"); });
        assertEquals(3, serializations.get());
    }
    @Test
    void convertsLoadedRecordsToStableObservations() {
        DungeonWorldProbe probe = new DungeonWorldProbe();

        DungeonWorldProbe.ProbeResult result = probe.fromLoadedData("minecraft:the_nether", 12,
            List.of(new DungeonWorldProbe.ChunkData(4, -2)),
            List.of(new DungeonWorldProbe.BlockEntityData("minecraft:chest", "net.minecraft.ChestBlockEntity",
                new BlockPos(65, 20, -31), "Loot cache", List.of("Items", "CustomName"))),
            List.of(new DungeonWorldProbe.EntityData("minecraft:zombie", "net.minecraft.entity.mob.ZombieEntity",
                17, "00000000-0000-0000-0000-000000000017", 65.5, 21, -30.5, "Guardian", Map.of("onFire", "false"))),
            List.of(new DungeonWorldProbe.PokemonData("cobblemon:ralts", "ralts", 31, false, true,
                List.of("galarian"), 66, 21, -30)),
            new DungeonWorldProbe.CrosshairData(DungeonObservation.Kind.BLOCK_ENTITY, "minecraft:chest",
                "net.minecraft.ChestBlockEntity", new BlockPos(65, 20, -31), "Loot cache"), 99L);

        assertEquals("minecraft:the_nether", result.dimension());
        assertEquals(12, result.loadedChunks());
        assertEquals(5, result.observations().size());
        DungeonObservation chunk = result.observations().get(0);
        assertEquals("minecraft:the_nether|chunk|4,-2", chunk.identity());
        DungeonObservation pokemon = result.observations().stream()
            .filter(o -> o.kind() == DungeonObservation.Kind.POKEMON).findFirst().orElseThrow();
        assertEquals("cobblemon:ralts", pokemon.registryId());
        assertEquals("31", pokemon.facts().get("level"));
        assertEquals("true", pokemon.facts().get("radiant"));
    }

    @Test
    void stableIdentityDoesNotUseDisplayName() {
        DungeonWorldProbe probe = new DungeonWorldProbe();
        DungeonWorldProbe.ProbeResult first = probe.fromLoadedData("minecraft:overworld", 1, List.of(),
            List.of(), List.of(new DungeonWorldProbe.EntityData("minecraft:zombie", "Zombie", 7,
                "uuid", 1, 2, 3, "First name", Map.of())), List.of(), null, 1);
        DungeonWorldProbe.ProbeResult second = probe.fromLoadedData("minecraft:overworld", 1, List.of(),
            List.of(), List.of(new DungeonWorldProbe.EntityData("minecraft:zombie", "Zombie", 7,
                "uuid", 1, 2, 3, "Renamed", Map.of())), List.of(), null, 2);

        assertEquals(first.observations().get(0).identity(), second.observations().get(0).identity());
        assertNotEquals(first.observations().get(0).name(), second.observations().get(0).name());
    }

    @Test
    void crosshairTargetIsRecordedAsItsOwnBoundedObservation() {
        DungeonWorldProbe.ProbeResult result = new DungeonWorldProbe().fromLoadedData("minecraft:overworld", 0,
            List.of(), List.of(), List.of(), List.of(),
            new DungeonWorldProbe.CrosshairData(DungeonObservation.Kind.ENTITY, "minecraft:armor_stand",
                "net.minecraft.entity.decoration.ArmorStandEntity", new BlockPos(1, 2, 3), "Marker"), 4);

        DungeonObservation target = assertSingle(result.observations());
        assertEquals(DungeonObservation.Kind.CROSSHAIR, target.kind());
        assertEquals("minecraft:overworld|crosshair|entity|minecraft:armor_stand|1,2,3", target.identity());
        assertEquals("Marker", target.name());
    }

    @Test
    void blockCrosshairUsesBlockKind() {
        DungeonObservation target = assertSingle(new DungeonWorldProbe().fromLoadedData("minecraft:overworld", 0,
            List.of(), List.of(), List.of(), List.of(),
            new DungeonWorldProbe.CrosshairData(DungeonObservation.Kind.BLOCK, "minecraft:chest", "ChestBlock",
                new BlockPos(4, 5, 6), "Chest"), 1).observations());
        assertEquals(DungeonObservation.Kind.CROSSHAIR, target.kind());
        assertEquals("block", target.facts().get("targetKind"));
    }

    @Test
    void pokemonFactsContainNoUnboundedPayload() {
        DungeonObservation observation = assertSingle(new DungeonWorldProbe().fromLoadedData("minecraft:overworld", 0,
            List.of(), List.of(), List.of(), List.of(new DungeonWorldProbe.PokemonData("cobblemon:ralts", "ralts",
                10, true, false, List.of("a"), 0, 0, 0)), null, 1).observations());

        assertTrue(observation.facts().keySet().stream().allMatch(DungeonPokemonFacts.ALLOWED_KEYS::contains));
        assertFalse(observation.facts().containsKey("rawNbt"));
    }

    @Test
    void nullClientProducesUnavailableResult() {
        DungeonWorldProbe.ProbeResult result = new DungeonWorldProbe().scan(null, 1);
        assertEquals("unavailable", result.dimension());
        assertEquals(0, result.loadedChunks());
        assertTrue(result.observations().isEmpty());
    }

    @Test
    void pokemonIdentityKeepsUuidAndRuntimeClassAndFacts() {
        DungeonWorldProbe.ProbeResult result = new DungeonWorldProbe().fromLoadedData("minecraft:overworld", 0,
            List.of(), List.of(), List.of(), List.of(new DungeonWorldProbe.PokemonData("cobblemon:ralts",
                "galarian", 42, false, true, List.of("galarian", "rare"), 91,
                "pokemon-uuid", "com.cobblemon.mod.common.entity.pokemon.PokemonEntity", 1, 2, 3,
                Map.of("rarity", "legendary", "speciesClass", "mythical", "ownerUuid", "owner-uuid"))), null, 1);

        DungeonObservation observation = assertSingle(result.observations());
        assertTrue(observation.identity().contains("pokemon-uuid"));
        assertEquals("com.cobblemon.mod.common.entity.pokemon.PokemonEntity", observation.className());
        assertEquals("42", observation.facts().get("level"));
        assertEquals("galarian,rare", observation.facts().get("aspects"));
        assertEquals("legendary", observation.facts().get("rarity"));
        assertEquals("owner-uuid", observation.facts().get("ownerUuid"));
    }

    @Test
    void blockEntityCarriesDisplayNameAndOnlyBoundedNbtKeys() {
        DungeonObservation observation = assertSingle(new DungeonWorldProbe().fromLoadedData("minecraft:overworld", 0,
            List.of(), List.of(new DungeonWorldProbe.BlockEntityData("minecraft:chest", "Chest",
                new BlockPos(1, 2, 3), "Treasure", List.of("Items", "CustomName", "unsafeValue"))),
            List.of(), List.of(), null, 1).observations());

        assertEquals("Treasure", observation.name());
        assertTrue(observation.facts().containsKey("nbtKey:Items"));
        assertTrue(observation.facts().containsKey("nbtKey:CustomName"));
        assertFalse(observation.facts().containsKey("nbtKey:unsafeValue"));
    }

    @Test
    void cursorLimitsEachPassAndContinuesAtNextCursor() {
        List<DungeonWorldProbe.EntityData> entities = List.of(
            new DungeonWorldProbe.EntityData("minecraft:zombie", "Zombie", 1, "u1", 1, 2, 3, "", Map.of()),
            new DungeonWorldProbe.EntityData("minecraft:zombie", "Zombie", 2, "u2", 2, 2, 3, "", Map.of()),
            new DungeonWorldProbe.EntityData("minecraft:zombie", "Zombie", 3, "u3", 3, 2, 3, "", Map.of()));
        DungeonWorldProbe probe = new DungeonWorldProbe();
        DungeonWorldProbe.BudgetedResult first = probe.fromLoadedData("minecraft:overworld", 0, List.of(), List.of(), entities,
            List.of(), null, 1, new DungeonWorldProbe.ScanCursor(), 2);
        DungeonWorldProbe.BudgetedResult second = probe.fromLoadedData("minecraft:overworld", 0, List.of(), List.of(), entities,
            List.of(), null, 2, first.cursor(), 2);

        assertEquals(2, first.result().observations().size());
        assertEquals(1, second.result().observations().size());
        assertEquals(3, first.result().observations().size() + second.result().observations().size());
    }

    @Test
    void cursorSnapshotsSourceOnceAtPassInitialization() {
        int[] consumed = {0};
        Iterable<DungeonWorldProbe.EntityData> input = () -> new java.util.Iterator<>() {
            private int next;
            public boolean hasNext() { return next < 10; }
            public DungeonWorldProbe.EntityData next() {
                consumed[0]++;
                int id = ++next;
                return new DungeonWorldProbe.EntityData("minecraft:zombie", "Zombie", id, "u" + id,
                    id, 2, 3, "", Map.of());
            }
        };
        DungeonWorldProbe.ScanCursor cursor = new DungeonWorldProbe.ScanCursor();
        new DungeonWorldProbe().fromLoadedData("minecraft:overworld", 1, List.of(), List.of(), input,
            List.of(), null, 1, cursor, 2);
        assertEquals(10, consumed[0]);
    }

    @Test
    void pokemonFallsBackToSpeciesPositionWhenUuidUnavailable() {
        DungeonObservation observation = assertSingle(new DungeonWorldProbe().fromLoadedData("minecraft:overworld", 0,
            List.of(), List.of(), List.of(), List.of(new DungeonWorldProbe.PokemonData("cobblemon:ralts", "", 0,
                false, false, List.of(), 17, "", "PokemonEntity", 1, 2, 3, Map.of())), null, 1).observations());
        assertTrue(observation.identity().contains("ralts|1.0,2.0,3.0"));
        assertFalse(observation.identity().endsWith("|17"));
    }

    @Test
    void entityPositionUsesFloorForNegativeCoordinates() {
        DungeonObservation observation = assertSingle(new DungeonWorldProbe().fromLoadedData("dungeons:dungeons", 0,
            List.of(), List.of(), List.of(new DungeonWorldProbe.EntityData("minecraft:item_display", "Display", 1,
                "uuid", -0.2, 4.9, -8.01, "Item Display", Map.of())), List.of(), null, 1).observations());

        assertEquals(new BlockPos(-1, 4, -9), observation.position());
    }

    @Test
    void blockCrosshairSourceDoesNotUseLoadCapableWorldStateLookup() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/dev/krul/cobblemon/dungeon/DungeonWorldProbe.java"));
        assertFalse(source.contains("client.world.getBlockState"));
        assertTrue(source.contains("chunk.getBlockState"));
    }

    @Test
    void formUsesPublicNameInsteadOfObjectToString() {
        assertEquals("Alolan", DungeonPokemonFacts.formName(new NamedForm()));
    }

    @Test
    void runtimeScanKeepsStateAndChunkOwnsLazyBlockEntityTraversal() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/dev/krul/cobblemon/dungeon/DungeonWorldProbe.java"));
        assertFalse(source.contains("new ScanCursor(), 128"));
        assertFalse(source.contains("Utils.blockEntities()"));
        assertTrue(source.contains("ChunkAccessor"));
        assertTrue(source.contains("getBlockEntities"));
        assertTrue(source.contains("complete"));
        assertTrue(source.contains("runtimeCursor"));
        assertTrue(source.contains("getOwnerUUID") || java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/dev/krul/cobblemon/dungeon/DungeonPokemonFacts.java")).contains("getOwnerUUID"));
        assertFalse(java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/dev/krul/cobblemon/dungeon/DungeonPokemonFacts.java"))
            .contains("resolve(known.species(), DiscoveryRarityIndex.empty())"));
        assertFalse(source.contains("backingBlockEntities"));
        assertTrue(source.contains("snapshot(((ChunkAccessor) chunk).getBlockEntities().values())"));
    }

    @Test
    void probeResultExposesPassCompletion() {
        DungeonWorldProbe.BudgetedResult result = new DungeonWorldProbe().fromLoadedData("minecraft:overworld", 1,
            List.of(), List.of(), List.of(), List.of(), null, 1, new DungeonWorldProbe.ScanCursor(), 1);
        assertTrue(result.complete());
        assertTrue(result.result().complete());
    }

    @Test
    void pokemonCoreStringsAreBounded() {
        String longText = "x".repeat(500);
        DungeonObservation observation = assertSingle(new DungeonWorldProbe().fromLoadedData("minecraft:overworld", 0,
            List.of(), List.of(), List.of(), List.of(new DungeonWorldProbe.PokemonData(longText, longText, 1,
                false, false, List.of(longText), 1, "uuid", longText, 0, 0, 0, Map.of())), null, 1).observations());
        assertTrue(observation.registryId().length() <= 128);
        assertTrue(observation.name().length() <= 128);
        assertTrue(observation.facts().get("form").length() <= 128);
        assertTrue(observation.facts().get("aspects").length() <= 128);
    }

    @Test
    void completedSameDimensionCursorStartsANewPass() {
        DungeonWorldProbe probe = new DungeonWorldProbe();
        DungeonWorldProbe.ScanCursor cursor = new DungeonWorldProbe.ScanCursor();
        List<DungeonWorldProbe.EntityData> entities = List.of(new DungeonWorldProbe.EntityData(
            "minecraft:zombie", "Zombie", 1, "uuid", 1, 2, 3, "", Map.of()));

        DungeonWorldProbe.BudgetedResult first = probe.fromLoadedData("minecraft:overworld", 0,
            List.of(), List.of(), entities, List.of(), null, 1, cursor, 8);
        DungeonWorldProbe.BudgetedResult second = probe.fromLoadedData("minecraft:overworld", 0,
            List.of(), List.of(), entities, List.of(), null, 2, cursor, 8);

        assertTrue(first.complete());
        assertTrue(second.complete());
        assertEquals(1, first.result().observations().size());
        assertEquals(1, second.result().observations().size());
    }

    @Test
    void rawBackingSlotsEachConsumeOneWorkUnitEvenWhenEmpty() {
        DungeonWorldProbe.ScanCursor cursor = new DungeonWorldProbe.ScanCursor();
        cursor.initializeBackingSlots(new AtomicReferenceArray<net.minecraft.world.chunk.Chunk>(3),
            List.of(), null);

        assertTrue(cursor.nextWork().consumed());
        assertTrue(cursor.nextWork().consumed());
        assertTrue(cursor.nextWork().consumed());
        assertNull(cursor.nextWork());
    }

    @Test
    void observationNamesAndJoinedFactsAreAlwaysBounded() {
        String longText = "x".repeat(500);
        DungeonWorldProbe.ProbeResult result = new DungeonWorldProbe().fromLoadedData("minecraft:overworld", 0,
            List.of(), List.of(new DungeonWorldProbe.BlockEntityData(longText, longText, new BlockPos(1, 2, 3), longText, List.of())),
            List.of(new DungeonWorldProbe.EntityData(longText, longText, 1, longText, 1, 2, 3, longText, Map.of("state", longText))),
            List.of(new DungeonWorldProbe.PokemonData(longText, longText, 1, false, false, List.of(longText, longText),
                1, longText, longText, 0, 0, 0, Map.of("rarity", longText))),
            new DungeonWorldProbe.CrosshairData(DungeonObservation.Kind.BLOCK, longText, longText, new BlockPos(4, 5, 6), longText), 1);

        for (DungeonObservation observation : result.observations()) {
            assertTrue(observation.registryId().length() <= 128);
            assertTrue(observation.className().length() <= 128);
            assertTrue(observation.name().length() <= 128);
            assertTrue(observation.facts().values().stream().allMatch(value -> value.length() <= 128));
        }
    }

    private static final class NamedForm {
        public String getName() { return "Alolan"; }
        @Override public String toString() { return "wrong-toString"; }
    }

    private static DungeonObservation assertSingle(List<DungeonObservation> observations) {
        assertEquals(1, observations.size());
        return observations.get(0);
    }
}

