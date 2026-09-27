package dev.krul.cobblemon.battle;

import org.junit.jupiter.api.Test;

import static dev.krul.cobblemon.battle.PokemonType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeEffectivenessTest {
    @Test
    void containsEverySingleTypeInteraction() {
        assertEquals(18, PokemonType.values().length);
        assertEquals(324, TypeEffectiveness.cells().size());
        for (PokemonType attack : PokemonType.values()) {
            for (PokemonType defend : PokemonType.values()) {
                assertTrue(TypeEffectiveness.cells().containsKey(new TypeEffectiveness.Cell(attack, defend)));
            }
        }
    }

    @Test
    void canonicalStrengthResistanceAndImmunityAreNotFlattened() {
        assertEquals(2.0, TypeEffectiveness.multiplier(FIRE, GRASS));
        assertEquals(0.5, TypeEffectiveness.multiplier(FIRE, WATER));
        assertEquals(0.0, TypeEffectiveness.multiplier(NORMAL, GHOST));
        assertEquals(0.0, TypeEffectiveness.multiplier(ELECTRIC, GROUND));
        assertEquals(2.0, TypeEffectiveness.multiplier(FAIRY, DRAGON));
    }

    @Test
    void dualTypeDefenceMultipliesBothIndependentCells() {
        assertEquals(4.0, TypeEffectiveness.multiplier(ICE, DRAGON, FLYING));
        assertEquals(0.25, TypeEffectiveness.multiplier(FIRE, WATER, DRAGON));
        assertEquals(0.0, TypeEffectiveness.multiplier(PSYCHIC, DARK, POISON));
    }

    @Test
    void idsAreNormalizedWithoutInventingUnknownTypes() {
        assertEquals(FIRE, PokemonType.fromId("cobblemon:fire").orElseThrow());
        assertEquals(FIGHTING, PokemonType.fromId("FIGHTING").orElseThrow());
        assertTrue(PokemonType.fromId("cosmic").isEmpty());
    }
}

