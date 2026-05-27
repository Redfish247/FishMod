package fishmod.mixin;

import fishmod.features.dungeon.PartyCommandHandler;
import fishmod.utils.config.values.FishSettings;
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

    // ── Command Parsing Logic ──────────────────────────────────────────────────

    private static final String CMD_ALT =
            "rtca|rtc|cata|pb|secrets|sa|runs|totalruns|dprofit|fps|tps|ping|ai|allinv|d|mp|collection|kick|warp|transfer|promote|corpse|corpses|bank|powder|nw|networth|level|sblvl|farming|nuc|nucleus|help|\\?|e|[fm][1-7]|t[1-5]";

    private static final Pattern PARTY_CMD = Pattern.compile(
            "^Party > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: [.!](" + CMD_ALT + ")(?:\\s+(\\w+)(?:\\s+(\\w+))?)?\\s*$");
    private static final Pattern GUILD_CMD = Pattern.compile(
            "^(?:Guild|G) > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: [.!](" + CMD_ALT + ")(?:\\s+(\\w+)(?:\\s+(\\w+))?)?\\s*$");
    private static final Pattern OFFICER_CMD = Pattern.compile(
            "^(?:Officer|O) > (?:\\[[^\\]]+\\] )*(\\w+)(?: \\[[^\\]]+\\])?: [.!](" + CMD_ALT + ")(?:\\s+(\\w+)(?:\\s+(\\w+))?)?\\s*$");
    private static final Pattern MSG_CMD = Pattern.compile(
            "^From (?:\\[[^\\]]+\\] )*(\\w+): [.!](" + CMD_ALT + ")(?:\\s+(\\w+)(?:\\s+(\\w+))?)?\\s*$");
    private static final Pattern TO_CMD = Pattern.compile(
            "^To (?:\\[[^\\]]+\\] )*(\\w+): [.!](" + CMD_ALT + ")(?:\\s+(\\w+)(?:\\s+(\\w+))?)?\\s*$");
    private static final Pattern ALL_CMD = Pattern.compile(
            "^(?:\\[[^\\]]+\\] )+(\\w+): [.!](" + CMD_ALT + ")(?:\\s+(\\w+)(?:\\s+(\\w+))?)?\\s*$");

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Text message, CallbackInfo ci) {
        String plain = message.getString().replaceAll("§.", "");

        if (System.currentTimeMillis() - fishmod.features.dungeon.ChatCommandState.lastPartyCommandAt < 6000) {
            if (plain.startsWith("Unknown party command")
                    || plain.startsWith("You are sending commands too fast")
                    || plain.startsWith("You cannot use party commands here")) {
                ci.cancel();
                return;
            }
        }

        if (tryDispatch(PARTY_CMD, plain, "pc ", null)) return;
        if (FishSettings.chatGuild && tryDispatch(GUILD_CMD, plain, "gc ", null)) return;
        if (FishSettings.chatOfficer && tryDispatch(OFFICER_CMD, plain, "oc ", null)) return;
        if (FishSettings.chatPrivate) {
            if (tryDispatch(MSG_CMD, plain, null, "msg ")) return;
            if (tryDispatch(TO_CMD, plain, null, "msg ")) return;
        }
        if (FishSettings.chatAll) {
            if (tryDispatch(ALL_CMD, plain, "ac ", null)) return;
        }

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
    private static final Pattern ALL_MSG     = Pattern.compile("^(?:\\[[^\\]]+\\] )+(\\w+): (.+)$");

    private static final String[] MEOWS = {"meow", "mrow", "meow meow", "nya~", "mrrp", "meow :3", ":3",
            "purr", "mew", "nyaa~", "purrr", "blep", "mlem", "meow >w<", "mrrp :3", "nya nya"};
    private static long lastMeowAt = 0;

    private static void tryMeow(String plain) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null || mc.player == null) return;

        String prefix = null; Matcher m;
        if ((m = PARTY_MSG.matcher(plain)).find()) prefix = "pc ";
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
                    if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand(reply);
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
        // The typer is always the message sender (group 1), so stats lookups (.nw/.cata/.pb/...)
        // with no explicit arg default to the SENDER, not the local player. For a DM we reply
        // privately to the sender; for channels we reply in that channel.
        String responder = (dmPrefix != null) ? dmPrefix + matchedName + " " : channelResponder;
        PartyCommandHandler.onPartyCommand(matchedName, cmd, rawArg1, rawArg2, responder);
        return true;
    }
}