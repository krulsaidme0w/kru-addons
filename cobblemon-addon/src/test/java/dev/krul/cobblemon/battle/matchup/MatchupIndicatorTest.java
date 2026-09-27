package dev.krul.cobblemon.battle.matchup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchupIndicatorTest {
    @Test
    void usesTwoOrThreeReadableArrowsForEffectiveness() {
        assertEquals("↑", MatchupIndicator.label(2.0, false));
        assertEquals("⇈", MatchupIndicator.label(4.0, false));
        assertEquals("↓", MatchupIndicator.label(.5, false));
        assertEquals("⇊", MatchupIndicator.label(.25, false));
    }

    @Test
    void incomingRiskReversesTheArrowDirection() {
        assertEquals("↑", MatchupIndicator.label(.5, true));
        assertEquals("↓", MatchupIndicator.label(2.0, true));
    }
}

