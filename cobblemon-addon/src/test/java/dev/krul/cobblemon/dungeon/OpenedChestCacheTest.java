package dev.krul.cobblemon.dungeon;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenedChestCacheTest {

    @Test
    void remembersAnOpenedChestUntilTheDimensionChanges() {
        OpenedChestCache cache = new OpenedChestCache();
        BlockPos chest = new BlockPos(12, 64, -3);

        cache.markOpen("dungeons:dungeons", chest);

        assertTrue(cache.isOpen("dungeons:dungeons", chest));
        assertFalse(cache.isOpen("minecraft:overworld", chest));

        cache.beginDimension("minecraft:overworld");
        assertFalse(cache.isOpen("dungeons:dungeons", chest));
        assertFalse(cache.isOpen("minecraft:overworld", chest));
    }

    @Test
    void repeatedSignalsAreIdempotentAndClearRemovesEverything() {
        OpenedChestCache cache = new OpenedChestCache();
        BlockPos chest = new BlockPos(1, 2, 3);

        assertTrue(cache.markOpen("dungeons:dungeons", chest));
        assertFalse(cache.markOpen("dungeons:dungeons", chest));
        assertEquals(1, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        assertFalse(cache.isOpen("dungeons:dungeons", chest));
    }
}
