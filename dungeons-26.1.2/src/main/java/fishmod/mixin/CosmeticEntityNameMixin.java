package fishmod.mixin;

import fishmod.cosmetic.NameRewriter;
import fishmod.cosmetic.NickState;
import fishmod.cosmetic.prestige.PrestigeLevelColors;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class CosmeticEntityNameMixin {
    // Pet tags have no ❤; mob tags ("[Lv5] Zombie 100/100❤") never match
    @org.spongepowered.asm.mixin.Unique
    private static final java.util.regex.Pattern fishmod$PET_LEVEL = java.util.regex.Pattern.compile("^\\s*\\[(?:Lvl? ?)?\\d+]\\s*(?=[^❤]+$)");

    @ModifyReturnValue(method = "getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"))
    private Component fishmod$cosmeticNameTag(Component original, Entity entity) {
        if (original == null) return original;
        if (FishSettings.petNametagHideLevel && entity instanceof ArmorStand) {
            String plain = ChatFormatting.stripFormatting(original.getString());
            java.util.regex.Matcher m = fishmod$PET_LEVEL.matcher(plain);
            if (m.find()) return NameRewriter.dropVisiblePrefix(original, m.end());
            return original;
        }
        boolean player = entity instanceof Player;
        boolean prestige = player && FishSettings.prestigeColorsEnabled && FishSettings.prestigeColorsNametags;
        boolean badges = player && FishSettings.badgesEnabled && FishSettings.badgesOnNametags;
        if (!NickState.isActive() && fishmod.cosmetic.RemoteNicks.isEmpty() && !prestige && !badges) return original;
        try {
            return fishmod.cosmetic.NameDecorCache.NAMETAG.get(entity.getUUID(), original, () -> fishmod$decorate(original, entity, prestige, badges));
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("CosmeticEntityNameMixin.1", "nametag decorate failed entity=" + entity.getType(), t);
            return original;
        }
    }

    private static Component fishmod$decorate(Component original, Entity entity, boolean prestige, boolean badges) {
        Component out = original;
        if (NickState.isActive()) {
            String real = NickState.realName();
            if (!real.isEmpty() && out.getString().contains(real))
                out = NameRewriter.replaceName(out, real, NickState.asComponent());
        }
        out = fishmod.cosmetic.RemoteNicks.applyResolvedOnly(out);
        if (prestige) out = PrestigeLevelColors.colorizeLevelPrefix(out);
        if (badges) out = fishmod.cosmetic.badge.BadgeRenderer.insertKnown(out, entity.getUUID().toString().replace("-", ""));
        return out;
    }
}
