package dev.krul.builder.ui;

/** Shared label and render gate for Builder's world overlay toggle. */
public final class BuilderOverlayVisibility {
    private BuilderOverlayVisibility() {}

    public static boolean shouldRender(boolean enabled, boolean captureSuppressed) {
        return enabled && !captureSuppressed;
    }

    public static String buttonLabel(boolean enabled) {
        return enabled ? "Overlay: ON" : "Overlay: OFF";
    }
}
