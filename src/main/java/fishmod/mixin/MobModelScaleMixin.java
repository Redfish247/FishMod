package fishmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fishmod.cosmetic.ScaleHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MobModelScaleMixin {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void fishmod$mobScaleIn(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector c, CameraRenderState cam, CallbackInfo ci) {
        if (state instanceof AvatarRenderState) return;
        ScaleHolder h = (ScaleHolder) state;
        float x = h.fishmod$getScaleX(), y = h.fishmod$getScaleY(), z = h.fishmod$getScaleZ();
        if ((x != 1.0f || y != 1.0f || z != 1.0f) && x > 0f && y > 0f && z > 0f) pose.scale(x, y, z);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
    private void fishmod$mobScaleOut(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector c, CameraRenderState cam, CallbackInfo ci) {
        if (state instanceof AvatarRenderState) return;
        ScaleHolder h = (ScaleHolder) state;
        float x = h.fishmod$getScaleX(), y = h.fishmod$getScaleY(), z = h.fishmod$getScaleZ();
        if ((x != 1.0f || y != 1.0f || z != 1.0f) && x > 0f && y > 0f && z > 0f) pose.scale(1f / x, 1f / y, 1f / z);
    }
}
