package dev.krul.cobblemon.battle.matrix;

import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Fighter;
import dev.krul.cobblemon.battle.matchup.MatchupSnapshot.Move;
import java.util.ArrayList;
import java.util.List;

/** One fighter per row, one opponent per column. Rendering and tooltips share these bounds. */
public final class BattleMatrixLayout {
    public static final int LEFT = 6, ROW_WIDTH = 116, MATRIX_X = LEFT + ROW_WIDTH;
    public static final int HEADER_HEIGHT = 36, BODY_TOP = 40, GROUP_ROW = 48;
    public static final int COLUMN_WIDTH = 108, VISIBLE_ENEMIES = 6;
    public static final int HEIGHT = BODY_TOP + 6 * GROUP_ROW + LEFT;

    private BattleMatrixLayout() {}

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
        public boolean intersects(Rect other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }

    public record Row(Fighter fighter, List<Move> moves, int y, int height) {
        public Row { moves = List.copyOf(moves); }
        public int bottom() { return y + height; }
    }
    public record Hit(int row, int column, int move) {}

    public static int width(int enemies) { return MATRIX_X + Math.clamp(enemies, 1, 6) * COLUMN_WIDTH + LEFT; }
    public static int height(int fighters) { return BODY_TOP + Math.clamp(fighters, 1, 6) * GROUP_ROW + LEFT; }
    public static Rect enemyHeader(int column) { return new Rect(MATRIX_X + column * COLUMN_WIDTH, 0, COLUMN_WIDTH, HEADER_HEIGHT); }
    public static Rect ownHeader(int row) { return new Rect(LEFT, BODY_TOP + row * GROUP_ROW, ROW_WIDTH, GROUP_ROW); }
    public static Rect enemyPortrait(int column) { return new Rect(MATRIX_X + column * COLUMN_WIDTH + 3, 6, 24, 24); }
    public static Rect ownPortrait(int row) { return new Rect(LEFT + 3, BODY_TOP + row * GROUP_ROW + 7, 24, 24); }
    public static Rect cell(int row, int column) {
        return new Rect(MATRIX_X + column * COLUMN_WIDTH, BODY_TOP + row * GROUP_ROW, COLUMN_WIDTH, GROUP_ROW);
    }
    public static Rect chip(int row, int column, int move) {
        Rect cell = cell(row, column);
        return new Rect(cell.x + 3 + (move % 2) * 53, cell.y + 3 + (move / 2) * 17, 49, 15);
    }
    public static List<Row> rows(List<BattleMatrixModel.MoveGroup> groups) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(6, groups.size()); i++) {
            var group = groups.get(i);
            rows.add(new Row(group.fighter(), group.moves().stream().limit(4).toList(), BODY_TOP + i * GROUP_ROW, GROUP_ROW));
        }
        return List.copyOf(rows);
    }
    public static Hit hit(List<BattleMatrixModel.MoveGroup> groups, int enemies, double x, double y) {
        for (int row = 0; row < Math.min(6, groups.size()); row++)
            for (int column = 0; column < Math.min(6, enemies); column++)
                for (int move = 0; move < Math.min(4, groups.get(row).moves().size()); move++)
                    if (chip(row, column, move).contains(x, y)) return new Hit(row, column, move);
        return null;
    }
}
