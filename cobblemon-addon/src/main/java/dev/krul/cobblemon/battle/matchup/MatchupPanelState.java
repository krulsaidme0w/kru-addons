package dev.krul.cobblemon.battle.matchup;

/** Local tab / pinned-card selection only; no battle recorder, wheel listener or battle actions. */
public final class MatchupPanelState {
    private boolean matrix, enabled;
    private String selectedFighter;
    private int page;
    public boolean matrix() { return matrix; }
    public String selectedFighter() { return selectedFighter; }
    public int page() { return page; }
    public void clearCard() { selectedFighter = null; page = 0; }
    public void retain(MatchupSnapshot snapshot) {
        if (selectedFighter != null && java.util.stream.Stream.concat(snapshot.enemies().stream(), snapshot.own().stream())
            .noneMatch(f -> selectedFighter.equals(f.id()))) clearCard();
    }
    public boolean setEnabled(boolean enabled) {
        if (enabled && !this.enabled) matrix = true;
        this.enabled = enabled;
        if (!enabled) { matrix = false; clearCard(); }
        return enabled;
    }
    public boolean click(MatchupPanelLayout layout, double x, double y, int button) {
        return click(layout, null, MatchupPanelRenderer.CardControls.EMPTY, x, y, button);
    }
    public boolean click(MatchupPanelLayout layout, MatchupSnapshot snapshot, MatchupPanelRenderer.CardControls card,
                         double x, double y, int button) {
        if (!enabled || button != 0) return false;
        if (matrix && selectedFighter != null && card.box().contains(x, y)) {
            if (card.close().contains(x, y)) clearCard();
            else if (card.previous().contains(x, y)) page = Math.max(0, card.page() - 1);
            else if (card.next().contains(x, y)) page = Math.min(card.pages() - 1, card.page() + 1);
            return true;
        }
        if (layout.historyTab().contains(x, y)) { matrix = false; clearCard(); return true; }
        if (layout.matchupTab().contains(x, y)) { matrix = true; return true; }
        if (matrix && snapshot != null) for (int row = 0; row < layout.visibleRows(); row++) {
            if (layout.enemyHeader(row).contains(x, y)) {
                String id = snapshot.enemies().get(row).id();
                selectedFighter = id.equals(selectedFighter) ? null : id;
                page = 0;
                return true;
            }
        }
        if (matrix && snapshot != null) for (int col = 0; col < layout.visibleColumns(); col++) {
            if (layout.ownHeader(col).contains(x, y)) {
                String id = snapshot.own().get(col).id();
                selectedFighter = id.equals(selectedFighter) ? null : id;
                page = 0;
                return true;
            }
        }
        clearCard();
        // Cells and the native frame still own drag initiation; only portraits/cards consume clicks.
        return false;
    }
}

