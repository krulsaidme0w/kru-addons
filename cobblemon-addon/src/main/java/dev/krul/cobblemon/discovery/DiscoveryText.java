package dev.krul.cobblemon.discovery;

import java.util.List;
import java.util.Locale;

public final class DiscoveryText {
    private DiscoveryText() {
    }

    public static List<String> lines(PokemonObservation observation, boolean showRarity) {
        StringBuilder title = new StringBuilder(observation.species());
        if (Boolean.TRUE.equals(observation.shiny())) title.append(" [shiny]");
        if (Boolean.TRUE.equals(observation.radiant())) title.append(" [radiant]");
        observation.speciesClasses().stream()
            .filter(value -> value.special())
            .sorted()
            .findFirst()
            .ifPresent(value -> title.append(" [")
                .append(value.name().toLowerCase(Locale.ROOT))
                .append(']'));
        if (showRarity && observation.spawnRarity() != SpawnRarity.UNKNOWN) {
            title.append(" [spawn: ")
                .append(observation.spawnRarity().name().toLowerCase(Locale.ROOT).replace('_', '-'))
                .append(']');
        }
        return List.of(title.toString());
    }
}
