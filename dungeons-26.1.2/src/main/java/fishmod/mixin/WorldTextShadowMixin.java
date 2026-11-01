package fishmod.mixin;

import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Text Shadow: drop shadow on nametags and floating world text
@Mixin({NameTagFeatureRenderer.class, TextFeatureRenderer.class})
public abstract class WorldTextShadowMixin {

    @ModifyArg(method = "renderTranslucent", require = 0, index = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"))
    private boolean fishmod$shadowComp(boolean shadow) {
        return shadow || FishSettings.worldTextShadow((Object) this instanceof NameTagFeatureRenderer);
    }

    @ModifyArg(method = "renderTranslucent", require = 0, index = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"))
    private boolean fishmod$shadowSeq(boolean shadow) {
        return shadow || FishSettings.worldTextShadow((Object) this instanceof NameTagFeatureRenderer);
    }
}
