package dev.krul.cobblemon.pcui.daycare;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.krul.cobblemon.pcui.daycare.DaycarePort.*;

class DaycareNativeRulesTest {
    final AcademyDaycareProfile profile = AcademyDaycareProfile.load();

    // Getter shape mirrors the pinned DialogueScreen ABI; exercise the native read-only decoder.
    public record DialogueFlags(Boolean getWaitingForServerUpdate, Boolean renderInput) {}

    @Test void nativeGradualTextAndServerWaitAreReadinessStatesNotDispatchFailures() {
        assertEquals(ConfirmationReadiness.WAITING, NativeDaycarePort.inputReadiness(new DialogueFlags(false, false)));
        assertEquals(ConfirmationReadiness.WAITING, NativeDaycarePort.inputReadiness(new DialogueFlags(true, true)));
        assertEquals(ConfirmationReadiness.READY, NativeDaycarePort.inputReadiness(new DialogueFlags(false, true)));
    }

    @Test void unavailableNativeReadinessDoesNotBecomeRetryPermission() {
        assertEquals(ConfirmationReadiness.UNAVAILABLE, NativeDaycarePort.inputReadiness(new Object()));
        assertEquals(ConfirmationReadiness.UNAVAILABLE, NativeDaycarePort.inputReadiness(new DialogueFlags(null, true)));
        assertEquals(ConfirmationReadiness.UNAVAILABLE, NativeDaycarePort.inputReadiness(new DialogueFlags(false, null)));
    }

    @Test void leftWithdrawalUsesKnownAcademyShiftRightClickContract() {
        assertEquals(net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, NativeDaycarePort.clickType(profile, false));
        assertEquals(net.minecraft.screen.slot.SlotActionType.PICKUP, NativeDaycarePort.clickType(profile, true));
    }

    @Test void academyTitleAndContainerShapeRecognizeOnlyCurrentPen() {
        assertEquals(3, profile.pen("Daycare ๓", 54, 90, false));
        assertEquals(-1, profile.pen("Daycare ๓", 27, 63, false));
        assertEquals(-1, profile.pen("Daycare ๗", 54, 90, false));
        assertEquals(-1, profile.pen("Daycare ๓", 54, 90, true));
        assertEquals(-1, profile.pen("Random chest", 54, 90, false));
    }

    @Test void unknownItemIsNotAnEmptyRole() {
        assertEquals(Role.OCCUPIED, profile.role("cobblemon:pokemon_model", false));
        assertEquals(Role.EMPTY, profile.role("minecraft:air", true));
        assertEquals(Role.UNKNOWN, profile.role("minecraft:stone", false));
        assertEquals(Role.UNKNOWN, profile.role("minecraft:paper", false));
    }

    @Test void reorderSelectsUniqueFirstPartyProjectionWithoutSlotGuessing() {
        var a = new Pokemon(new UUID(0, 1), "ralts", Set.of(), 12);
        var b = new Pokemon(new UUID(0, 2), "abra", Set.of(), 7);
        var choices = List.of(new DaycareNativeRules.Choice(b.projection(), 7, true),
            new DaycareNativeRules.Choice(a.projection(), 12, true));
        assertEquals(1, DaycareNativeRules.pickIndex(a, List.of(a, b), choices));
        var twin = new Pokemon(new UUID(0, 3), "ralts", Set.of(), 12);
        assertEquals(-1, DaycareNativeRules.pickIndex(a, List.of(a, twin), choices));
        assertEquals(-1, DaycareNativeRules.pickIndex(a, List.of(a, b), List.of(choices.get(1), choices.get(1))));
        assertEquals(-1, DaycareNativeRules.pickIndex(a, List.of(a, b), List.of(
            choices.get(0), new DaycareNativeRules.Choice(a.projection(), 12, false))));
    }

    @Test void sameClassUnrelatedConfirmationOrPickerIsNotAccepted() {
        assertTrue(profile.pickerTitle("Daycare"));
        assertFalse(profile.pickerTitle("Wonder Trade"));
        assertEquals(0, profile.confirmation("Remove this Pokémon from daycare?", false,
            List.of("Yes", "No")));
        assertEquals(1, profile.confirmation("Insert Pokémon into daycare?", true,
            List.of("Cancel", "Confirm")));
        assertEquals(-1, profile.confirmation("Release this Pokémon?", false, List.of("Yes", "No")));
        assertEquals(-1, profile.confirmation("Remove from daycare?", true, List.of("Yes", "No")));
        assertEquals(-1, profile.confirmation("Remove from daycare?", false, List.of("Okay")));
        assertEquals(-1, profile.confirmation("Remove from daycare?", false, List.of("Yes", "Confirm")));
    }
}
