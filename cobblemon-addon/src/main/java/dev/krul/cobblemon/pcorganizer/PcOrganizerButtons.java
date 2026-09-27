package dev.krul.cobblemon.pcorganizer;

/** Shared drawing/input geometry: a visible control has one exact hit rectangle. */
enum PcOrganizerButtons {
    PREVIEW(8, 120), APPLY(169, 120), STOP(8, 148), LOCK(169, 148), SCOPE(8, 176), SORT(169, 176);
    final int x, y;
    static final int WIDTH = 153, HEIGHT = 21;
    PcOrganizerButtons(int x, int y) { this.x = x; this.y = y; }
    static PcOrganizerButtons at(double x, double y) {
        for (var button : values()) if (x >= button.x && x < button.x + WIDTH && y >= button.y && y < button.y + HEIGHT) return button;
        return null;
    }
}
