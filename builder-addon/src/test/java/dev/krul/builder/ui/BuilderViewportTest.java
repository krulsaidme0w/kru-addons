package dev.krul.builder.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuilderViewportTest {
    @Test void chatContentUsesOneAndAHalfTimesThePreviousHeightWithViewportClamping() {
        assertEquals(450, BuilderViewport.contentHeight(1000, 450, 0.675));
        assertEquals(337.5, BuilderViewport.contentHeight(500, 450, 0.675));
    }

    @Test void settingsViewportUsesWindowSpaceAfterReservingOnlyItsChrome() {
        assertEquals(581, BuilderViewport.maxWindowViewHeight(629, 48));
    }

    @Test void shortWindowNeverGivesTheViewportNegativeHeight() {
        assertEquals(0, BuilderViewport.maxWindowViewHeight(40, 48));
    }

    @Test void scrollViewportUsesPhysicalWindowHeightEvenWhenScreenHeightStartsAtZero() {
        assertEquals(1008, BuilderViewport.maxHeight(1080, 72));
    }

    @Test void shortPhysicalWindowDoesNotForceViewportPastItsAvailableHeight() {
        assertEquals(108, BuilderViewport.maxHeight(180, 72));
    }

    @Test void chatLayoutKeepsComposerAndTranscriptVisibleAndCollapsesSidebarWhenNarrow() {
        var narrow = BuilderViewport.chatLayout(854, 480, 1);
        var desktop = BuilderViewport.chatLayout(1191, 628, 1);
        var large = BuilderViewport.chatLayout(1920, 1080, 2);
        assertFalse(narrow.sidebarVisible());
        assertTrue(desktop.sidebarVisible());
        assertTrue(large.sidebarVisible());
        for (var layout : java.util.List.of(narrow, desktop, large)) {
            assertTrue(layout.transcriptHeight() >= 120 * 1);
            assertTrue(layout.composerHeight() >= 42);
        }
        assertTrue(desktop.sidebarWidth() >= 145);
        assertTrue(large.sidebarWidth() <= 460);
    }
}
