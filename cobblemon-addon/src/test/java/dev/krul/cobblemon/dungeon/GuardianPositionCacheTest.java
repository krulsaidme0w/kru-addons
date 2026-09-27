package dev.krul.cobblemon.dungeon;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuardianPositionCacheTest {
    @Test
    void remembersCoordinatesAcrossEntityRefreshesAndDeduplicatesThem() {
        GuardianPositionCache cache = new GuardianPositionCache();
        BlockPos position = new BlockPos(12, 64, -3);

        assertTrue(cache.remember("dungeons:dungeons", position));
        assertFalse(cache.remember("dungeons:dungeons", position));
        assertTrue(cache.contains("dungeons:dungeons", position));
        assertEquals(1, cache.positions("dungeons:dungeons").size());
    }

    @Test
    void dimensionChangeAndClearRemoveRememberedGuardians() {
        GuardianPositionCache cache = new GuardianPositionCache();
        BlockPos position = new BlockPos(1, 2, 3);

        cache.remember("dungeons:dungeons", position);
        cache.beginDimension("minecraft:overworld");
        assertFalse(cache.contains("dungeons:dungeons", position));

        cache.remember("minecraft:overworld", position);
        cache.clear();
        assertEquals(0, cache.size());
    }
}

