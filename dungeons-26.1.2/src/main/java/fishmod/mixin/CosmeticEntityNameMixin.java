package fishmod.mixin;

import fishmod.cosmetic.NameRewriter;
import fishmod.cosmetic.NickState;
import fishmod.cosmetic.prestige.PrestigeLevelColors;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class CosmeticEntityNameMixin {

    @ModifyReturnValue(method = "getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"))
    private Component fishmod$cosmeticNameTag(Component original, Entity entity) {
        if (original == null) return original;
        Component out = original;
        if (NickState.isActive()) {
            String real = NickState.realName();
            if (!real.isEmpty() && out.getString().contains(real))
                out = NameRewriter.replaceName(out, real, NickState.asComponent());
        }
        // Only re-style already-resolved nicks here; no per-frame name→uuid lookups
        out = fishmod.cosmetic.RemoteNicks.applyResolvedOnly(out);
        // Prestige Colors: recolour the leading [level] badge on player nametags
        if (entity instanceof Player
                && FishSettings.prestigeColorsEnabled && FishSettings.prestigeColorsNametags) {
            out = PrestigeLevelColors.colorizeLevelPrefix(out);
        }
        return out;
    }
}
