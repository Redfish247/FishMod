package fishmod.mixin;
//? if <26.2 {

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTextureView;
import fishmod.utils.rendering.UiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

@Mixin(CommandEncoder.class)
public class GlCommandEncoderUiTargetMixin {

    @Inject(
        method = "createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;",
        at = @At("HEAD")
    )
    private void fishmod$recordPassTarget(Supplier<String> label, GpuTextureView color, OptionalInt clearColor, GpuTextureView depth, OptionalDouble clearDepth, CallbackInfoReturnable<?> cir) {
        if (color == null) return;
        var main = net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
        if (main == null || color.getWidth(0) != main.width || color.getHeight(0) != main.height) return;
        UiRenderer.lastPassView = color;
    }
}
//?} else if <26.3 {

/*import com.mojang.blaze3d.systems.CommandEncoder;
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
*///?} else {

/*import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import fishmod.utils.rendering.UiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"com.mojang.renderpearl.backend.opengl.GlCommandEncoder", "com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder"})
public class GlCommandEncoderUiTargetMixin {

    @Inject(
        method = "createRenderPass(Lcom/mojang/renderpearl/api/commands/RenderPassDescriptor;)Lcom/mojang/renderpearl/backend/api/RenderPassBackend;",
        at = @At("HEAD")
    )
    private void fishmod$recordPassTarget(RenderPassDescriptor descriptor, CallbackInfoReturnable<?> cir) {
        var atts = descriptor.colorAttachments();
        if (atts == null || atts.isEmpty() || atts.get(0) == null) return;
        var view = atts.get(0).textureView();
        if (view == null) return;
        var main = fishmod.compat.McCompatKt.getMainTarget(net.minecraft.client.Minecraft.getInstance());
        if (main == null || view.getWidth(0) != main.width || view.getHeight(0) != main.height) return;
        UiRenderer.lastPassView = view;
    }
}
*///?}
