package fishmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.cosmetic.ScaleHolder;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.data.EntityTools;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererScaleMixin {

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("TAIL"))
    private void fishmod$applyCustomSize(AvatarRenderState state, PoseStack matrices, CallbackInfo ci) {
        if (fishmod.utils.debug.FishDebug.skipScale) return;
        ScaleHolder h = (ScaleHolder) state;
        float x = h.fishmod$getScaleX(), y = h.fishmod$getScaleY(), z = h.fishmod$getScaleZ();
        boolean changed = x != 1.0f || y != 1.0f || z != 1.0f;
        if (changed && x > 0f && y > 0f && z > 0f) matrices.scale(x, y, z);
    }

    @ModifyReturnValue(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("RETURN"))
    private boolean fishmod$showOwnLabel(boolean original, Avatar entity, double dist) {
        if (original) return true;
        return entity instanceof Player p && EntityTools.isClientPlayer(p) && FishSettings.nickPreviewEnabled;
    }
}
