package dev.krul.cobblemon.gui.overlay;

import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** 1.21.1 DrawContext scissors take absolute GUI pixels, not local matrix coordinates. */
public final class GuiScissor {
    private GuiScissor() {}
    public static int[] bounds(Matrix4f matrix, double left, double top, double right, double bottom) {
        Vector3f a = matrix.transformPosition(new Vector3f((float) left, (float) top, 0));
        Vector3f b = matrix.transformPosition(new Vector3f((float) right, (float) bottom, 0));
        return new int[]{(int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
            (int) Math.ceil(Math.max(a.x, b.x)), (int) Math.ceil(Math.max(a.y, b.y))};
    }
    public static void enable(DrawContext context, double left, double top, double right, double bottom) {
        int[] bounds = bounds(context.getMatrices().peek().getPositionMatrix(), left, top, right, bottom);
        context.enableScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
    }
}
