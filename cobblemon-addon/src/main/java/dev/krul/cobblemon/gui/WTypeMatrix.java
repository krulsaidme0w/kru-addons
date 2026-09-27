package dev.krul.cobblemon.gui;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import dev.krul.cobblemon.battle.PokemonType;
import dev.krul.cobblemon.battle.TypeEffectiveness;
import dev.krul.cobblemon.battle.TypeMatrixPresentation;
import meteordevelopment.meteorclient.utils.render.color.Color;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public final class WTypeMatrix extends WWidget {
    private static final Color GRID = new Color(CobblemonUiStyle.GRID);
    private static final Color NEUTRAL = new Color(CobblemonUiStyle.CELL_NEUTRAL);
    private static final Color SUPER = new Color(CobblemonUiStyle.POSITIVE);
    private static final Color RESISTED = new Color(CobblemonUiStyle.NEGATIVE);
    private static final Color IMMUNE = new Color(0xFF52525B);
    private static final Color HIGHLIGHT = new Color(0x42FFD35A);
    private static final Color SELECTED = new Color(CobblemonUiStyle.CELL_SELECTED);
    private final TypeMatrixSelection selection = new TypeMatrixSelection(1, 1, 1, 1);
    private double headerWidth;
    private double headerHeight;
    private double cellWidth;
    private double cellHeight;
    private double matrixWidth;
    private double matrixHeight;

    @Override
    protected void onCalculateSize() {
        headerWidth = theme.scale(54);
        headerHeight = theme.scale(54);
        cellWidth = theme.scale(42);
        cellHeight = theme.scale(31);
        matrixWidth = headerWidth + PokemonType.values().length * cellWidth;
        matrixHeight = headerHeight + PokemonType.values().length * cellHeight;
        selection.resize(headerWidth, headerHeight, cellWidth, cellHeight);
        width = matrixWidth;
        height = matrixHeight + theme.textHeight() + pad() * 2;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        selection.hover(mouseX - x, mouseY - y);
        PokemonType attack = selection.activeAttack().orElse(null);
        PokemonType defend = selection.activeDefend().orElse(null);
        renderer.quad(x, y, matrixWidth, matrixHeight, new Color(CobblemonUiStyle.PANEL_BACKGROUND_SOLID));
        for (int row = 0; row < PokemonType.values().length; row++) {
            PokemonType rowType = PokemonType.values()[row];
            drawBadge(renderer, rowType, x, y + headerHeight + row * cellHeight,
                headerWidth, cellHeight);
            for (int column = 0; column < PokemonType.values().length; column++) {
                PokemonType columnType = PokemonType.values()[column];
                double multiplier = TypeEffectiveness.multiplier(rowType, columnType);
                double cellX = x + headerWidth + column * cellWidth;
                double cellY = y + headerHeight + row * cellHeight;
                renderer.quad(cellX, cellY, cellWidth, cellHeight, cellColor(multiplier));
                if (multiplier != 1.0) {
                    String text = multiplier == 0.5 ? ".5" : multiplier == 0.0 ? "0" : "2";
                    renderer.text(text, cellX + (cellWidth - theme.textWidth(text)) / 2,
                        cellY + (cellHeight - theme.textHeight()) / 2, new Color(CobblemonUiStyle.TEXT_PRIMARY), false);
                }
            }
        }
        for (int column = 0; column < PokemonType.values().length; column++) {
            drawBadge(renderer, PokemonType.values()[column],
                x + headerWidth + column * cellWidth, y, cellWidth, headerHeight);
        }
        if (attack != null) renderer.quad(x, y + headerHeight + attack.ordinal() * cellHeight,
            matrixWidth, cellHeight, HIGHLIGHT);
        if (defend != null) renderer.quad(x + headerWidth + defend.ordinal() * cellWidth, y,
            cellWidth, matrixHeight, HIGHLIGHT);
        drawGrid(renderer);
        if (selection.locked() && attack != null && defend != null) {
            outline(renderer, x + headerWidth + defend.ordinal() * cellWidth,
                y + headerHeight + attack.ordinal() * cellHeight, cellWidth, cellHeight, SELECTED);
        }
        String axes = "A\\D";
        renderer.text(axes, x + (headerWidth - theme.textWidth(axes)) / 2,
            y + (headerHeight - theme.textHeight()) / 2,
            new Color(CobblemonUiStyle.TEXT_PRIMARY), false);
        updateTooltip(mouseX - x, mouseY - y);
        String summary = TypeMatrixPresentation.describe(attack, defend);
        renderer.text(summary, x, y + matrixHeight + pad(), new Color(CobblemonUiStyle.TEXT_MUTED), false);
    }

    private void updateTooltip(double localX, double localY) {
        PokemonType hoveredAttack = selection.hoveredAttack().orElse(null);
        PokemonType hoveredDefend = selection.hoveredDefend().orElse(null);
        if (hoveredAttack != null && localX < headerWidth) {
            tooltip = TypeMatrixPresentation.headerTooltip(hoveredAttack, true);
        } else if (hoveredDefend != null && localY < headerHeight) {
            tooltip = TypeMatrixPresentation.headerTooltip(hoveredDefend, false);
        } else if (hoveredAttack != null && hoveredDefend != null) {
            tooltip = TypeMatrixPresentation.describe(hoveredAttack, hoveredDefend);
        } else tooltip = null;
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button, boolean used) {
        if (used || button != GLFW_MOUSE_BUTTON_LEFT || !isOver(mouseX, mouseY)) return false;
        double localX = mouseX - x;
        double localY = mouseY - y;
        selection.hover(localX, localY);
        PokemonType attack = selection.hoveredAttack().orElse(null);
        PokemonType defend = selection.hoveredDefend().orElse(null);
        if (attack != null && defend != null) selection.clickCell(attack, defend);
        else if (attack != null && localX < headerWidth) selection.clickAttackHeader(attack);
        else if (defend != null && localY < headerHeight) selection.clickDefendHeader(defend);
        return true;
    }

    @Override
    public boolean onKeyPressed(int key, int mods) {
        if (key != GLFW_KEY_ESCAPE || !selection.locked()) return false;
        selection.clear();
        return true;
    }

    private void drawBadge(GuiRenderer renderer, PokemonType type, double badgeX, double badgeY,
                           double badgeWidth, double badgeHeight) {
        renderer.quad(badgeX, badgeY, badgeWidth, badgeHeight, new Color(type.argb()));
        String text = type.abbreviation();
        renderer.text(text, badgeX + (badgeWidth - theme.textWidth(text)) / 2,
            badgeY + (badgeHeight - theme.textHeight()) / 2, new Color(CobblemonUiStyle.TEXT_PRIMARY), false);
    }

    private void drawGrid(GuiRenderer renderer) {
        double line = theme.scale(1);
        for (int index = 0; index <= PokemonType.values().length; index++) {
            double offsetX = headerWidth + index * cellWidth;
            double offsetY = headerHeight + index * cellHeight;
            renderer.quad(x + offsetX, y, line, matrixHeight, GRID);
            renderer.quad(x, y + offsetY, matrixWidth, line, GRID);
        }
        outline(renderer, x, y, matrixWidth, matrixHeight, GRID);
    }

    private static Color cellColor(double multiplier) {
        if (multiplier == 0.0) return IMMUNE;
        if (multiplier < 1.0) return RESISTED;
        if (multiplier > 1.0) return SUPER;
        return NEUTRAL;
    }

    private void outline(GuiRenderer renderer, double boxX, double boxY, double boxWidth,
                         double boxHeight, Color color) {
        double line = theme.scale(1);
        renderer.quad(boxX, boxY, boxWidth, line, color);
        renderer.quad(boxX, boxY + boxHeight - line, boxWidth, line, color);
        renderer.quad(boxX, boxY, line, boxHeight, color);
        renderer.quad(boxX + boxWidth - line, boxY, line, boxHeight, color);
    }

}
