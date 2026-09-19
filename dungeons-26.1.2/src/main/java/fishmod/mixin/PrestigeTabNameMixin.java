package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.cosmetic.prestige.PrestigeLevelColors;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Prestige Colors: recolour the leading [level] badge on tab-list entries. */
@Mixin(PlayerTabOverlay.class)
public class PrestigeTabNameMixin {

    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component fishmod$prestigeTabName(Component original) {
        if (original == null || !FishSettings.prestigeColorsEnabled || !FishSettings.prestigeColorsTab) {
            return original;
        }
        return PrestigeLevelColors.colorizeLevelPrefix(original);
    }
}
