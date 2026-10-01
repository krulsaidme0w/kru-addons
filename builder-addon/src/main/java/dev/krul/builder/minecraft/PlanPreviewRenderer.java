package dev.krul.builder.minecraft;

import dev.krul.builder.blueprint.PlanPreviewModel;
import dev.krul.builder.blueprint.PlanPreviewRaster;
import net.minecraft.client.MinecraftClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;

/** Produces a bounded local schematic with actual inventory-model sprites; never reads or changes world state. */
public final class PlanPreviewRenderer {
    private final MinecraftClient client;
    private final BlockPreviewRenderer blocks;
    public PlanPreviewRenderer(MinecraftClient client) {
        this.client = Objects.requireNonNull(client); blocks = new BlockPreviewRenderer(client);
    }

    public byte[] renderNow(PlanPreviewModel.Projection projection, BooleanSupplier stillCurrent) throws IOException {
        if (!client.isOnThread()) throw new IllegalStateException("Plan preview must run on the Minecraft client thread");
        if (projection.cells().isEmpty()) throw new IOException("Plan has no cells to preview");
        long distinct = projection.cells().stream().map(cell -> cell.state().id()).distinct().limit(33).count();
        if (distinct > 32) throw new IOException("Preview has more than 32 distinct block models");
        var models = new LinkedHashMap<String, BufferedImage>();
        for (var cell : projection.cells()) {
            if (!stillCurrent.getAsBoolean()) throw new IOException("Plan preview request was cancelled");
            String id = cell.state().id();
            if (models.containsKey(id)) continue;
            if (id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air")) {
                models.put(id, new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));
                continue;
            }
            try {
                var preview = blocks.renderNow(id);
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(preview.bytes()));
                if (image == null) throw new IOException("Block inventory model could not be decoded: " + id);
                models.put(id, image);
            } catch (Exception failure) {
                if (failure instanceof IOException io) throw io;
                throw new IOException("Block inventory model is unavailable: " + id, failure);
            }
        }
        if (!stillCurrent.getAsBoolean()) throw new IOException("Plan preview request was cancelled");
        return PlanPreviewRaster.render(projection, models);
    }
}
