package dev.krul.builder.minecraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/** Renders the exact registry item's default stack into the block renderer's private framebuffer. */
public final class ItemPreviewRenderer {
    private final MinecraftClient client;
    private final BlockPreviewRenderer renderer;

    public ItemPreviewRenderer(MinecraftClient client) {
        this.client = Objects.requireNonNull(client);
        this.renderer = new BlockPreviewRenderer(client);
    }

    public Optional<BlockPreviewRenderer.PreviewPng> renderNow(String exactId) throws IOException {
        if (!client.isOnThread()) throw new IllegalStateException("Item preview must run on the Minecraft client thread");
        Identifier id = Identifier.tryParse(exactId);
        if (id == null || !Registries.ITEM.containsId(id)) throw new IllegalArgumentException("Unknown exact item ID");
        ItemStack stack = new ItemStack(Registries.ITEM.get(id));
        if (stack.isEmpty() || client.getItemRenderer().getModels().getModel(stack) == client.getBakedModelManager().getMissingModel())
            return Optional.empty();
        return Optional.of(renderer.renderStackNow(stack));
    }
}
