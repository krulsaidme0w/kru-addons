package dev.krul.cobblemon.battle.matrix;

import org.junit.jupiter.api.Test;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Move;
import dev.krul.cobblemon.battle.BattleMove;
import dev.krul.cobblemon.battle.MoveCategory;
import dev.krul.cobblemon.battle.PokemonType;
import java.util.List;
import java.util.Collections;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class BattleMatrixLayoutTest {
    @Test void sixFightersWithUnknownMovesRemainSixVisibleRows() {
        var groups = IntStream.range(0, 6)
            .mapToObj(i -> new BattleMatrixModel.MoveGroup(null, List.of())).toList();
        var rows = BattleMatrixLayout.rows(groups);
        assertEquals(6, rows.size(), "Unknown moves must not hide roster slots");
        assertTrue(rows.getLast().bottom() <= BattleMatrixLayout.HEIGHT);
    }
    @Test void allSixEnemyColumnsFitNormalViewportWithoutPaging() {
        assertTrue(BattleMatrixLayout.VISIBLE_ENEMIES >= 6);
        assertTrue((BattleMatrixLayout.MATRIX_X + 6 * BattleMatrixLayout.COLUMN_WIDTH) * .8 <= 640);
        assertTrue(BattleMatrixLayout.HEIGHT * .8 <= 300);
    }

    @Test void rosterWidthUsesOnlyPresentColumnsAndClampsAtSix() {
        assertEquals(344, BattleMatrixLayout.width(2));
        assertEquals(776, BattleMatrixLayout.width(6));
        assertEquals(776, BattleMatrixLayout.width(8));
    }

    @Test void fourMoveChipsStayInsideEachOfThirtySixCellsAndDoNotOverlap() {
        for (int row = 0; row < 6; row++) for (int column = 0; column < 6; column++) {
            var cell = BattleMatrixLayout.cell(row, column);
            assertTrue(cell.bottom() <= BattleMatrixLayout.HEIGHT);
            assertTrue(cell.right() <= BattleMatrixLayout.width(6));
            assertFalse(cell.intersects(BattleMatrixLayout.enemyPortrait(column)));
            assertFalse(cell.intersects(BattleMatrixLayout.ownPortrait(row)));
            for (int slot = 0; slot < 4; slot++) {
                var chip = BattleMatrixLayout.chip(row, column, slot);
                assertTrue(chip.x() >= cell.x() && chip.y() >= cell.y());
                assertTrue(chip.right() <= cell.right() && chip.bottom() <= cell.bottom());
                for (int other = slot + 1; other < 4; other++)
                    assertFalse(chip.intersects(BattleMatrixLayout.chip(row, column, other)));
            }
        }
    }

    @Test void hitTestFindsLastChipAndRejectsGuttersHeadersAndAbsentMoves() {
        var move = new Move(
            new BattleMove("fire", "Fire Blast", PokemonType.FIRE, MoveCategory.SPECIAL, 110),
            -1, false, false, false);
        var groups = IntStream.range(0, 6).mapToObj(i ->
            new BattleMatrixModel.MoveGroup(null, Collections.nCopies(4, move))).toList();
        assertEquals(6, BattleMatrixLayout.rows(groups).size());
        assertEquals(4, BattleMatrixLayout.rows(groups).getLast().moves().size());
        var last = BattleMatrixLayout.chip(5, 5, 3);
        assertEquals(new BattleMatrixLayout.Hit(5, 5, 3),
            BattleMatrixLayout.hit(groups, 6, last.x() + 2, last.y() + 2));
        var first = BattleMatrixLayout.chip(0, 0, 0);
        assertEquals(new BattleMatrixLayout.Hit(0, 0, 0), BattleMatrixLayout.hit(groups, 6, first.x() + 2, first.y() + 2));
        var second = BattleMatrixLayout.chip(0, 0, 1);
        assertEquals(new BattleMatrixLayout.Hit(0, 0, 1), BattleMatrixLayout.hit(groups, 6, second.x() + 2, second.y() + 2));
        var third = BattleMatrixLayout.chip(0, 0, 2);
        assertEquals(new BattleMatrixLayout.Hit(0, 0, 2), BattleMatrixLayout.hit(groups, 6, third.x() + 2, third.y() + 2));
        assertNull(BattleMatrixLayout.hit(groups, 6, second.right(), second.y() + 2)); // exclusive chip edge
        assertNull(BattleMatrixLayout.hit(groups, 6, BattleMatrixLayout.cell(5, 5).right() - 1,
            BattleMatrixLayout.cell(5, 5).bottom() - 1));
        assertNull(BattleMatrixLayout.hit(groups, 6, 725, 20));
        assertNull(BattleMatrixLayout.hit(groups, 2, last.x() + 2, last.y() + 2));
        assertNull(BattleMatrixLayout.hit(List.of(new BattleMatrixModel.MoveGroup(null, List.of(move))), 1, 185, 44));
        assertNull(BattleMatrixLayout.hit(groups, 6, Double.NaN, 252));
    }
}
