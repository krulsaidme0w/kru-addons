package dev.krul.cobblemon.pcorganizer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcOrganizerGroupsTest {
    @Test void overflowBoxesFormOneTabAndCycleThroughActualBoxes() {
        var names = List.of("MAIN", "SHINIES", "WATER 1/2", "SHINIES 2/2", "WATER 2/2", "SPARE 1");
        var snapshot = new PcOrganizerSnapshot(UUID.randomUUID(), names.size(), names,
            List.of(PcCollectionPreparationTest.pokemon(2, 0, false, "ordinary"), PcCollectionPreparationTest.pokemon(4, 0, false, "ordinary")));
        var groups = PcOrganizerGroups.from(snapshot);
        var water = groups.stream().filter(g -> g.label().equals("WATER")).findFirst().orElseThrow();
        assertEquals(List.of(2, 4), water.boxes());
        assertEquals(2, water.count());
        assertEquals(4, water.nextBox(2));
        assertEquals(2, water.nextBox(4));
        assertEquals(2, water.nextBox(0));
        assertEquals(List.of(1, 3), groups.stream().filter(g -> g.label().equals("SHINIES")).findFirst().orElseThrow().boxes());
    }
    @Test void tabHitTargetsRespectGuttersAndLastPartialRow() {
        assertEquals(0, PcOrganizerGroups.at(9, 239, 4));
        assertEquals(3, PcOrganizerGroups.at(9, 263, 4));
        assertEquals(-1, PcOrganizerGroups.at(112, 239, 4));
        assertEquals(-1, PcOrganizerGroups.at(120, 263, 4));
        assertEquals(-1, PcOrganizerGroups.at(9, 260, 4));
    }
}
