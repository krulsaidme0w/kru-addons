package dev.krul.builder.minecraft;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

/** Renders one exact block item into a private 128px framebuffer, never the world framebuffer. */
public final class BlockPreviewRenderer {
    public record PreviewPng(byte[] bytes, int width, int height) {
        public PreviewPng { bytes = Objects.requireNonNull(bytes).clone(); validatePng(bytes, width, height); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    private static final int SIZE = 128;
    private final MinecraftClient client;
    public BlockPreviewRenderer(MinecraftClient client) { this.client = Objects.requireNonNull(client); }

    public CompletableFuture<PreviewPng> render(String exactId) {
        var result = new CompletableFuture<PreviewPng>();
        client.execute(() -> {
            try { result.complete(renderOnClient(exactId)); }
            catch (Throwable failure) { result.completeExceptionally(new IllegalStateException("Block preview is unavailable", failure)); }
        });
        return result;
    }

    public PreviewPng renderNow(String exactId) throws IOException {
        if (!client.isOnThread()) throw new IllegalStateException("Block preview must run on the Minecraft client thread");
        return renderOnClient(exactId);
    }

    private PreviewPng renderOnClient(String exactId) throws IOException {
        Identifier id = Identifier.tryParse(exactId);
        if (id == null || !Registries.BLOCK.containsId(id)) throw new IllegalArgumentException("Unknown exact block ID");
        Block block = Registries.BLOCK.get(id);
        ItemStack stack = new ItemStack(block.asItem());
        if (stack.isEmpty()) throw new IllegalStateException("Block has no inventory item model");
        return renderStackOnClient(stack);
    }

    PreviewPng renderStackNow(ItemStack stack) throws IOException {
        if (!client.isOnThread()) throw new IllegalStateException("Item preview must run on the Minecraft client thread");
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Item stack is empty");
        return renderStackOnClient(stack);
    }

    private PreviewPng renderStackOnClient(ItemStack stack) throws IOException {
        RenderSystem.assertOnRenderThread();
        var model = client.getItemRenderer().getModels().getModel(stack);
        if (model == client.getBakedModelManager().getMissingModel()) throw new IllegalStateException("Block item model is missing");

        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter oldSorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        var framebuffer = new PreviewFramebuffer();
        boolean modelViewPushed = false;
        boolean framebufferBound = false;
        try {
            modelView.pushMatrix(); modelViewPushed = true;
            modelView.identity(); RenderSystem.applyModelViewMatrix();
            framebuffer.setClearColor(0f, 0f, 0f, 0f);
            framebuffer.beginWrite(true); framebufferBound = true; framebuffer.clear(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, SIZE, SIZE, 0, 1000, 3000), VertexSorter.BY_Z);
            try (var allocator = new BufferAllocator(1_048_576)) {
                var immediate = VertexConsumerProvider.immediate(allocator);
                var context = new DrawContext(client, immediate);
                context.getMatrices().push(); context.getMatrices().translate(32, 32, 0); context.getMatrices().scale(4f, 4f, 1f);
                context.drawItem(stack, 0, 0); context.getMatrices().pop(); context.draw();
            }
            try (NativeImage image = new NativeImage(SIZE, SIZE, true)) {
                image.loadFromTextureImage(framebuffer.getColorAttachment(), true);
                return new PreviewPng(image.getBytes(), SIZE, SIZE);
            }
        } finally {
            try {
                if (framebufferBound) framebuffer.endWrite();
            } finally {
                try { client.getFramebuffer().beginWrite(true); }
                finally {
                    try { if (modelViewPushed) modelView.popMatrix(); }
                    finally {
                        try { if (modelViewPushed) RenderSystem.applyModelViewMatrix(); }
                        finally {
                            try { RenderSystem.setProjectionMatrix(oldProjection, oldSorting); }
                            finally { framebuffer.delete(); }
                        }
                    }
                }
            }
        }
    }

    static void validatePng(byte[] bytes, int width, int height) {
        if (bytes.length < 24 || bytes.length > 128 * 1024 || width < 1 || height < 1 || width > SIZE || height > SIZE)
            throw new IllegalArgumentException("Preview PNG is outside the size limits");
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) throw new IllegalArgumentException("Preview is not a matching PNG");
        } catch (IOException e) { throw new IllegalArgumentException("Preview PNG cannot be decoded", e); }
    }

    private static final class PreviewFramebuffer extends SimpleFramebuffer {
        private PreviewFramebuffer() { super(SIZE, SIZE, true, MinecraftClient.IS_SYSTEM_MAC); }
    }
}
