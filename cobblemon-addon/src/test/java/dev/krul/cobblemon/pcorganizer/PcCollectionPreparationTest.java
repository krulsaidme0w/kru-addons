package dev.krul.cobblemon.pcorganizer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcCollectionPreparationTest {
    @Test void previewCreatesNamedDestinationsAndLeavesNamesUntilAfterSorting() {
        var shiny = pokemon(0, 0, true, "legendary");
        var special = pokemon(0, 1, false, "paradox");
        var ordinary = pokemon(0, 2, false, "ordinary");
        var before = new PcOrganizerSnapshot(UUID.randomUUID(), 8,
            List.of("Box 1", "Box 2", "Box 3", "Box 4", "Box 5", "Box 6", "Box 7", "Box 8"),
            List.of(shiny, special, ordinary));
        var preview = PcOrganizerPreparation.prepareCollection(before, Set.of());
        assertEquals(List.of("MAIN", "SPECIAL", "SHINIES", "UTILITY", "TRADE", "BREEDING"), preview.result().boxNames().subList(0, 6));
        assertEquals(2, at(preview.result(), shiny.uuid()).box());
        assertEquals(1, at(preview.result(), special.uuid()).box());
        assertEquals(6, at(preview.result(), ordinary.uuid()).box());
        assertEquals(before.boxNames(), preview.afterSort().boxNames());
        assertEquals(preview.result().pokemon(), preview.afterSort().pokemon());
        assertFalse(preview.sortSettings().boxPolicy().isProtected(1));
        assertFalse(preview.sortSettings().boxPolicy().isProtected(2));
        assertFalse(preview.renames().isEmpty());
        assertEquals(before, preview.before());
    }
    @Test void namedMainMovesAsOneBoxThenIsProtectedFromAutoRouting() {
        var main = pokemon(6, 9, true, "legendary");
        var before = new PcOrganizerSnapshot(UUID.randomUUID(), 8,
            List.of("Box 1", "SPECIAL", "SHINIES", "UTILITY", "TRADE", "BREEDING", "MAIN", "Box 8"), List.of(main));
        var preview = PcOrganizerPreparation.prepareCollection(before, Set.of());
        assertEquals(0, at(preview.result(), main.uuid()).box());
        assertEquals(9, at(preview.result(), main.uuid()).slot());
        assertTrue(preview.sortSettings().boxPolicy().isProtected(0));
        assertTrue(preview.sort().moves().isEmpty());
    }
    private static PcOrganizerPokemon at(PcOrganizerSnapshot s, UUID id) { return s.pokemon().stream().filter(p -> p.uuid().equals(id)).findFirst().orElseThrow(); }
    static PcOrganizerPokemon pokemon(int box, int slot, boolean shiny, String kind) {
        return new PcOrganizerPokemon(UUID.randomUUID(), box, slot, "cobblemon:test", "water/dragon", "", shiny, false, kind, "", "", 1, 100, 1);
    }
}
