package fishmod.mixin;

import fishmod.features.dungeon.PartyCommandHandler;
import fishmod.utils.config.values.FishSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

@Mixin(ChatComponent.class)
public class ChatHudMixin {

    // ── Command Parsing Logic ──────────────────────────────────────────────────

    private static final String CMD_ALT =
            "rtca|rtc|crtc|cata|pb|secrets|sa|runs|totalruns|dprofit|fps|tps|ping|ai|allinv|d|mp|collection|kick|warp|w|transfer|pt|ptme|promote|demote|corpse|corpses|bank|powder|nw|networth|level|sblvl|farming|nuc|nucleus|worm|scatha|help|\\?|e|[fm][1-7]|t[1-5]";

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

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        // Chat filter: hide selected spam lines at DISPLAY time. Packet-level parsers (dungeon
        // splits/score, Simon Says, …) already ran via ON_GAME_MESSAGE before the line reaches
        // here, so suppressing it now never breaks those features.
        if (fishmod.features.ChatFilter.shouldHide(message)) { ci.cancel(); return; }

        String plain = message.getString().replaceAll("§.", "");

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

        // Collapse identical repeats into a single "(N)"-counted line. Runs last so filtered/
        // dispatched lines are already handled; cancels + re-adds the message when it collapses.
        if (FishSettings.chatCompact
                && fishmod.features.CompactChat.tryCompact(message, (ChatComponent) (Object) this, ci)) return;

        if (FishSettings.chatMeow) tryMeow(plain);
    }

    // ── Meow auto-responder ─────────────────────────────────────────────────────
    private static final Pattern MEOW_WORD = Pattern.compile(
            "(?i)(\\bm+e+o+w+\\b|\\bm+e+w+\\b|\\bmr+o+w+\\b|\\bmrr+p+\\b|\\bnya+n*\\b|\\bmiaou+\\b"
          + "|\\bp+u*rr+\\b|\\bmlem\\b|\\bblep\\b|\\bpsp+s+\\b|:3)");
    private static final Pattern PARTY_MSG   = Pattern.compile("^Party > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: (.+)$");
    private static final Pattern GUILD_MSG   = Pattern.compile("^(?:Guild|G) > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: (.+)$");
    private static final Pattern OFFICER_MSG = Pattern.compile("^(?:Officer|O) > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: (.+)$");
    private static final Pattern FROM_MSG    = Pattern.compile("^From (?:\\[[^\\]]+\\] )*(\\w+): (.+)$");
    private static final Pattern ALL_MSG     = Pattern.compile("^(?:\\[[^\\]]+\\] )*(\\w+): (.+)$");

    private static final String[] MEOWS = {"meow", "mrow", "meow meow", "nya~", "mrrp", "meow :3", ":3",
            "purr", "mew", "nyaa~", "purrr", "blep", "mlem", "meow >w<", "mrrp :3", "nya nya", "Mreow!"};
    private static long lastMeowAt = 0;

    private static void tryMeow(String plain) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return;

        String prefix = null; Matcher m;
        if (FishSettings.chatParty && (m = PARTY_MSG.matcher(plain)).find()) prefix = "pc ";
        else if (FishSettings.chatGuild && (m = GUILD_MSG.matcher(plain)).find()) prefix = "gc ";
        else if (FishSettings.chatOfficer && (m = OFFICER_MSG.matcher(plain)).find()) prefix = "oc ";
        else if (FishSettings.chatPrivate && (m = FROM_MSG.matcher(plain)).find()) prefix = "msg " + m.group(1) + " ";
        else if (FishSettings.chatAll && (m = ALL_MSG.matcher(plain)).find()) prefix = "ac ";
        else return;

        String sender = m.group(1);
        String body = m.group(2);
        if (sender.equalsIgnoreCase(mc.player.getName().getString())) return;
        if (!MEOW_WORD.matcher(body).find()) return;

        long now = System.currentTimeMillis();
        if (now - lastMeowAt < 4000) return;
        lastMeowAt = now;

        String reply = prefix + MEOWS[(int) (Math.random() * MEOWS.length)];
        java.util.concurrent.CompletableFuture.delayedExecutor(400, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(() -> mc.execute(() -> {
                    if (mc.getConnection() != null) mc.getConnection().sendCommand(reply);
                }));
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
        // The typer is always the message sender (group 1), so stats lookups (.nw/.cata/.pb/...)
        // with no explicit arg default to the SENDER, not the local player. For a DM we reply
        // privately to the sender; for channels we reply in that channel.
        String responder = (dmPrefix != null) ? dmPrefix + matchedName + " " : channelResponder;
        PartyCommandHandler.onPartyCommand(matchedName, cmd, rawArg1, rawArg2, rawArg3, responder);
        return true;
    }
}