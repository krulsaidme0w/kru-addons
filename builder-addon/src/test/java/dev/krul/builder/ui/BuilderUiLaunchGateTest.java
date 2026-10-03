package dev.krul.builder.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BuilderUiLaunchGateTest {
    @Test void waitsForThemeAndAnUnoccupiedScreen() {
        assertFalse(BuilderUiLaunchGate.shouldOpen(true, false, false));
        assertFalse(BuilderUiLaunchGate.shouldOpen(true, true, true));
        assertTrue(BuilderUiLaunchGate.shouldOpen(true, true, false));
        assertFalse(BuilderUiLaunchGate.shouldOpen(false, true, false));
    }
}
