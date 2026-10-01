package dev.krul.builder.ui;

final class BuilderViewport {
    record ChatLayout(boolean sidebarVisible, double sidebarWidth, double transcriptHeight, double composerHeight) {}
    private BuilderViewport() {}

    static ChatLayout chatLayout(int physicalWidth, int physicalHeight, double scale) {
        double safeScale = Math.max(0.5, scale);
        boolean sidebar = physicalWidth >= 900 * safeScale;
        double width = sidebar ? Math.max(145 * safeScale, Math.min(230 * safeScale, physicalWidth * 0.22)) : 0;
        double transcript = Math.min(450 * safeScale, Math.max(120 * safeScale, physicalHeight * 0.58));
        double composer = 42 * safeScale;
        return new ChatLayout(sidebar, width, transcript, composer);
    }

    static double maxHeight(int physicalWindowHeight, double bottomInset) {
        return Math.max(0, physicalWindowHeight - bottomInset);
    }

    static double maxWindowViewHeight(int physicalWindowHeight, double windowChromeHeight) {
        return maxHeight(physicalWindowHeight, windowChromeHeight);
    }

    static double contentHeight(int physicalWindowHeight, double maximum, double viewportFraction) {
        return Math.min(maximum, Math.max(0, physicalWindowHeight * viewportFraction));
    }
}
