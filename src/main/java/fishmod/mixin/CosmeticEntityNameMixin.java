package fishmod.mixin;

import fishmod.cosmetic.NameRewriter;
import fishmod.cosmetic.NickState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class CosmeticEntityNameMixin {

    @ModifyReturnValue(method = "getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"))
    private Component fishmod$cosmeticNameTag(Component original) {
        if (original == null) return original;
        Component out = original;
        if (NickState.isActive()) {
            String real = NickState.realName();
            if (!real.isEmpty() && out.getString().contains(real))
                out = NameRewriter.replaceName(out, real, NickState.asComponent());
        }
        // Render path: only re-style already-known nicks. Discovery is chat-driven (CosmeticChatMixin)
        // so per-frame nametag draws never fire name→uuid / /nicks lookups.
        return fishmod.cosmetic.RemoteNicks.applyResolvedOnly(out);
    }
}
