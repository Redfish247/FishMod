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

    @ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), argsOnly = true)
    private Component fishmod$cosmeticAddMessage1(Component msg) {
        return fishmod$swap(msg);
    }

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
        at = @At("HEAD"), argsOnly = true)
    private Component fishmod$cosmeticAddMessage2(Component msg) {
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
        // Streamer Mode: §k-scramble your own IGN in chat too.
        return fishmod.features.StreamerMode.censorChat(out);
    }
}
