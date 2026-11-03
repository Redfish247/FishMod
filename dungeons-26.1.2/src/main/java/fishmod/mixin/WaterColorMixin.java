package fishmod.mixin;

import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColors.class)
public class WaterColorMixin {

    @Inject(method = "getAverageWaterColor", at = @At("HEAD"), cancellable = true)
    private static void fishmod$waterColor(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (FishSettings.customWaterEnabled) cir.setReturnValue(FishSettings.customWaterColor & 0xFFFFFF);
    }
}
