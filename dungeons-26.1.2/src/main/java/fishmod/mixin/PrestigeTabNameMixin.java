package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.cosmetic.prestige.PrestigeLevelColors;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerTabOverlay.class)
public class PrestigeTabNameMixin {

    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component fishmod$prestigeTabName(Component original, PlayerInfo playerInfo) {
        if (original == null) return original;
        Component out = original;
        if (FishSettings.prestigeColorsEnabled && FishSettings.prestigeColorsTab) {
            out = PrestigeLevelColors.colorizeLevelPrefix(out);
        }
        if (FishSettings.badgesEnabled && FishSettings.badgesOnTab
                && playerInfo != null && playerInfo.getProfile() != null && playerInfo.getProfile().id() != null) {
            out = fishmod.cosmetic.badge.BadgeRenderer.insertKnown(out, playerInfo.getProfile().id().toString().replace("-", ""));
        }
        return out;
    }
}
