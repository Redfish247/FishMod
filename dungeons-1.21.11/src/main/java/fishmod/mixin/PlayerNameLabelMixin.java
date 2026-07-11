package fishmod.mixin;

import fishmod.cosmetic.NickState;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.data.EntityUtil;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Force the local player's own nametag to render (vanilla skips it as the camera entity), so you can
 *  see your own nick above your head with the [level] prefix + emblem. Only flips the local player's
 *  result to true — other players are untouched. */
@Mixin(PlayerEntityRenderer.class)
public class PlayerNameLabelMixin {

    @ModifyReturnValue(method = "hasLabel(Lnet/minecraft/entity/PlayerLikeEntity;D)Z", at = @At("RETURN"))
    private boolean fishmod$showOwnLabel(boolean original, PlayerLikeEntity entity, double dist) {
        if (original) return true;
        if (entity instanceof PlayerEntity p && EntityUtil.isClientPlayer(p)
                && FishSettings.nickPreviewEnabled && NickState.isActive()) {
            return true;
        }
        return original;
    }
}
