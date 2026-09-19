package fishmod.mixin;

import fishmod.cosmetic.NameRewriter;
import fishmod.cosmetic.NickState;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public abstract class CosmeticChatMixin {

    // All add*Message paths funnel into this private 4-arg addMessage — hook once, no double-swap
    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
        at = @At("HEAD"), argsOnly = true)
    private Component fishmod$cosmeticAddMessage(Component msg) {
        return fishmod$swap(msg);
    }

    private static Component fishmod$swap(Component msg) {
        if (msg == null) return msg;
        Component out = msg;
        if (NickState.isActive()) {
            String real = NickState.realName();
            if (!real.isEmpty() && out.getString().contains(real))
                out = NameRewriter.replaceName(out, real, NickState.asComponent());
        }
        out = fishmod.cosmetic.RemoteNicks.apply(out);
        // Prestige Colors: recolour the [level] badge on chat lines
        if (fishmod.utils.config.values.FishSettings.prestigeColorsEnabled
                && fishmod.utils.config.values.FishSettings.prestigeColorsChat) {
            out = fishmod.cosmetic.prestige.PrestigeLevelColors.colorizeChatLevel(out);
        }
        return out;
    }
}
