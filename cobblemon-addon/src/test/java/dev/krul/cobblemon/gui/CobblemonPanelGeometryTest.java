package dev.krul.cobblemon.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CobblemonPanelGeometryTest {
    @Test
    void clampKeepsPanelAndTooltipInsideViewport() {
        var panel = CobblemonPanelGeometry.clamp(780, 470, 180, 140, 800, 500, 8);
        assertEquals(new CobblemonPanelGeometry.Bounds(612, 352, 180, 140), panel);

        var tooltip = CobblemonPanelGeometry.tooltip(panel, 790, 490, 220, 90, 800, 500, 8);
        assertTrue(tooltip.x() >= 8 && tooltip.y() >= 8);
        assertTrue(tooltip.x() + tooltip.width() <= 792);
        assertTrue(tooltip.y() + tooltip.height() <= 492);
    }

    @Test
    void containsUsesHalfOpenEdgesSoClicksOutsideDoNotLeakIntoPanel() {
        var box = new CobblemonPanelGeometry.Bounds(10, 20, 100, 60);
        assertTrue(box.contains(10, 20));
        assertTrue(box.contains(109, 79));
        assertFalse(box.contains(110, 40));
        assertFalse(box.contains(50, 80));
    }

    @Test
    void invalidDimensionsProduceSafeEmptyBounds() {
        assertEquals(new CobblemonPanelGeometry.Bounds(0, 0, 0, 0),
            CobblemonPanelGeometry.clamp(10, 10, -4, 20, 100, 100, 8));
        assertEquals(new CobblemonPanelGeometry.Bounds(0, 0, 0, 0),
            CobblemonPanelGeometry.tooltip(new CobblemonPanelGeometry.Bounds(10, 10, 0, 0), 0, 0,
                20, 20, 100, 100, 8));
    }
}
