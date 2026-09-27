package dev.krul.cobblemon.battle.matchup;

/** Stable, readable effectiveness glyphs for the compact matchup grid. */
public final class MatchupIndicator {
    private MatchupIndicator() {}

    public static String label(Double value, boolean incoming) {
        if (value == null) return "?";
        if (value == 1) return "1";
        if (value == 0) return "×";
        boolean favorable = incoming ? value < 1 : value > 1;
        int arrows = value >= 4 || value <= .25 ? 3 : 2;
        if (arrows == 3) return favorable ? "⇈" : "⇊";
        return favorable ? "↑" : "↓";
    }
}

