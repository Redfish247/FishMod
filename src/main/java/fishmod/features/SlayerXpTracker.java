package fishmod.features;

import fishmod.utils.Constants;
import fishmod.utils.config.values.FishSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SlayerXpTracker {

    // XP per tier: index 0 unused, 1-5 = T1-T5
    private static final long[] TIER_XP = { 0, 5, 25, 125, 500, 1500 };

    // Detects boss tier from spawn/quest messages: "Tier I", "Tier 3", etc.
    private static final Pattern TIER_PATTERN =
        Pattern.compile("Tier[:\\s]+([1-5]|I{1,3}V?|I?V)", Pattern.CASE_INSENSITIVE);

    private static final Pattern COMPLETE_PATTERN =
        Pattern.compile("SLAYER QUEST COMPLETE");

    private static final long WINDOW_MS = 3_600_000L;

    private record XpEntry(long timeMs, long xp) {}
    private record KillEntry(long timeMs) {}

    private static final Deque<XpEntry>   xpEntries   = new ArrayDeque<>();
    private static final Deque<KillEntry> killEntries = new ArrayDeque<>();

    private static final Pattern SLAYER_SIDEBAR = Pattern.compile(
        "Revenant|Tarantula|Sven|Voidgloom|Inferno|Bloodfiend|Slayer Quest",
        Pattern.CASE_INSENSITIVE);

    private static int currentTier = 0;
    private static boolean bossInSidebar = false;
    public static boolean isBossActive() { return bossInSidebar && FishSettings.slayerXpEnabled; }
    private static int tickCount = 0;

    private static final Path SAVE_FILE = Paths.get("config/fishmod/slayer_tracker.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static int btnX, btnY, btnW, btnH;
    private static boolean btnVisible = false;
    private static int pauseBtnX, pauseBtnY, pauseBtnW, pauseBtnH;
    private static boolean paused = false;
    private static long pauseStartedMs = 0;
    private static long lastActivityMs = 0;
    private static boolean autoPaused = false;

    private static class SaveData {
        long[] xpTimes; long[] xpAmts;
        long[] killTimes;
        boolean paused;
        long pauseStartedMs;
        boolean autoPaused;
        long lastActivityMs;
    }

    // Shift all window entries forward so an idle/paused gap doesn't dilute the rate.
    private static void shiftEntries(long delta) {
        if (delta <= 0) return;
        java.util.List<XpEntry> xs = new java.util.ArrayList<>();
        for (XpEntry e : xpEntries) xs.add(new XpEntry(e.timeMs() + delta, e.xp()));
        xpEntries.clear(); xpEntries.addAll(xs);
        java.util.List<KillEntry> ks = new java.util.ArrayList<>();
        for (KillEntry e : killEntries) ks.add(new KillEntry(e.timeMs() + delta));
        killEntries.clear(); killEntries.addAll(ks);
    }

    private static void noteActivity() {
        long now = System.currentTimeMillis();
        if (paused && autoPaused) {
            if (pauseStartedMs > 0) shiftEntries(now - pauseStartedMs);
            pauseStartedMs = 0;
            paused = false;
            autoPaused = false;
        }
        lastActivityMs = now;
    }

    private static void autoPause(long freezeAtMs) {
        if (paused) return;
        paused = true;
        autoPaused = true;
        pauseStartedMs = (freezeAtMs > 0 ? freezeAtMs : System.currentTimeMillis());
    }

    private static void tickAutoPause() {
        if (paused || lastActivityMs <= 0) return;
        if (System.currentTimeMillis() - lastActivityMs >= 60_000L) autoPause(lastActivityMs);
    }

    public static void reset() {
        xpEntries.clear();
        killEntries.clear();
        currentTier = 0;
        paused = false;
        pauseStartedMs = 0;
        autoPaused = false;
        lastActivityMs = 0;
        save();
    }

    private static synchronized void load() {
        try {
            if (!Files.exists(SAVE_FILE)) return;
            SaveData d = GSON.fromJson(Files.readString(SAVE_FILE), SaveData.class);
            if (d == null) return;
            if (d.xpTimes != null && d.xpAmts != null) {
                int n = Math.min(d.xpTimes.length, d.xpAmts.length);
                for (int i = 0; i < n; i++) xpEntries.addLast(new XpEntry(d.xpTimes[i], d.xpAmts[i]));
            }
            if (d.killTimes != null) {
                for (long t : d.killTimes) killEntries.addLast(new KillEntry(t));
            }
            paused = d.paused;
            pauseStartedMs = d.pauseStartedMs;
            autoPaused = d.autoPaused;
            lastActivityMs = d.lastActivityMs;
        } catch (IOException | RuntimeException ignored) {}
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            SaveData d = new SaveData();
            d.xpTimes  = xpEntries.stream().mapToLong(XpEntry::timeMs).toArray();
            d.xpAmts   = xpEntries.stream().mapToLong(XpEntry::xp).toArray();
            d.killTimes = killEntries.stream().mapToLong(KillEntry::timeMs).toArray();
            d.paused = paused;
            d.pauseStartedMs = pauseStartedMs;
            d.autoPaused = autoPaused;
            d.lastActivityMs = lastActivityMs;
            Files.writeString(SAVE_FILE, GSON.toJson(d));
        } catch (IOException ignored) {}
    }

public static void init() {
        load();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> autoPause(System.currentTimeMillis()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.slayerXpEnabled) return;
            tickAutoPause();
            if (client.player == null || client.level == null) { bossInSidebar = false; return; }
            if (++tickCount < 10) return;
            tickCount = 0;
            Scoreboard sb = client.level.getScoreboard();
            Objective sidebar = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (sidebar == null) { bossInSidebar = false; return; }
            boolean found = false;
            Collection<PlayerScoreEntry> entries = sb.listPlayerScores(sidebar);
            for (PlayerScoreEntry entry : entries) {
                String owner = entry.owner();
                PlayerTeam team = sb.getPlayersTeam(owner);
                String raw = team != null
                    ? team.getPlayerPrefix().getString() + owner + team.getPlayerSuffix().getString()
                    : entry.ownerName().getString();
                String line = raw.replaceAll("§.", "").replaceAll("[^\\x20-\\x7E]", "").trim();
                if (SLAYER_SIDEBAR.matcher(line).find()) { found = true; break; }
            }
            bossInSidebar = found;
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!FishSettings.slayerXpEnabled) return;
            if (paused && !autoPaused) return; // manual pause stops recording; auto-pause resumes on a kill
            String plain = message.getString().replaceAll("§.", "").trim();
            long now = System.currentTimeMillis();

            Matcher tm = TIER_PATTERN.matcher(plain);
            if (tm.find()) {
                int t = parseTier(tm.group(1));
                if (t > 0) currentTier = t;
            }

            if (COMPLETE_PATTERN.matcher(plain).find()) {
                noteActivity();
                now = System.currentTimeMillis();
                killEntries.addLast(new KillEntry(now));
                long xp = xpForCurrentTier();
                if (xp > 0) { xpEntries.addLast(new XpEntry(now, xp)); }
                currentTier = 0;
                save();
            }
        });
    }

    private static long xpForCurrentTier() {
        int tier = currentTier > 0 ? currentTier : 4; // fallback T4 if unknown
        long base = TIER_XP[Math.min(tier, 5)];
        return fishmod.utils.MayorApi.isAatroxSlayerBonusActive() ? Math.round(base * 1.25) : base;
    }

    private static int parseTier(String s) {
        return switch (s.toUpperCase()) {
            case "1", "I"   -> 1;
            case "2", "II"  -> 2;
            case "3", "III" -> 3;
            case "4", "IV"  -> 4;
            case "5", "V"   -> 5;
            default         -> 0;
        };
    }

    private static long getXpPerHour() {
        long now = (paused && pauseStartedMs > 0) ? pauseStartedMs : System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;
        while (!xpEntries.isEmpty() && xpEntries.peekFirst().timeMs() < cutoff) xpEntries.pollFirst();
        if (xpEntries.isEmpty()) return 0;
        long total = 0;
        for (XpEntry e : xpEntries) total += e.xp();
        long windowMs = now - xpEntries.peekFirst().timeMs();
        if (windowMs < 1000) return 0;
        return total * 3_600_000L / windowMs;
    }

    private static double getKillsPerHour() {
        long now = (paused && pauseStartedMs > 0) ? pauseStartedMs : System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;
        while (!killEntries.isEmpty() && killEntries.peekFirst().timeMs() < cutoff) killEntries.pollFirst();
        if (killEntries.size() < 2) return 0;
        long windowMs = killEntries.peekLast().timeMs() - killEntries.peekFirst().timeMs();
        if (windowMs < 1000) return 0;
        return (killEntries.size() - 1) * 3_600_000.0 / windowMs;
    }

    private static String[] buildLines() {
        long xphr = getXpPerHour();
        double bosses = getKillsPerHour();
        String xpLine   = "§7XP/hr: §a"    + (xphr == 0 ? "§8—" : fmt(xphr)) + (paused ? " §e§l(PAUSED)" : "");
        String bossLine = "§7Bosses/hr: §e" + (bosses == 0 ? "§8—" : String.format("%.1f", bosses))
                        + (fishmod.utils.MayorApi.isAatroxSlayerBonusActive() ? " §6(Aatrox)" : "");
        return new String[] { xpLine, bossLine };
    }

    public static void renderHud(GuiGraphicsExtractor ctx, net.minecraft.client.DeltaTracker tick) {
        btnVisible = false;
        if (!FishSettings.slayerXpEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        if (!bossInSidebar) return;

        int x = FishSettings.slayerXpHudX;
        int y = FishSettings.slayerXpHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.slayerXpScale;
        ctx.pose().pushMatrix();
        ctx.pose().translate((float)x, (float)y);
        ctx.pose().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
    }

    public static void renderInScreen(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        btnVisible = false;
        if (!FishSettings.slayerXpEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?>)) return;
        if (!bossInSidebar) return;

        int x = FishSettings.slayerXpHudX;
        int y = FishSettings.slayerXpHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.slayerXpScale;

        String resetLabel = "§l[ Reset ]";
        String pauseLabel = paused ? "§l[ Resume ]" : "§l[ Pause ]";
        int resetW = mc.font.width(resetLabel);
        int pauseW = mc.font.width(pauseLabel);
        int padX = 4, padY = 3;
        int localBtnY = lh * lines.length - 2;
        int localResetW = resetW + padX * 2;
        int localPauseW = pauseW + padX * 2;
        int localBtnH = Constants.TEXT_HEIGHT + padY * 2 + 1;
        int gap = 4;
        btnX = x;
        btnY = y + (int)(localBtnY * sc);
        btnW = (int)(localResetW * sc);
        btnH = (int)(localBtnH * sc);
        int localPauseX = localResetW + gap;
        pauseBtnX = x + (int)(localPauseX * sc);
        pauseBtnY = btnY;
        pauseBtnW = (int)(localPauseW * sc);
        pauseBtnH = btnH;
        boolean resetHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean pauseHover = mouseX >= pauseBtnX && mouseX <= pauseBtnX + pauseBtnW && mouseY >= pauseBtnY && mouseY <= pauseBtnY + pauseBtnH;
        String shownReset = resetHover ? "§c§l[ Reset ]" : resetLabel;
        String shownPause = pauseHover ? (paused ? "§a§l[ Resume ]" : "§e§l[ Pause ]") : pauseLabel;

        ctx.pose().pushMatrix();
        ctx.pose().translate((float)x, (float)y);
        ctx.pose().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.text(mc.font, shownReset, padX, localBtnY + padY, 0xFFFFFFFF, true);
        ctx.text(mc.font, shownPause, localPauseX + padX, localBtnY + padY, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
        btnVisible = true;
    }

    public static boolean handleScreenClick(double mx, double my) {
        if (!btnVisible) return false;
        if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
            reset();
            return true;
        }
        if (mx >= pauseBtnX && mx <= pauseBtnX + pauseBtnW && my >= pauseBtnY && my <= pauseBtnY + pauseBtnH) {
            long now = System.currentTimeMillis();
            if (!paused) {
                paused = true;
                autoPaused = false;
                pauseStartedMs = now;
            } else {
                pauseStartedMs = 0;
                paused = false;
                autoPaused = false;
                lastActivityMs = now;
            }
            save();
            return true;
        }
        return false;
    }

    private static String fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fk", n / 1_000.0);
        return Long.toString(n);
    }
}
