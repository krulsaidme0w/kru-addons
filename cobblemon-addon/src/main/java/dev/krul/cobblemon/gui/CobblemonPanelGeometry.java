package dev.krul.cobblemon.gui;

/** Pure geometry helpers used by screens and overlays; no Minecraft state. */
public final class CobblemonPanelGeometry {
    private CobblemonPanelGeometry() {}

    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return width > 0 && height > 0 && px >= x && py >= y
                && px < x + width && py < y + height;
        }
    }

    public static Bounds clamp(int x, int y, int width, int height,
                               int viewportWidth, int viewportHeight, int margin) {
        if (width <= 0 || height <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
            return new Bounds(0, 0, 0, 0);
        }
        int safeMargin = Math.max(0, margin);
        int usableWidth = Math.max(0, viewportWidth - safeMargin * 2);
        int usableHeight = Math.max(0, viewportHeight - safeMargin * 2);
        int safeWidth = Math.min(width, usableWidth);
        int safeHeight = Math.min(height, usableHeight);
        int left = Math.clamp(x, safeMargin, Math.max(safeMargin, viewportWidth - safeMargin - safeWidth));
        int top = Math.clamp(y, safeMargin, Math.max(safeMargin, viewportHeight - safeMargin - safeHeight));
        return new Bounds(left, top, safeWidth, safeHeight);
    }

    public static Bounds tooltip(Bounds anchor, int mouseX, int mouseY, int width, int height,
                                 int viewportWidth, int viewportHeight, int margin) {
        if (anchor.width() <= 0 || anchor.height() <= 0 || width <= 0 || height <= 0) {
            return new Bounds(0, 0, 0, 0);
        }
        int x = mouseX + CobblemonUiStyle.PADDING + CobblemonUiStyle.GAP;
        if (x + width > viewportWidth - margin) x = mouseX - width + CobblemonUiStyle.PADDING * 2;
        int y = mouseY + CobblemonUiStyle.PADDING + CobblemonUiStyle.GAP;
        if (y + height > viewportHeight - margin) y = mouseY - height + CobblemonUiStyle.PADDING - 2;
        return clamp(x, y, width, height, viewportWidth, viewportHeight, margin);
    }
}
