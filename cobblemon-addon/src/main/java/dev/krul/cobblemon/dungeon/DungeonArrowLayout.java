package dev.krul.cobblemon.dungeon;

/** Pure screen-space triangle geometry for an off-screen target indicator. */
public final class DungeonArrowLayout {
    private DungeonArrowLayout() {
    }

    public static double[] triangle(double targetX, double targetY, double width, double height,
                                    double inset, double size) {
        double centerX = width / 2;
        double centerY = height / 2;
        double dx = targetX - centerX;
        double dy = targetY - centerY;
        double length = Math.hypot(dx, dy);
        if (length < 0.001) return new double[] {centerX, centerY, centerX, centerY, centerX, centerY};
        double ux = dx / length;
        double uy = dy / length;
        double radius = Math.max(0, Math.min(width, height) / 2 - inset);
        double tipX = centerX + ux * radius;
        double tipY = centerY + uy * radius;
        double baseX = tipX - ux * size * 1.5;
        double baseY = tipY - uy * size * 1.5;
        double px = -uy * size * 0.75;
        double py = ux * size * 0.75;
        return new double[] {tipX, tipY, baseX + px, baseY + py, baseX - px, baseY - py};
    }
}

