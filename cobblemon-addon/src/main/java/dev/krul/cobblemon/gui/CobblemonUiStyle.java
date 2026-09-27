package dev.krul.cobblemon.gui;

/**
 * Small, dependency-free visual contract shared by Cobblemon addon surfaces.
 * Colors are ARGB because both Meteor's GuiRenderer and DrawContext accept
 * the same solid-color representation through their adapters.
 */
public final class CobblemonUiStyle {
    private CobblemonUiStyle() {}

    public static final int PANEL_BACKGROUND = 0xE6111111;
    public static final int PANEL_BACKGROUND_SOLID = 0xFF111111;
    public static final int PANEL_BORDER = 0xFF2B2B35;
    public static final int GRID = 0x806B6B78;
    public static final int TEXT_PRIMARY = 0xFFF0F0F0;
    public static final int TEXT_MUTED = 0xFFB8B8C4;
    public static final int ACCENT = 0xFFFFD35A;
    public static final int POSITIVE = 0xFFA8E0AC;
    public static final int NEGATIVE = 0xFFFFB0A7;
    public static final int WARNING = 0xFFFFD28A;
    public static final int CELL_NEUTRAL = 0xFF24242C;
    public static final int CELL_SELECTED = 0xFFFFD35A;

    public static final int PADDING = 8;
    public static final int GAP = 4;
    public static final int ROW_HEIGHT = 12;
    public static final int BORDER = 1;
}
