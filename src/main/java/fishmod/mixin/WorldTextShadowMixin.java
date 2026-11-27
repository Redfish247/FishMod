package fishmod.mixin;
//? if <26.2 {

import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NameTagFeatureRenderer.class)
public abstract class WorldTextShadowMixin {

    @ModifyArg(method = "renderTranslucent", require = 0, index = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"))
    private boolean fishmod$shadowComp(boolean shadow) {
        return shadow || FishSettings.worldTextShadow(true);
    }

    @ModifyArg(method = "renderTranslucent", require = 0, index = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"))
    private boolean fishmod$shadowSeq(boolean shadow) {
        return shadow || FishSettings.worldTextShadow(true);
    }
}
//?} else if <26.3 {

/*import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NameTagFeatureRenderer.class)
public abstract class WorldTextShadowMixin {

    @ModifyArg(method = "prepareText", require = 0, index = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"))
    private static boolean fishmod$shadowNameTag(boolean shadow) {
        return shadow || FishSettings.worldTextShadow(true);
    }
}
*///?} else {

/*import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SubmitNodeCollection.class)
public abstract class WorldTextShadowMixin {

    @ModifyArg(method = "nameTag", require = 0, index = 3, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Content$Text;<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V"))
    private static boolean fishmod$shadowNameTag(boolean shadow) {
        return shadow || FishSettings.worldTextShadow(true);
    }
}
*///?}
