package dev.krul.cobblemon.pcui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.pcui.UiToolsLayout.Action.*;

class UiToolsLayoutTest {
    @Test void essentialAndAdvancedButtonsHaveSeparateHitTargets() {
        var controls = UiToolsLayout.controls(true, true);
        assertEquals(SAVE, controls.stream().filter(c -> c.contains(40, 70)).findFirst().orElseThrow().action());
        assertEquals(LOAD, controls.stream().filter(c -> c.contains(200, 70)).findFirst().orElseThrow().action());
        assertEquals(FABRICATE, controls.stream().filter(c -> c.contains(40, 225)).findFirst().orElseThrow().action());
        for (var a : controls) {
            assertEquals(1, controls.stream().filter(b -> b.contains(a.x() + 1, a.y() + 1)).count());
            assertTrue(a.y() + 20 < UiToolsLayout.height(true, true) - 17);
        }
        assertTrue(controls.stream().noneMatch(c -> c.contains(154, 70)));
    }
    @Test void collapsedPanelDoesNotExposeHiddenAdvancedActionsOrPcControls() {
        var actions = UiToolsLayout.controls(false, false).stream().map(UiToolsLayout.Control::action).toList();
        assertTrue(actions.containsAll(java.util.List.of(SAVE, LOAD, PACKETS, DELAY, MORE)));
        assertFalse(actions.contains(FABRICATE)); assertFalse(actions.contains(PC_IN));
    }
}
