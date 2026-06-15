package fishmod.mixin;

import fishmod.cosmetic.NameRewriter;
import fishmod.cosmetic.NickState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class CosmeticEntityNameMixin {

    @ModifyReturnValue(method = "getDisplayName(Lnet/minecraft/entity/Entity;)Lnet/minecraft/text/Text;", at = @At("RETURN"))
    private Text fishmod$cosmeticNameTag(Text original) {
        if (original == null) return original;
        Text out = original;
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
