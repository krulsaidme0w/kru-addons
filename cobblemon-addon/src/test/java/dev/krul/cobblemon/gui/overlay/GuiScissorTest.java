package dev.krul.cobblemon.gui.overlay;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuiScissorTest {
    @Test void translatedScaledBodyClipsAtRenderedLocationNotScreenOrigin() {
        var matrix = new Matrix4f().translate(200, 50, 400).scale(.5f, .5f, 1).translate(0, 22, 0);
        var rect = GuiScissor.bounds(matrix, 0, 0, 400, 200);
        assertArrayEquals(new int[]{200, 61, 400, 161}, rect);
    }
    @Test void nestedPortraitAndScrolledContentUseTheSameGuiCoordinates() {
        var matrix = new Matrix4f().translate(100, 20, 400).scale(1.5f, 1.5f, 1).translate(-10, -5, 0);
        assertArrayEquals(new int[]{115, 27, 145, 58}, GuiScissor.bounds(matrix, 20, 10, 40, 30));
    }
}
