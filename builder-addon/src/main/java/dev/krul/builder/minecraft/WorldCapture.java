package dev.krul.builder.minecraft;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;
import meteordevelopment.meteorclient.events.render.RenderAfterWorldEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Captures only the world framebuffer before HUD rendering; PNG conversion runs off-thread. */
public final class WorldCapture {
    private final MinecraftClient client;
    private final Path directory;
    private final Executor encoder;

    public WorldCapture(MinecraftClient client, Path directory, Executor encoder) {
        this.client = Objects.requireNonNull(client);
        this.directory = Objects.requireNonNull(directory);
        this.encoder = Objects.requireNonNull(encoder);
    }

    /** Requests the next RenderAfterWorldEvent. The returned path is addon-owned until released. */
    public CompletableFuture<Path> capture() {
        return CaptureLifecycle.request();
    }

    /** Keeps the addon preview out of the framebuffer for the pending capture frame. */
    public static boolean suppressPreviewThisFrame() { return CaptureLifecycle.suppressPreview(); }

    @EventHandler
    public void onAfterWorld(RenderAfterWorldEvent ignored) {
        CompletableFuture<Path> request;
        request = CaptureLifecycle.current(); if (request == null || request.isDone()) return;
        if (!client.isOnThread()) { CaptureLifecycle.failWrongThread(request); return; }
        if (client.world == null) { request.completeExceptionally(new IllegalStateException("No client world to capture")); finish(request); return; }
        try {
            int width = client.getWindow().getFramebufferWidth(), height = client.getWindow().getFramebufferHeight();
            if (width < 1 || height < 1 || (long) width * height > 16_777_216L) throw new IllegalStateException("Invalid framebuffer dimensions");
            ByteBuffer pixels = BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(width, height), 4));
            int previousAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            try {
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
                GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            } finally { GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousAlignment); }
            encoder.execute(() -> encode(request, width, height, pixels));
        } catch (Throwable failure) {
            request.completeExceptionally(failure); finish(request);
        }
    }

    private void encode(CompletableFuture<Path> request, int width, int height, ByteBuffer data) {
        Path capture = null;
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            int p = 0;
            for (int y = height - 1; y >= 0; y--) for (int x = 0; x < width; x++) {
                int red = data.get(p++) & 255, green = data.get(p++) & 255, blue = data.get(p++) & 255; p++;
                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
            Files.createDirectories(directory);
            capture = Files.createTempFile(directory, "mcp-view-", ".png");
            if (!javax.imageio.ImageIO.write(image, "PNG", capture.toFile())) throw new IOException("PNG encoder unavailable");
            CaptureLifecycle.completeOwned(request, capture, this::release);
        } catch (Throwable failure) { release(capture); request.completeExceptionally(failure); }
        finally { finish(request); }
    }

    private void finish(CompletableFuture<Path> request) { CaptureLifecycle.finish(request); }

    /** Delete the one-shot MCP capture after its response has been encoded. */
    public void release(Path capture) {
        if (capture == null || !capture.normalize().startsWith(directory.normalize())) return;
        try { Files.deleteIfExists(capture); } catch (IOException ignored) { }
    }
}
