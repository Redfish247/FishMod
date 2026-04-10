package blade.addon.features.dungeon;

import blade.addon.utils.HypixelApi;
import blade.addon.utils.Location;
import blade.addon.utils.config.values.FishSettings;
import blade.addon.utils.Misc;
import blade.addon.utils.dungeon.RunHistory;
import blade.addon.utils.events.Events;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles party commands typed by the local player:
 *   .ai / .allinv  — /party settings allinvite
 *   .pb            — send M7 personal best to party chat
 *   .cata          — send cata level (requires API key)
 *   .rtca          — send runs-to-class-50 (requires API key)
 *   .f1-.f7        — /joininstance catacombs_floor_X  (after 30s in dungeon)
 *   .m1-.m7        — /joininstance master_catacombs_floor_X
 */
public class PartyCommandHandler {

    private static final Pattern DEFEAT_PATTERN = Pattern.compile(
            "^\\s*☠ Defeated .+ in ((?:\\d+[hms] ?)+?)\\s*(?:\\(NEW RECORD!\\))?\\s*$");

    // Matches party chat commands: "Party > [RANK] PlayerName: .rtca" etc.
    // Group 1 = IGN, Group 2 = command name
    private static final Pattern PARTY_RTCA = Pattern.compile(
            "^Party > (?:\\[[^\\]]+\\] )*(\\w+): [.!](rtca|cata|pb)\\s*$");

    private static final String[] NUM_WORDS =
            {"one", "two", "three", "four", "five", "six", "seven"};

    private static final String PB_PATH  = "config/fishmod-pb.json";
    private static final Gson   GSON     = new GsonBuilder().setPrettyPrinting().create();

    // floor key (e.g. "M7") → best run time in seconds
    private static final Map<String, Long> personalBests = new HashMap<>();

    private static long dungeonEnteredAt = 0;

    // TPS tracking — rolling average of last 20 client tick intervals
    private static final long[] TICK_TIMES = new long[20];
    private static int tickIdx = 0;
    private static long lastTickMs = -1;

    static { loadPbs(); }

    public static void init() {
        // Track when the player enters a dungeon (for 30s joininstance guard)
        Events.ON_LOCATION_CHANGE.register(loc -> {
            if (loc == Location.DUNGEON) dungeonEnteredAt = System.currentTimeMillis();
            return false;
        });

        // Track run completions for PB storage
        Events.ON_GAME_MESSAGE.register(msg -> {
            if (!Location.inDungeon()) return false;
            Matcher m = DEFEAT_PATTERN.matcher(msg.getString());
            if (m.find()) {
                double secs = parseTime(m.group(1).trim());
                if (secs > 0) {
                    String floor = normalizeFloor(scanTabFloor(MinecraftClient.getInstance()));
                    if (!floor.isEmpty()) {
                        Long prev = personalBests.get(floor);
                        if (prev == null || (long) secs < prev) {
                            personalBests.put(floor, (long) secs);
                            savePbs();
                        }
                    }
                }
            }
            return false;
        });

        // TPS tick timer
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.currentTimeMillis();
            if (lastTickMs > 0) {
                TICK_TIMES[tickIdx % TICK_TIMES.length] = now - lastTickMs;
                tickIdx++;
            }
            lastTickMs = now;
        });

        // Intercept .cmd and !cmd typed directly in chat (non-/pc mode)
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String t = message.trim();
            if (!t.startsWith(".") && !t.startsWith("!")) return true;
            String cmd = t.substring(1).toLowerCase().trim();
            return !handleCommand(cmd);
        });
    }

    /** Called from ChatHudMixin for every party message — catches all packet types. */
    public static void onPartyCommand(String ign, String cmd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;
        switch (cmd) {
            case "rtca"               -> { if (FishSettings.pcRtca)      runRtcaForPlayer(mc, ign); }
            case "cata"               -> { if (FishSettings.pcCata)      runCataForPlayer(mc, ign); }
            case "pb"                 -> { if (FishSettings.pcPb)        runPbForPlayer(mc, ign);   }
            case "fps"                -> { if (FishSettings.pcFps)       sendFps(mc);               }
            case "tps"                -> { if (FishSettings.pcTps)       sendTps(mc);               }
            case "ping"               -> { if (FishSettings.pcPing)      sendPing(mc);              }
            default                   -> { if (cmd.matches("[fm][1-7]") && FishSettings.pcJoinFloor) handleJoinInstance(cmd, mc); }
        }
    }

    // ─── command dispatcher ───────────────────────────────────────────────────

    private static boolean handleCommand(String fullCmd) {
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
                if (!FishSettings.pcAllinvite) return false;
                mc.send(() -> mc.getNetworkHandler().sendChatCommand("party settings allinvite"));
                return true;
            case "pb":
                if (!FishSettings.pcPb) return false;
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
                if (!FishSettings.pcFps) return false;
                sendFps(mc);
                return true;
            case "tps":
                if (!FishSettings.pcTps) return false;
                sendTps(mc);
                return true;
            case "ping":
                if (!FishSettings.pcPing) return false;
                sendPing(mc);
                return true;
        }

        if (cmd.matches("[fm][1-7]")) {
            if (!FishSettings.pcJoinFloor) return false;
            handleJoinInstance(cmd, mc);
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
            mc.send(() -> mc.getNetworkHandler().sendChatCommand("pc " + ign + " Cata: " + level + " | " + toNext + " XP to next"));
        });
    }

    private static void runPbForPlayer(MinecraftClient mc, String ign) {
        // PB is tracked locally — if we have one for the local player only
        String localName = mc.player != null ? mc.player.getName().getString() : "";
        if (ign.equalsIgnoreCase(localName)) {
            sendPb(mc);
        } else {
            Misc.addChatMessage(Text.literal("§cPBs are only tracked for yourself."));
        }
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
        mc.send(() -> mc.getNetworkHandler().sendChatCommand("pc " + out));
    }

    // ─── local command implementations ───────────────────────────────────────

    private static void sendPb(MinecraftClient mc) {
        Long pb = personalBests.get("M7");
        if (pb == null) {
            if (personalBests.isEmpty()) {
                Misc.addChatMessage(Text.literal("§cNo PBs tracked yet — complete a run first."));
            } else {
                // Show all stored PBs locally
                personalBests.forEach((floor, t) ->
                        Misc.addChatMessage(Text.literal("§e" + floor + " PB: §f" + formatTime(t))));
            }
            return;
        }
        long finalPb = pb;
        mc.send(() -> mc.getNetworkHandler().sendChatCommand("pc M7 PB: " + formatTime(finalPb)));
    }

    private static void sendCata(MinecraftClient mc) {
        String ign = mc.player != null ? mc.player.getName().getString() : "?";
        HypixelApi.getPlayerDungeonData(mc, data -> {
            String level  = HypixelApi.formatLevel(data.cataXp);
            String toNext = HypixelApi.xpToNextLevel(data.cataXp);
            mc.send(() -> mc.getNetworkHandler().sendChatCommand("pc " + ign + " Cata: " + level + " | " + toNext + " XP to next"));
        });
    }

    private static void sendRtca(MinecraftClient mc) {
        String ign = mc.player != null ? mc.player.getName().getString() : "?";
        HypixelApi.getPlayerDungeonData(mc, data -> buildAndSendRtca(mc, data, ign));
    }

    private static void handleJoinInstance(String cmd, MinecraftClient mc) {
        if (!Location.inDungeon()) {
            mc.send(() -> mc.getNetworkHandler().sendChatCommand("pc Not in a dungeon."));
            return;
        }
        long elapsed = System.currentTimeMillis() - dungeonEnteredAt;
        if (elapsed < 30_000L) {
            long rem = (30_000L - elapsed) / 1_000L + 1L;
            mc.send(() -> mc.getNetworkHandler().sendChatCommand("pc Wait another " + rem + "s before joining."));
            return;
        }
        char type    = cmd.charAt(0);           // 'f' or 'm'
        int  num     = cmd.charAt(1) - '0';     // 1-7
        String floor = (type == 'm' ? "master_" : "") + "catacombs_floor_" + NUM_WORDS[num - 1];
        String joinCmd = "joininstance " + floor;
        Misc.addChatMessage(Text.literal("§7[FM] Sending: /" + joinCmd));
        mc.send(() -> mc.getNetworkHandler().sendChatCommand(joinCmd));
    }

    private static void sendFps(MinecraftClient mc) {
        int fps = mc.getCurrentFps();
        mc.send(() -> mc.getNetworkHandler().sendChatCommand("pc FPS: " + fps));
    }

    private static void sendTps(MinecraftClient mc) {
        int filled = Math.min(tickIdx, TICK_TIMES.length);
        if (filled == 0) {
            mc.send(() -> mc.getNetworkHandler().sendChatCommand("pc TPS: N/A"));
            return;
        }
        long sum = 0;
        for (int i = 0; i < filled; i++) sum += TICK_TIMES[i];
        double avgMs = (double) sum / filled;
        double tps = Math.min(20.0, 1000.0 / avgMs);
        String formatted = String.format("%.1f", tps);
        mc.send(() -> mc.getNetworkHandler().sendChatCommand("pc TPS: " + formatted));
    }

    private static void sendPing(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        if (entry == null) return;
        int ping = entry.getLatency();
        mc.send(() -> mc.getNetworkHandler().sendChatCommand("pc Ping: " + ping + "ms"));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** Scans the tab list for the ⏣ The Catacombs (…) line and returns the floor name inside. */
    private static String scanTabFloor(MinecraftClient mc) {
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        if (handler == null) return "";
        for (PlayerListEntry entry : handler.getPlayerList()) {
            if (entry.getDisplayName() == null) continue;
            String line = entry.getDisplayName().getString().replaceAll("§.", "").trim();
            if (line.startsWith("⏣ The Catacombs")) {
                int s = line.indexOf('('), e = line.indexOf(')');
                if (s >= 0 && e > s) return line.substring(s + 1, e);
            }
        }
        return "";
    }

    /**
     * Converts a tab-list floor string to a short key like "M7" or "F4".
     * Input examples: "Master Mode Floor VII", "Floor IV"
     */
    private static String normalizeFloor(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        raw = raw.trim();
        boolean master = raw.startsWith("Master Mode");
        String[] words = raw.split(" ");
        String last = words[words.length - 1];
        int num = romanToInt(last);
        if (num <= 0) return "";
        return (master ? "M" : "F") + num;
    }

    private static int romanToInt(String r) {
        return switch (r.toUpperCase()) {
            case "I"    -> 1;
            case "II"   -> 2;
            case "III"  -> 3;
            case "IV"   -> 4;
            case "V"    -> 5;
            case "VI"   -> 6;
            case "VII"  -> 7;
            default     -> -1;
        };
    }

    /** Parses a time string like "05m 20s", "1h 05m 30s", "45s", "02m" into total seconds. */
    private static double parseTime(String t) {
        double total = 0;
        Matcher m = Pattern.compile("(\\d+)([hms])").matcher(t);
        while (m.find()) {
            int v = Integer.parseInt(m.group(1));
            char u = m.group(2).charAt(0);
            if (u == 'h')      total += v * 3600.0;
            else if (u == 'm') total += v * 60.0;
            else               total += v;
        }
        return total;
    }

    /** Formats seconds as "5m 20s" or "45s". */
    private static String formatTime(long secs) {
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        if (h > 0) return h + "h " + String.format("%02d", m) + "m " + String.format("%02d", s) + "s";
        if (m > 0) return m + "m " + String.format("%02d", s) + "s";
        return s + "s";
    }

    // ─── persistence ─────────────────────────────────────────────────────────

    private static void loadPbs() {
        File f = new File(PB_PATH);
        if (!f.exists()) return;
        try (Reader r = new FileReader(f)) {
            Type type = new TypeToken<Map<String, Long>>() {}.getType();
            Map<String, Long> loaded = GSON.fromJson(r, type);
            if (loaded != null) personalBests.putAll(loaded);
        } catch (Exception ignored) {}
    }

    private static void savePbs() {
        try {
            File f = new File(PB_PATH);
            f.getParentFile().mkdirs();
            try (Writer w = new FileWriter(f)) { GSON.toJson(personalBests, w); }
        } catch (Exception ignored) {}
    }
}
