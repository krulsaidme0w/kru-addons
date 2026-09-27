package dev.krul.cobblemon.discovery;

import dev.krul.cobblemon.species.SpeciesClass;

import java.util.Set;

public enum DiscoverySpeciesClassFilter {
    ANY,
    SPECIAL,
    LEGENDARY,
    MYTHICAL,
    ULTRA_BEAST,
    PARADOX;

    public boolean matches(Set<SpeciesClass> classes) {
        Set<SpeciesClass> actual = classes == null ? Set.of() : classes;
        return switch (this) {
            case ANY -> true;
            case SPECIAL -> actual.stream().anyMatch(SpeciesClass::special);
            case LEGENDARY -> actual.contains(SpeciesClass.LEGENDARY);
            case MYTHICAL -> actual.contains(SpeciesClass.MYTHICAL);
            case ULTRA_BEAST -> actual.contains(SpeciesClass.ULTRA_BEAST);
            case PARADOX -> actual.contains(SpeciesClass.PARADOX);
        };
    }
}

