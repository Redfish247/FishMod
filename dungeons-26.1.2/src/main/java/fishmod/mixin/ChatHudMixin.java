package fishmod.mixin;

import fishmod.utils.config.values.FishSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

@Mixin(ChatComponent.class)
public class ChatHudMixin {

    private static final Pattern FROM_MSG = Pattern.compile("^From (?:\\[[^\\]]+\\] )*(\\w+): (.+)$");

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        try {
            String messageText = message.getString();
            fishmod.features.Ragnarock.checkP5Taunt(messageText);

            if (fishmod.features.ChatFilter.shouldHide(message)
                    || fishmod.features.chat.ChatRuleHandler.shouldHideAtDisplay(message)) {
                fishmod.features.chat.ChatHideState.noteSuppressed();
                ci.cancel();
                return;
            }
            if (fishmod.features.chat.ChatHideState.shouldSwallowBlank(message)) { ci.cancel(); return; }

            if (!FishSettings.chatParty && !FishSettings.chatGuild
                    && !FishSettings.chatPrivate && !FishSettings.chatAll && !FishSettings.pfStatsEnabled
                    && !(FishSettings.chatFeatureEnabled && FishSettings.chatCompact)
                    && System.currentTimeMillis() - fishmod.features.dungeon.ChatCommandState.lastPartyCommandAt >= 6000) {
                return;
            }

            String plain = fishmod.utils.HypixelApi.STRIP_COLOR.matcher(messageText).replaceAll("");

            if (System.currentTimeMillis() - fishmod.features.dungeon.ChatCommandState.lastPartyCommandAt < 6000) {
                if (plain.startsWith("Unknown party command")
                        || plain.startsWith("You are sending commands too fast")
                        || plain.startsWith("You cannot use party commands here")) {
                    ci.cancel();
                    return;
                }
            }

            if (FishSettings.pfStatsEnabled) {
                Matcher pfm = FROM_MSG.matcher(plain);
                if (pfm.find()) fishmod.features.dungeon.PartyFinderStats.onWhisper(pfm.group(1));
            }

            if (FishSettings.chatFeatureEnabled && FishSettings.chatCompact
                    && fishmod.features.CompactChat.tryCompact(message, (ChatComponent) (Object) this, ci)) return;
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ChatHudMixin.1", "chat addMessage hook failed", t);
        }
    }
}