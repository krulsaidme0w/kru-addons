package dev.krul.cobblemon.battle.matchup;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchupPanelStateTest {
    @Test void enemyClickPinsByStableIdentityAndTabSwitchOrDisableClosesTheCard() {
        var state = new MatchupPanelState();
        state.setEnabled(true);
        var snapshot = MatchupRenderPreviewTest.teams();
        var layout = MatchupPanelLayout.fit(new MatchupPanelLayout.Rect(500, 160, 228, 232), 784, 420, 6, 6);
        var header = layout.enemyHeader(1);
        var empty = MatchupPanelRenderer.CardControls.EMPTY;
        assertTrue(state.click(layout, snapshot, empty, header.x() + 2, header.y() + 2, 0));
        assertEquals(snapshot.enemies().get(1).id(), state.selectedFighter());
        var surface = new MatchupPanelRendererTest.Surface();
        var card = MatchupPanelRenderer.render(surface, layout, snapshot, MatchupCalculator.calculate(snapshot), true, state.selectedFighter(), 0, 0, 0);
        assertTrue(state.click(layout, snapshot, card, card.box().x() + 5, card.box().y() + 30, 0), "Reading card must not start native drag");
        assertEquals(snapshot.enemies().get(1).id(), state.selectedFighter());
        state.retain(new MatchupSnapshot(snapshot.battleId(), 2, snapshot.own(), snapshot.enemies().reversed(), java.util.List.of()));
        assertEquals(snapshot.enemies().get(1).id(), state.selectedFighter(), "Roster reordering must not select another opponent");
        assertTrue(state.click(layout, snapshot, card, card.close().x() + 1, card.close().y() + 1, 0));
        assertNull(state.selectedFighter());
        state.click(layout, snapshot, empty, header.x() + 2, header.y() + 2, 0);
        state.click(layout, snapshot, empty, layout.historyTab().x() + 1, layout.historyTab().y() + 1, 0);
        assertNull(state.selectedFighter());
        assertFalse(state.matrix());
        state.setEnabled(false);
        state.setEnabled(true);
        state.click(layout, snapshot, empty, header.x() + 2, header.y() + 2, 0);
        state.retain(new MatchupSnapshot(snapshot.battleId(), 2, snapshot.own(), java.util.List.of(), java.util.List.of()));
        assertNull(state.selectedFighter(), "Missing identities leave no stale card");
    }
    @Test void activationOpensMatchupsButRefreshingEnabledDoesNotOverrideHistoryChoice() {
        var state = new MatchupPanelState();
        var layout = MatchupPanelLayout.fit(new MatchupPanelLayout.Rect(100, 100, 228, 232), 784, 420, 6, 6);
        state.setEnabled(true);
        assertTrue(state.matrix());
        state.click(layout, layout.historyTab().x() + 2, layout.historyTab().y() + 2, 0);
        state.setEnabled(true);
        assertFalse(state.matrix());
        state.setEnabled(false);
        state.setEnabled(true);
        assertTrue(state.matrix());
    }
    @Test void compactPanelCannotInterceptNativeFrameThroughHiddenControls() {
        var state = new MatchupPanelState();
        state.setEnabled(true);
        var layout = MatchupPanelLayout.fit(new MatchupPanelLayout.Rect(100, 100, 153, 46), 784, 420, 6, 6);
        state.click(layout, layout.matchupTab().x() + 2, layout.matchupTab().y() + 2, 0);
        assertEquals(0, layout.visibleColumns());
        assertFalse(state.click(layout, 160, 101, 0), "Native top frame must still start dragging");
    }
    @Test void disabledModulePassesInputAndReturnsToNativeHistory() {
        var state = new MatchupPanelState();
        var layout = MatchupPanelLayout.fit(new MatchupPanelLayout.Rect(100, 100, 500, 280), 784, 420, 1, 1);
        assertFalse(state.click(layout, layout.matchupTab().x() + 2, layout.matchupTab().y() + 2, 0));
        state.setEnabled(true);
        assertTrue(state.click(layout, layout.matchupTab().x() + 2, layout.matchupTab().y() + 2, 0));
        assertTrue(state.matrix());
        state.setEnabled(false);
        assertFalse(state.matrix());
        assertFalse(state.click(layout, 150, 150, 0));
        state.setEnabled(true);
        assertFalse(state.click(layout, 150, 150, 0));
    }
    @Test void hoverOnlyMatrixPassesClicksForNativeDraggingAndOwnsOnlyTabs() {
        var state = new MatchupPanelState();
        state.setEnabled(true);
        var layout = MatchupPanelLayout.fit(new MatchupPanelLayout.Rect(100, 100, 500, 280), 784, 420, 1, 1);
        assertFalse(state.click(layout, 150, 150, 0));
        assertTrue(state.click(layout, layout.matchupTab().x() + 3, layout.matchupTab().y() + 3, 0));
        assertFalse(state.click(layout, 150, 150, 0), "Hover-only interior must let native pane start dragging");
        assertFalse(state.click(layout, 101, 101, 0));
        assertFalse(state.click(layout, 598, 378, 0), "Native resize handle remains usable");
        assertFalse(state.click(layout, 20, 350, 0));
        assertTrue(state.click(layout, layout.historyTab().x() + 3, layout.historyTab().y() + 3, 0));
        assertFalse(state.matrix());
    }
    @Test void otherMouseButtonsDoNotSwitchTabs() {
        var state = new MatchupPanelState();
        state.setEnabled(true);
        var layout = MatchupPanelLayout.fit(new MatchupPanelLayout.Rect(100, 100, 500, 280), 784, 420, 1, 1);
        state.click(layout, layout.historyTab().x() + 2, layout.historyTab().y() + 2, 0);
        assertFalse(state.click(layout, layout.matchupTab().x() + 2, layout.matchupTab().y() + 2, 1));
        assertFalse(state.matrix());
        state.click(layout, layout.matchupTab().x() + 2, layout.matchupTab().y() + 2, 0);
        assertFalse(state.click(layout, 150, 150, 3), "Native mouse-side-button bindings must pass through the matrix");
    }
}

