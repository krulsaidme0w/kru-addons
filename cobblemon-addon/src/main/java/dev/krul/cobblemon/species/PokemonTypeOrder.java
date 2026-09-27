package dev.krul.cobblemon.species;

import java.util.List;

/** Canonical order used when a species source cannot preserve primary-type order. */
public final class PokemonTypeOrder {
    private static final List<String> VALUES = List.of(
        "normal", "fire", "water", "electric", "grass", "ice", "fighting",
        "poison", "ground", "flying", "psychic", "bug", "rock", "ghost",
        "dragon", "dark", "steel", "fairy"
    );

    private PokemonTypeOrder() {}

    public static int rank(String type) {
        int index = VALUES.indexOf(SpeciesFacts.normalizeLabel(type));
        return index < 0 ? Integer.MAX_VALUE : index;
    }
}

