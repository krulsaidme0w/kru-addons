package dev.krul.cobblemon.mixin;

import dev.krul.cobblemon.gui.overlay.OverlayHost;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Mouse.class, priority = 1100)
public abstract class OverlayMouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true, require = 1)
    private void kru$button(long window, int button, int action, int mods, CallbackInfo info) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle() && OverlayHost.mouseButton(button, action, mods)) info.cancel();
    }
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true, require = 1)
    private void kru$scroll(long window, double horizontal, double vertical, CallbackInfo info) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle() && OverlayHost.scroll(vertical)) info.cancel();
    }
}
