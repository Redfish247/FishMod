package fishmod.mixin;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import fishmod.utils.rendering.UiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 26.2 side: render passes are described by a RenderPassDescriptor now.
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public class GlCommandEncoderUiTargetMixin {

    @Inject(
        method = "createRenderPass(Lcom/mojang/blaze3d/systems/RenderPassDescriptor;)Lcom/mojang/blaze3d/systems/RenderPassBackend;",
        at = @At("HEAD")
    )
    private void fishmod$recordPassTarget(RenderPassDescriptor descriptor, CallbackInfoReturnable<?> cir) {
        var atts = descriptor.colorAttachments;
        if (atts == null || atts.isEmpty() || atts.get(0) == null) return;
        var view = atts.get(0).textureView();
        if (view == null || !(view.texture() instanceof GlTexture tex)) return;
        // Only full-window passes; picture-in-picture/blur/offscreen passes would swallow the UI.
        var main = fishmod.compat.McCompatKt.getMainTarget(net.minecraft.client.Minecraft.getInstance());
        if (main == null || tex.getWidth(0) != main.width || tex.getHeight(0) != main.height) return;
        UiRenderer.lastPassColorTex = tex.glId();
    }
}
