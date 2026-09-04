package fishmod.mixin;

import fishmod.features.dungeon.PartyCommandHandler;
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

    private static final String CMD_ALT =
            "rtca|rtc|crtc|cata|pb|secrets|sa|runs|totalruns|dprofit|crit|fps|tps|ping|ai|allinv|d|mp|collection|kick|warp|w|transfer|pt|ptme|promote|demote|corpse|corpses|bank|powder|nw|networth|level|sblvl|farming|nuc|nucleus|worm|scatha|help|\\?|e|[fm][1-7]|t[1-5]";

    // Up to 3 args captured (groups 3/4/5): .crtc needs [name] [class] [level].
    private static final String ARG_TAIL = "(?:\\s+(\\w+)(?:\\s+(\\w+)(?:\\s+(\\w+))?)?)?\\s*$";

    private static final Pattern PARTY_CMD = Pattern.compile(
            "^Party > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: [.!](" + CMD_ALT + ")" + ARG_TAIL);
    private static final Pattern GUILD_CMD = Pattern.compile(
            "^(?:Guild|G) > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: [.!](" + CMD_ALT + ")" + ARG_TAIL);
    private static final Pattern OFFICER_CMD = Pattern.compile(
            "^(?:Officer|O) > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: [.!](" + CMD_ALT + ")" + ARG_TAIL);
    private static final Pattern MSG_CMD = Pattern.compile(
            "^From (?:\\[[^\\]]+\\] )*(\\w+): [.!](" + CMD_ALT + ")" + ARG_TAIL);
    private static final Pattern TO_CMD = Pattern.compile(
            "^To (?:\\[[^\\]]+\\] )*(\\w+): [.!](" + CMD_ALT + ")" + ARG_TAIL);
    private static final Pattern ALL_CMD = Pattern.compile(
            "^(?:\\[[^\\]]+\\] )*(\\w+): [.!](" + CMD_ALT + ")" + ARG_TAIL);
    private static final Pattern FROM_MSG = Pattern.compile("^From (?:\\[[^\\]]+\\] )*(\\w+): (.+)$");

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        // Cancel at addMessage() HEAD: packet parsers already ran, and no blank slot is left behind
        if (fishmod.features.ChatFilter.shouldHide(message)
                || fishmod.features.chat.ChatRuleHandler.shouldHideAtDisplay(message)) {
            fishmod.features.chat.ChatHideState.noteSuppressed();
            ci.cancel();
            return;
        }
        if (fishmod.features.chat.ChatHideState.shouldSwallowBlank(message)) { ci.cancel(); return; }

        // Skip the strip/regex when nothing downstream needs the plain text
        if (!FishSettings.chatParty && !FishSettings.chatGuild && !FishSettings.chatOfficer
                && !FishSettings.chatPrivate && !FishSettings.chatAll && !FishSettings.pfStatsEnabled
                && !(FishSettings.chatFeatureEnabled && FishSettings.chatCompact)
                && System.currentTimeMillis() - fishmod.features.dungeon.ChatCommandState.lastPartyCommandAt >= 6000) {
            return;
        }

        String plain = fishmod.utils.HypixelApi.STRIP_COLOR.matcher(message.getString()).replaceAll("");

        if (System.currentTimeMillis() - fishmod.features.dungeon.ChatCommandState.lastPartyCommandAt < 6000) {
            if (plain.startsWith("Unknown party command")
                    || plain.startsWith("You are sending commands too fast")
                    || plain.startsWith("You cannot use party commands here")) {
                ci.cancel();
                return;
            }
        }

        if (FishSettings.chatParty && tryDispatch(PARTY_CMD, plain, "pc ", null)) return;
        if (FishSettings.chatGuild && tryDispatch(GUILD_CMD, plain, "gc ", null)) return;
        if (FishSettings.chatOfficer && tryDispatch(OFFICER_CMD, plain, "oc ", null)) return;
        if (FishSettings.chatPrivate) {
            if (tryDispatch(MSG_CMD, plain, null, "msg ")) return;
            if (tryDispatch(TO_CMD, plain, null, "msg ")) return;
        }
        if (FishSettings.chatAll) {
            if (tryDispatch(ALL_CMD, plain, "ac ", null)) return;
        }

        if (FishSettings.pfStatsEnabled) {
            Matcher pfm = FROM_MSG.matcher(plain);
            if (pfm.find()) fishmod.features.dungeon.PartyFinderStats.onWhisper(pfm.group(1));
        }

        // Runs last (after filter/dispatch); cancels + re-adds the message when it collapses
        if (FishSettings.chatFeatureEnabled && FishSettings.chatCompact
                && fishmod.features.CompactChat.tryCompact(message, (ChatComponent) (Object) this, ci)) return;
    }

    private static boolean tryDispatch(Pattern p, String plain, String channelResponder, String dmPrefix) {
        Matcher m = p.matcher(plain);
        if (!m.find()) return false;
        fishmod.features.dungeon.ChatCommandState.lastPartyCommandAt = System.currentTimeMillis();
        String matchedName = m.group(1);
        String cmd = m.group(2);
        String rawArg1 = m.group(3);
        String rawArg2 = m.group(4);
        String rawArg3 = m.group(5);
        // No-arg stats lookups default to the sender (group 1), not the local player
        String responder = (dmPrefix != null) ? dmPrefix + matchedName + " " : channelResponder;
        PartyCommandHandler.onPartyCommand(matchedName, cmd, rawArg1, rawArg2, rawArg3, responder);
        return true;
    }
}