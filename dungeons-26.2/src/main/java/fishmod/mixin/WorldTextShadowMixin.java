package fishmod.mixin;

import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Text Shadow: drop shadow on nametags (world text is in SubmitTextShadowMixin, which skips signs)
@Mixin(NameTagFeatureRenderer.class)
public abstract class WorldTextShadowMixin {

    @ModifyArg(method = "prepareText", require = 0, index = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"))
    private static boolean fishmod$shadowNameTag(boolean shadow) {
        return shadow || FishSettings.worldTextShadow(true);
    }
}
