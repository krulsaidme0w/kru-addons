package dev.krul.cobblemon.gui.overlay;

/** Contents never own a Minecraft screen or its input lifecycle. */
public interface OverlayContent {
    String title();
    int width();
    int height();
    void render(OverlayCanvas canvas, double mouseX, double mouseY);
    default void tick() {}
    default boolean click(double x, double y, int button) { return false; }
    default boolean scroll(double x, double y, double amount) { return false; }
    default boolean key(int key) { return false; }
    /** A live, cheap context key; null means this contextual panel is ineligible. */
    default boolean contextual() { return false; }
    default Object contextKey() { return null; }
    default double defaultScale() { return 1.0; }
    default boolean textFocused() { return false; }
    default void blur() {}
    default boolean key(int key, int mods) { return key(key); }
    default boolean charTyped(int codePoint, int mods) { return false; }
    default java.util.List<String> tooltip(double x, double y) { return java.util.List.of(); }
    default void reset() {}
}
