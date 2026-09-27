package dev.krul.cobblemon.pokemon;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CobblemonPokemonReaderTest {
    @Test
    void radiantAspectIsExposedAsConfirmedAppearanceFact() {
        var data = CobblemonPokemonReader.readPokemon(new FakePokemon()).orElseThrow();
        assertEquals("cobblemon:ralts", data.species());
        assertNull(data.radiant());
        assertTrue(data.aspects().contains("radiant"));
    }

    @Test
    void optionalLiveDetailsAreCollectedIndependently() {
        var data = CobblemonPokemonReader.readPokemon(new DetailedPokemon()).orElseThrow();
        assertEquals("Alolan", data.details().get("form"));
        assertEquals("42", data.details().get("level"));
        assertEquals("FEMALE", data.details().get("gender"));
    }

    public static class FakePokemon {
        public FakeSpecies getSpecies() { return new FakeSpecies(); }
        public Boolean getShiny() { return false; }
        public List<String> getAspects() { return List.of("radiant", "day"); }
    }

    public static final class FakeSpecies {
        public String getResourceIdentifier() { return "cobblemon:ralts"; }
    }

    public static final class DetailedPokemon extends FakePokemon {
        public FakeForm getForm() { return new FakeForm(); }
        public int getLevel() { return 42; }
        public String getGender() { return "FEMALE"; }
    }

    public static final class FakeForm {
        public String getName() { return "Alolan"; }
    }
}
