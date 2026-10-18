package fishmod.mixin;

import fishmod.utils.config.values.Visual;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NameTagFeatureRenderer.class)
public class NameTagOnTopMixin {

    // Second drawInBatch is the depth-tested pass; draw it without depth so holograms stay readable behind foliage
    @ModifyArg(
        method = "renderTranslucent",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V", ordinal = 1),
        index = 7
    )
    private Font.DisplayMode fishmod$nametagOnTop(Font.DisplayMode mode) {
        return Visual.nametagsOnTop ? Font.DisplayMode.SEE_THROUGH : mode;
    }
}
