package dev.krul.cobblemon.pcorganizer;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcOrganizerClassificationReaderTest {
    public record TypedPokemon(List<Object> types) { public List<Object> getTypes() { return types; } }
    public record Type(String name) { public String getName() { return name; } }
    @Test void unreadablePrimaryCannotPromoteSecondaryIntoPrimaryPosition() {
        assertEquals("/water", PcOrganizerLiveReader.typeNames(new TypedPokemon(List.of(new Object(), new Type("Water")))));
        assertEquals("fire/water", PcOrganizerLiveReader.typeNames(new TypedPokemon(List.of(new Type("Fire"), new Type("Water")))));
    }
    public record Pokemon(Object species) { public Object getSpecies() { return species; } }
    public record Species(List<String> labels) { public List<String> getLabels() { return labels; } }
    @Test void readsIntrinsicSpecialClassesRatherThanSpawnRarity() {
        assertEquals("legendary,restricted", PcOrganizerLiveReader.intrinsicClass(new Pokemon(new Species(List.of("legendary", "restricted", "rare")))));
        assertEquals("ultra_beast", PcOrganizerLiveReader.intrinsicClass(new Pokemon(new Species(List.of("ultra-beast")))));
        assertEquals("ordinary", PcOrganizerLiveReader.intrinsicClass(new Pokemon(new Species(List.of("fossil")))));
    }
    @Test void distinguishesKnownOrdinaryFromMissingLabels() {
        assertEquals("ordinary", PcOrganizerLiveReader.intrinsicClass(new Pokemon(new Species(List.of()))));
        assertEquals("", PcOrganizerLiveReader.intrinsicClass(new Pokemon(new Object())));
        assertEquals("", PcOrganizerLiveReader.intrinsicClass(new Pokemon(new Species(null))));
    }
}
