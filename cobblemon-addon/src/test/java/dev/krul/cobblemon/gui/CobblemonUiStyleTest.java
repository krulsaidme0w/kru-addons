package dev.krul.cobblemon.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CobblemonUiStyleTest {
    @Test
    void sharedPaletteKeepsPanelsOnOneMeteorVisualContract() {
        assertEquals(0xE6111111, CobblemonUiStyle.PANEL_BACKGROUND);
        assertEquals(0xFF2B2B35, CobblemonUiStyle.PANEL_BORDER);
        assertEquals(0xFFF0F0F0, CobblemonUiStyle.TEXT_PRIMARY);
        assertEquals(0xFFB8B8C4, CobblemonUiStyle.TEXT_MUTED);
        assertEquals(0xFFFFD35A, CobblemonUiStyle.ACCENT);
        assertEquals(8, CobblemonUiStyle.PADDING);
        assertEquals(4, CobblemonUiStyle.GAP);
    }

    @Test
    void colorsAreOpaqueWhenUsedForSolidMeteorSurfaceLayers() {
        assertEquals(0xFF, CobblemonUiStyle.PANEL_BORDER >>> 24);
        assertEquals(0xFF, CobblemonUiStyle.TEXT_PRIMARY >>> 24);
        assertEquals(0xFF, CobblemonUiStyle.TEXT_MUTED >>> 24);
        assertEquals(0xFF, CobblemonUiStyle.ACCENT >>> 24);
    }
}
