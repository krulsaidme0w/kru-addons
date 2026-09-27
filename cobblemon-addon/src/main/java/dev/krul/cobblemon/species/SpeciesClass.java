package dev.krul.cobblemon.species;

import java.util.Locale;
import java.util.Optional;

/** Intrinsic class supplied by a Cobblemon species definition label. */
public enum SpeciesClass {
    LEGENDARY,
    MYTHICAL,
    ULTRA_BEAST,
    PARADOX,
    RESTRICTED,
    FOSSIL,
    BABY;

    public static Optional<SpeciesClass> fromLabel(String label) {
        if (label == null || label.isBlank()) return Optional.empty();
        String normalized = SpeciesFacts.normalizeLabel(label);
        for (SpeciesClass value : values()) {
            if (value.name().toLowerCase(Locale.ROOT).equals(normalized)) return Optional.of(value);
        }
        return Optional.empty();
    }

    public boolean special() {
        return this == LEGENDARY || this == MYTHICAL || this == ULTRA_BEAST
            || this == PARADOX || this == RESTRICTED;
    }
}

