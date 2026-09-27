package dev.krul.cobblemon.pcorganizer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PcOrganizerLayoutPlannerTest {
    @Test void buildsRequestedFixedOrderAndKeepsOtherBoxesAfterIt() {
        var plan = PcOrganizerLayoutPlanner.plan(List.of("BREEDING", "Water", "MAIN", "TRADE", "UTILITY", "SHINIES", "SPECIAL", "Fire"));
        assertEquals(List.of(2, 6, 5, 4, 3, 0, 1, 7), plan.sourceOrder());
        assertEquals(List.of("MAIN", "SPECIAL", "SHINIES", "UTILITY", "TRADE", "BREEDING", "Water", "Fire"), plan.targetNames());
        assertTrue(plan.requiresReorder());
    }

    @Test void duplicateFixedBoxBlocksBeforeMutation() {
        assertThrows(IllegalArgumentException.class, () -> PcOrganizerLayoutPlanner.plan(
            List.of("MAIN", "SPECIAL", "SHINIES", "UTILITY", "TRADE", "TRADE")));
    }

    @Test void defaultBoxesNeedNoSpecialNamesOrMinimumCapacity() {
        var plan = PcOrganizerLayoutPlanner.plan(List.of("Box 1", "Box 2"));
        assertEquals(List.of(0, 1), plan.sourceOrder());
        assertFalse(plan.requiresReorder());
    }

    @Test void existingMainMovesFirstWithoutInventingMissingZones() {
        var plan = PcOrganizerLayoutPlanner.plan(List.of("Box 1", "Box 2", "MAIN", "Box 4"));
        assertEquals(List.of(2, 0, 1, 3), plan.sourceOrder());
        assertEquals(List.of("MAIN", "Box 1", "Box 2", "Box 4"), plan.targetNames());
    }

    @Test void breedingKeepsSixthDestinationEvenWhenOtherRolesAreMissing() {
        var plan = PcOrganizerLayoutPlanner.plan(List.of("BREEDING", "Box 2", "MAIN", "Box 4", "Box 5", "Box 6", "Box 7"));
        assertEquals(List.of(2, 1, 3, 4, 5, 0, 6), plan.sourceOrder());
    }

    @Test void alreadyOrderedLayoutIsPreviewComplete() {
        var plan = PcOrganizerLayoutPlanner.plan(List.of("MAIN", "SPECIAL", "SHINIES", "UTILITY", "TRADE", "BREEDING", "Fire"));
        assertFalse(plan.requiresReorder());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), plan.sourceOrder());
    }
}
