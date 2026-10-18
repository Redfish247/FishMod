package fishmod.mixin;

import fishmod.utils.config.values.Visual;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public class TextDisplayOnTopMixin {

    // Local 8 is the see-through flag; force it so hologram text displays aren't hidden by foliage/glass/water
    @ModifyVariable(
        method = "submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V",
        at = @At("STORE"),
        index = 8
    )
    private boolean fishmod$textDisplayOnTop(boolean seeThrough) {
        return Visual.nametagsOnTop || seeThrough;
    }
}
