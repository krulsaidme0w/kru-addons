package dev.krul.cobblemon.discovery;

public enum DiscoveryAppearanceFilter {
    ANY,
    SHINY,
    RADIANT,
    SHINY_OR_RADIANT;

    public boolean matches(PokemonObservation observation) {
        if (observation == null) return false;
        return switch (this) {
            case ANY -> true;
            case SHINY -> Boolean.TRUE.equals(observation.shiny());
            case RADIANT -> Boolean.TRUE.equals(observation.radiant());
            case SHINY_OR_RADIANT -> Boolean.TRUE.equals(observation.shiny())
                || Boolean.TRUE.equals(observation.radiant());
        };
    }
}
