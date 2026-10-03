package dev.krul.cobblemon.hover;

/** Restrained background accent derived only from a complete six-stat IV set. */
public enum IvTintTier {
    NONE(0), IV_60(60), IV_80(80), IV_90(90), IV_95(95);

    private final int minimumPercent;
    IvTintTier(int minimumPercent) { this.minimumPercent = minimumPercent; }
    public int minimumPercent() { return minimumPercent; }

    public static IvTintTier forPercentage(int percentage) {
        if (percentage >= 95) return IV_95;
        if (percentage >= 90) return IV_90;
        if (percentage >= 80) return IV_80;
        if (percentage >= 60) return IV_60;
        return NONE;
    }

    public static IvTintTier from(int[] ivs) {
        if (ivs == null || ivs.length != 6) return NONE;
        int total = 0;
        for (int iv : ivs) {
            if (iv < 0 || iv > 31) return NONE;
            total += iv;
        }
        return forPercentage(total * 100 / (31 * 6));
    }
}
