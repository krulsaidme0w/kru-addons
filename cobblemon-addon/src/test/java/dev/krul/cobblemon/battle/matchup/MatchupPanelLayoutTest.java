package dev.krul.cobblemon.battle.matchup;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.battle.matchup.MatchupPanelLayout.*;

class MatchupPanelLayoutTest {
    @Test void initialSixBySixLeavesPaddingAndKeepsEveryCellReachableAtCommonGuiScales() {
        for (int[] viewport : new int[][]{{640, 360}, {800, 465}, {426, 260}, {533, 310}}) {
            var pane = new MatchupPaneGeometry().update(true, new Rect(0, 20, 150, 46),
                viewport[0], viewport[1], 6, 6);
            var layout = fit(pane, viewport[0], viewport[1], 6, 6);
            assertEquals(6, layout.visibleColumns());
            assertEquals(6, layout.visibleRows());
            assertEquals(pane.width(), pane.height(), "The initial matrix is square");
            assertTrue(pane.height() <= 227, "Reduced height must not regress to the old tall pane");
            assertTrue(pane.x() + pane.width() <= viewport[0] - 20, "Native width setter edge guard");
            assertTrue(pane.y() + pane.height() <= viewport[1] - 20, "Native height setter edge guard");
            for (int c = 0; c < 6; c++) for (int r = 0; r < 6; r++) {
                Rect cell = layout.cell(c, r);
                inside(cell, viewport[0], viewport[1]);
                assertArrayEquals(new int[]{c, r}, layout.hit(cell.x() + cell.width() / 2., cell.y() + cell.height() / 2.));
            }
            Rect bottomRight = layout.cell(5, 5);
            assertTrue(bottomRight.x() + bottomRight.width() < layout.body().x() + layout.body().width());
            assertTrue(bottomRight.y() + bottomRight.height() < layout.body().y() + layout.body().height());
        }
    }

    @Test void backdropCoversHeaderRuleButPreservesSevenPixelNativeFrame() {
        Rect pane = new Rect(100, 100, 228, 232);
        var layout = fit(pane, 784, 420, 6, 6);
        assertEquals(107, layout.backdrop().x());
        assertEquals(107, layout.backdrop().y());
        assertTrue(layout.backdrop().contains(107, 110), "Native header starts at x+7");
        assertFalse(layout.backdrop().contains(106, 110), "The left texture is seven pixels wide");
        assertFalse(layout.backdrop().contains(110, 106), "The top texture is seven pixels tall");
    }
    @Test void allRectanglesStayInViewportWhenNativeLogIsDraggedOffscreen() {
        for (int[] size : new int[][]{{320, 240}, {784, 420}, {1568, 840}}) {
            for (Rect pane : new Rect[]{new Rect(size[0] - 200, size[1] - 150, 350, 250), new Rect(-30, -20, 700, 450)}) {
                var layout = fit(pane, size[0], size[1], 6, 6);
                inside(layout.body(), size[0], size[1]);
                inside(layout.historyTab(), size[0], size[1]);
                inside(layout.matchupTab(), size[0], size[1]);
                for (int c = 0; c < layout.visibleColumns(); c++) for (int r = 0; r < layout.visibleRows(); r++) inside(layout.cell(c, r), size[0], size[1]);
            }
        }
    }
    @Test void iconGridDoesNotGrowWithResourcePackTextWidth() {
        var visible = fit(new Rect(0, 20, 700, 390), 784, 420, 6, 6, 86);
        assertEquals(6, visible.visibleColumns());
        assertTrue(visible.cellWidth() <= 26);
        var compact = fit(new Rect(0, 20, 700, 390), 784, 420, 6, 6, 120);
        assertEquals(6, compact.visibleColumns());
        assertEquals(6, compact.visibleRows());
    }
    @Test void emptyRosterHasNoPhantomCellTargets() {
        var empty = fit(new Rect(0, 20, 700, 390), 784, 420, 0, 6);
        assertEquals(0, empty.visibleColumns());
        assertEquals(0, empty.visibleRows());
        assertNull(empty.hit(120, 100));
    }
    static void inside(Rect r, int width, int height) {
        assertTrue(r.x() >= 0 && r.y() >= 0 && r.width() >= 0 && r.height() >= 0);
        assertTrue(r.x() + r.width() <= width && r.y() + r.height() <= height, r.toString());
    }
}

