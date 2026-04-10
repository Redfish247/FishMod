package blade.addon.features.dungeon;

import blade.addon.utils.config.values.FishSettings;
import blade.addon.utils.events.Events;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatically accepts any party invite, sends "test", then leaves.
 * Toggle via /fm > My Features > Auto Accept Party Invite.
 */
public class PartyInviteAccepter {

    // Matches Hypixel party invite messages:
    //   "[MVP+] XeonTheTerrible1 has invited you to join [MVP+] dhoq's party!"
    //   "PlayerName has invited you to join their party!"
    private static final Pattern INVITE_PATTERN = Pattern.compile(
            "(\\w+) (?:has )?invited you to join");

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "party-invite-accepter");
                t.setDaemon(true);
                return t;
            });

    public static void init() {
        Events.ON_GAME_MESSAGE.register(msg -> {
            if (!FishSettings.autoAcceptPartyInvite) return false;
            String plain = msg.getString().replaceAll("§.", "").trim();
            checkAndHandle(plain);
            return false;
        });
    }

    public static void onMessage(String plain) {
        if (!FishSettings.autoAcceptPartyInvite) return;
        checkAndHandle(plain);
    }

    private static void checkAndHandle(String plain) {
        Matcher m = INVITE_PATTERN.matcher(plain);
        if (m.find()) {
            handleInvite(m.group(1));
        }
    }

    private static void handleInvite(String sender) {
        MinecraftClient mc = MinecraftClient.getInstance();

        sendCommand(mc, "party accept " + sender, 0);
        sendCommand(mc, "pc Party > [YOUTUBE] Future77: IM NOT GAY BRO", 500);
        sendCommand(mc, "p leave", 1000);
    }

    private static void sendCommand(MinecraftClient mc, String command, long delayMs) {
        SCHEDULER.schedule(() -> mc.execute(() -> {
            if (mc.getNetworkHandler() == null) return;
            if (mc.currentScreen != null) mc.setScreen(null);
            mc.getNetworkHandler().sendChatCommand(command);
        }), delayMs, TimeUnit.MILLISECONDS);
    }

    private static void sendMessage(MinecraftClient mc, String message, long delayMs) {
        SCHEDULER.schedule(() -> mc.execute(() -> {
            if (mc.getNetworkHandler() == null) return;
            if (mc.currentScreen != null) mc.setScreen(null);
            mc.getNetworkHandler().sendChatMessage(message);
        }), delayMs, TimeUnit.MILLISECONDS);
    }
}
