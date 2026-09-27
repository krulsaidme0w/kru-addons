package dev.krul.cobblemon.dungeon;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DungeonRenderPerformanceTest {
    static List<DungeonObservation> fixture() {
        var observations = new ArrayList<DungeonObservation>();
        for (int i = 0; i < 3200; i++) {
            boolean chest = i < 2300;
            observations.add(new DungeonObservation(chest ? DungeonObservation.Kind.BLOCK_ENTITY : DungeonObservation.Kind.ENTITY,
                "dungeons:dungeons", "target-" + i, chest ? "cobblemon:chest" : "minecraft:armor_stand",
                "fixture", chest ? "Gilded Chest" : "oasislayout1v13",
                new BlockPos(i % 100, 0, i / 100), chest ? Map.of() : Map.of("head_item_id", "minecraft:paper", "head_model_data", "1150"), 100));
        }
        return List.copyOf(observations);
    }

    @Test void measuresRepeatedSelectionOfLoadedDungeonScale() {
        var observations = fixture();
        for (int i = 0; i < 20; i++) select(observations);
        long started = System.nanoTime();
        for (int i = 0; i < 100; i++) assertEquals(3200, select(observations).size());
        System.out.printf("DUNGEON_BENCH baseline selections=100 observations=3200 totalMs=%.3f%n",
            (System.nanoTime() - started) / 1_000_000.0);
        var cache = new DungeonRenderCache();
        Object world = new Object();
        started = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            assertEquals(3200, cache.get(1 + i / 5, world, "all", () -> select(observations)).size());
        }
        System.out.printf("DUNGEON_BENCH sharedSecond consumers=100 ticks=20 observations=3200 totalMs=%.3f%n",
            (System.nanoTime() - started) / 1_000_000.0);
    }

    static List<DungeonRenderTarget> select(List<DungeonObservation> observations) {
        return DungeonRenderSelector.select(observations, "dungeons:dungeons", 100, 15000, 0, 0, 0, 1024, DungeonTargetFilters.ALL, TestProfiles.PROFILE);
    }
}
