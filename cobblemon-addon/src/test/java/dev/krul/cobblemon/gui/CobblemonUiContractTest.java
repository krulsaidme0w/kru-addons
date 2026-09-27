package dev.krul.cobblemon.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CobblemonUiContractTest {
    @Test
    void typeChartAndBattleSurfacesShareMeteorPalette() {
        assertEquals(0xFF111111, CobblemonUiStyle.PANEL_BACKGROUND_SOLID);
        assertEquals(0xFF2B2B35, CobblemonUiStyle.PANEL_BORDER);
        assertEquals(0xFFF0F0F0, CobblemonUiStyle.TEXT_PRIMARY);
        assertEquals(0xFFB8B8C4, CobblemonUiStyle.TEXT_MUTED);
        assertEquals(0xFFFFD28A, CobblemonUiStyle.WARNING);
    }

    @Test
    void panelBoundsStayInsideViewportAndRespectMargin() {
        var bounds = CobblemonPanelGeometry.clamp(-30, -10, 900, 700, 640, 360, 8);

        assertEquals(new CobblemonPanelGeometry.Bounds(8, 8, 624, 344), bounds);
        assertTrue(bounds.contains(8, 8));
        assertFalse(bounds.contains(632, 352));
    }

    @Test
    void tooltipFlipsAndClampsInsteadOfEscapingViewport() {
        var anchor = new CobblemonPanelGeometry.Bounds(600, 330, 20, 20);
        var tooltip = CobblemonPanelGeometry.tooltip(anchor, 620, 350, 180, 80, 640, 360, 4);

        assertTrue(tooltip.x() < 620, "tooltip must flip left of the cursor");
        assertTrue(tooltip.y() < 350, "tooltip must flip above the cursor");
        assertTrue(tooltip.x() >= 4 && tooltip.y() >= 4);
        assertTrue(tooltip.x() + tooltip.width() <= 636);
        assertTrue(tooltip.y() + tooltip.height() <= 356);
    }

    @Test
    void unsupportedGeometryProducesNonInteractiveEmptyBounds() {
        var empty = CobblemonPanelGeometry.clamp(10, 10, 0, 0, 640, 360, 8);

        assertEquals(new CobblemonPanelGeometry.Bounds(0, 0, 0, 0), empty);
        assertFalse(empty.contains(10, 10));
    }
}
