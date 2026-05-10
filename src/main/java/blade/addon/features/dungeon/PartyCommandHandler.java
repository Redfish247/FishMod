package blade.addon.features.dungeon;

import blade.addon.utils.HypixelApi;
import blade.addon.utils.Location;
import blade.addon.utils.config.values.FishSettings;
import blade.addon.utils.Misc;
import blade.addon.utils.events.Events;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles party commands typed by the local player:
 *   .ai / .allinv  — /p settings allinvite
 *   .pb            — fetch M7 PB from Hypixel API and send to party chat
 *   .cata          — send cata level (requires API key)
 *   .rtca          — send runs-to-class-50 (requires API key)
 *   .e             — /joininstance catacombs_entrance
 *   .f1-.f7        — /joininstance catacombs_floor_X
 *   .m1-.m7        — /joininstance master_catacombs_floor_X
 */
public class PartyCommandHandler {

    private static final String[] NUM_WORDS =
            {"one", "two", "three", "four", "five", "six", "seven"};

    private static final String[] KUUDRA_TIERS =
            {"normal", "hot", "burning", "fiery", "infernal"};

    private static long dungeonEnteredAt = 0;

    // TPS tracking — rolling average of last 20 client tick intervals
    private static final long[] TICK_TIMES = new long[20];
    private static int tickIdx = 0;
    private static long lastTickMs = -1;

    public static void init() {
        // Track when the player enters a dungeon or Kuudra (for 30s joininstance guard)
        Events.ON_LOCATION_CHANGE.register(loc -> {
            if (loc == Location.DUNGEON || loc == Location.KUUDRA) dungeonEnteredAt = System.currentTimeMillis();
            return false;
        });

        // Server tick timing for real TPS (CommonPingS2CPacket fires once per server tick)
        Events.ON_SERVER_TICK.register(() -> {
            long now = System.currentTimeMillis();
            if (lastTickMs > 0) {
                TICK_TIMES[tickIdx % TICK_TIMES.length] = now - lastTickMs;
                tickIdx++;
            }
            lastTickMs = now;
            return false;
        });

    }

    /** Called from ChatHudMixin for every party command message. typer = who typed it, ign = lookup target, cmd = the command. */
    public static void onPartyCommand(String typer, String ign, String cmd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;
        boolean isMe = mc.player != null && typer.equalsIgnoreCase(mc.player.getName().getString());
        switch (cmd) {
            case "rtca"   -> { if (FishSettings.pcRtca)      runRtcaForPlayer(mc, ign); }
            case "cata"   -> { if (FishSettings.pcCata)      runCataForPlayer(mc, ign); }
            case "pb"     -> { if (FishSettings.pcPb)        runPbForPlayer(mc, ign);   }
            // Local-only: only the typer's own mod responds (avoids everyone spamming their own stats)
            case "fps"    -> { if (FishSettings.pcFps    && isMe) sendFps(mc);  }
            case "tps"    -> { if (FishSettings.pcTps    && isMe) sendTps(mc);  }
            case "ping"   -> { if (FishSettings.pcPing   && isMe) sendPing(mc); }
            case "ai", "allinv" -> { if (FishSettings.pcAllinvite && isMe) sendCmd(mc, "p settings allinvite"); }
            case "d"            -> { if (FishSettings.pcDisband   && isMe) sendCmd(mc, "p disband");             }
            default -> {
                if ((cmd.matches("[fm][1-7]") || cmd.equals("e")) && FishSettings.pcJoinFloor) handleJoinInstance(cmd, mc);
                else if (cmd.matches("t[1-5]") && FishSettings.pcJoinFloor) handleKuudra(cmd, mc);
            }
        }
    }

    /** Sends a command to the server after a short delay to avoid rate-limiting. */
    private static void sendCmd(MinecraftClient mc, String command) {
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS)
            .execute(() -> mc.execute(() -> {
                if (mc.getNetworkHandler() != null)
                    mc.getNetworkHandler().sendChatCommand(command);
            }));
    }

    // ─── command dispatcher ───────────────────────────────────────────────────

    public static boolean handleCommand(String fullCmd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return false;

        // Split "rtca PlayerName" → cmd="rtca", arg="PlayerName" (or null)
        String[] parts = fullCmd.split("\\s+", 2);
        String cmd = parts[0];
        String arg = parts.length > 1 ? parts[1] : null;
        // If no arg, default to local player name
        String localName = mc.player != null ? mc.player.getName().getString() : null;
        String target = arg != null ? arg : localName;

        switch (cmd) {
            case "ai": case "allinv":
                if (!FishSettings.pcAllinvite || target == null) return false;
                sendCmd(mc, "p settings allinvite");
                return true;
            case "pb":
                if (!FishSettings.pcPb || target == null) return false;
                sendPb(mc);
                return true;
            case "cata":
                if (!FishSettings.pcCata || target == null) return false;
                runCataForPlayer(mc, target);
                return true;
            case "rtca":
                if (!FishSettings.pcRtca || target == null) return false;
                runRtcaForPlayer(mc, target);
                return true;
            case "fps":
                if (!FishSettings.pcFps || target == null) return false;
                sendFps(mc);
                return true;
            case "tps":
                if (!FishSettings.pcTps || target == null) return false;
                sendTps(mc);
                return true;
            case "ping":
                if (!FishSettings.pcPing || target == null) return false;
                sendPing(mc);
                return true;
            case "d":
                if (!FishSettings.pcDisband || target == null) return false;
                sendCmd(mc, "p disband");
                return true;
        }

        if (cmd.equals("e") || cmd.matches("[fm][1-7]")) {
            if (!FishSettings.pcJoinFloor) return false;
            handleJoinInstance(cmd, mc);
            return true;
        }
        if (cmd.matches("t[1-5]")) {
            if (!FishSettings.pcJoinFloor) return false;
            handleKuudra(cmd, mc);
            return true;
        }
        return false;
    }

    // ─── command implementations ──────────────────────────────────────────────

    // ─── party-triggered lookups (by IGN) ────────────────────────────────────

    private static void runRtcaForPlayer(MinecraftClient mc, String ign) {
        HypixelApi.getByName(mc, ign, data -> buildAndSendRtca(mc, data, ign));
    }

    private static void runCataForPlayer(MinecraftClient mc, String ign) {
        HypixelApi.getByName(mc, ign, data -> {
            String level  = HypixelApi.formatLevel(data.cataXp);
            String toNext = HypixelApi.xpToNextLevel(data.cataXp);
            sendCmd(mc, "pc " + ign + " Cata: " + level + " | " + toNext + " XP to next");
        });
    }

    private static void runPbForPlayer(MinecraftClient mc, String ign) {
        HypixelApi.getByName(mc, ign, data -> {
            String m7pb = data.masterPbs[7];
            sendCmd(mc, "pc " + ign + " M7 PB: " + (m7pb != null ? m7pb : "N/A"));
        });
    }

    private static void buildAndSendRtca(MinecraftClient mc, HypixelApi.DungeonData data, String ign) {
        long xpPerRun = Math.max(1, FishSettings.rtcaClassXpPerRun);
        long passiveXp = (long)(xpPerRun * 0.2);

        String[] classes    = {"healer", "mage", "berserk", "archer", "tank"};
        String[] shortNames = {"H",      "M",    "B",       "A",      "T"   };

        long[] xpLeft = new long[5];
        for (int i = 0; i < 5; i++) {
            xpLeft[i] = Math.max(0L, HypixelApi.XP_FOR_50 - data.classXp.getOrDefault(classes[i], 0L));
        }

        long[] runsPerClass = new long[5];
        for (int guard = 0; guard < 2_000_000; guard++) {
            int pick = 0;
            for (int i = 1; i < 5; i++) if (xpLeft[i] > xpLeft[pick]) pick = i;
            if (xpLeft[pick] <= 0) break;
            runsPerClass[pick]++;
            for (int i = 0; i < 5; i++)
                xpLeft[i] = Math.max(0L, xpLeft[i] - (i == pick ? xpPerRun : passiveXp));
        }

        StringBuilder sb = new StringBuilder(ign + " RTCA: ");
        for (int i = 0; i < 5; i++) {
            sb.append(shortNames[i]).append(": ");
            if (runsPerClass[i] == 0)           sb.append("done");
            else if (runsPerClass[i] >= 1_000)  sb.append(String.format("%.1fk", runsPerClass[i] / 1_000.0));
            else                                sb.append(runsPerClass[i]);
            if (i < 4) sb.append(" | ");
        }
        String out = sb.toString();
        sendCmd(mc, "pc " + out);
    }

    // ─── local command implementations ───────────────────────────────────────

    private static void sendPb(MinecraftClient mc) {
        HypixelApi.getPlayerDungeonData(mc, data -> {
            String m7pb = data.masterPbs[7];
            sendCmd(mc, "pc M7 PB: " + (m7pb != null ? m7pb : "N/A"));
        });
    }

    private static void handleJoinInstance(String cmd, MinecraftClient mc) {
        long elapsed = System.currentTimeMillis() - dungeonEnteredAt;
        if (elapsed < 30_000L) {
            long rem = (30_000L - elapsed) / 1_000L + 1L;
            sendCmd(mc, "pc Wait " + rem + "s before joining.");
            return;
        }
        String floor;
        if (cmd.equals("e")) {
            floor = "catacombs_entrance";
        } else {
            char type = cmd.charAt(0);
            int  num  = cmd.charAt(1) - '0';
            floor = (type == 'm' ? "master_" : "") + "catacombs_floor_" + NUM_WORDS[num - 1];
        }
        String joinCmd = "joininstance " + floor;
        Misc.addChatMessage(Text.literal("§7[FM] Sending: /" + joinCmd));
        sendCmd(mc, joinCmd);
    }

    private static void handleKuudra(String cmd, MinecraftClient mc) {
        long elapsed = System.currentTimeMillis() - dungeonEnteredAt;
        if (elapsed < 30_000L) {
            long rem = (30_000L - elapsed) / 1_000L + 1L;
            sendCmd(mc, "pc Wait " + rem + "s before joining Kuudra.");
            return;
        }
        int tier = cmd.charAt(1) - '1'; // t1=0 … t5=4
        String joinCmd = "joininstance kuudra_" + KUUDRA_TIERS[tier];
        Misc.addChatMessage(Text.literal("§7[FM] Sending: /" + joinCmd));
        sendCmd(mc, joinCmd);
    }

    private static void sendFps(MinecraftClient mc) {
        int fps = mc.getCurrentFps();
        sendCmd(mc, "pc FPS: " + fps);
    }

    private static void sendTps(MinecraftClient mc) {
        int filled = Math.min(tickIdx, TICK_TIMES.length);
        if (filled == 0) {
            sendCmd(mc, "pc TPS: N/A");
            return;
        }
        long sum = 0;
        for (int i = 0; i < filled; i++) sum += TICK_TIMES[i];
        double avgMs = (double) sum / filled;
        double tps = Math.min(20.0, 1000.0 / avgMs);
        String formatted = String.format("%.1f", tps);
        sendCmd(mc, "pc TPS: " + formatted);
    }

    private static void sendPing(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        if (entry == null) return;
        int ping = entry.getLatency();
        String pingStr = ping >= 0 ? ping + "ms" : "N/A";
        sendCmd(mc, "pc Ping: " + pingStr);
    }


}
