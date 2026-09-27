package dev.krul.cobblemon.battle.facts;

import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.krul.cobblemon.battle.PokemonType.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleFactsModelTest {
    static Fighter fighter(String id) {
        return new Fighter(id, "Ralts", List.of(PSYCHIC, FAIRY), List.of(PSYCHIC, FAIRY), List.of(),
            20, 100, true, false, "Synchronize", "Leftovers", true, "", List.of());
    }

    @Test void legacyDefaultsNeverBecomeCurrentFacts() {
        Fighter f = fighter("a");
        assertEquals("?", BattleFactsModel.types(f.facts().types()));
        assertEquals("?", BattleFactsModel.health(f.facts().health()));
        String text = String.join("\n", BattleFactsModel.details(f, false));
        assertTrue(text.contains("Ability: ?"));
        assertTrue(text.contains("Item: ?"));
        assertTrue(text.contains("Changes: ?"));
        assertTrue(text.contains("Base types (species/profile only): Psychic / Fairy"));
        assertFalse(text.contains("100"));
        assertFalse(text.contains("Synchronize"));
    }

    @Test void exactOwnHealthAndDisplayedOpponentPercentAreDistinct() {
        assertEquals("72/183", BattleFactsModel.health(new Fact<>(new Health(72, 183, 39.34), Freshness.CURRENT, "visible")));
        assertEquals("39.3%", BattleFactsModel.health(new Fact<>(new Health(-1, -1, 39.34), Freshness.CURRENT, "visible")));
        assertEquals("~39.3%", BattleFactsModel.health(new Fact<>(new Health(-1, -1, 39.34), Freshness.LAST_OBSERVED, "bench")));
        assertEquals("?", BattleFactsModel.health(new Fact<>(new Health(-1, -1, Double.NaN), Freshness.CURRENT, "invalid")));
    }

    @Test void lastObservedAndProfileSourcesAreNeverLabeledCurrent() {
        var last = new Fact<>("brn", Freshness.LAST_OBSERVED, "Last opponent projection");
        assertEquals("~brn", BattleFactsModel.value(last));
        assertEquals("last observed | Last opponent projection", BattleFactsModel.provenance(last));
        var profile = new Fact<>("blaze", Freshness.PROFILE, "Own Pokemon profile");
        assertTrue(BattleFactsModel.provenance(profile).contains("current battle value unknown"));
    }

    @Test void unknownSlotsNeverAcquireANameLevelOrHealthyStatus() {
        var hidden = new Fighter("hidden", "Unknown", List.of(), List.of(), List.of(), 0, -1,
            false, false, "", "", false, "", List.of());
        assertEquals("?", BattleFactsModel.name(hidden));
        assertEquals("?", BattleFactsModel.level(hidden));
        assertEquals("Unrevealed", BattleFactsModel.position(hidden));
        assertEquals("?", BattleFactsModel.value(hidden.facts().status()));
    }

    @Test void detailsWrapLongNamesWithoutLosingTheirTextOrSplittingSurrogatePairs() {
        String text = "Revealed " + "A".repeat(80) + " \uD83D\uDE00";
        var wrapped = BattleFactsModel.wrap(List.of(text), 32, s -> s.codePointCount(0, s.length()) * 4);
        assertEquals(text.replace(" ", ""), String.join("", wrapped).replace(" ", ""));
        assertTrue(wrapped.stream().allMatch(s -> s.codePointCount(0, s.length()) <= 8));
        assertTrue(wrapped.stream().noneMatch(s -> Character.isHighSurrogate(s.charAt(s.length() - 1))));
    }

    @Test void structuredFactsExcludeLegacyCalculatorNotes() {
        var f = new Fighter("a", "Ralts", List.of(), List.of(), List.of(), 20, 50,
            true, false, "", "", false, "", List.of("best risk potential recommendation"));
        String text = String.join("\n", BattleFactsModel.details(f, true));
        assertFalse(text.matches("(?s).*(best|risk|potential|recommendation).*"));
    }
}
