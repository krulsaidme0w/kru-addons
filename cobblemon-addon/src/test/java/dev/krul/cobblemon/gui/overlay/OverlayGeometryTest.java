package dev.krul.cobblemon.gui.overlay;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverlayGeometryTest {
    @Test void oversizedPanelFitsViewportAndInputUsesRenderedScale() {
        var g = OverlayGeometry.fit(900, -30, 400, 300, 2, 208, 158);
        assertEquals(.5, g.scale(), .0001);
        assertEquals(4, g.x(), .0001);
        assertEquals(4, g.y(), .0001);
        assertEquals(200, g.localX(104), .0001);
        assertEquals(150, g.localY(79), .0001);
        assertTrue(g.contains(203, 153));
        assertFalse(g.contains(205, 153));
    }
    @Test void resizeKeepsPanelReachableAndInvalidSavedNumbersAreSanitized() {
        var g = OverlayGeometry.fit(Double.NaN, Double.POSITIVE_INFINITY, 300, 200, Double.NaN, 800, 500);
        assertTrue(Double.isFinite(g.x()));
        assertTrue(Double.isFinite(g.y()));
        assertEquals(1, g.scale());
        var shrunk = OverlayGeometry.fit(750, 400, 300, 200, 1, 500, 300);
        assertEquals(196, shrunk.x());
        assertEquals(96, shrunk.y());
    }
    @Test void releaseFollowsPressOwnerEvenOutsidePanelOrAfterClose() {
        var p = new PointerCapture();
        assertFalse(p.release(0));
        p.capture(0);
        assertFalse(p.release(1));
        assertTrue(p.release(0));
        assertFalse(p.release(0));
    }
    @Test void tinyViewportClipsInsteadOfShrinkingBelowReadableMinimum() {
        var g = OverlayGeometry.fit(0, 0, 400, 300, 1, 108, 88);
        assertEquals(.5, g.scale());
        assertEquals(200, g.width());
        assertEquals(160, g.height());
        assertFalse(g.contains(105, 30));
    }
}
