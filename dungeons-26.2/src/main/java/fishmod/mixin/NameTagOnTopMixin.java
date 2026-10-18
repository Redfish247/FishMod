package fishmod.mixin;

import fishmod.utils.config.values.Visual;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(NameTagFeatureRenderer.class)
public class NameTagOnTopMixin {

    // Depth-tested NORMAL pass drawn without depth so holograms stay readable behind foliage/glass/water
    @ModifyArg(
        method = "buildGroup",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$GlyphRenderer;prepare(Lnet/minecraft/client/renderer/feature/NameTagFeatureRenderer$Submit;Lnet/minecraft/client/gui/Font$DisplayMode;)V"),
        index = 1
    )
    private Font.DisplayMode fishmod$nametagOnTop(Font.DisplayMode mode) {
        return Visual.nametagsOnTop && mode == Font.DisplayMode.NORMAL ? Font.DisplayMode.SEE_THROUGH : mode;
    }
}
