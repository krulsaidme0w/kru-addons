package dev.krul.builder.minecraft;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
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
}
