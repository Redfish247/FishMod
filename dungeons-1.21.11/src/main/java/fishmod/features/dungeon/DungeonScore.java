package fishmod.features.dungeon;

import fishmod.features.FishHudEditor;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live S+ tracker using Odin's exact scoring formula.
 * Data sources: tab list (puzzles/secrets/rooms/crypts/deaths/time), sidebar (cleared %, floor),
 * chat (mimic/prince/blood-door/death messages). No map scanning required for the score itself.
 */
public class DungeonScore {

    // ── Floor + required-secret-percent table (from Odin DungeonEnums.Floor) ──
    private enum Floor {
        E(0.3f), F1(0.3f), F2(0.4f), F3(0.5f), F4(0.6f), F5(0.7f), F6(0.85f), F7(1f),
        M1(1f), M2(1f), M3(1f), M4(1f), M5(1f), M6(1f), M7(1f);
        final float requiredPercentage;
        Floor(float p) { requiredPercentage = p; }
        int floorNumber() {
            return switch (this) { case E -> 0; case F1, M1 -> 1; case F2, M2 -> 2; case F3, M3 -> 3;
                case F4, M4 -> 4; case F5, M5 -> 5; case F6, M6 -> 6; case F7, M7 -> 7; };
        }
    }

    // ── Tab list regexes (port of Odin's DungeonListener) ──
    private static final Pattern SECRET_PCT_PAT      = Pattern.compile("^\\s*Secrets Found:\\s*([\\d.]+)%$");
    private static final Pattern SECRET_COUNT_PAT    = Pattern.compile("^\\s*Secrets Found:\\s*(\\d+)$");
    private static final Pattern COMPLETED_ROOMS_PAT = Pattern.compile("^\\s*Completed Rooms:\\s*(\\d+)$");
    private static final Pattern PUZZLE_COUNT_PAT    = Pattern.compile("^Puzzles:\\s*\\((\\d+)\\)$");
    private static final Pattern PUZZLE_STATUS_PAT   = Pattern.compile("^\\s*([\\w?]+(?: \\w+)*|\\?\\?\\?):\\s*\\[([✖✔✦])\\]");
    private static final Pattern DEATHS_PAT          = Pattern.compile("^Team Deaths:\\s*(\\d+)$");
    private static final Pattern CRYPTS_PAT          = Pattern.compile("^\\s*Crypts:\\s*(\\d+)$");
    private static final Pattern TIME_PAT            = Pattern.compile("^\\s*Time:\\s*((?:\\d+h ?)?(?:\\d+m ?)?\\d+s)$");
    private static final Pattern CLEARED_SIDEBAR_PAT = Pattern.compile("^Cleared:\\s*(\\d+)% \\(\\d+\\)$");
    private static final Pattern FLOOR_PAT           = Pattern.compile("The Catacombs \\((\\w+)\\)");

    // ── Chat regexes ──
    private static final Pattern DEATH_CHAT      = Pattern.compile("☠ \\S+ (?:was|were) killed by|☠ \\S+ (?:died|quit)|and became a ghost");
    private static final Pattern EXPECTING_BLOOD = Pattern.compile("^\\[BOSS\\] The Watcher: You have proven yourself");
    private static final Pattern PARTY_MSG       = Pattern.compile("^Party > .*?: (.+)$");
    private static final Pattern MIMIC_CHAT      = Pattern.compile("(?i)mimic (?:killed|slain|dead)|killed a mimic|\\$skytils-dungeon-score-mimic\\$");
    private static final Pattern PRINCE_CHAT     = Pattern.compile("(?i)prince (?:killed|slain|dead)|killed the prince|\\$skytils-dungeon-score-prince\\$");

    // ── State ──
    private static Floor currentFloor = null;
    private static int secretCount = 0;
    private static float secretsPercent = 0f;
    private static int completedRooms = 0;
    private static int percentCleared = 0;
    private static int puzzleCount = 0;
    private static int puzzlesCompleted = 0;
    private static int deathCount = 0;
    private static int cryptCount = 0;
    private static boolean mimicKilled = false;
    private static boolean princeKilled = false;
    private static boolean bloodDone = false;
    private static boolean expectingBloodUpdate = false;
    private static long runStartMs = -1;
    private static boolean alerted270 = false;
    private static boolean alerted300 = false;
    private static boolean alertedMissing = false;

    /** Total puzzle count for this run, parsed from the tab list's "Puzzles: (N)" line. 0 if not seen yet. */
    public static int getPuzzleCount() {
        return puzzleCount;
    }

    /** Secrets found so far this run, from the tab list's secret-percent/count lines. */
    public static int getSecretCount() {
        return secretCount;
    }

    /** Estimated total secrets in the dungeon (back-computed from found-count and percent). 0 if not known yet. */
    public static int getTotalSecrets() {
        return totalSecrets();
    }

    public static void init() {
        FishHudEditor.register("Dungeon Score",
                () -> FishSettings.dungeonScoreHudX, v -> FishSettings.dungeonScoreHudX = v,
                () -> FishSettings.dungeonScoreHudY, v -> FishSettings.dungeonScoreHudY = v,
                160, 14 * 3,
                () -> FishSettings.dungeonScoreScale, v -> FishSettings.dungeonScoreScale = v,
                () -> FishSettings.dungeonScoreEnabled
                        && Location.getCurrentLocation() == Location.DUNGEON
                        && !fishmod.utils.dungeon.Phase.inBoss());

        Events.ON_LOCATION_CHANGE.register(loc -> {
            if (loc == Location.DUNGEON) {
                resetRun();
                runStartMs = System.currentTimeMillis();
            }
            return false;
        });

        Events.ON_GAME_MESSAGE.register(message -> {
            if (!FishSettings.dungeonScoreEnabled) return false;
            String s = message.getString().replaceAll("§.", "");
            if (DEATH_CHAT.matcher(s).find()) deathCount++;
            if (EXPECTING_BLOOD.matcher(s).find()) expectingBloodUpdate = true;
            Matcher fm = FLOOR_PAT.matcher(s);
            if (fm.find()) {
                try { currentFloor = Floor.valueOf(fm.group(1)); } catch (IllegalArgumentException ignored) {}
            }
            Matcher pm = PARTY_MSG.matcher(s);
            if (pm.find()) {
                String body = pm.group(1).toLowerCase();
                if (MIMIC_CHAT.matcher(body).find() && currentFloor != null && (currentFloor.floorNumber() == 6 || currentFloor.floorNumber() == 7)) mimicKilled = true;
                if (PRINCE_CHAT.matcher(body).find()) princeKilled = true;
            } else {
                // Local "Mimic dead!" type messages
                if (MIMIC_CHAT.matcher(s).find() && currentFloor != null && (currentFloor.floorNumber() == 6 || currentFloor.floorNumber() == 7)) mimicKilled = true;
                if (PRINCE_CHAT.matcher(s).find()) princeKilled = true;
            }
            return false;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.dungeonScoreEnabled) return;
            if (client.player == null || client.world == null) return;
            if (Location.getCurrentLocation() != Location.DUNGEON) return;
            scanTick++;
            if (scanTick < 10) return;
            scanTick = 0;
            scanTabList(client);
            scanSidebar(client);
            checkScoreAlerts();

            if (!alertedMissing && FishSettings.dungeonScoreMissingMsg
                    && runStartMs > 0 && System.currentTimeMillis() - runStartMs >= 60_000
                    && !fishmod.utils.dungeon.Phase.inBoss()) {
                alertedMissing = true;
                sendMissingScoreMessage();
            }
        });
    }

    private static int scanTick = 0;
    private static final Map<String, String> puzzleStatuses = new HashMap<>();

    private static void resetRun() {
        currentFloor = null;
        secretCount = 0;
        secretsPercent = 0f;
        completedRooms = 0;
        percentCleared = 0;
        puzzleCount = 0;
        puzzlesCompleted = 0;
        deathCount = 0;
        cryptCount = 0;
        mimicKilled = false;
        princeKilled = false;
        bloodDone = false;
        expectingBloodUpdate = false;
        runStartMs = -1;
        alerted270 = false;
        alerted300 = false;
        alertedMissing = false;
        puzzleStatuses.clear();
    }

    private static void scanTabList(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null) return;
        int newPuzzlesCompleted = 0;
        for (var entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getDisplayName() == null) continue;
            String line = entry.getDisplayName().getString().replaceAll("§.", "");

            Matcher m;
            if ((m = SECRET_PCT_PAT.matcher(line)).find())      try { secretsPercent  = Float.parseFloat(m.group(1)); } catch (NumberFormatException ignored) {}
            if ((m = SECRET_COUNT_PAT.matcher(line)).find())    try { secretCount     = Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
            if ((m = COMPLETED_ROOMS_PAT.matcher(line)).find()) try { completedRooms  = Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
            if ((m = PUZZLE_COUNT_PAT.matcher(line)).find())    try { puzzleCount     = Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
            if ((m = DEATHS_PAT.matcher(line)).find())          try { deathCount      = Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
            if ((m = CRYPTS_PAT.matcher(line)).find())          try { cryptCount      = Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}

            m = PUZZLE_STATUS_PAT.matcher(line);
            if (m.find() && !m.group(1).equals("???")) {
                String status = m.group(2);
                if (status.equals("✔")) newPuzzlesCompleted++;
            }
        }
        puzzlesCompleted = newPuzzlesCompleted;
    }

    private static void scanSidebar(MinecraftClient mc) {
        var sb = mc.world.getScoreboard();
        var sidebar = sb.getObjectiveForSlot(net.minecraft.scoreboard.ScoreboardDisplaySlot.SIDEBAR);
        if (sidebar == null) return;
        for (var entry : sb.getScoreboardEntries(sidebar)) {
            var team = sb.getScoreHolderTeam(entry.owner());
            String raw = team != null
                    ? team.getPrefix().getString() + entry.owner() + team.getSuffix().getString()
                    : entry.name().getString();
            String line = raw.replaceAll("§.", "").replaceAll("[^\\x20-\\x7E%()]", "").trim();
            Matcher m = CLEARED_SIDEBAR_PAT.matcher(line);
            if (m.find()) {
                try {
                    int newPct = Integer.parseInt(m.group(1));
                    if (percentCleared != newPct && expectingBloodUpdate) {
                        bloodDone = true;
                        expectingBloodUpdate = false;
                    }
                    percentCleared = newPct;
                } catch (NumberFormatException ignored) {}
            }
            m = FLOOR_PAT.matcher(line);
            if (m.find()) {
                try { currentFloor = Floor.valueOf(m.group(1)); } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    /** Odin's formula: score = exploration + skill(20..100) + 100 + bonus. */
    private static int totalSecrets() {
        if (secretCount == 0 || secretsPercent == 0f) return 0;
        return (int) Math.floor(100f / secretsPercent * secretCount + 0.5);
    }
    private static int totalRooms() {
        if (completedRooms == 0 || percentCleared == 0) return 0;
        return (int) Math.floor((completedRooms / (percentCleared * 0.01f)) + 0.4f);
    }
    private static int getBonusScore() {
        int s = Math.min(cryptCount, 5);
        if (mimicKilled) s += 2;
        if (princeKilled) s += 1;
        if (fishmod.utils.MayorApi.isPaulDungeonBonusActive()) s += 10;
        return s;
    }
    private static int inBoss() { return 0; } // approximate: treat as not-in-boss until phase tracking added

    public static int computeScore() {
        if (currentFloor == null) return 0;
        int total = totalRooms() != 0 ? totalRooms() : 36;
        int completed = completedRooms + (bloodDone ? 0 : 1) + (inBoss() == 1 ? 0 : 1);

        int exploration;
        int ts = totalSecrets();
        int secretScore = 0;
        if (ts > 0) {
            secretScore = (int) Math.floor(secretCount / (ts * (double) currentFloor.requiredPercentage) * 40.0);
            secretScore = Math.max(0, Math.min(40, secretScore));
        }
        int roomScore = Math.max(0, Math.min(60, (int) Math.floor(completed / (float) total * 60f)));
        exploration = secretScore + roomScore;

        int skillRooms = Math.max(0, Math.min(80, (int) Math.floor(completed / (float) total * 80f)));
        int puzzlePenalty = (puzzleCount - puzzlesCompleted) * 10;
        int deathPenalty = Math.max(0, deathCount * 2 - 1);
        int skill = Math.max(20, Math.min(100, 20 + skillRooms - puzzlePenalty - deathPenalty));

        int bonus = getBonusScore();
        return exploration + skill + 100 + bonus;
    }

    /**
     * One-minute check-in: projects the end-of-run score assuming a full clear (all rooms,
     * all puzzles) with the secrets and bonuses collected so far, then breaks the gap to 300
     * down into the remaining bonus sources plus however many extra secrets cover the rest.
     */
    /** Projected end-of-run score assuming a full clear (all rooms, all puzzles) with current secrets/bonuses. */
    private static int projectedFullClearScore() {
        int ts = totalSecrets();
        double reqPct = currentFloor != null ? currentFloor.requiredPercentage : 1.0;
        int secretScore = 0;
        if (ts > 0) secretScore = Math.max(0, Math.min(40, (int) Math.floor(secretCount / (ts * reqPct) * 40.0)));
        int deathPenalty = Math.max(0, deathCount * 2 - 1);
        int skill = Math.max(20, Math.min(100, 100 - deathPenalty));
        return 60 + secretScore + skill + 100 + getBonusScore();
    }

    private static void sendMissingScoreMessage() {
        int ts = totalSecrets();
        double reqPct = currentFloor != null ? currentFloor.requiredPercentage : 1.0;
        int projected = projectedFullClearScore();
        int missing = 300 - projected;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;

        if (missing <= 0) {
            mc.getNetworkHandler().sendChatCommand("pc On pace for 300! (projected " + projected + " on full clear)");
            return;
        }

        java.util.List<String> parts = new java.util.ArrayList<>();
        int remaining = missing;

        if (!princeKilled && remaining > 0) {
            parts.add("1 Prince [1 score max 1]");
            remaining -= 1;
        }
        int cryptsAvail = 5 - Math.min(cryptCount, 5);
        if (cryptsAvail > 0 && remaining > 0) {
            int take = Math.min(cryptsAvail, remaining);
            parts.add(take + " Crypt" + (take == 1 ? "" : "s") + " [1 each max 5]");
            remaining -= take;
        }
        boolean mimicFloor = currentFloor != null && (currentFloor.floorNumber() == 6 || currentFloor.floorNumber() == 7);
        if (!mimicKilled && mimicFloor && remaining > 0) {
            parts.add("1 Mimic [2 score max 1]");
            remaining -= 2;
        }
        if (remaining > 0) {
            if (ts > 0) {
                double perSecret = 40.0 / (ts * reqPct);
                int need = (int) Math.ceil(remaining / perSecret);
                parts.add(need + " Secret" + (need == 1 ? "" : "s") + " [" + remaining + " score]");
            } else {
                parts.add("Secrets [" + remaining + " score]");
            }
        }

        mc.getNetworkHandler().sendChatCommand("pc " + missing + " Score Missing (" + String.join(", ", parts) + ")");
    }

    /** Fires the customizable 270/300 alerts once per run each. Jumping straight past 270 skips its alert. */
    private static void checkScoreAlerts() {
        int score = computeScore();
        if (!alerted270 && score >= 270 && score < 300) {
            alerted270 = true;
            fireScoreAlert(FishSettings.score270TitleEnabled, FishSettings.score270ChatEnabled, FishSettings.score270Text);
        }
        if (!alerted300 && score >= 300) {
            alerted270 = true;
            alerted300 = true;
            fireScoreAlert(FishSettings.score300TitleEnabled, FishSettings.score300ChatEnabled, FishSettings.score300Text);
        }
    }

    private static void fireScoreAlert(boolean title, boolean chat, String text) {
        var msg = net.minecraft.text.Text.literal(text.replace('&', '§'));
        if (title) fishmod.utils.Misc.setTitle(msg);
        if (chat) fishmod.utils.Misc.addChatMessage(msg);
    }

    private static String colorizeScore(int s) {
        if (s < 270) return "§c" + s;
        if (s < 300) return "§e" + s;
        return "§a" + s;
    }
    private static String colorizeCrypts(int c) {
        if (c < 3) return "§c" + c;
        if (c < 5) return "§e" + c;
        return "§a" + c;
    }
    private static String colorizeDeaths(int d) {
        int floor = currentFloor != null ? currentFloor.floorNumber() : 7;
        if (d == 0) return "§a0";
        if (d <= (floor < 6 ? 2 : 3)) return "§e" + d;
        if (d == (floor < 6 ? 3 : 4)) return "§c" + d;
        return "§4" + d;
    }
    private static String grade(int s) {
        if (s >= 300) return "§a§lS+";
        if (s >= 270) return "§eS";
        if (s >= 230) return "§6A";
        if (s >= 160) return "§cB";
        if (s >= 100) return "§4C";
        return "§4D";
    }

    public static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        if (!FishSettings.dungeonScoreEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;
        if (Location.getCurrentLocation() != Location.DUNGEON) return;
        if (fishmod.utils.dungeon.Phase.inBoss()) return;

        int score = computeScore();
        int ts = totalSecrets();
        int needed = currentFloor != null && ts > 0
                ? Math.max(0, (int) Math.ceil(ts * currentFloor.requiredPercentage))
                : 0;

        String tail = FishSettings.dungeonScoreShowLeft
                ? "§7-§c" + Math.max(0, 300 - projectedFullClearScore()) + "§7 left"
                : "§7-§c" + (ts > 0 ? ts : "?");
        String secretsLine = "§7Secrets: §b" + secretCount + "§7-§e" + needed + tail
                + "   §7Score: " + colorizeScore(score);
        String statsLine = "§7Deaths: " + colorizeDeaths(deathCount)
                + "  §7M:" + (mimicKilled ? "§a✔" : "§c✘")
                + " §7P:" + (princeKilled ? "§a✔" : "§c✘")
                + "  §7Crypts: " + colorizeCrypts(Math.min(cryptCount, 5))
                + (fishmod.utils.MayorApi.isPaulDungeonBonusActive() ? " §6Paul" : "");

        String[] lines = { secretsLine, statsLine};

        int x = FishSettings.dungeonScoreHudX;
        int y = FishSettings.dungeonScoreHudY;
        int lh = Constants.TEXT_HEIGHT + 2;
        float sc = (float) FishSettings.dungeonScoreScale;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.drawText(mc.textRenderer, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();
    }
}
