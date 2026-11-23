package fishmod.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import fishmod.utils.rendering.UiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommandEncoder.class)
public class GlCommandEncoderUiTargetMixin {

    @Inject(
        method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPass;",
        at = @At("HEAD")
    )
    private void fishmod$recordPassTarget(RenderPassDescriptor descriptor, CallbackInfoReturnable<?> cir) {
        var atts = descriptor.colorAttachments;
        if (atts == null || atts.isEmpty() || atts.get(0) == null) return;
        var view = atts.get(0).textureView();
        if (view == null) return;
        var main = fishmod.compat.McCompatKt.getMainTarget(net.minecraft.client.Minecraft.getInstance());
        if (main == null || view.getWidth(0) != main.width || view.getHeight(0) != main.height) return;
        UiRenderer.lastPassView = view;
    }
}
