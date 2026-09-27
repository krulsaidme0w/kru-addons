package dev.krul.cobblemon.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TypeChartContentTest {
    @Test void hoverExplainsCellRatherThanPinnedSelection() {
        var chart = new TypeChartContent();
        // Grid at (8,8), headers 28, cell 23x17; Fire row 1 / Grass column 4.
        assertEquals("Fire -> Grass: 2x", chart.tooltip(36 + 4 * 23 + 3, 36 + 17 + 3).getFirst());
        chart.click(36 + 4 * 23 + 3, 36 + 17 + 3, 0);
        assertEquals("Electric -> Ground: 0x", chart.tooltip(36 + 8 * 23 + 3, 36 + 3 * 17 + 3).getFirst());
    }

    @Test void headersAndNeutralCellsHaveTooltipButMarginsDoNot() {
        var chart = new TypeChartContent();
        assertEquals("Normal -> Normal: 1x", chart.tooltip(40, 40).getFirst());
        assertEquals("Normal - attack (row)", chart.tooltip(12, 40).getFirst());
        assertEquals("Normal - defence (column)", chart.tooltip(40, 12).getFirst());
        assertTrue(chart.tooltip(2, 2).isEmpty());
        assertTrue(chart.tooltip(chart.width(), chart.height()).isEmpty());
    }
    @Test void viewportHoverUsesPanelScaleTitleOffsetAndScrollTogether() {
        var chart = new TypeChartContent();
        var geometry = dev.krul.cobblemon.gui.overlay.OverlayGeometry.fit(100, 50, chart.width(), chart.height() + 22, .8, 1000, 800);
        // Fire/Grass cell (131,56), scrolled (20,10), below 22px title, at scale .8.
        var point = geometry.contentPoint(188.8, 104.4, 22, 20, 10);
        assertEquals("Fire -> Grass: 2x", chart.tooltip(point.x(), point.y()).getFirst());
    }
}
