package dev.krul.builder.minecraft;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class BlockPreviewRendererTest {
    @Test void acceptsOneSmallDecodablePngAndClonesItsBytes() throws Exception {
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        var out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out);
        byte[] bytes = out.toByteArray(); var preview = new BlockPreviewRenderer.PreviewPng(bytes, 32, 32); bytes[0] = 0;
        assertEquals(32, preview.width()); assertEquals((byte) 0x89, preview.bytes()[0]);
    }
    @Test void rejectsOversizedOrMismatchedPreview() throws Exception {
        var image = new BufferedImage(129, 129, BufferedImage.TYPE_INT_ARGB);
        var out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out);
        assertThrows(IllegalArgumentException.class, () -> new BlockPreviewRenderer.PreviewPng(out.toByteArray(), 129, 129));
        assertThrows(IllegalArgumentException.class, () -> new BlockPreviewRenderer.PreviewPng(new byte[32], 32, 32));
    }
}
