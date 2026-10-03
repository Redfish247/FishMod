package fishmod.mixin;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import fishmod.utils.rendering.UiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

// Records the colour texture of each render pass so the UI overlay lands on whatever GuiRenderer actually drew into.
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public class GlCommandEncoderUiTargetMixin {

    @Inject(
        method = "createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPassBackend;",
        at = @At("HEAD")
    )
    private void fishmod$recordPassTarget(Supplier<String> label, GpuTextureView color, OptionalInt clearColor, GpuTextureView depth, OptionalDouble clearDepth, CallbackInfoReturnable<?> cir) {
        if (color == null || !(color.texture() instanceof GlTexture tex)) return;
        // Only full-window passes; picture-in-picture/blur/offscreen passes would swallow the UI.
        var main = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        if (main == null || tex.getWidth(0) != main.width || tex.getHeight(0) != main.height) return;
        UiRenderer.lastPassColorTex = tex.glId();
    }
}
