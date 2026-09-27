package dev.krul.cobblemon.battle.matchup;

import dev.krul.cobblemon.battle.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.battle.PokemonType.*;
import static dev.krul.cobblemon.battle.matchup.MatchupSnapshot.*;

class MatchupCalculatorTest {
    @Test void iceTypeWarnsGarchompBeforeMovesAreRevealedAndTackleDoesNotEraseIt() {
        var mine = fighter("garchomp", List.of(DRAGON, GROUND), List.of(move("Dragon Claw", DRAGON, 10, false)));
        for (var moves : List.of(List.<Move>of(), List.of(move("Tackle", NORMAL, -1, false)))) {
            var result = cell(mine, fighter("ice", List.of(ICE), moves));
            assertTrue(result.details().stream().anyMatch(s -> s.contains("Potential") && s.contains("Ice x4")),
                "A type-based Ice threat remains distinct from known attacks");
            assertEquals(moves.isEmpty() ? null : 1.0, result.incoming().multiplier());
        }
    }

    @Test void groundPotentialDistinguishesFlyingImmunityFromWaterNeutrality() {
        var enemy = fighter("ground", List.of(GROUND), List.of());
        assertTrue(cell(fighter("bird", List.of(FLYING, STEEL), List.of()), enemy).details().stream().anyMatch(s -> s.contains("Ground x0")));
        assertTrue(cell(fighter("water", List.of(WATER), List.of()), enemy).details().stream().anyMatch(s -> s.contains("Ground x1")));
    }

    @Test void unknownIdentityDoesNotGeneratePotentialNeutralOrThreat() {
        var result = cell(fighter("m", List.of(DRAGON, GROUND), List.of()), fighter("unknown", List.of(), List.of()));
        assertTrue(result.details().stream().anyMatch(s -> s.contains("Potential: ?")));
    }

    @Test void baseOnlyDefenderStillReceivesPotentialTypeAssessment() {
        var mine = fighterWithTypes("mine", List.of(), List.of(GRASS), List.of());
        var result = cell(mine, fighter("fire", List.of(FIRE), List.of()));
        assertEquals(2.0, result.potential().multiplier());
        assertEquals(FIRE, result.potential().type());
    }

    @Test void baseTypesAreUsedWhenCurrentBattleTypesAreNotYetReported() {
        var mine = fighterWithTypes("mine", List.of(FIRE), List.of(FIRE), List.of(move("Flamethrower", FIRE, 10, false)));
        var target = fighterWithTypes("target", List.of(), List.of(GRASS), List.of());
        assertEquals(2.0, cell(mine, target).outgoing().multiplier());
    }

    @Test void currentTypesOverrideBaseTypesForPotentialAssessment() {
        var mine = fighterWithTypes("mine", List.of(FIRE), List.of(GRASS), List.of());
        var result = cell(mine, fighter("fire", List.of(FIRE), List.of()));
        assertEquals(.5, result.potential().multiplier());
    }

    @Test void activeTeraTypeIsUsedForTheCurrentChartAndExplained() {
        var mine = new Fighter("mine", "Tera Fire", List.of(FIRE), List.of(WATER), List.of(),
            50, 100, true, false, "", "", true, "", List.of(), Portrait.UNKNOWN, FIRE, true);
        var enemy = fighter("enemy", List.of(ELECTRIC), List.of(move("Thunderbolt", ELECTRIC, 10, false)));

        var result = cell(mine, enemy);

        assertEquals(1.0, result.incoming().multiplier(), "The active Fire Tera type replaces the Water base type");
        assertTrue(MatchupCalculator.fighterDetails(mine, enemy).stream()
            .anyMatch(line -> line.contains("Tera: Fire") && line.contains("active")));
    }

    @Test void missingCurrentAndBaseTypesKeepPotentialUnknown() {
        var mine = fighterWithTypes("mine", List.of(), List.of(), List.of());
        var result = cell(mine, fighter("fire", List.of(FIRE), List.of()));
        assertNull(result.potential().multiplier());
    }

    @Test void revealedCoverageCanOutweighTypePriorWithoutInventingEnemyMoves() {
        var mine = fighter("garchomp", List.of(DRAGON, GROUND), List.of(move("Earthquake", GROUND, 10, false)));
        var result = cell(mine, fighter("electric", List.of(ELECTRIC), List.of(move("Ice Beam", ICE, -1, false))));
        assertEquals(2.0, result.outgoing().multiplier(), "Offensive advantage is separate from incoming danger");
        assertEquals(4.0, MatchupCalculator.risk(result));
        assertFalse(MatchupCalculator.potentialDominates(result));
        assertEquals(0.0, result.potential().multiplier());
        assertEquals("Ice Beam", result.incoming().move());
    }
    static Fighter fighter(String id, List<PokemonType> types, List<Move> moves) {
        return new Fighter(id, "Same species", types, types, moves, 50, 100, true, false, "", "", true, "", List.of());
    }
    static Fighter fighterWithTypes(String id, List<PokemonType> types, List<PokemonType> baseTypes, List<Move> moves) {
        return new Fighter(id, "Same species", types, baseTypes, moves, 50, 100, true, false, "", "", true, "", List.of());
    }
    static Move move(String id, PokemonType type, int pp, boolean disabled) {
        return new Move(new BattleMove(id, id, type, MoveCategory.SPECIAL, 90), pp, disabled, true, false);
    }
    static MatchupCalculator.Cell cell(Fighter mine, Fighter enemy) {
        return MatchupCalculator.calculate(new MatchupSnapshot(UUID.randomUUID(), 1, List.of(mine), List.of(enemy), List.of())).getFirst();
    }

    @Test void dualTypesAndStabAreSeparateAndEnemyMovesStayUnknown() {
        var result = cell(fighter("mine", List.of(ICE), List.of(move("icebeam", ICE, 10, false))),
            fighter("enemy", List.of(GROUND, FLYING), List.of()));
        assertEquals(4.0, result.outgoing().multiplier());
        assertEquals(1.5, result.outgoing().stab());
        assertNull(result.incoming().multiplier());
    }
    @Test void noPpAndDisabledMovesDoNotWinSelection() {
        var result = cell(fighter("mine", List.of(WATER), List.of(move("icebeam", ICE, 0, false),
            move("blizzard", ICE, 5, true), move("surf", WATER, 5, false))), fighter("enemy", List.of(DRAGON, FLYING), List.of()));
        assertEquals("surf", result.outgoing().move());
        assertEquals(.5, result.outgoing().multiplier());
        assertTrue(result.details().stream().anyMatch(s -> s.contains("PP")));
    }
    @Test void typeImmunityIsZeroNotUnknown() {
        var result = cell(fighter("mine", List.of(NORMAL), List.of(move("tackle", NORMAL, 10, false))),
            fighter("enemy", List.of(GHOST), List.of(move("shadowball", GHOST, -1, false))));
        assertEquals(0.0, result.outgoing().multiplier());
        assertEquals(0.0, result.incoming().multiplier());
    }
    @Test void lastSeenAbilityDoesNotCreateCurrentImmunity() {
        var attacker = fighter("mine", List.of(GROUND), List.of(move("earthquake", GROUND, 5, false)));
        var enemy = new Fighter("enemy", "Rotom", List.of(ELECTRIC, GHOST), List.of(ELECTRIC, GHOST),
            List.of(), 50, 100, true, false, "levitate", "", true, "", List.of());
        assertEquals(0.0, cell(attacker, enemy).outgoing().multiplier());
        var benched = new Fighter(enemy.id(), enemy.name(), enemy.types(), enemy.baseTypes(), enemy.moves(),
            50, 100, false, false, "levitate", "", false, "", List.of());
        assertEquals(2.0, cell(attacker, benched).outgoing().multiplier());
    }
    @Test void unavailableOrSpecialOnlyIsNotReportedAsAnOrdinaryAttack() {
        var special = new Move(new BattleMove("seismictoss", "Seismic Toss", FIGHTING, MoveCategory.PHYSICAL, 1), 10, false, true, true);
        assertNull(cell(fighter("mine", List.of(FIGHTING), List.of(special)), fighter("e", List.of(NORMAL), List.of())).outgoing().multiplier());
        var fainted = new Fighter("mine", "Fainted", List.of(FIRE), List.of(FIRE), List.of(move("flamethrower", FIRE, 10, false)),
            1, 0, false, true, "", "", false, "", List.of());
        var result = cell(fainted, fighter("e", List.of(GRASS), List.of()));
        assertTrue(result.unavailable());
        assertNull(result.outgoing().multiplier());
    }
    @Test void unknownTypesAreNotNeutralAndLevelGapIsVisible() {
        var mine = new Fighter("mine", "Ralts", List.of(PSYCHIC, FAIRY), List.of(PSYCHIC, FAIRY),
            List.of(move("confusion", PSYCHIC, 10, false)), 1, 100, true, false, "", "", false, "", List.of());
        var result = cell(mine, fighter("e", List.of(), List.of()));
        assertNull(result.outgoing().multiplier());
        assertTrue(result.details().stream().anyMatch(s -> s.contains("Lv.1") && s.contains("Lv.50")));
    }
    @Test void sixBySixKeepsSameSpeciesIndividualsSeparate() {
        List<Fighter> own = new ArrayList<>(), enemies = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            own.add(fighter("mine" + i, List.of(WATER), List.of()));
            enemies.add(fighter("enemy" + i, List.of(FIRE), List.of()));
        }
        var result = MatchupCalculator.calculate(new MatchupSnapshot(UUID.randomUUID(), 1, own, enemies, List.of()));
        assertEquals(36, result.size());
        assertEquals(36, result.stream().map(c -> c.ownId() + "/" + c.enemyId()).distinct().count());
        assertEquals("mine5", result.getLast().ownId());
    }
    @Test void everyRevealedMoveExplainsItsTypeAndThisTargetsMultiplier() {
        var mine = fighter("mine", List.of(WATER, DRAGON), List.of(move("surf", WATER, 5, false)));
        var enemy = fighter("enemy", List.of(FIRE), List.of(move("flamethrower", FIRE, -1, false), move("icebeam", ICE, -1, false), move("thunderbolt", ELECTRIC, 0, false)));
        var details = MatchupCalculator.fighterDetails(enemy, mine);
        assertTrue(details.stream().anyMatch(s -> s.contains("flamethrower") && s.contains("Fire") && s.contains("x0.25")));
        assertTrue(details.stream().anyMatch(s -> s.contains("icebeam") && s.contains("Ice") && s.contains("x1")));
        assertTrue(details.stream().anyMatch(s -> s.contains("thunderbolt") && s.contains("Electric") && s.contains("No PP")));
    }

    @Test void overviewPrioritizesLowHealthAndLevelGapWithoutDumpingEveryMove() {
        var mine = new Fighter("mine", "Ralts", List.of(PSYCHIC, FAIRY), List.of(PSYCHIC, FAIRY),
            List.of(move("Disarming Voice", FAIRY, 14, false), move("Confusion", PSYCHIC, 20, false)),
            1, 9, true, false, "synchronize", "", true, "", List.of());
        var enemy = fighter("e", List.of(BUG, GRASS), List.of(move("Tackle", NORMAL, -1, false)));
        var lines = cell(mine, enemy).details();
        assertTrue(lines.size() <= 6, "Default explanation must be a short decision summary");
        assertTrue(lines.stream().anyMatch(s -> s.contains("9%") && s.contains("HP")));
        assertTrue(lines.stream().anyMatch(s -> s.contains("Careful") && s.contains("level")));
        assertFalse(lines.stream().anyMatch(s -> s.contains("Confusion")), "Other moves belong in explicit details");
    }

    @Test void expandedDetailsExplainStatusMovesAndRoundReportedStatMultipliers() {
        var growl = new Move(new BattleMove("growl", "Growl", NORMAL, MoveCategory.STATUS, 0), 39, false, true, false);
        var mine = new Fighter("mine", "Ralts", List.of(FAIRY), List.of(FAIRY), List.of(growl),
            1, 9, true, false, "synchronize", "", true, "", List.of("Reported buffs: {ATTACK=0.6666666666666666}"));
        var details = MatchupCalculator.fighterDetails(mine, fighter("e", List.of(GRASS), List.of()));
        assertTrue(details.stream().anyMatch(s -> s.contains("Growl") && s.contains("Status") && s.contains("39") && !s.contains("?")));
        assertTrue(details.stream().anyMatch(s -> s.contains("Attack x0.67")));
        assertFalse(details.stream().anyMatch(s -> s.contains("0.666666") || s.contains("Reported buffs") || s.contains("ordinary multiplier")));
    }

    @Test void statusMovesStillShowTheirDisabledAndEmptyPpRestrictions() {
        var growl = new Move(new BattleMove("growl", "Growl", NORMAL, MoveCategory.STATUS, 0), 0, false, true, false);
        var taunt = new Move(new BattleMove("taunt", "Taunt", DARK, MoveCategory.STATUS, 0), 10, true, true, false);
        var details = MatchupCalculator.fighterDetails(fighter("m", List.of(NORMAL), List.of(growl, taunt)), fighter("e", List.of(GRASS), List.of()));
        assertTrue(details.stream().anyMatch(s -> s.contains("Growl") && s.contains("No PP")));
        assertTrue(details.stream().anyMatch(s -> s.contains("Taunt") && s.contains("Disabled")));
    }

    @Test void abilityImmunityStillExplainsWhyTheDisplayedTypeHitIsZero() {
        var enemy = new Fighter("e", "Rotom", List.of(ELECTRIC, GHOST), List.of(ELECTRIC, GHOST), List.of(),
            50, 100, true, false, "levitate", "", true, "", List.of());
        var result = cell(fighter("m", List.of(GROUND), List.of(move("Earthquake", GROUND, 10, false))), enemy);
        assertEquals(0.0, result.outgoing().multiplier());
        assertTrue(result.details().stream().anyMatch(s -> s.contains("Hit:") && s.contains("levitate")));
        assertTrue(MatchupCalculator.fighterDetails(fighter("m", List.of(GROUND), List.of(move("Earthquake", GROUND, 10, false))), enemy)
            .stream().anyMatch(s -> s.contains("Earthquake") && s.contains("levitate")));
    }

    @Test void unconfirmedDefenderEffectsAreQualifiedBesideTheAttackNotOnlyInItsOwnProfile() {
        var enemy = new Fighter("e", "Rotom", List.of(ELECTRIC, GHOST), List.of(ELECTRIC, GHOST), List.of(),
            50, 100, true, false, "levitate", "", false, "", List.of());
        var mine = fighter("m", List.of(GROUND), List.of(move("Earthquake", GROUND, 10, false)));
        var result = cell(mine, enemy);
        assertEquals(2.0, result.outgoing().multiplier());
        assertTrue(result.details().stream().anyMatch(s -> s.contains("Hit:") && s.contains("type only")));
        assertTrue(MatchupCalculator.fighterDetails(mine, enemy).stream().anyMatch(s -> s.contains("Earthquake") && s.contains("type only")));
    }

    @Test void withheldIdentityKeepsItsExplanationInExpandedDetails() {
        var unknown = new Fighter("e", "Unknown", List.of(), List.of(), List.of(), 0, -1, false, false, "", "", false, "",
            List.of("Ambiguous identity / Illusion: projection does not match roster UUID; facts withheld."));
        assertTrue(MatchupCalculator.fighterDetails(unknown, fighter("m", List.of(FAIRY), List.of()))
            .stream().anyMatch(s -> s.contains("Identity not confirmed")));
    }
}
