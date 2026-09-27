package dev.krul.cobblemon.battle.matchup;

import static dev.krul.cobblemon.battle.matchup.MatchupPanelLayout.*;

/** Shared tab geometry, never battle state. Restore the native rectangle only on close. */
public final class MatchupPaneGeometry {
    private Rect original;
    private int viewportWidth, viewportHeight;

    public Rect update(boolean matrix, Rect current, int width, int height, int own, int enemies) {
        if (original == null) {
            if (!matrix) return current;
            original = current;
            viewportWidth = width;
            viewportHeight = height;
            Rect compact = compactPane(current, width, height, 6, 6);
            return clampToViewport(new Rect(width - compact.width() - 24, height - compact.height() - 24,
                compact.width(), compact.height()), width, height);
        }
        if (viewportWidth == width && viewportHeight == height) return current;
        viewportWidth = width;
        viewportHeight = height;
        return clampToViewport(current, width, height);
    }

    private static Rect clampToViewport(Rect pane, int width, int height) {
        int paneWidth = Math.min(pane.width(), width);
        int paneHeight = Math.min(pane.height(), Math.max(0, height - 16));
        int top = Math.min(16, height);
        return new Rect(Math.clamp(pane.x(), 0, Math.max(0, width - paneWidth)),
            Math.clamp(pane.y(), top, Math.max(top, height - paneHeight)), paneWidth, paneHeight);
    }

    public Rect restore() {
        Rect previous = original;
        original = null;
        return previous;
    }
}

