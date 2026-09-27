package dev.krul.cobblemon.pokemon;

import java.util.Map;
import java.util.Set;

/** Null appearance values are unavailable, never a confirmed false. Aspects retain their literal IDs. */
public record CobblemonPokemonData(String species, Boolean shiny, Boolean radiant, Set<String> aspects,
                                   Map<String, String> details) {
    public CobblemonPokemonData(String species, Boolean shiny, Boolean radiant, Set<String> aspects) {
        this(species, shiny, radiant, aspects, Map.of());
    }
    public CobblemonPokemonData {
        aspects = aspects == null ? Set.of() : Set.copyOf(aspects);
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
