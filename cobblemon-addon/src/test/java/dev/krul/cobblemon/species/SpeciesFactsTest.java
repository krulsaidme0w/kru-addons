package dev.krul.cobblemon.species;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeciesFactsTest {
    @Test
    void legendaryLabelClassifiesCobalionIndependentlyOfEncounterData() {
        SpeciesFacts facts = SpeciesFacts.from(
            "cobblemon:cobalion",
            638,
            Set.of("steel", "fighting"),
            Set.of("undiscovered"),
            Set.of("gen5", "legendary")
        );

        assertEquals(Set.of(SpeciesClass.LEGENDARY), facts.classes());
        assertTrue(facts.authoritative());
        assertEquals(638, facts.nationalDex());
    }

    @Test
    void preservesMultipleLabelsAndNormalizesOfficialClassNames() {
        SpeciesFacts facts = SpeciesFacts.from(
            "custom:beast",
            1,
            Set.of(),
            Set.of("undiscovered"),
            Set.of("Mythical", "Ultra Beast", "Gen 9")
        );

        assertEquals(Set.of("mythical", "ultra_beast", "gen_9"), facts.labels());
        assertEquals(Set.of(SpeciesClass.MYTHICAL, SpeciesClass.ULTRA_BEAST), facts.classes());
    }

    @Test
    void unavailableDefinitionStaysUnknown() {
        SpeciesFacts facts = SpeciesFacts.unknown("custom:missingno");

        assertFalse(facts.authoritative());
        assertTrue(facts.labels().isEmpty());
        assertTrue(facts.classes().isEmpty());
        assertEquals(-1, facts.nationalDex());
    }

    @Test
    void unorderedFactoryTypesUseCanonicalPokemonOrderForPrimaryType() {
        SpeciesFacts facts = SpeciesFacts.from(
            "cobblemon:pelipper",
            279,
            Set.of("flying", "water"),
            Set.of("water_1", "flying"),
            Set.of()
        );

        assertEquals("water", facts.primaryType());
    }
}

