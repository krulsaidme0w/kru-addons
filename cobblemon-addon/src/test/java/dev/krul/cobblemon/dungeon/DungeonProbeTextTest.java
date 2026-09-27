package dev.krul.cobblemon.dungeon;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonProbeTextTest {
    @Test
    void scanReportsDeterministicCompletePassKindCounts() {
        DungeonObservationIndex.Summary summary = new DungeonObservationIndex.Summary(
            "minecraft:the_nether", 8, 0, 10,
            Map.of(DungeonObservation.Kind.CHUNK, 3,
                DungeonObservation.Kind.BLOCK_ENTITY, 2,
                DungeonObservation.Kind.ENTITY, 4,
                DungeonObservation.Kind.POKEMON, 2,
                DungeonObservation.Kind.CROSSHAIR, 1));
        String line = DungeonProbeText.scan(new DungeonWorldProbe.ProbeResult("minecraft:the_nether", 3, List.of(), true), summary);
        assertEquals("DUNGEON_SCAN dimension=minecraft%3Athe_nether loadedChunks=3 chunks=3 blockEntities=2 entities=4 pokemon=2 crosshair=1 complete=true pending=0", line);
    }
    @Test
    void percentCodecRoundTripsEveryLogDelimiterControlAndUnicodeCharacter() {
        String adversarial = "% whitespace\tnewline\ncomma,colon:brackets[]equals=backslash\\ кириллица 😀";
        String encoded = DungeonProbeText.encode(adversarial);
        assertEquals(adversarial, DungeonProbeText.decode(encoded));
        assertFalse(encoded.matches(".*[\\s,:\\[\\]=\\\\].*"));
        assertTrue(encoded.contains("%"));
        assertEquals("-", DungeonProbeText.decode(DungeonProbeText.encode("-")));
        assertEquals("", DungeonProbeText.decode(DungeonProbeText.encode("")));
    }

    @Test
    void observationLineUsesEncodedFieldsAndSortedFacts() {
        DungeonObservation observation = new DungeonObservation(DungeonObservation.Kind.ENTITY,
            "dim with space", "id,=\\", "registry:thing", "Clazz[1]", "name\n", null,
            Map.of("z:key", "value,[]", "a", "100%"), 1);
        String line = DungeonProbeText.observation(observation);
        assertTrue(line.startsWith("DUNGEON_OBSERVATION kind=entity"));
        assertTrue(line.contains("dimension=dim%20with%20space"));
        assertTrue(line.indexOf("a:100%25") < line.indexOf("z%3Akey:value%2C%5B%5D"));
        assertFalse(line.contains("name=name\n"));
    }
}

