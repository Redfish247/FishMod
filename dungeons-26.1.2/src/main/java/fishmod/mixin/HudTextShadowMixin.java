package fishmod.mixin;

import fishmod.utils.rendering.FishHudLayer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// "Shadow all text" option (see FishHudLayer.shadow)
@Mixin(GuiGraphicsExtractor.class)
public abstract class HudTextShadowMixin {

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private boolean fishmod$shadowComp(boolean shadow) {
        return FishHudLayer.shadow(shadow);
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private boolean fishmod$shadowStr(boolean shadow) {
        return FishHudLayer.shadow(shadow);
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private boolean fishmod$shadowSeq(boolean shadow) {
        return FishHudLayer.shadow(shadow);
    }
}
