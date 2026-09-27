package dev.krul.cobblemon.dungeon;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DungeonRenderCacheTest {
    @Test void sharesSelectionAcrossFramesAndTicksUntilOneSecondHasElapsed() {
        var cache = new DungeonRenderCache();
        Object world = new Object();
        var observations = DungeonRenderPerformanceTest.fixture();
        var first = cache.get(1, world, "all", () -> DungeonRenderPerformanceTest.select(observations));
        for (int i = 0; i < 100; i++) {
            assertSame(first, cache.get(1, world, "all", () -> fail("Repeated world selection in one tick")));
        }
        assertEquals(3200, first.size());
        for (int tick = 2; tick <= 20; tick++) {
            assertSame(first, cache.get(tick, world, "all", () -> fail("Selection before one second")));
        }
        assertTrue(cache.get(21, world, "all", List::of).isEmpty());
    }

    @Test void worldSettingsAndExplicitResetInvalidateEvenWithinSameTick() {
        var cache = new DungeonRenderCache();
        Object world = new Object();
        var targets = DungeonRenderPerformanceTest.select(DungeonRenderPerformanceTest.fixture());
        cache.get(1, world, "all", () -> targets);
        assertTrue(cache.get(1, world, "none", List::of).isEmpty());
        assertSame(targets, cache.get(1, new Object(), "none", () -> targets));
        cache.clear();
        assertTrue(cache.get(1, world, "all", List::of).isEmpty());
    }
}

