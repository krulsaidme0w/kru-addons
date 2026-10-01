package dev.krul.builder.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuilderFailureDedupeTest {
    @Test void reportsFailureOnceUntilRecoveryThenReportsSameFailureAgain() {
        var state = BuilderFailureDedupe.State.empty();

        var first = BuilderFailureDedupe.advance(state, true, "provider failed");
        assertEquals("provider failed", first.report());

        var repeated = BuilderFailureDedupe.advance(first.state(), true, "provider failed");
        assertNull(repeated.report());

        var recovered = BuilderFailureDedupe.advance(repeated.state(), false, "ready");
        assertNull(recovered.report());

        var failedAgain = BuilderFailureDedupe.advance(recovered.state(), true, "provider failed");
        assertEquals("provider failed", failedAgain.report());
    }

    @Test void reportsNewFailureBeforeAnObservedRecovery() {
        var first = BuilderFailureDedupe.advance(BuilderFailureDedupe.State.empty(), true, "first");
        var second = BuilderFailureDedupe.advance(first.state(), true, "second");

        assertEquals("second", second.report());
    }
}
