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
        if (original == null || playerInfo == null || playerInfo.getProfile() == null || playerInfo.getProfile().id() == null) return original;
        boolean prestige = FishSettings.prestigeColorsEnabled && FishSettings.prestigeColorsTab;
        boolean badges = FishSettings.badgesEnabled && FishSettings.badgesOnTab;
        if (!prestige && !badges) return original;
        java.util.UUID id = playerInfo.getProfile().id();
        return fishmod.cosmetic.NameDecorCache.TAB.get(id, original, () -> {
            Component out = original;
            if (prestige) out = PrestigeLevelColors.colorizeLevelPrefix(out);
            if (badges) out = fishmod.cosmetic.badge.BadgeRenderer.insertKnown(out, id.toString().replace("-", ""));
            return out;
        });
    }
}
