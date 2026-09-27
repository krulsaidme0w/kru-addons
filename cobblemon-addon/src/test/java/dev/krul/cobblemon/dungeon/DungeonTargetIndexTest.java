package dev.krul.cobblemon.dungeon;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DungeonTargetIndexTest {
    @Test void allLoadedTargetsSurviveBeyondDiagnosticCapacity() {
        var index = new DungeonTargetIndex(TestProfiles.PROFILE);
        for (int i = 0; i < 5000; i++) index.observe(observation("chest-" + i, "cobblemon:chest"));
        assertEquals(5000, index.observations().size());
        assertEquals("chest-0", index.observations().getFirst().identity());
    }
    @Test void pruningUnloadedTargetsDoesNotRetainHistoricalRunData() {
        var index = new DungeonTargetIndex(TestProfiles.PROFILE);
        index.observe(observation("loaded", "cobblemon:chest"));
        index.observe(observation("gone", "cobblemon:chest"));
        index.retainLoaded(java.util.Set.of("loaded"));
        assertEquals(java.util.List.of("loaded"), index.observations().stream().map(DungeonObservation::identity).toList());
    }
    @Test void diagnosticNoiseCannotEvictRecognizedTargetsDuringLongPass() {
        var index = new DungeonTargetIndex(TestProfiles.PROFILE);
        index.observe(observation("chest", "cobblemon:chest"));
        for (int i = 0; i < 8000; i++) index.observe(observation("noise-" + i, "minecraft:sign"));
        assertEquals(1, index.observations().size());
        assertEquals("chest", index.observations().getFirst().identity());
        assertEquals(1, DungeonRenderSelector.select(index.observations(), "dungeon", 60_000,
            Long.MAX_VALUE, 0, 0, 0, 1024, DungeonTargetFilters.ALL, TestProfiles.PROFILE).size());
    }
    @Test void reclassificationAndSessionResetRemoveOldTargets() {
        var index = new DungeonTargetIndex(TestProfiles.PROFILE);
        index.observe(observation("chest", "cobblemon:chest"));
        index.observe(observation("chest", "minecraft:sign"));
        assertTrue(index.observations().isEmpty());
        index.observe(observation("chest", "cobblemon:chest"));
        index.clear();
        assertTrue(index.observations().isEmpty());
    }
    private static DungeonObservation observation(String identity, String registry) {
        return new DungeonObservation(DungeonObservation.Kind.BLOCK_ENTITY, "dungeon", identity,
            registry, "fixture", "", new BlockPos(1, 2, 3), Map.of(), 100);
    }
}
