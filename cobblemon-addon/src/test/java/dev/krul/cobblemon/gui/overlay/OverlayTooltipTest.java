package dev.krul.cobblemon.gui.overlay;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverlayTooltipTest {
    @Test void tooltipFlipsAtBottomRightInsteadOfLeavingViewport() {
        var box = OverlayTooltip.place(395, 295, 120, 40, 400, 300);
        assertTrue(box.x() >= 0 && box.y() >= 0);
        assertTrue(box.x() + box.width() <= 400);
        assertTrue(box.y() + box.height() <= 300);
        assertTrue(box.x() < 395 && box.y() < 295);
    }
    @Test void oversizedTooltipIsClippedToTinyViewport() {
        var box = OverlayTooltip.place(10, 10, 500, 90, 80, 60);
        assertTrue(box.width() <= 80 && box.height() <= 60);
        assertTrue(box.x() >= 0 && box.y() >= 0);
    }
    @Test void longExplanationWrapsWithoutDroppingWordsOrSplittingSurrogates() {
        var lines = OverlayTooltip.wrap(java.util.List.of("Type chart; not damage. Abilities and items excluded."), 18, String::length);
        assertEquals("Type chart; not damage. Abilities and items excluded.", String.join(" ", lines));
        assertTrue(lines.stream().allMatch(line -> line.length() <= 18));
        assertEquals(java.util.List.of("ab", "\ud83d\ude00", "cd"), OverlayTooltip.wrap(java.util.List.of("ab\ud83d\ude00cd"), 2, String::length));
    }
}
