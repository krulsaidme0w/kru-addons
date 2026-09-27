package dev.krul.cobblemon.observation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.krul.cobblemon.observation.FactValue.*;
import static org.junit.jupiter.api.Assertions.*;

class PokemonFactReaderTest {
    public static class FailedProjection { public Object getPokemon() { throw new IllegalStateException(); } }
    public static class NullProjection { public Object getPokemon() { return null; } }
    @Test void failedEntityProjectionIsUnsupportedWhileActualNullIsNotSent() {
        assertEquals(Availability.READER_UNSUPPORTED, PokemonFactReader.readEntityProjection(new FailedProjection(), new PokemonFactReader.RegistryCache()).get("species").availability());
        assertEquals(Availability.NOT_SENT, PokemonFactReader.readEntityProjection(new NullProjection(), new PokemonFactReader.RegistryCache()).get("species").availability());
    }
    public static class CountedSpecies extends Species {
        int reads;
        @Override public Iterable<Element> getTypes() { reads++; return super.getTypes(); }
        @Override public Set<String> getLabels() { reads++; return super.getLabels(); }
    }
    public static class CachedPokemon extends Pokemon {
        final CountedSpecies species = new CountedSpecies();
        @Override public Species getSpecies() { return species; }
    }
    @Test void registryEnrichmentIsCachedBeforeInvokingGettersAgain() {
        var pokemon = new CachedPokemon(); var cache = new PokemonFactReader.RegistryCache();
        for (int i = 0; i < 20; i++) PokemonFactReader.readPokemon(pokemon, cache);
        assertEquals(2, pokemon.species.reads);
        cache.clear(); PokemonFactReader.readPokemon(pokemon, cache);
        assertEquals(4, pokemon.species.reads);
    }
    public record Element(String name) { public String getName() { return name; } }
    public static class Species {
        public String getResourceIdentifier() { return "cobblemon:ralts"; }
        public Iterable<Element> getTypes() { return List.of(new Element("psychic"), new Element("fairy")); }
        public Set<String> getLabels() { return Set.of("legendary"); }
    }
    public static class Pokemon {
        public Species getSpecies() { return new Species(); }
        public UUID getUuid() { return new UUID(0, 7); }
        public boolean getShiny() { return false; }
        public Set<String> getAspects() { return Set.of("radiant"); }
        public String getGender() { return "FEMALE"; }
        public Object getForm() { throw new IllegalStateException("partial ABI failure"); }
        public int getLevel() { return 1; } // Default is not the synchronized entity LABEL_LEVEL.
        public String getNature() { throw new AssertionError("Must never inspect private defaults"); }
        public Object getIvs() { throw new AssertionError("Must never inspect private defaults"); }
        public Object getEvs() { throw new AssertionError("Must never inspect private defaults"); }
        public Object getAbility() { throw new AssertionError("Must never inspect private defaults"); }
        public Object getMoveSet() { throw new AssertionError("Must never inspect private defaults"); }
        public int getHp() { throw new AssertionError("Must never inspect private defaults"); }
        public Object getHeldItem() { throw new AssertionError("Must never inspect private defaults"); }
    }

    @Test void brokenFormGetterPreservesOtherSynchronizedFields() {
        var facts = PokemonFactReader.readPokemon(new Pokemon());
        assertEquals("cobblemon:ralts", facts.get("species").value());
        assertEquals(false, facts.get("shiny").value());
        assertEquals("00000000-0000-0000-0000-000000000007", facts.get("pokemonUuid").value());
        assertEquals("female", facts.get("gender").value());
        assertEquals(Availability.READER_UNSUPPORTED, facts.get("form").availability());
    }

    @Test void missingOwnershipGetterNeverProvesWild() {
        var facts = PokemonFactReader.readPokemon(new Pokemon());
        assertNull(facts.get("wild").value());
        assertNull(facts.get("ownerUuid").value());
        assertFalse(ObservationSnapshotTest.snapshot(facts).scalarFacts().containsKey("wild"));
    }

    public static class StorageDefaults extends Pokemon {
        public UUID getOwnerUUID() { return null; }
        public boolean isWild() { return true; }
    }
    @Test void availableStorageOwnershipDefaultsAreNotEntityOwnershipEvidence() {
        var facts = PokemonFactReader.readPokemon(new StorageDefaults());
        assertNull(facts.get("wild").value());
        assertNull(facts.get("level").value());
    }

    @Test void privateDefaultsStayNotSentAndServerAspectsAreNotInterpreted() {
        var facts = PokemonFactReader.readPokemon(new Pokemon());
        for (String key : List.of("ivs", "evs", "nature", "ability", "moves", "heldItem", "hp")) {
            assertEquals(Availability.NOT_SENT, facts.get(key).availability(), key);
            assertNull(facts.get(key).value(), key);
        }
        assertEquals("radiant", facts.get("aspects").value());
        assertFalse(facts.containsKey("radiant"));
        assertEquals(Source.REGISTRY_REFERENCE, facts.get("referenceTypes").source());
        assertEquals("psychic,fairy", facts.get("referenceTypes").value());
        assertEquals(Source.REGISTRY_REFERENCE, facts.get("speciesClass").source());
        assertFalse(facts.containsKey("types"));
    }

    public static class MissingSpecies extends Pokemon {
        @Override public Species getSpecies() { throw new NoClassDefFoundError("optional class"); }
    }
    @Test void brokenSpeciesDoesNotDiscardAppearanceAndNullDoesNotCreateFalseFacts() {
        var facts = PokemonFactReader.readPokemon(new MissingSpecies());
        assertEquals(false, facts.get("shiny").value());
        assertNull(facts.get("species").value());
        assertTrue(ObservationSnapshotTest.snapshot(PokemonFactReader.readPokemon(null)).scalarFacts().isEmpty());
    }

    public static class OversizedAspects extends Pokemon {
        @Override public Set<String> getAspects() { return Set.of("x".repeat(257)); }
    }
    @Test void oversizeTokensRemainUnknownInsteadOfInventingTruncatedTokens() {
        var facts = PokemonFactReader.readPokemon(new OversizedAspects());
        assertEquals(Availability.INVALID, facts.get("aspects").availability());
        assertFalse(ObservationSnapshotTest.snapshot(facts).scalarFacts().containsKey("aspects"));
    }

    public static class MalformedAspects extends Pokemon {
        @Override public Set<String> getAspects() { return Set.of("one\ntwo\nthree"); }
    }
    @Test void multilineAspectCannotBecomeAMatchableToken() {
        var facts = PokemonFactReader.readPokemon(new MalformedAspects());
        assertEquals(Availability.INVALID, facts.get("aspects").availability());
        assertFalse(ObservationSnapshotTest.snapshot(facts).scalarFacts().containsKey("aspects"));
    }
}
