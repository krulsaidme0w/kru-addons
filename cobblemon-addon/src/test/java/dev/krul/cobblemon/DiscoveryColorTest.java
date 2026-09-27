package dev.krul.cobblemon;

import dev.krul.cobblemon.discovery.PokemonObservation;
import dev.krul.cobblemon.discovery.SpawnRarity;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryColorTest {
    @Test
    void shinyAndRadiantShareOneHighVisibilityColor() throws Exception {
        Color shiny = observationColor(observation(true, false));
        Color radiant = observationColor(observation(false, true));

        assertEquals(255, shiny.r);
        assertEquals(215, shiny.g);
        assertEquals(0, shiny.b);
        assertEquals(shiny.r, radiant.r);
        assertEquals(shiny.g, radiant.g);
        assertEquals(shiny.b, radiant.b);
        assertEquals(shiny.a, radiant.a);
    }

    private static Color observationColor(PokemonObservation observation) throws Exception {
        Method method = Discovery.class.getDeclaredMethod("observationColor", PokemonObservation.class);
        method.setAccessible(true);
        return (Color) method.invoke(null, observation);
    }

    private static PokemonObservation observation(boolean shiny, boolean radiant) {
        return PokemonObservation.builder(1, "cobblemon:test")
            .shiny(shiny)
            .radiant(radiant)
            .spawnRarity(SpawnRarity.COMMON)
            .seenAt(1)
            .build();
    }
}

