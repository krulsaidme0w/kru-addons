package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PcPreviewFactsTest {
    @Test void noPreviewIsNotInventedPokemon() {
        assertEquals(java.util.List.of("No Pokemon selected."), PcPreviewFacts.read(null));
    }

    @Test void missingAndThrowingGettersStayUnknownRatherThanZeroOrFalse() {
        var facts = PcPreviewFacts.read(new PartialPokemon());
        assertTrue(facts.contains("Level: unknown | Gender: unknown"));
        assertTrue(facts.contains("HP: unknown / unknown | Shiny: unknown"));
        assertTrue(facts.contains("Species: cobblemon:ralts"));
    }

    @Test void copiesOnlyObservedValuesWithExplicitUnknownSecondaryType() {
        var facts = PcPreviewFacts.read(new KnownPokemon());
        assertTrue(facts.contains("UUID: 00000000-0000-0000-0000-000000000123"));
        assertTrue(facts.contains("Level: 12 | Gender: FEMALE"));
        assertTrue(facts.contains("HP: 20 / 30 | Shiny: false"));
        assertTrue(facts.contains("Types: psychic / unknown"));
    }

    @Test void observedNullSecondaryTypeMeansNone() {
        assertTrue(PcPreviewFacts.read(new MonotypePokemon()).contains("Types: psychic / none"));
    }

    public static class MonotypePokemon extends KnownPokemon {
        public Type getSecondaryType() { return null; }
    }

    public static class PartialPokemon {
        public Species getSpecies() { return new Species(); }
        public int getLevel() { throw new IllegalStateException("unavailable"); }
    }
    public static class Species {
        public String getResourceIdentifier() { return "cobblemon:ralts"; }
    }
    public static class Type {
        public String getName() { return "psychic"; }
    }
    public static class KnownPokemon extends PartialPokemon {
        public UUID getUuid() { return UUID.fromString("00000000-0000-0000-0000-000000000123"); }
        @Override public int getLevel() { return 12; }
        public String getGender() { return "FEMALE"; }
        public int getCurrentHealth() { return 20; }
        public int getMaxHealth() { return 30; }
        public boolean getShiny() { return false; }
        public Type getPrimaryType() { return new Type(); }
        // Absent getter is deliberately different from a known null secondary type.
    }
}
