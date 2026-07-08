package fishmod.mixin;

import fishmod.features.BossBarFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public class FishBossBarHudMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void fishDrawBossHp(GuiGraphicsExtractor context, CallbackInfo ci) {
        BossBarFeature.renderAfterVanilla(context);
    }
}
