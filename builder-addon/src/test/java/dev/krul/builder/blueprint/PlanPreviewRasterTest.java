package dev.krul.builder.blueprint;

import dev.krul.builder.model.*;
import dev.krul.builder.project.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanPreviewRasterTest {
    @Test void emitsACompactSchematicPngWithBoundedDimensionsAndExactPaletteKeys() throws Exception {
        var bounds = new Bounds(new Cell(0, 0, 0), new Cell(2, 3, 2));
        var plan = new BuildPlan(UUID.randomUUID(), 1, 1, List.of(new BuildStage("base", "Base", Set.of(), Map.of(
            new Cell(0, 0, 0), state("minecraft:oak_planks"), new Cell(1, 1, 1), state("minecraft:glass")))));
        var projection = PlanPreviewModel.project(plan, bounds);
        var models = Map.of("minecraft:oak_planks", icon(new Color(145, 92, 46)),
            "minecraft:glass", icon(new Color(80, 180, 220, 150)));
        byte[] png = PlanPreviewRaster.render(projection, models);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(PlanPreviewRaster.WIDTH, image.getWidth());
        assertEquals(PlanPreviewRaster.HEIGHT, image.getHeight());
        assertTrue(png.length <= PlanPreviewRaster.MAX_BYTES);
        assertEquals(new Color(18, 23, 29).getRGB(), image.getRGB(2, 2));
    }

    @Test void missingExactBlockAppearanceNeverSilentlyFallsBackToAnotherModel() {
        var bounds = new Bounds(new Cell(0, 0, 0), new Cell(0, 0, 0));
        var plan = new BuildPlan(UUID.randomUUID(), 1, 1, List.of(new BuildStage("base", "Base", Set.of(),
            Map.of(new Cell(0, 0, 0), state("example:unknown")))));
        assertThrows(java.io.IOException.class, () -> PlanPreviewRaster.render(PlanPreviewModel.project(plan, bounds), Map.of()));
    }

    private static StateKey state(String id) { return new StateKey(id, Map.of()); }
    private static BufferedImage icon(Color color) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try { graphics.setColor(color); graphics.fillRect(2, 2, 12, 12); } finally { graphics.dispose(); }
        return image;
    }
}
