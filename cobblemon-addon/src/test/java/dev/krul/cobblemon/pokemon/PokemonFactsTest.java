package dev.krul.cobblemon.pokemon;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PokemonFactsTest {
    public record Species(String id) { public String getResourceIdentifier() { return id; } }
    public record IncompletePokemon(Species species) { public Species getSpecies() { return species; } }
    public record KnownPokemon(Species species, boolean shiny, Set<String> aspects) {
        public Species getSpecies() { return species; }
        public boolean getShiny() { return shiny; }
        public Set<String> getAspects() { return aspects; }
    }

    @Test void missingAppearanceFieldsRemainUnknown() {
        var facts = CobblemonPokemonReader.readPokemon(new IncompletePokemon(new Species("custom:bird"))).orElseThrow();
        assertEquals("custom:bird", facts.species());
        assertNull(facts.shiny());
        assertNull(facts.radiant());
    }

    @Test void standardAppearanceIsReadWithoutInterpretingServerAspectNames() {
        var facts = CobblemonPokemonReader.readPokemon(new KnownPokemon(new Species("cobblemon:eevee"), true,
            Set.of("radiant"))).orElseThrow();
        assertEquals(Boolean.TRUE, facts.shiny());
        assertNull(facts.radiant());
        assertEquals(Set.of("radiant"), facts.aspects());
    }
}
