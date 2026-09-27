package dev.krul.cobblemon.battle.matchup;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.battle.matchup.MatchupPanelLayout.*;

class MatchupInlineLayoutTest {
    @Test void compactWindowFitsAllThirtySixPairsWithoutTextColumns() {
        var layout = fit(new Rect(0, 20, 250, 240), 784, 420, 6, 6);
        assertEquals(6, layout.visibleColumns());
        assertEquals(6, layout.visibleRows());
        assertTrue(layout.cellWidth() >= 20 && layout.cellWidth() <= 24);
    }

    @Test void shortWindowFitsAllRowsWithoutReservingAnInlineCard() {
        var layout = fit(new Rect(0, 20, 700, 220), 784, 420, 6, 6);
        assertEquals(6, layout.visibleRows());
        assertEquals(6, layout.visibleColumns());
    }

    @Test void expandedWindowKeepsTheMatrixCompact() {
        var layout = fit(new Rect(0, 20, 700, 390), 784, 420, 6, 6);
        assertEquals(6, layout.visibleRows());
        assertEquals(6, layout.visibleColumns());
        var last = layout.cell(5, 5);
        assertArrayEquals(new int[]{5, 5}, layout.hit(last.x() + 2, last.y() + 2));
        assertTrue(last.x() + last.width() - layout.body().x() <= 220);
    }

    @Test void singlePairDoesNotStretchAcrossTheWholeBattleLog() {
        var layout = fit(new Rect(0, 20, 700, 390), 784, 420, 1, 1);
        assertEquals(1, layout.visibleColumns());
        assertTrue(layout.cellWidth() <= 140);
        assertNull(layout.hit(600, 85));
    }
}
