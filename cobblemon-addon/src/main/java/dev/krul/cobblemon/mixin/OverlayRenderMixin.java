package dev.krul.cobblemon.mixin;

import dev.krul.cobblemon.gui.overlay.OverlayHost;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After native GUI draw, before vanilla restores the GUI model-view matrix. */
@Mixin(GameRenderer.class)
public abstract class OverlayRenderMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;draw()V", shift = At.Shift.AFTER), require = 1)
    private void kru$overlays(RenderTickCounter counter, boolean tick, CallbackInfo info) { OverlayHost.render(); }
}
