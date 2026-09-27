package dev.krul.cobblemon.species;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class LiveCobblemonSpeciesFactsResolverTest {
    @Test
    void readsAllFactsFromTheSpeciesDefinitionAndCachesByNormalizedId() {
        FakeSpecies cobalion = new FakeSpecies();
        int[] lookups = {0};
        LiveCobblemonSpeciesFactsResolver resolver = new LiveCobblemonSpeciesFactsResolver(species -> {
            lookups[0]++;
            return species.equals("cobblemon:cobalion") ? cobalion : null;
        });

        SpeciesFacts first = resolver.resolve(" Cobblemon:Cobalion ");
        SpeciesFacts second = resolver.resolve("cobblemon:cobalion");

        assertSame(first, second);
        assertEquals(1, lookups[0]);
        assertEquals(638, first.nationalDex());
        assertEquals(Set.of("steel", "fighting"), first.types());
        assertEquals(Set.of("undiscovered"), first.eggGroups());
        assertEquals(Set.of("gen5", "legendary"), first.labels());
        assertEquals(Set.of(SpeciesClass.LEGENDARY), first.classes());
    }

    @Test
    void missingOrUnreadableDefinitionRemainsUnknown() {
        LiveCobblemonSpeciesFactsResolver missing = new LiveCobblemonSpeciesFactsResolver(species -> null);
        LiveCobblemonSpeciesFactsResolver broken = new LiveCobblemonSpeciesFactsResolver(species -> new Object());

        assertFalse(missing.resolve("custom:missing").authoritative());
        assertFalse(broken.resolve("custom:broken").authoritative());
    }

    @Test
    void preservesTheOfficialPrimaryTypeFromTheLiveSpeciesOrder() {
        LiveCobblemonSpeciesFactsResolver resolver = new LiveCobblemonSpeciesFactsResolver(
            species -> new FakeRalts());

        SpeciesFacts facts = resolver.resolve("cobblemon:ralts");

        assertEquals("psychic", facts.primaryType());
        assertEquals(Set.of("psychic", "fairy"), facts.types());
    }

    public static final class FakeSpecies {
        public int getNationalPokedexNumber() { return 638; }
        public Set<String> getLabels() { return Set.of("gen5", "legendary"); }
        public List<FakeType> getTypes() { return List.of(new FakeType("steel"), new FakeType("fighting")); }
        public Set<FakeEggGroup> getEggGroups() { return Set.of(new FakeEggGroup("UNDISCOVERED")); }
    }

    public static final class FakeRalts {
        public int getNationalPokedexNumber() { return 280; }
        public Set<String> getLabels() { return Set.of("gen3"); }
        public List<FakeType> getTypes() { return List.of(new FakeType("psychic"), new FakeType("fairy")); }
        public Set<FakeEggGroup> getEggGroups() { return Set.of(new FakeEggGroup("HUMAN_LIKE")); }
    }

    public record FakeType(String name) {
        public String getName() { return name; }
    }

    public record FakeEggGroup(String name) {
        public String getShowdownID() { return name; }
    }
}

