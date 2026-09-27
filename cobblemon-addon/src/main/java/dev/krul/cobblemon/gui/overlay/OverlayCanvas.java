package dev.krul.cobblemon.gui.overlay;

import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.DrawContext;
import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.utils.Utils;
import java.util.ArrayList;
import java.util.List;

/** Meteor's font and theme, rendered in a composable local GUI coordinate system. */
public final class OverlayCanvas {
    private final DrawContext context;
    private final int text, muted, accent, background, border;
    private record Label(String text, int x, int y, int width, int color) {}
    private final List<Label> labels = new ArrayList<>();
    public OverlayCanvas(DrawContext context) {
        this.context = context;
        var theme = GuiThemes.get();
        if (theme instanceof MeteorGuiTheme meteor) {
            text = meteor.textColor.get().getPacked();
            muted = meteor.textSecondaryColor.get().getPacked();
            accent = meteor.accentColor.get().getPacked();
            background = meteor.backgroundColor.get(false, false).getPacked() | 0xE0000000;
            border = meteor.outlineColor.get(false, false).getPacked();
        } else {
            text = 0xFFF0F0F0; muted = 0xFFAAAAAA; accent = 0xFF913DE2;
            background = 0xED141414; border = 0xFF383838;
        }
    }
    public DrawContext context() { return context; }
    public int text() { return text; }
    public int muted() { return muted; }
    public int accent() { return accent; }
    public int background() { return background; }
    public int border() { return border; }
    public static int textWidth(String value) {
        var renderer = TextRenderer.get();
        return (int) Math.ceil(renderer.getWidth(value) * 9 / renderer.getHeight());
    }
    public void fill(int x, int y, int width, int height, int color) {
        if (width > 0 && height > 0) context.fill(x, y, x + width, y + height, color);
    }
    public void outline(int x, int y, int width, int height, int color) {
        fill(x, y, width, 1, color); fill(x, y + height - 1, width, 1, color);
        fill(x, y, 1, height, color); fill(x + width - 1, y, 1, height, color);
    }
    public void flush() {
        context.draw();
        if (labels.isEmpty()) return;
        var renderer = TextRenderer.get();
        // Renderer size differs between Meteor custom and Minecraft fonts; normalize to 9 GUI px.
        double scale = 9 / renderer.getHeight();
        boolean previous3D = Utils.rendering3D;
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(context.getMatrices().peek().getPositionMatrix());
        RenderSystem.applyModelViewMatrix();
        // Meteor's 3D mesh path subtracts camera coordinates. This is an ordinary GUI layer.
        Utils.rendering3D = false;
        boolean began = false;
        try {
            renderer.begin(scale, false, false);
            began = true;
            for (var label : labels) {
                String result = label.text;
                if (label.width > 0 && renderer.getWidth(result) > label.width) {
                    int end = result.length();
                    while (end > 0 && renderer.getWidth(result.substring(0, end) + "...") > label.width)
                        end = result.offsetByCodePoints(end, -1);
                    result = end == 0 ? "" : result.substring(0, end) + "...";
                }
                renderer.render(result, label.x, label.y, new Color(label.color));
            }
        } finally {
            labels.clear();
            try { if (began) renderer.end(); }
            finally {
                Utils.rendering3D = previous3D;
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        }
    }
    public void text(String value, int x, int y, int color) {
        if (value == null || value.isEmpty()) return;
        labels.add(new Label(value, x, y, -1, color));
    }
    public void textFit(String value, int x, int y, int maxWidth, int color) {
        if (value == null || maxWidth <= 0) return;
        labels.add(new Label(value, x, y, maxWidth, color));
    }
    public void button(String label, int x, int y, int width, int height, boolean hovered) {
        fill(x, y, width, height, hovered ? 0xFF414141 : 0xFF242424);
        outline(x, y, width, height, hovered ? accent : border);
        textFit(label, x + 4, y + (height - 9) / 2, width - 8, text);
    }
    public void rightArrow(int x, int y, int length, int color) {
        fill(x, y + 3, length, 1, color);
        for (int i = 0; i < 4; i++) fill(x + length - 1 - i, y + 3 - i, 1, 1 + 2 * i, color);
    }
}
