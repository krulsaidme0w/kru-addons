package dev.krul.cobblemon.hover;

/** Pure viewport placement for the detail card and hovered slot. */
public final class HoverCardLayout {
    private HoverCardLayout() {}

    public static Rect place(double mouseX, double mouseY, double cardWidth, double cardHeight,
                             double viewportWidth, double viewportHeight, Rect hoveredSlot) {
        double width = Math.min(Math.max(0, cardWidth), Math.max(0, viewportWidth));
        double height = Math.min(Math.max(0, cardHeight), Math.max(0, viewportHeight));
        double x = clamp(mouseX + 12, width, viewportWidth);
        double y = clamp(mouseY + 12, height, viewportHeight);
        Rect primary = new Rect(x, y, width, height);
        if (hoveredSlot == null || !primary.intersects(hoveredSlot)) return primary;
        Rect left = new Rect(clamp(mouseX - width - 12, width, viewportWidth), y, width, height);
        if (!left.intersects(hoveredSlot)) return left;
        Rect above = new Rect(x, clamp(mouseY - height - 12, height, viewportHeight), width, height);
        return above.intersects(hoveredSlot) ? primary : above;
    }

    private static double clamp(double position, double size, double viewport) {
        return Math.max(0, Math.min(position, Math.max(0, viewport - size)));
    }

    public record Rect(double x, double y, double width, double height) {
        public boolean intersects(Rect other) {
            return other != null && x < other.x + other.width && x + width > other.x
                && y < other.y + other.height && y + height > other.y;
        }
    }
}
