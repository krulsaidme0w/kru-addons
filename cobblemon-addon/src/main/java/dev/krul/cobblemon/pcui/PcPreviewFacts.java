package dev.krul.cobblemon.pcui;

import java.util.List;
import static dev.krul.cobblemon.pcui.PcReflection.*;

/** Values from the selected client Pokemon only; no inferred stats or server-link claims. */
public final class PcPreviewFacts {
    private PcPreviewFacts() {}

    public static List<String> read(Object pokemon) {
        if (pokemon == null) return List.of("No Pokemon selected.");
        var secondary = PcReflection.read(pokemon, "getSecondaryType");
        String second = secondary.available() && secondary.value() == null ? "none"
            : value(get(secondary.value(), "getName"));
        return List.of(
            "Species: " + value(get(get(pokemon, "getSpecies"), "getResourceIdentifier")),
            "UUID: " + value(get(pokemon, "getUuid")),
            "Level: " + value(get(pokemon, "getLevel")) + " | Gender: " + value(get(pokemon, "getGender")),
            "Types: " + value(get(get(pokemon, "getPrimaryType"), "getName")) + " / " + second,
            "HP: " + value(get(pokemon, "getCurrentHealth")) + " / " + value(get(pokemon, "getMaxHealth"))
                + " | Shiny: " + value(get(pokemon, "getShiny"))
        );
    }
}
