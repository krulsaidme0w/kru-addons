package dev.krul.cobblemon.pcorganizer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PcOrganizerPlannerTest {
    private static final UUID STORE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void activeFiltersAreCombinedAndOnlyMatchingPokemonAreReordered() {
        var snapshot = snapshot(
            pokemon(0, 0, "b", "water", 10), pokemon(0, 1, "c", "fire", 20), pokemon(0, 2, "a", "fire", 30));
        var settings = new PcOrganizerSettings(PcOrganizerRunMode.PREVIEW,
            new PcOrganizerFilter("", "fire", "", false, false, "", "", "", ""),
            PcOrganizerSortKey.SPECIES, PcOrganizerSortKey.ORIGINAL_SLOT, PcOrganizerSortKey.ORIGINAL_SLOT,
            new PcOrganizerBoxPolicy(1, Set.of(), Map.of()));
        var plan = PcOrganizerPlanner.plan(snapshot, settings);
        assertEquals(List.of("a", "c"), plan.moves().stream().map(PcOrganizerPlan.Move::pokemon).map(PcOrganizerPokemon::species).toList());
        assertEquals("a", plan.moves().getFirst().pokemon().species());
        assertEquals(2, plan.moves().getFirst().fromSlot());
        assertEquals(1, plan.moves().getFirst().toSlot());
    }

    @Test void protectedBoxPokemonNeverParticipateOrBecomeDestination() {
        var snapshot = snapshot(pokemon(0, 0, "z", "fire", 10), pokemon(1, 0, "a", "fire", 20));
        var settings = new PcOrganizerSettings(PcOrganizerRunMode.APPLY, PcOrganizerFilter.empty(),
            PcOrganizerSortKey.SPECIES, PcOrganizerSortKey.ORIGINAL_SLOT, PcOrganizerSortKey.ORIGINAL_SLOT,
            new PcOrganizerBoxPolicy(2, Set.of(1), Map.of()));
        var plan = PcOrganizerPlanner.plan(snapshot, settings);
        assertTrue(plan.moves().isEmpty());
        assertEquals(1, plan.protectedCount());
    }

    @Test void equalSortValuesUseOriginalPositionAsStableTieBreak() {
        var snapshot = snapshot(pokemon(0, 0, "same", "fire", 10), pokemon(0, 1, "same", "fire", 10));
        var settings = PcOrganizerSettings.defaults(1);
        assertTrue(PcOrganizerPlanner.plan(snapshot, settings).moves().isEmpty());
    }

    private static PcOrganizerSnapshot snapshot(PcOrganizerPokemon... values) {
        return new PcOrganizerSnapshot(STORE, 2, List.of("Box 1", "Main"), List.of(values));
    }
    private static PcOrganizerPokemon pokemon(int box, int slot, String species, String type, int ivTotal) {
        return new PcOrganizerPokemon(UUID.randomUUID(), box, slot, species, type, "", false, false,
            "", "", "", 1, ivTotal, 0);
    }
}
