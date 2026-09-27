package dev.krul.cobblemon.gui.overlay;

/** Viewport-space tooltip geometry, independent of the panel's scroll and scale. */
public final class OverlayTooltip {
    private OverlayTooltip() {}
    public static java.util.List<String> wrap(java.util.List<String> lines, int maxWidth,
                                              java.util.function.ToIntFunction<String> measure) {
        var result = new java.util.ArrayList<String>();
        for (String line : lines) for (String paragraph : line.split("\\R", -1)) {
            String rest = paragraph.strip();
            if (rest.isEmpty()) { result.add(""); continue; }
            while (!rest.isEmpty()) {
                int end = 0;
                while (end < rest.length()) {
                    int next = rest.offsetByCodePoints(end, 1);
                    if (measure.applyAsInt(rest.substring(0, next)) > maxWidth) break;
                    end = next;
                }
                if (end == 0) end = rest.offsetByCodePoints(0, 1);
                if (end < rest.length()) {
                    int space = rest.lastIndexOf(' ', end);
                    if (space > 0) end = space;
                }
                result.add(rest.substring(0, end).stripTrailing());
                rest = rest.substring(end).stripLeading();
            }
        }
        return java.util.List.copyOf(result);
    }
    public record Box(int x, int y, int width, int height) {}
    public static Box place(double mouseX, double mouseY, int width, int height, int viewportWidth, int viewportHeight) {
        int w = Math.min(width, Math.max(1, viewportWidth - 4));
        int h = Math.min(height, Math.max(1, viewportHeight - 4));
        int x = (int) mouseX + 10, y = (int) mouseY + 12;
        if (x + w > viewportWidth - 2) x = (int) mouseX - w - 8;
        if (y + h > viewportHeight - 2) y = (int) mouseY - h - 8;
        return new Box(Math.clamp(x, 0, Math.max(0, viewportWidth - w)),
            Math.clamp(y, 0, Math.max(0, viewportHeight - h)), w, h);
    }
}
