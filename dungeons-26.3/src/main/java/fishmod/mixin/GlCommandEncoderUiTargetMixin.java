package fishmod.mixin;

import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import fishmod.utils.rendering.UiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 26.3 side: the GL backend moved to com.mojang.renderpearl.backend.opengl.
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
public class GlCommandEncoderUiTargetMixin {

    @Inject(
        method = "createRenderPass(Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)Lcom/mojang/renderpearl/backend/api/RenderPassBackend;",
        at = @At("HEAD")
    )
    private void fishmod$recordPassTarget(RenderPassDescriptor descriptor, CallbackInfoReturnable<?> cir) {
        var atts = descriptor.colorAttachments();
        if (atts == null || atts.isEmpty() || atts.get(0) == null) return;
        var view = atts.get(0).textureView();
        if (view == null || !(view.texture() instanceof GlTexture tex)) return;
        // Only full-window passes; picture-in-picture/blur/offscreen passes would swallow the UI.
        var main = fishmod.compat.McCompatKt.getMainTarget(net.minecraft.client.Minecraft.getInstance());
        if (main == null || tex.getWidth(0) != main.width || tex.getHeight(0) != main.height) return;
        UiRenderer.lastPassColorTex = tex.glId();
    }
}
