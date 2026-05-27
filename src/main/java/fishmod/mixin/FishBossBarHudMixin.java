package fishmod.mixin;

import fishmod.features.BossBarFeature;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public class FishBossBarHudMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void fishDrawBossHp(DrawContext context, CallbackInfo ci) {
        BossBarFeature.renderAfterVanilla(context);
    }
}
