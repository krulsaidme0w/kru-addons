package dev.krul.cobblemon.pcorganizer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcOrganizerPreparationTest {
    @Test void partialTypeNamesStillAllowOtherTypesInOrdinaryBoxes() {
        var fire = pokemon(0, 0, "fire", "fire");
        var water = pokemon(1, 0, "water", "water");
        var before = snapshot(List.of("WATER", "Box 2"), List.of(fire, water));
        var preview = PcOrganizerPreparation.prepare(before, settings(before), true, Set.of());
        assertEquals(water.uuid(), preview.result().pokemon().get(0).uuid());
        assertEquals(fire.uuid(), preview.result().pokemon().get(1).uuid());
    }

    @Test void currentBoxSortingDoesNotRequireMatchingTypeDestinations() {
        var before = snapshot(List.of("WATER"), List.of(pokemon(0, 0, "water", "water"), pokemon(0, 1, "fire", "fire")));
        var preview = PcOrganizerPreparation.prepare(before, settings(before), false, Set.of());
        assertEquals("fire", preview.result().pokemon().get(0).species());
        assertEquals("water", preview.result().pokemon().get(1).species());
    }
    @Test void defaultsSortByTypeWithoutNamedZones() {
        var water = pokemon(0, 0, "a", "water");
        var fire = pokemon(1, 0, "z", "fire");
        var before = snapshot(List.of("Box 1", "Box 2"), List.of(water, fire));
        var preview = PcOrganizerPreparation.prepare(before, settings(before), true, Set.of());
        assertTrue(preview.layout().moves().isEmpty());
        assertEquals(fire.uuid(), preview.result().pokemon().get(0).uuid());
        assertEquals(water.uuid(), preview.result().pokemon().get(1).uuid());
        assertEquals(before.boxNames(), preview.result().boxNames());
    }

    @Test void namedMainMovesTogetherBeforeSortingOrdinaryPokemon() {
        var water = pokemon(0, 0, "a", "water");
        var main = pokemon(1, 7, "protected", "water");
        var fire = pokemon(2, 0, "z", "fire");
        var before = snapshot(List.of("Box 1", "MAIN", "Box 3"), List.of(water, main, fire));
        var preview = PcOrganizerPreparation.prepare(before, settings(before), true, Set.of());
        assertEquals(List.of("MAIN", "Box 1", "Box 3"), preview.result().boxNames());
        assertEquals(main.uuid(), preview.result().pokemon().get(0).uuid());
        assertEquals(7, preview.result().pokemon().get(0).slot());
        assertEquals(fire.uuid(), preview.result().pokemon().get(1).uuid());
        assertEquals(water.uuid(), preview.result().pokemon().get(2).uuid());
        assertTrue(preview.sortSettings().boxPolicy().isProtected(0));
        assertFalse(preview.sortSettings().boxPolicy().isProtected(1));
    }

    @Test void explicitProtectedBoxCannotBeUsedForReordering() {
        var before = snapshot(List.of("Box 1", "MAIN"), List.of(pokemon(1, 0, "main", "fire")));
        assertThrows(IllegalArgumentException.class,
            () -> PcOrganizerPreparation.prepare(before, settings(before), true, Set.of(0)));
    }

    @Test void currentBoxModeDoesNotReorderWholePc() {
        var before = snapshot(List.of("Box 1", "MAIN"), List.of(pokemon(1, 0, "main", "fire")));
        var preview = PcOrganizerPreparation.prepare(before, settings(before), false, Set.of());
        assertEquals(before.boxNames(), preview.result().boxNames());
        assertTrue(preview.layout().moves().isEmpty());
    }

    static PcOrganizerSettings settings(PcOrganizerSnapshot s) {
        return new PcOrganizerSettings(PcOrganizerRunMode.APPLY, PcOrganizerFilter.empty(), PcOrganizerSortKey.TYPE,
            PcOrganizerSortKey.SPECIES, PcOrganizerSortKey.ORIGINAL_SLOT, PcOrganizerBoxPolicy.forSnapshot(s, Set.of(), null));
    }
    static PcOrganizerSnapshot snapshot(List<String> names, List<PcOrganizerPokemon> pokemon) {
        return new PcOrganizerSnapshot(UUID.randomUUID(), names.size(), names, pokemon);
    }
    static PcOrganizerPokemon pokemon(int box, int slot, String species, String type) {
        return new PcOrganizerPokemon(UUID.randomUUID(), box, slot, species, type, "", false, false, "", "", "", 1, 0, 0);
    }
}
