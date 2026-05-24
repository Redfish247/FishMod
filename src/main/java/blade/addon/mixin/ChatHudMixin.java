package blade.addon.mixin;

import blade.addon.features.dungeon.PartyCommandHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    // Shared command alternation reused across all chat-channel patterns
    private static final String CMD_ALT =
            "rtca|rtc|cata|pb|secrets|sa|runs|totalruns|dprofit|fps|tps|ping|ai|allinv|d|mp|collection|kick|warp|transfer|promote|corpse|corpses|bank|nw|networth|help|\\?|e|[fm][1-7]|t[1-5]";

    // Hypixel chat-channel formats. All capture: (1) typer IGN, (2) command, (3) arg1, (4) arg2.
    // Party  > [rank] Name: .cmd
    private static final Pattern PARTY_CMD = Pattern.compile(
            "^Party > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: [.!](" + CMD_ALT + ")(?:\\s+(\\w+)(?:\\s+(\\w+))?)?\\s*$");
    // Guild  > [rank] Name [GuildRank]: .cmd
    private static final Pattern GUILD_CMD = Pattern.compile(
            "^(?:Guild|G) > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: [.!](" + CMD_ALT + ")(?:\\s+(\\w+)(?:\\s+(\\w+))?)?\\s*$");
    // Officer > [rank] Name [GuildRank]: .cmd
    private static final Pattern OFFICER_CMD = Pattern.compile(
            "^(?:Officer|O) > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: [.!](" + CMD_ALT + ")(?:\\s+(\\w+)(?:\\s+(\\w+))?)?\\s*$");
    // From [rank] Name: .cmd   (incoming private message — group 1 = sender, who we reply to)
    private static final Pattern MSG_CMD = Pattern.compile(
            "^From (?:\\[[^\\]]+\\] )*(\\w+): [.!](" + CMD_ALT + ")(?:\\s+(\\w+)(?:\\s+(\\w+))?)?\\s*$");
    // To [rank] Name: .cmd     (outgoing private message we typed ourselves — group 1 = recipient, who we reply to)
    private static final Pattern TO_CMD = Pattern.compile(
            "^To (?:\\[[^\\]]+\\] )*(\\w+): [.!](" + CMD_ALT + ")(?:\\s+(\\w+)(?:\\s+(\\w+))?)?\\s*$");
    // All chat: [rank] Name: .cmd   (no channel prefix — gated behind a setting to avoid false positives)
    private static final Pattern ALL_CMD = Pattern.compile(
            "^(?:\\[[^\\]]+\\] )+(\\w+): [.!](" + CMD_ALT + ")(?:\\s+(\\w+)(?:\\s+(\\w+))?)?\\s*$");

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Text message, CallbackInfo ci) {
        String plain = message.getString().replaceAll("§.", "");

        // Suppress Hypixel error replies that fire after FishMod's own party commands.
        // Widen the window to 6s and cover the related rate-limit / permission errors.
        if (System.currentTimeMillis() - blade.addon.features.dungeon.ChatCommandState.lastPartyCommandAt < 6000) {
            if (plain.startsWith("Unknown party command")
                    || plain.startsWith("You are sending commands too fast")
                    || plain.startsWith("You cannot use party commands here")) {
                ci.cancel();
                return;
            }
        }

        // Try each channel in turn. Responder = chat-command prefix used to reply in the same channel.
        if (tryDispatch(PARTY_CMD,   plain, "pc ",  null)) return;
        if (blade.addon.utils.config.values.FishSettings.chatGuild
                && tryDispatch(GUILD_CMD,   plain, "gc ",  null)) return;
        if (blade.addon.utils.config.values.FishSettings.chatOfficer
                && tryDispatch(OFFICER_CMD, plain, "oc ",  null)) return;
        if (blade.addon.utils.config.values.FishSettings.chatPrivate) {
            if (tryDispatch(MSG_CMD, plain, null, "msg ")) return;
            if (tryDispatch(TO_CMD,  plain, null, "msg ")) return;
        }
        if (blade.addon.utils.config.values.FishSettings.chatAll) {
            if (tryDispatch(ALL_CMD,    plain, "ac ",  null)) return;
        }

        // Not a command — try the meow auto-responder.
        if (blade.addon.utils.config.values.FishSettings.chatMeow) tryMeow(plain);
    }

    // ── Meow auto-responder ─────────────────────────────────────────────────────
    private static final Pattern MEOW_WORD = Pattern.compile("(?i)(\\bm+e+o+w+\\b|\\bmr+o+w+\\b|\\bmrr+p+\\b|\\bnya+\\b|\\bmiaou+\\b)");
    // Each captures (1) sender, (2) message body.
    private static final Pattern PARTY_MSG   = Pattern.compile("^Party > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: (.+)$");
    private static final Pattern GUILD_MSG   = Pattern.compile("^(?:Guild|G) > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: (.+)$");
    private static final Pattern OFFICER_MSG = Pattern.compile("^(?:Officer|O) > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: (.+)$");
    private static final Pattern FROM_MSG    = Pattern.compile("^From (?:\\[[^\\]]+\\] )*(\\w+): (.+)$");
    private static final Pattern ALL_MSG     = Pattern.compile("^(?:\\[[^\\]]+\\] )+(\\w+): (.+)$");

    private static final String[] MEOWS = {"meow", "mrow", "meow meow", "nya~", "mrrp", "meow :3", ":3"};
    private static long lastMeowAt = 0;

    private static void tryMeow(String plain) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null || mc.player == null) return;

        String prefix = null; Matcher m;
        if ((m = PARTY_MSG.matcher(plain)).find()) prefix = "pc ";
        else if (blade.addon.utils.config.values.FishSettings.chatGuild   && (m = GUILD_MSG.matcher(plain)).find())   prefix = "gc ";
        else if (blade.addon.utils.config.values.FishSettings.chatOfficer && (m = OFFICER_MSG.matcher(plain)).find()) prefix = "oc ";
        else if (blade.addon.utils.config.values.FishSettings.chatPrivate && (m = FROM_MSG.matcher(plain)).find())    prefix = "msg " + m.group(1) + " ";
        else if (blade.addon.utils.config.values.FishSettings.chatAll      && (m = ALL_MSG.matcher(plain)).find())     prefix = "ac ";
        else return;

        String sender = m.group(1);
        String body   = m.group(2);
        // Don't respond to ourselves (avoids infinite meow loops).
        if (sender.equalsIgnoreCase(mc.player.getName().getString())) return;
        if (!MEOW_WORD.matcher(body).find()) return;

        long now = System.currentTimeMillis();
        if (now - lastMeowAt < 4000) return; // global cooldown
        lastMeowAt = now;

        String reply = prefix + MEOWS[(int) (Math.random() * MEOWS.length)];
        java.util.concurrent.CompletableFuture.delayedExecutor(400, java.util.concurrent.TimeUnit.MILLISECONDS)
            .execute(() -> mc.execute(() -> {
                if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand(reply);
            }));
    }

    /**
     * If the pattern matches, dispatches to PartyCommandHandler with the right responder prefix.
     * For DMs the responder is "msg <typer> " (built from the matched IGN). For channel chats it
     * is the literal chat-command prefix like "pc " or "gc ".
     */
    private static boolean tryDispatch(Pattern p, String plain, String channelResponder, String dmPrefix) {
        Matcher m = p.matcher(plain);
        if (!m.find()) return false;
        blade.addon.features.dungeon.ChatCommandState.lastPartyCommandAt = System.currentTimeMillis();
        String matchedName = m.group(1); // sender (From) or recipient (To) for DMs; typer for channel chats
        String cmd     = m.group(2);
        String rawArg1 = m.group(3);
        String rawArg2 = m.group(4);
        String responder;
        String typer;
        if (dmPrefix != null) {
            // DM: reply goes to the other party, and we treat the local player as the typer so
            // self-only commands (.fps/.tps/.ping/.dprofit/.corpse) work and default stat lookups
            // resolve to "my" stats rather than the other party's IGN.
            responder = dmPrefix + matchedName + " ";
            MinecraftClient mc = MinecraftClient.getInstance();
            typer = (mc.player != null) ? mc.player.getName().getString() : matchedName;
        } else {
            responder = channelResponder;
            typer = matchedName;
        }
        PartyCommandHandler.onPartyCommand(typer, cmd, rawArg1, rawArg2, responder);
        return true;
    }
}
