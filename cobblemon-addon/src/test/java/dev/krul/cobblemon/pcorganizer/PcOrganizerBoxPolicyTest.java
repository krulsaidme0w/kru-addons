package dev.krul.cobblemon.pcorganizer;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PcOrganizerBoxPolicyTest {
    @Test void protectedBoxesAreExcludedFromBothSourceAndDestination() {
        var policy = new PcOrganizerBoxPolicy(6, Set.of(1), Map.of(PcOrganizerBoxRole.MAIN, 3));
        assertEquals(Set.of(1, 3), policy.protectedIndexes());
        assertTrue(policy.isProtected(1));
        assertTrue(policy.isProtected(3));
        assertFalse(policy.isProtected(2));
    }

    @Test void noImplicitProtectionIsAddedWhenUserHasNotSelectedAnyBox() {
        var policy = new PcOrganizerBoxPolicy(6, Set.of(), Map.of());
        assertTrue(policy.protectedIndexes().isEmpty());
    }

    @Test void invalidBoxIndexesAreRejectedBeforeRuntime() {
        assertThrows(IllegalArgumentException.class, () -> new PcOrganizerBoxPolicy(6, Set.of(6), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new PcOrganizerBoxPolicy(0, Set.of(), Map.of()));
    }

    @Test void settingsDefaultToPreview() {
        var settings = PcOrganizerSettings.defaults(6);
        assertEquals(PcOrganizerRunMode.PREVIEW, settings.runMode());
        assertEquals(Set.of(), settings.boxPolicy().protectedIndexes());
    }
}
