package dev.krul.builder.minecraft;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaptureLifecycleTest {
    @Test void wrongThreadFailureReleasesPreviewSuppressionAndAllowsRetry() {
        CompletableFuture<Path> failed = CaptureLifecycle.request();
        assertTrue(CaptureLifecycle.suppressPreview());

        CaptureLifecycle.failWrongThread(failed);

        assertTrue(failed.isCompletedExceptionally());
        assertFalse(CaptureLifecycle.suppressPreview());
        CompletableFuture<Path> retry = CaptureLifecycle.request();
        assertNotSame(failed, retry);
        CaptureLifecycle.finish(retry);
    }

    @Test void encodedCaptureIsReleasedWhenCancellationWinsCompletionRace() {
        CompletableFuture<Path> request = CaptureLifecycle.request();
        Path ownedCapture = Path.of("owned-capture.png");
        ArrayList<Path> released = new ArrayList<>();
        request.cancel(false);

        boolean completed = CaptureLifecycle.completeOwned(request, ownedCapture, released::add);

        assertFalse(completed);
        assertEquals(java.util.List.of(ownedCapture), released);
    }
}
