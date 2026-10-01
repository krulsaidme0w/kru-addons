package dev.krul.builder.archive;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuilderDiagnosticMapperTest {
    @Test void classifiesPlanValidationAndProvidesConcreteRetryAction() {
        var diagnostic = BuilderDiagnosticMapper.fromAgentFailure(
            "Builder rejected the proposal: OUT_OF_BOUNDS at [12,4,2]", 1234);

        assertEquals(BuilderDiagnostic.Category.INVALID_PROPOSAL, diagnostic.category());
        assertEquals("[12,4,2]", diagnostic.detail().substring(diagnostic.detail().indexOf("[12,4,2]")));
        assertTrue(diagnostic.nextAction().contains("bounds"));
    }

    @Test void classifiesTransportFailureAndRedactsCredentials() {
        var diagnostic = BuilderDiagnosticMapper.fromAgentFailure(
            "Codex connection failed: authorization=Bearer abc.def refresh_token=secret-value", 1234);

        assertEquals(BuilderDiagnostic.Category.AUTH_TRANSPORT, diagnostic.category());
        assertFalse(diagnostic.detail().contains("abc.def"));
        assertFalse(diagnostic.detail().contains("secret-value"));
        assertTrue(diagnostic.detail().contains("[redacted]"));
    }

    @Test void classifiesOrdinaryAgentFailureAsModelOrToolFailure() {
        var diagnostic = BuilderDiagnosticMapper.fromAgentFailure("Local block catalog request failed", 1234);

        assertEquals(BuilderDiagnostic.Category.MODEL_TOOL, diagnostic.category());
        assertEquals(1, diagnostic.occurrences());
        assertEquals(1234, diagnostic.occurredAtEpochMillis());
    }

    @Test void boundsLargeProviderFailureBeforeBuildingTheDiagnostic() {
        var diagnostic = BuilderDiagnosticMapper.fromAgentFailure("x".repeat(9000), 1234);

        assertTrue(diagnostic.detail().length() <= 8192);
        assertTrue(diagnostic.cause().length() <= 2048);
    }
}
