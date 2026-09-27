package dev.krul.cobblemon.mixin;

import dev.krul.cobblemon.gui.overlay.OverlayHost;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Keyboard.class, priority = 1100)
public abstract class OverlayKeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true, require = 1)
    private void kru$key(long window, int key, int scancode, int action, int mods, CallbackInfo info) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle() && OverlayHost.key(key, action, mods)) info.cancel();
    }
    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true, require = 1)
    private void kru$char(long window, int codePoint, int mods, CallbackInfo info) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle() && OverlayHost.charTyped(codePoint, mods)) info.cancel();
    }
}
