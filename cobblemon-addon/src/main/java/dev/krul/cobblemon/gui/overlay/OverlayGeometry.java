package dev.krul.cobblemon.gui.overlay;

/** One coordinate transform for rendering and hit-testing; sizes are local GUI pixels. */
public record OverlayGeometry(double x, double y, double width, double height, double scale) {
    public static OverlayGeometry fit(double x, double y, double width, double height,
                                      double scale, double viewportWidth, double viewportHeight) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        scale = Double.isFinite(scale) ? Math.clamp(scale, .5, 2) : 1;
        scale = Math.max(.5, Math.min(scale, Math.min(Math.max(1, viewportWidth - 8) / width,
            Math.max(1, viewportHeight - 8) / height)));
        width = Math.min(width, Math.max(1, viewportWidth - 8) / scale);
        height = Math.min(height, Math.max(1, viewportHeight - 8) / scale);
        x = Double.isFinite(x) ? x : 4;
        y = Double.isFinite(y) ? y : 4;
        x = Math.clamp(x, 4, Math.max(4, viewportWidth - width * scale - 4));
        y = Math.clamp(y, 4, Math.max(4, viewportHeight - height * scale - 4));
        return new OverlayGeometry(x, y, width, height, scale);
    }
    public double localX(double screenX) { return (screenX - x) / scale; }
    public double localY(double screenY) { return (screenY - y) / scale; }
    public record Point(double x, double y) {}
    public Point contentPoint(double screenX, double screenY, double titleHeight, double scrollX, double scrollY) {
        return new Point(localX(screenX) + scrollX, localY(screenY) - titleHeight + scrollY);
    }
    public boolean contains(double screenX, double screenY) {
        return screenX >= x && screenY >= y && localX(screenX) < width && localY(screenY) < height;
    }
}
