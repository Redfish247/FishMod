package fishmod.mixin;

import fishmod.cosmetic.NickState;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.data.EntityUtil;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Force the local player's own nametag to render (vanilla skips it as the camera entity), so you can
 *  see your own nick above your head with the [level] prefix + emblem. Only flips the local player's
 *  result to true — other players are untouched. */
@Mixin(AvatarRenderer.class)
public class PlayerNameLabelMixin {

    @ModifyReturnValue(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("RETURN"))
    private boolean fishmod$showOwnLabel(boolean original, Avatar entity, double dist) {
        if (original) return true;
        if (entity instanceof Player p && EntityUtil.isClientPlayer(p)
                && FishSettings.nickPreviewEnabled && NickState.isActive()) {
            return true;
        }
        return original;
    }
}
