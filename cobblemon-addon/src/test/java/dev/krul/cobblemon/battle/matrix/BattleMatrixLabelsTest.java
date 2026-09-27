package dev.krul.cobblemon.battle.matrix;

import org.junit.jupiter.api.Test;
import java.util.List;
import dev.krul.cobblemon.battle.PokemonType;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleMatrixLabelsTest {
    @Test void unknownIdentityHeaderExplainsWithheldFactsUsingReaderNote() {
        String reason = "Ambiguous identity / Illusion: projection does not match roster UUID; facts withheld.";
        Fighter fighter = new Fighter("slot", "Unknown", List.of(), List.of(), List.of(), -1, -1,
            false, false, "", "", false, "", List.of(reason));
        assertEquals(List.of("Unknown", "Current battle typing unknown.", reason), BattleMatrixContent.fighterTooltip(fighter));
    }
    @Test void moveAbbreviationsDistinguishSameTypeAndSameInitials() {
        assertEquals(List.of("FB1", "FF", "FB3", "FL"), BattleMatrixLabels.abbreviations(
            List.of("Fire Blast", "Fire Fang", "Flame Burst", "Flamethrower")));
        assertEquals(List.of("PS1", "PS2", "PS3", "PS4"), BattleMatrixLabels.abbreviations(
            List.of("Psychic", "Psyshock", "Psybeam", "Psycho Shift")));
    }
    @Test void blankOrLocalizedNamesStillHaveUsableDistinctLabels() {
        assertEquals(List.of("?1", "?2", "ÉC", "雷"), BattleMatrixLabels.abbreviations(
            List.of("", " ", "Éclair Charge", "雷")));
    }

    @Test void headersUseCurrentFactsAndOtherwiseShowKnownBaseTypes() {
        for (Freshness freshness : Freshness.values()) {
            Fighter fighter = new Fighter("own", "Charizard", List.of(), List.of(PokemonType.FIRE),
                List.of(), 50, 100, true, false, "", "", false, "", List.of(), Portrait.UNKNOWN,
                null, false, new Facts(null, new Fact<>(List.of(PokemonType.WATER), freshness, "fixture"),
                    null, null, null, null));
            assertEquals(freshness == Freshness.CURRENT ? "Water" : "Fire · base", BattleMatrixContent.typeText(fighter));
        }
    }
}
