package dev.krul.cobblemon.discovery;

import java.util.Locale;

/** Exact optional species target shared by Discovery rendering and Auto RTP. */
public final class DiscoveryTarget {
    private DiscoveryTarget() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(":") ? normalized : "cobblemon:" + normalized;
    }

    public static boolean matches(String rawTarget, String observedSpecies) {
        String target = normalize(rawTarget);
        return target.isEmpty() || target.equals(normalize(observedSpecies));
    }

    public static boolean active(String rawTarget) {
        return !normalize(rawTarget).isEmpty();
    }
}

