package dev.krul.cobblemon.battle.matchup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.battle.matchup.MatchupPanelLayout.*;

class MatchupPaneGeometryTest {
    private final MatchupPaneGeometry geometry = new MatchupPaneGeometry();
    private final Rect nativePane = new Rect(450, 170, 650, 420);

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "6, 1", "6, 6"})
    void firstMatchupsUpdateReservesSixBySixAtBottomRight(int own, int enemies) {
        Rect pane = geometry.update(true, nativePane, 1200, 800, own, enemies);

        assertEquals(new Rect(958, 558, 218, 218), pane);
        var layout = fit(pane, 1200, 800, 6, 6);
        assertEquals(6, layout.visibleColumns());
        assertEquals(6, layout.visibleRows());
    }

    @Test void historyBeforeFirstMatchupsUpdateLeavesNativeGeometryAlone() {
        assertEquals(nativePane, geometry.update(false, nativePane, 1200, 800, 6, 6));
        Rect moved = new Rect(300, 100, 500, 300);
        assertEquals(moved, geometry.update(false, moved, 800, 600, 6, 1));
        assertNull(geometry.restore());

        assertEquals(new Rect(558, 358, 218, 218), geometry.update(true, moved, 800, 600, 1, 1));
        assertEquals(moved, geometry.restore());
    }

    @Test void tabsShareManualGeometryWithoutRestoringOrRecompacting() {
        geometry.update(true, nativePane, 1200, 800, 6, 6);
        Rect resized = new Rect(420, 200, 320, 300);

        assertEquals(resized, geometry.update(true, resized, 1200, 800, 6, 6));
        assertEquals(resized, geometry.update(false, resized, 1200, 800, 6, 6));
        Rect draggedInHistory = new Rect(250, 150, 340, 320);
        assertEquals(draggedInHistory, geometry.update(false, draggedInHistory, 1200, 800, 6, 6));
        assertEquals(draggedInHistory, geometry.update(true, draggedInHistory, 1200, 800, 6, 6));
        assertEquals(nativePane, geometry.restore());
    }

    @Test void rosterGrowthAndShrinkagePreserveManualGeometry() {
        geometry.update(true, nativePane, 1200, 800, 1, 1);
        Rect resized = new Rect(420, 200, 320, 300);

        assertEquals(resized, geometry.update(true, resized, 1200, 800, 6, 6));
        assertEquals(resized, geometry.update(true, resized, 1200, 800, 1, 1));
        assertEquals(resized, geometry.update(true, resized, 1200, 800, 0, 0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void unchangedViewportDoesNotClampManualGeometry(boolean matrix) {
        geometry.update(true, nativePane, 1200, 800, 6, 6);
        Rect draggedOutside = new Rect(-20, -5, 1400, 850);

        assertEquals(draggedOutside, geometry.update(matrix, draggedOutside, 1200, 800, 6, 6));
        Rect madeSmall = new Rect(500, 300, 150, 100);
        assertEquals(madeSmall, geometry.update(matrix, madeSmall, 1200, 800, 6, 6));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void viewportShrinkClampsPositionWithoutRecompactingManualSize(boolean matrix) {
        geometry.update(true, nativePane, 1200, 800, 6, 6);
        Rect resized = new Rect(900, 600, 280, 260);
        Rect clamped = geometry.update(matrix, resized, 800, 600, 6, 6);

        assertEquals(new Rect(520, 340, 280, 260), clamped);
        assertEquals(clamped, geometry.update(!matrix, clamped, 800, 600, 1, 1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void viewportGrowthPreservesManualGeometryInsteadOfReanchoring(boolean matrix) {
        geometry.update(true, nativePane, 1200, 800, 6, 6);
        Rect resized = new Rect(420, 200, 320, 300);

        assertEquals(resized, geometry.update(matrix, resized, 1600, 1000, 6, 6));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void viewportChangeKeepsTabsAboveThePaneVisible(boolean matrix) {
        geometry.update(true, nativePane, 1200, 800, 6, 6);

        assertEquals(new Rect(0, 16, 280, 260),
            geometry.update(matrix, new Rect(-30, -10, 280, 260), 800, 600, 6, 6));
    }

    @ParameterizedTest
    @CsvSource({"180, 140, 16, 124", "120, 10, 10, 0", "0, 0, 0, 0"})
    void smallViewportBoundsInitialPaneAndLaterManualSize(int width, int height, int top, int paneHeight) {
        Rect expected = new Rect(0, top, width, paneHeight);
        Rect initial = geometry.update(true, nativePane, width, height, 6, 6);
        assertEquals(initial.width(), initial.height());
        assertTrue(initial.width() <= Math.max(0, width - 24));
        assertTrue(initial.height() <= Math.max(0, height - 40));

        geometry.update(true, expected, 1200, 800, 6, 6);
        assertEquals(expected, geometry.update(true, new Rect(900, 600, 320, 300), width, height, 6, 6));
    }

    @Test void closeRestoresOriginalGeometryOnceAndNextSessionCapturesItsOwnOriginal() {
        geometry.update(true, nativePane, 1200, 800, 6, 6);
        geometry.update(false, new Rect(400, 200, 300, 280), 800, 600, 1, 1);

        assertEquals(nativePane, geometry.restore());
        assertNull(geometry.restore());

        Rect nextNative = new Rect(200, 80, 500, 350);
        assertEquals(new Rect(558, 358, 218, 218), geometry.update(true, nextNative, 800, 600, 1, 1));
        assertEquals(nextNative, geometry.restore());
        assertNull(geometry.restore());
    }
}

