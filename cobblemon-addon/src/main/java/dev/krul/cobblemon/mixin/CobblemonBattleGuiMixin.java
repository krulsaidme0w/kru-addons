package dev.krul.cobblemon.mixin;

import dev.krul.cobblemon.battle.matchup.BattleMatchupPanel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Installed 1.21.1 fork descriptors are checked by MatchupMixinContractTest. */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.client.gui.battle.BattleGUI", remap = false)
public abstract class CobblemonBattleGuiMixin extends Screen implements dev.krul.cobblemon.battle.matchup.BattleOverlaySupport.Hook {
    @Unique private boolean kru$hookObserved;
    @Override public boolean kru$renderHookObserved() { return kru$hookObserved; }
    @Unique private final BattleMatchupPanel meteor$matchups = new BattleMatchupPanel();

    protected CobblemonBattleGuiMixin(Text title) { super(title); }

    @Inject(method = "method_25394", at = @At("HEAD"), require = 0)
    private void meteor$sizeMatchups(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        kru$hookObserved = true;
        meteor$matchups.beforeRender(this);
    }

    @Override
    public void removed() {
        meteor$matchups.close();
        super.removed();
    }

    @Inject(method = "method_25394", at = @At("TAIL"), require = 0)
    private void meteor$renderMatchups(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        meteor$matchups.render(this, context, mouseX, mouseY);
    }

    @Inject(method = "method_25402", at = @At("HEAD"), cancellable = true, require = 0)
    private void meteor$clickMatchups(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> info) {
        if (meteor$matchups.click(mouseX, mouseY, button)) info.setReturnValue(true);
    }

}
