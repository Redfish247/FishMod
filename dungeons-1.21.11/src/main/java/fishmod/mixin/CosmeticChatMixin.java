package fishmod.mixin;

import fishmod.cosmetic.NameRewriter;
import fishmod.cosmetic.NickState;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public abstract class CosmeticChatMixin {

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), argsOnly = true)
    private Text fishmod$cosmeticAddMessage1(Text msg) {
        return fishmod$swap(msg);
    }

    @ModifyVariable(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"), argsOnly = true)
    private Text fishmod$cosmeticAddMessage2(Text msg) {
        return fishmod$swap(msg);
    }

    private static Text fishmod$swap(Text msg) {
        if (msg == null) return msg;
        Text out = msg;
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
