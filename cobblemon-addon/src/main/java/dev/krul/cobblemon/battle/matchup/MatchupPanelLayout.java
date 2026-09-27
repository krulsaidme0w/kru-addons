package dev.krul.cobblemon.battle.matchup;

/** Pixel geometry shared by rendering, hover hit testing and offline previews. */
public record MatchupPanelLayout(Rect body, Rect backdrop, Rect historyTab, Rect matchupTab, int labelWidth,
                                 int cellWidth, int visibleColumns, int visibleRows, int viewportWidth, int viewportHeight) {
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    public static Rect compactPane(Rect anchor, int viewportWidth, int viewportHeight, int columns, int rows) {
        // About 70% of the previous 302px height, with a floor for six readable
        // 24px rows/columns. Native setters require a 20px right/bottom margin.
        int minimum = Math.max(22 + (Math.max(1, columns) + 1) * 24,
            50 + (Math.max(1, rows) + 1) * 24);
        int side = Math.max(212, minimum);
        side = Math.min(side, Math.max(0, Math.min(viewportWidth - 24, viewportHeight - 40)));
        int width = side, height = side;
        return new Rect(Math.clamp(anchor.x(), 0, Math.max(0, viewportWidth - width)),
            Math.clamp(anchor.y(), Math.min(16, viewportHeight), Math.max(Math.min(16, viewportHeight), viewportHeight - height)), width, height);
    }
    public static MatchupPanelLayout fit(Rect pane, int viewportWidth, int viewportHeight, int columns, int rows) {
        return fit(pane, viewportWidth, viewportHeight, columns, rows, 0);
    }
    public static MatchupPanelLayout fit(Rect pane, int viewportWidth, int viewportHeight, int columns, int rows, int ignoredTextWidth) {
        int left = Math.clamp(pane.x() + 8, 0, viewportWidth), top = Math.clamp(pane.y() + 8, 0, viewportHeight);
        int right = Math.clamp(pane.x() + pane.width() - 14, left, viewportWidth);
        int bottom = Math.clamp(pane.y() + pane.height() - 8, top, viewportHeight);
        Rect body = new Rect(left, top, right - left, bottom - top);
        // Native text starts left of our content padding. Cover that margin too,
        // without covering the frame or the lower-right native resize handle.
        int bgLeft = Math.clamp(pane.x() + 7, 0, viewportWidth), bgTop = Math.clamp(pane.y() + 7, 0, viewportHeight);
        int bgRight = Math.clamp(pane.x() + pane.width() - 13, bgLeft, viewportWidth);
        int bgBottom = Math.clamp(pane.y() + pane.height() - 5, bgTop, viewportHeight);
        Rect backdrop = new Rect(bgLeft, bgTop, bgRight - bgLeft, bgBottom - bgTop);
        int tabX = Math.clamp(pane.x() + 8, 0, Math.max(0, viewportWidth - 144));
        int tabY = Math.clamp(pane.y() - 15, 0, Math.max(0, viewportHeight - 14));
        Rect history = new Rect(tabX, tabY, Math.min(60, viewportWidth), Math.min(14, viewportHeight));
        Rect matchups = new Rect(Math.min(tabX + 62, viewportWidth), tabY, Math.min(82, Math.max(0, viewportWidth - tabX - 62)), Math.min(14, viewportHeight));
        int size = columns <= 0 || rows <= 0 ? 0 : Math.min(22, Math.min(body.width() / (columns + 1), (body.height() - 34) / (rows + 1)));
        boolean fits = size >= 20;
        return new MatchupPanelLayout(body, backdrop, history, matchups, fits ? size : 0, fits ? size : 0,
            fits ? columns : 0, fits ? rows : 0, viewportWidth, viewportHeight);
    }
    public Rect cell(int column, int row) { return new Rect(body.x() + labelWidth + column * cellWidth, body.y() + 12 + cellWidth + row * cellWidth, cellWidth, cellWidth); }
    public Rect ownHeader(int column) { return new Rect(cell(column, 0).x(), body.y() + 12, cellWidth, cellWidth); }
    public Rect enemyHeader(int row) { return new Rect(body.x(), cell(0, row).y(), labelWidth, cellWidth); }
    public int[] hit(double mouseX, double mouseY) {
        for (int col = 0; col < visibleColumns; col++) for (int row = 0; row < visibleRows; row++) {
            if (cell(col, row).contains(mouseX, mouseY)) return new int[]{col, row};
        }
        return null;
    }
}
