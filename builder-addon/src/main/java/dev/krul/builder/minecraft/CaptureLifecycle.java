package dev.krul.builder.minecraft;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Small lifecycle boundary kept independent of Minecraft for capture error handling. */
final class CaptureLifecycle {
    private static CompletableFuture<Path> pending;
    private static volatile boolean suppressPreview;

    private CaptureLifecycle() {}

    static synchronized CompletableFuture<Path> request() {
        if (pending != null && pending.isDone()) finish(pending);
        if (pending != null) throw new IllegalStateException("A world capture is already pending");
        pending = new CompletableFuture<>();
        suppressPreview = true;
        return pending;
    }

    static synchronized CompletableFuture<Path> current() {
        if (pending != null && pending.isDone()) finish(pending);
        return pending;
    }
    static boolean suppressPreview() { return suppressPreview; }

    static boolean completeOwned(CompletableFuture<Path> request, Path path, Consumer<Path> release) {
        if (request.complete(path)) return true;
        release.accept(path);
        return false;
    }

    static void failWrongThread(CompletableFuture<Path> request) {
        request.completeExceptionally(new IllegalStateException("Capture event was not on client thread"));
        finish(request);
    }

    static synchronized void finish(CompletableFuture<Path> request) {
        if (pending == request) { pending = null; suppressPreview = false; }
    }
}
