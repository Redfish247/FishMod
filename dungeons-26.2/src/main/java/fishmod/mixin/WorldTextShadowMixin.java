package fishmod.mixin;

import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Text Shadow: drop shadow on nametags and floating world text (26.2 builds text via prepareText)
@Mixin({NameTagFeatureRenderer.class, TextFeatureRenderer.class})
public abstract class WorldTextShadowMixin {

    @ModifyArg(method = "prepareText", require = 0, index = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"))
    private static boolean fishmod$shadowNameTag(boolean shadow) {
        return shadow || FishSettings.worldTextShadow(true);
    }

    @ModifyArg(method = "buildGroup", require = 0, index = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"))
    private static boolean fishmod$shadowText(boolean shadow) {
        return shadow || FishSettings.worldTextShadow(false);
    }
}
