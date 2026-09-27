package dev.krul.cobblemon.gui;

import dev.krul.cobblemon.battle.PokemonType;
import dev.krul.cobblemon.battle.TypeEffectiveness;
import dev.krul.cobblemon.gui.overlay.OverlayCanvas;
import dev.krul.cobblemon.gui.overlay.OverlayContent;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

/** A compact matrix, independent of the native PC/battle screen underneath. */
public final class TypeChartContent implements OverlayContent {
    private static final PokemonType[] TYPES = PokemonType.values();
    private static final int LEFT = 8, TOP = 8, HEADER = 28, CELL = 23, ROW = 17;
    private final TypeMatrixSelection selection = new TypeMatrixSelection(HEADER, HEADER, CELL, ROW);
    @Override public String title() { return "Pokémon Type Chart"; }
    @Override public int width() { return LEFT * 2 + HEADER + CELL * TYPES.length; }
    @Override public int height() { return TOP + HEADER + ROW * TYPES.length + 8; }
    @Override public double defaultScale() { return .8; }
    @Override public void render(OverlayCanvas c, double mx, double my) {
        selection.hover(mx - LEFT, my - TOP);
        var attack = selection.activeAttack().orElse(null);
        var defence = selection.activeDefend().orElse(null);
        for (int row = 0; row < TYPES.length; row++) {
            var a = TYPES[row];
            int y = TOP + HEADER + row * ROW;
            c.fill(LEFT, y, HEADER - 1, ROW - 1, a.argb());
            c.text(a.abbreviation(), LEFT + 3, y + 4, 0xFFFFFFFF);
            for (int col = 0; col < TYPES.length; col++) {
                var d = TYPES[col];
                int x = LEFT + HEADER + col * CELL;
                double m = TypeEffectiveness.multiplier(a, d);
                int color = m == 0 ? 0xFF4B4B55 : m == .5 ? 0xFF765454 : m == 2 ? 0xFF446949 : 0xFF25252C;
                c.fill(x, y, CELL - 1, ROW - 1, color);
                if (a == attack || d == defence) c.fill(x, y, CELL - 1, ROW - 1, 0x26FFFFFF);
                if (m != 1) c.text(m == .5 ? ".5" : m == 0 ? "0" : "2", x + 6, y + 4, c.text());
                if (a == attack && d == defence) c.outline(x, y, CELL - 1, ROW - 1, c.accent());
            }
        }
        for (int col = 0; col < TYPES.length; col++) {
            int x = LEFT + HEADER + col * CELL;
            c.fill(x, TOP, CELL - 1, HEADER - 1, TYPES[col].argb());
            c.text(TYPES[col].abbreviation(), x + 2, TOP + 10, 0xFFFFFFFF);
        }
        c.text("A/D", LEFT + 3, TOP + 10, c.muted());
    }
    @Override public java.util.List<String> tooltip(double x, double y) {
        if (x < LEFT || y < TOP || x >= width() - LEFT || y >= height() - 8) return java.util.List.of();
        selection.hover(x - LEFT, y - TOP);
        var attack = selection.hoveredAttack().orElse(null);
        var defence = selection.hoveredDefend().orElse(null);
        if (attack != null && defence != null) {
            double m = TypeEffectiveness.multiplier(attack, defence);
            return java.util.List.of(attack.label() + " -> " + defence.label() + ": " + (m == .5 ? "1/2" : (int) m) + "x");
        }
        if (attack != null) return java.util.List.of(attack.label() + " - attack (row)");
        if (defence != null) return java.util.List.of(defence.label() + " - defence (column)");
        return java.util.List.of("Attack: rows; defence: columns");
    }
    @Override public boolean click(double x, double y, int button) {
        if (button != 0) return false;
        selection.hover(x - LEFT, y - TOP);
        var a = selection.hoveredAttack().orElse(null);
        var d = selection.hoveredDefend().orElse(null);
        if (a != null && d != null) selection.clickCell(a, d);
        else if (a != null) selection.clickAttackHeader(a);
        else if (d != null) selection.clickDefendHeader(d);
        return true;
    }
    @Override public boolean key(int key) {
        if (key != GLFW_KEY_ESCAPE || !selection.locked()) return false;
        selection.clear(); return true;
    }
    @Override public void reset() { selection.clear(); }
}
