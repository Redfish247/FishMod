package fishmod.mixin;

import fishmod.features.fishing.FishingWorldSettings;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Full water vision at once, so underwater fog clears instantly
@Mixin(LocalPlayer.class)
public class WaterVisionMixin {

    @Inject(method = "getWaterVision", at = @At("HEAD"), cancellable = true)
    private void fishmod$instantWaterVision(CallbackInfoReturnable<Float> cir) {
        if (FishingWorldSettings.instantWaterFog) cir.setReturnValue(1.0f);
    }
}
