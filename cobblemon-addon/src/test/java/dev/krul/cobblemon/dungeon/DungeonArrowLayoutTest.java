package dev.krul.cobblemon.dungeon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonArrowLayoutTest {
    @Test
    void pointsRightTowardOffscreenTargetAtBoundedRadius() {
        double[] triangle = DungeonArrowLayout.triangle(1_000, 50, 200, 100, 10, 8);

        assertEquals(140, triangle[0], 0.001);
        assertEquals(50, triangle[1], 0.001);
        assertTrue(triangle[2] < triangle[0]);
        assertTrue(triangle[4] < triangle[0]);
    }

    @Test
    void pointsUpTowardOffscreenTarget() {
        double[] triangle = DungeonArrowLayout.triangle(100, -1_000, 200, 100, 10, 8);

        assertEquals(100, triangle[0], 0.001);
        assertEquals(10, triangle[1], 0.001);
        assertTrue(triangle[3] > triangle[1]);
        assertTrue(triangle[5] > triangle[1]);
    }
}

