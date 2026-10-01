package dev.krul.cobblemon.battle.matrix;

import dev.krul.cobblemon.battle.BattleMove;
import dev.krul.cobblemon.battle.MoveCategory;
import dev.krul.cobblemon.battle.PokemonType;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fact;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Facts;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fighter;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Freshness;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Move;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static dev.krul.cobblemon.battle.PokemonType.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleMatrixModelTest {
    @Test void calculatesOrdinaryTypeChartValuesIncludingDualTypesAndImmunities() {
        assertEquals("2", evaluate(move("Flamethrower", FIRE), current("Venusaur", GRASS)).label());
        assertEquals("4", evaluate(move("Ice Beam", ICE), current("Garchomp", DRAGON, GROUND)).label());
        assertEquals("0", evaluate(move("Tackle", NORMAL), current("Gengar", GHOST)).label());
        assertEquals("0", evaluate(move("Thunderbolt", ELECTRIC), current("Golem", GROUND)).label());
        assertEquals("1", evaluate(move("Flamethrower", FIRE), current("Scizor", BUG, WATER)).label());
    }

    @Test void refusesToInventValuesForUnknownStatusOrSpecialMoveSemantics() {
        assertEquals("?", evaluate(move("Mystery", null), current("Venusaur", GRASS)).label());
        assertEquals("—", evaluate(move("Will-O-Wisp", FIRE, MoveCategory.STATUS, false), current("Venusaur", GRASS)).label());
        var dynamic = evaluate(move("Flying Press", FIGHTING, MoveCategory.PHYSICAL, true), current("Venusaur", GRASS));
        assertEquals("?", dynamic.label());
        assertTrue(dynamic.tooltip().stream().anyMatch(line -> line.contains("nominal type is not confirmed")));
    }

    @Test void refusesSpeciesBaseTypesWithoutCurrentBattleEvidence() {
        var cell = evaluate(move("Ice Beam", ICE), baseOnly("Garchomp", DRAGON, GROUND));

        assertEquals("?", cell.label());
        assertNull(cell.multiplier());
        assertEquals(BattleMatrixCell.Evidence.UNKNOWN, cell.evidence());
    }

    @Test void knownTypeChangeWithoutKnownResultForbidsBaseFallback() {
        var target = fighter("Garchomp", List.of(), List.of(DRAGON, GROUND), true, Facts.UNKNOWN);

        var cell = evaluate(move("Ice Beam", ICE), target);

        assertEquals("?", cell.label());
        assertEquals(BattleMatrixCell.Evidence.UNKNOWN, cell.evidence());
        assertNull(cell.multiplier());
    }

    @Test void lastObservedTypesNeverAuthorizeCurrentMultiplierButAvailabilityRemainsVisible() {
        var attack = new Move(new BattleMove("flamethrower", "Flamethrower", FIRE, MoveCategory.SPECIAL, 90),
            7, true, true, false);

        var cell = evaluate(attack, current("Venusaur", Freshness.LAST_OBSERVED, GRASS));

        assertEquals("?", cell.label());
        assertNull(cell.multiplier());
        assertTrue(cell.tooltip().stream().anyMatch(line -> line.contains("PP 7") && line.contains("disabled")));
    }

    @Test void specialMoveBadgeHidesNominalTypeWhileOrdinaryBadgeKeepsKnownType() {
        var dynamic = move("Terrain Pulse", NORMAL, MoveCategory.SPECIAL, true);
        var ordinary = move("Flamethrower", FIRE, MoveCategory.SPECIAL, false);

        assertEquals("?", BattleMatrixContent.typeBadge(dynamic));
        assertEquals("FIR", BattleMatrixContent.typeBadge(ordinary));
    }

    @Test void preservesRosterRowsWithoutMovesAndUnrevealedEnemySlots() {
        Fighter ownWithMove = fighter("Charizard", List.of(FIRE, FLYING), List.of(FIRE, FLYING), false,
            types(Freshness.CURRENT, FIRE, FLYING), move("Flamethrower", FIRE));
        Fighter ownWithoutMoves = fighter("Blastoise", List.of(WATER), List.of(WATER), false,
            types(Freshness.CURRENT, WATER));
        Fighter revealed = current("Venusaur", GRASS, POISON);
        Fighter hidden = fighter("Unknown", List.of(), List.of(), false, Facts.UNKNOWN);
        var snapshot = new MatchupSnapshot(java.util.UUID.randomUUID(), 3,
            List.of(ownWithMove, ownWithoutMoves), List.of(revealed, hidden), List.of());

        assertEquals(List.of("Charizard", "Blastoise"), BattleMatrixModel.groups(snapshot).stream().map(group -> group.fighter().name()).toList());
        assertEquals(List.of("Venusaur", "Unknown"), BattleMatrixModel.enemies(snapshot).stream().map(Fighter::name).toList());
    }

    @Test void profileFactsAreNotCurrentEvenWhenSpeciesAndTypesAgree() {
        assertNull(evaluate(move("Ice Beam", ICE), current("Garchomp", Freshness.PROFILE, DRAGON, GROUND)).multiplier());
    }

    @Test void storedTeraFieldIsNotAnAlternativeToCurrentTypeEvidence() {
        Fighter target = new Fighter("target", "Garchomp", List.of(), List.of(DRAGON, GROUND), List.of(),
            50, 100, true, false, "", "", false, "", List.of(), MatchupSnapshot.Portrait.UNKNOWN,
            FIRE, true, Facts.UNKNOWN);
        assertNull(evaluate(move("Surf", WATER), target).multiplier());
    }

    @Test void currentEvidenceOverridesSpeciesAndDoesNotProduceApproximateLabels() {
        var target = fighter("Garchomp", List.of(FIRE), List.of(DRAGON, GROUND), true, types(Freshness.CURRENT, FIRE));
        var cell = evaluate(move("Surf", WATER), target);
        assertEquals("2", cell.label());
        assertEquals(BattleMatrixCell.Evidence.CURRENT, cell.evidence());
    }

    @Test void capsBothRosterAxesAtSixAndMovesAtFourWithoutInventingMissingSlots() {
        var fighters = java.util.stream.IntStream.range(0, 8).mapToObj(i -> fighter("Pokemon" + i,
            List.of(), List.of(), false, Facts.UNKNOWN, move("A", FIRE), move("B", WATER),
            move("C", GRASS), move("D", ICE), move("E", NORMAL))).toList();
        var snapshot = new MatchupSnapshot(java.util.UUID.randomUUID(), 1, fighters, fighters, List.of());
        assertEquals(6, BattleMatrixModel.groups(snapshot).size());
        assertEquals(4, BattleMatrixModel.groups(snapshot).getFirst().moves().size());
        assertEquals(6, BattleMatrixModel.enemies(snapshot).size());
    }

    @Test void coverageCountsOnlyRevealedTargetsWithAConfirmedSuperEffectiveKnownMove() {
        var moves = List.of(move("Flamethrower", FIRE));
        var foes = List.of(current("Venusaur", GRASS, POISON), current("Gengar", GHOST),
            baseOnly("Unknown battle typing", GRASS));

        assertEquals(new BattleMatrixModel.Coverage(1, 2), BattleMatrixModel.coverage(moves, foes));
    }

    private static BattleMatrixCell evaluate(Move move, Fighter target) {
        return BattleMatrixModel.evaluate(move, target);
    }

    private static Move move(String name, PokemonType type) {
        return move(name, type, MoveCategory.SPECIAL, false);
    }

    private static Move move(String name, PokemonType type, MoveCategory category, boolean special) {
        return new Move(new BattleMove(name.toLowerCase().replace("-", ""), name, type, category,
            category == MoveCategory.STATUS ? 0 : 90), -1, false, false, special);
    }

    private static Fighter current(String name, PokemonType... current) {
        return current(name, Freshness.CURRENT, current);
    }

    private static Fighter current(String name, Freshness freshness, PokemonType... current) {
        return fighter(name, List.of(current), List.of(current), false, types(freshness, current));
    }

    private static Fighter baseOnly(String name, PokemonType... base) {
        return fighter(name, List.of(base), List.of(base), false, Facts.UNKNOWN);
    }

    private static Facts types(Freshness freshness, PokemonType... types) {
        return new Facts(Fact.unknown(), new Fact<>(List.of(types), freshness, "Fixture battle types"),
            Fact.unknown(), Fact.unknown(), Fact.unknown(), Fact.unknown());
    }

    private static Fighter fighter(String name, List<PokemonType> current, List<PokemonType> base,
                                   boolean teraActive, Facts facts, Move... moves) {
        return new Fighter(name.toLowerCase(), name, current, base, List.of(moves), 50, 100,
            false, false, "", "", false, "", List.of(),
            name.equals("Unknown") ? MatchupSnapshot.Portrait.UNKNOWN
                : new MatchupSnapshot.Portrait("cobblemon:" + name.toLowerCase(), Set.of()),
            null, teraActive, facts);
    }
}
