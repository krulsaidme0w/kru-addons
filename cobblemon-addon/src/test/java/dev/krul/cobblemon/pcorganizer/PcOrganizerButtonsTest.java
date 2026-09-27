package dev.krul.cobblemon.pcorganizer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcOrganizerButtonsTest {
    @Test void visibleButtonsHaveDistinctActions() {
        assertEquals(PcOrganizerButtons.PREVIEW, PcOrganizerButtons.at(50, 130));
        assertEquals(PcOrganizerButtons.APPLY, PcOrganizerButtons.at(200, 130));
        assertEquals(PcOrganizerButtons.STOP, PcOrganizerButtons.at(50, 155));
        assertEquals(PcOrganizerButtons.LOCK, PcOrganizerButtons.at(200, 155));
        assertEquals(PcOrganizerButtons.SCOPE, PcOrganizerButtons.at(50, 185));
        assertEquals(PcOrganizerButtons.SORT, PcOrganizerButtons.at(200, 185));
    }
    @Test void guttersAndOutsideNeverTriggerOperations() {
        assertNull(PcOrganizerButtons.at(165, 130));
        assertNull(PcOrganizerButtons.at(50, 144));
        assertNull(PcOrganizerButtons.at(322, 130));
        assertNull(PcOrganizerButtons.at(50, 197));
    }
}
