package dev.krul.builder.ui;

import dev.krul.builder.archive.BuilderDiagnostic;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuilderUiPresentationTest {
    @Test void overlayToggleControlsRenderingWithoutChangingModuleState() {
        assertEquals("Overlay: ON", BuilderOverlayVisibility.buttonLabel(true));
        assertEquals("Overlay: OFF", BuilderOverlayVisibility.buttonLabel(false));
        assertEquals(true, BuilderOverlayVisibility.shouldRender(true, false));
        assertEquals(false, BuilderOverlayVisibility.shouldRender(false, false));
        assertEquals(false, BuilderOverlayVisibility.shouldRender(true, true));
    }

    @Test void primaryActionFollowsConnectionAndBusyPhase() {
        assertEquals(BuilderUiPresentation.PrimaryAction.CONNECT,
            BuilderUiPresentation.primaryAction(BuilderUiState.Phase.DISCONNECTED, false, false));
        assertEquals(BuilderUiPresentation.PrimaryAction.SEND,
            BuilderUiPresentation.primaryAction(BuilderUiState.Phase.READY, true, false));
        assertEquals(BuilderUiPresentation.PrimaryAction.CANCEL,
            BuilderUiPresentation.primaryAction(BuilderUiState.Phase.READY, true, true));
        assertEquals(BuilderUiPresentation.PrimaryAction.CANCEL,
            BuilderUiPresentation.primaryAction(BuilderUiState.Phase.WAITING, true, false));
    }

    @Test void failedStatusShowsActionableReasonAndRedactsCredentials() {
        assertEquals("Request failed · Codex turn did not complete", BuilderUiPresentation.statusSummary("Codex turn did not complete", true));
        assertEquals("Request failed · Authorization: Bearer [redacted]", BuilderUiPresentation.statusSummary("Authorization: Bearer abc123", true));
    }

    @Test void failedStatusIsBoundedForTheHud() {
        String longFailure = "x".repeat(300);
        assertEquals(180, BuilderUiPresentation.statusSummary(longFailure, true).length());
        assertTrue(BuilderUiPresentation.statusSummary(longFailure, true).endsWith("…"));
    }

    @Test void signInActionsAppearOnlyForTheAvailableServerCredentials() {
        assertEquals(new BuilderUiPresentation.SignInActions(false, false),
            BuilderUiPresentation.signInActions(null, null));
        assertEquals(new BuilderUiPresentation.SignInActions(true, false),
            BuilderUiPresentation.signInActions(URI.create("https://example.test/device"), null));
        assertEquals(new BuilderUiPresentation.SignInActions(false, true),
            BuilderUiPresentation.signInActions(null, "ABCD-1234"));
        assertEquals(new BuilderUiPresentation.SignInActions(true, true),
            BuilderUiPresentation.signInActions(URI.create("https://example.test/device"), "ABCD-1234"));
    }

    @Test void diagnosticTranscriptTextShowsCauseDetailAndNextActionWithoutTruncation() {
        String detail = "block validation coordinate=[4,7,2] " + "x".repeat(300);
        var diagnostic = new BuilderDiagnostic(BuilderDiagnostic.Category.INVALID_PROPOSAL, "Plan validation",
            "Target is outside the selected bounds", detail, "Reduce the coordinates to the current selection", 1, 1);

        String shown = BuilderUiPresentation.diagnosticText(diagnostic);
        assertTrue(shown.contains("Target is outside the selected bounds"));
        assertTrue(shown.contains(detail));
        assertTrue(shown.contains("Reduce the coordinates to the current selection"));
    }
}
