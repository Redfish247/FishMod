package fishmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fishmod.cosmetic.ScaleHolder;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the customizable player size. {@code scale()} runs after the renderer has translated to the
 * player's feet and before the model is drawn, so a matrix scale here grows/shrinks the model (with
 * armor + held items) around the feet, leaving the nametag untouched. X/Y/Z are independent. Render-only
 * — no hitbox or attribute change. The values are stashed on the render state by {@code EntityRendererMixin}.
 */
@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererScaleMixin {

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("TAIL"))
    private void fishmod$applyCustomSize(AvatarRenderState state, PoseStack matrices, CallbackInfo ci) {
        ScaleHolder h = (ScaleHolder) state;
        float x = h.fishmod$getScaleX(), y = h.fishmod$getScaleY(), z = h.fishmod$getScaleZ();
        boolean changed = x != 1.0f || y != 1.0f || z != 1.0f;
        if (changed && x > 0f && y > 0f && z > 0f) matrices.scale(x, y, z);
    }
}
