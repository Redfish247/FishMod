package fishmod.features;

import fishmod.utils.Constants;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill XP tracker — reads the action-bar skill XP as it ticks up and shows
 * level, progress %, XP needed to next level, XP/hr, session gained, and time.
 * Auto-pauses when XP stops coming in.
 */
public class SkillTracker {

    // Cumulative XP required to REACH each skill level (index = level, 0..60). Standard Hypixel table.
    private static final long[] CUM = {
        0L,50L,175L,375L,675L,1175L,1925L,2925L,4425L,6425L,
        9925L,14925L,22425L,32425L,47425L,67425L,97425L,147425L,222425L,322425L,
        522425L,822425L,1222425L,1722425L,2322425L,3022425L,3822425L,4722425L,5722425L,6822425L,
        8022425L,9322425L,10722425L,12222425L,13822425L,15522425L,17322425L,19222425L,21222425L,23322425L,
        25522425L,27822425L,30222425L,32722425L,35322425L,38072425L,40972425L,44072425L,47472425L,51172425L,
        55172425L,59472425L,64072425L,68972425L,74172425L,79672425L,85472425L,91572425L,97972425L,104672425L,111672425L
    };

    // Cumulative XP to reach the top of the standard table (level 60).
    private static final long MAX_CUM = CUM[CUM.length - 1];
    // Overflow leveling (Hypixel "overflow skills", same constants every mod uses):
    // first overflow level costs 7,600,000; slope is 600,000 and doubles every 10 levels.
    private static final long OVERFLOW_BASE  = 7_600_000L;
    private static final long OVERFLOW_SLOPE = 600_000L;

    private static final DecimalFormat NUM = new DecimalFormat("#,###");
    private static final long WINDOW_MS = 3_600_000L;
    private static final long IDLE_PAUSE_MS = 20_000L;

    // Action bar: "... +12.5 Farming (2,601,816/2,693,800) ..." OR newer
    // "... +12.5 Farming 53 (3,012,209/5,500,000) ..." with explicit level (group 3 optional).
    private static final Pattern SKILL_AB = Pattern.compile(
        "\\+([\\d,]+(?:\\.\\d+)?)\\s+(Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Taming|Carpentry|Runecrafting|Social)(?:\\s+(\\d+))?\\s*\\(([\\d,]+(?:\\.\\d+)?)/([\\d,]+(?:\\.\\d+)?)\\)");

    private record XpEntry(long timeMs, long xp) {}
    private static final Deque<XpEntry> window = new ArrayDeque<>();

    private static String  skill        = null;
    private static long    curXp        = 0;   // current XP within the level
    private static long    neededXp     = 0;   // total XP this level requires
    private static int     level        = -1;
    private static long    sessionGained = 0;
    private static long    sessionStartMs = 0;
    private static long    pausedAccumMs  = 0;
    private static long    lastActivityMs = 0;
    private static long    pauseStartedMs = 0;
    private static boolean paused = false;
    private static boolean primed = false;   // baseline set; first reading must not count as gained
    public  static boolean debugDump = false;

    public static boolean hasData() { return FishSettings.skillTrackerEnabled && skill != null; }

    public static void reset() {
        window.clear();
        skill = null; curXp = 0; neededXp = 0; level = -1;
        sessionGained = 0; sessionStartMs = 0; pausedAccumMs = 0;
        lastActivityMs = 0; pauseStartedMs = 0; paused = false;
        primed = false;
    }

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!FishSettings.skillTrackerEnabled || !overlay) return;
            String plain = message.getString().replaceAll("§.", "");
            if (debugDump && plain.toLowerCase().matches(".*(farming|mining|combat|foraging|fishing|enchanting|alchemy|taming).*")) {
                fishmod.utils.Misc.addChatMessage(net.minecraft.network.chat.Component.literal("§b[skill] §7" + plain));
            }
            Matcher m = SKILL_AB.matcher(plain);
            if (!m.find()) return;

            String sk   = m.group(2);
            String lvlGroup = m.group(3);                    // explicit level when shown (newer SkyBlock fmt)
            long a      = parseNum(m.group(4));
            long b      = parseNum(m.group(5));
            long now    = System.currentTimeMillis();

            // Switching skills resets the session.
            if (!sk.equals(skill)) {
                reset();
                skill = sk;
                sessionStartMs = now;
            }

            long gain;
            if (!primed) {
                gain = 0;                               // first reading = baseline, don't count
            } else if (b == neededXp && a >= curXp) {
                gain = a - curXp;                       // same level, progressed
            } else if (b != neededXp || a < curXp) {
                // Level-up: finish old level + progress into new.
                gain = (neededXp > curXp ? neededXp - curXp : 0) + a;
            } else {
                gain = 0;
            }

            curXp = a;
            neededXp = b;
            // Prefer the explicit level from the action bar (covers overflow levels past the table).
            if (lvlGroup != null) {
                try { level = Integer.parseInt(lvlGroup); } catch (NumberFormatException e) { level = levelForNeeded(b); }
            } else {
                level = levelForNeeded(b);
            }
            primed = true;

            if (sessionStartMs == 0) sessionStartMs = now;
            if (gain > 0) {
                resumeIfPaused(now);
                sessionGained += gain;
                window.addLast(new XpEntry(now, gain));
                lastActivityMs = now;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.skillTrackerEnabled || skill == null) return;
            if (!paused && lastActivityMs > 0 && System.currentTimeMillis() - lastActivityMs >= IDLE_PAUSE_MS) {
                paused = true;
                pauseStartedMs = lastActivityMs;
            }
        });
    }

    private static void resumeIfPaused(long now) {
        if (paused) {
            if (pauseStartedMs > 0) pausedAccumMs += now - pauseStartedMs;
            pauseStartedMs = 0;
            paused = false;
        }
    }

    private static long parseNum(String s) {
        try { return (long) Double.parseDouble(s.replace(",", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    /** Finds the completed level whose to-next-level requirement equals `needed`, or -1. */
    private static int levelForNeeded(long needed) {
        for (int l = 0; l < CUM.length - 1; l++) {
            if (CUM[l + 1] - CUM[l] == needed) return l;
        }
        // Overflow brackets past level 60.
        int level = CUM.length - 1;
        long slope = OVERFLOW_SLOPE, xpForCurr = OVERFLOW_BASE;
        while (xpForCurr <= needed && level < 1000) {
            if (xpForCurr == needed) return level;
            level++;
            xpForCurr += slope;
            if (level % 10 == 0) slope *= 2;
        }
        return -1;
    }

    /**
     * Resolves a cumulative total XP into {completed level, XP into level, XP needed for level},
     * extending past the standard table with overflow levels.
     */
    private static long[] resolveCumulative(long total) {
        if (total < MAX_CUM) {
            int l = 0;
            for (int i = CUM.length - 1; i >= 0; i--) if (total >= CUM[i]) { l = i; break; }
            if (l >= CUM.length - 1) l = CUM.length - 2;
            return new long[] { l, total - CUM[l], CUM[l + 1] - CUM[l] };
        }
        int level = CUM.length - 1;
        long xpCurrent = total - MAX_CUM;
        long slope = OVERFLOW_SLOPE, xpForCurr = OVERFLOW_BASE;
        while (xpCurrent > xpForCurr) {
            level++;
            xpCurrent -= xpForCurr;
            xpForCurr += slope;
            if (level % 10 == 0) slope *= 2;
        }
        return new long[] { level, xpCurrent, xpForCurr };
    }

    private static long getXpPerHour() {
        long now = paused && pauseStartedMs > 0 ? pauseStartedMs : System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;
        while (!window.isEmpty() && window.peekFirst().timeMs() < cutoff) window.pollFirst();
        if (window.isEmpty()) return 0;
        long total = 0;
        for (XpEntry e : window) total += e.xp();
        long span = now - window.peekFirst().timeMs();
        if (span < 1000) return 0;
        return total * 3_600_000L / span;
    }

    private static long sessionMs() {
        if (sessionStartMs == 0) return 0;
        long end = paused && pauseStartedMs > 0 ? pauseStartedMs : System.currentTimeMillis();
        return Math.max(0, end - sessionStartMs - pausedAccumMs);
    }

    private static String[] buildLines() {
        int lvl; double pct; long remaining; String maxTag = "";
        if (neededXp > 0) {
            // Within-level form: (currentLevelXp / levelTotal)
            // Prefer the explicit level captured from the action bar; falls back to table lookup.
            lvl = (level >= 0) ? level : levelForNeeded(neededXp);
            pct = curXp * 100.0 / neededXp;
            remaining = Math.max(0, neededXp - curXp);
        } else {
            // Cumulative form: action bar shows (totalXp / 0) — derive from the XP table (with overflow).
            long[] r = resolveCumulative(curXp);
            lvl = (int) r[0];
            long into = r[1], need = r[2];
            pct = need > 0 ? into * 100.0 / need : 100;
            remaining = Math.max(0, need - into);
        }
        String lvlStr = lvl >= 0 ? String.valueOf(lvl) : "?";
        long xphr = getXpPerHour();
        return new String[] {
            "§7Skill: §a" + cap(skill) + " " + lvlStr + maxTag + (paused ? " §e§l(PAUSED)" : ""),
            "§7Progress: §a" + String.format("%.1f", pct) + "%",
            "§7Needed: §f" + NUM.format(remaining),
            "§7XP/hr: §a" + (xphr == 0 ? "§8—" : NUM.format(xphr)),
            "§7Gained: §a+" + NUM.format(sessionGained),
            "§7Time: §f" + fmtTime(sessionMs())
        };
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return "Skill";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static String fmtTime(long ms) {
        long s = ms / 1000;
        long h = s / 3600, mn = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "h " + mn + "m";
        if (mn > 0) return mn + "m " + sec + "s";
        return sec + "s";
    }

    public static void renderHud(GuiGraphicsExtractor ctx, net.minecraft.client.DeltaTracker tick) {
        if (!hasData()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;
        draw(ctx, mc);
    }

    public static void renderInScreen(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!hasData()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) return;
        draw(ctx, mc);
    }

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc) {
        int x = FishSettings.skillTrackerHudX;
        int y = FishSettings.skillTrackerHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.skillTrackerScale;
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
    }
}
