package dev.krul.cobblemon.gui.overlay;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Only created from the world (no underlying screen), to let vanilla own cursor capture. */
final class OverlayInteractionScreen extends Screen {
    OverlayInteractionScreen() { super(Text.literal("Cobblemon overlays")); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override public boolean shouldPause() { return false; }
}
