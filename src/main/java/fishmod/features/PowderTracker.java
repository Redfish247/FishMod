package fishmod.features;

import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.DeltaTracker;
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

public class PowderTracker {

    private static final Pattern MITHRIL_PATTERN  = Pattern.compile("Mithril.*?([\\d,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GEMSTONE_PATTERN = Pattern.compile("Gemstone.*?([\\d,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GLACITE_PATTERN  = Pattern.compile("Glacite.*?([\\d,]+)", Pattern.CASE_INSENSITIVE);

    private static final long WINDOW_MS = 3_600_000L;

    private record Entry(long timeMs, long amount) {}

    private static final Deque<Entry> mithril  = new ArrayDeque<>();
    private static final Deque<Entry> gemstone = new ArrayDeque<>();
    private static final Deque<Entry> glacite  = new ArrayDeque<>();
    private static long sessionStartMs = -1;

    private static long lastMithril  = -1;
    private static long lastGemstone = -1;
    private static long lastGlacite  = -1;
    private static int tickCount = 0;

    private static final Path SAVE_FILE = Paths.get("config/fishmod/powder_tracker.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static int btnX, btnY, btnW, btnH;
    private static boolean btnVisible = false;
    private static int pauseBtnX, pauseBtnY, pauseBtnW, pauseBtnH;
    private static boolean paused = false;
    private static long pauseStartedMs = 0;
    private static long lastActivityMs = 0;
    private static boolean autoPaused = false;

    private static class SaveData {
        long[] mithrilTimes; long[] mithrilAmts;
        long[] gemstoneTimes; long[] gemstoneAmts;
        long[] glaciteTimes; long[] glaciteAmts;
        long sessionStartMs;
        boolean paused;
        long pauseStartedMs;
        boolean autoPaused;
        long lastActivityMs;
    }

    private static void noteActivity() {
        long now = System.currentTimeMillis();
        if (paused && autoPaused) {
            if (pauseStartedMs > 0 && sessionStartMs > 0) sessionStartMs += (now - pauseStartedMs);
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
        if (paused || sessionStartMs <= 0 || lastActivityMs <= 0) return;
        if (System.currentTimeMillis() - lastActivityMs >= 60_000L) autoPause(lastActivityMs);
    }

public static void init() {
        load();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> autoPause(System.currentTimeMillis()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.powderTrackerEnabled) return;
            tickAutoPause();
            if (client.player == null || client.level == null) return;
            if (!inMiningArea()) autoPause(System.currentTimeMillis());
            tickCount++;
            if (tickCount < 20) return;
            tickCount = 0;
            scanScoreboard(client);
        });
    }

    public static void reset() {
        mithril.clear();
        gemstone.clear();
        glacite.clear();
        lastMithril = -1;
        lastGemstone = -1;
        lastGlacite = -1;
        sessionStartMs = -1;
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
            loadDeque(mithril, d.mithrilTimes, d.mithrilAmts);
            loadDeque(gemstone, d.gemstoneTimes, d.gemstoneAmts);
            loadDeque(glacite, d.glaciteTimes, d.glaciteAmts);
            sessionStartMs = d.sessionStartMs == 0 ? -1 : d.sessionStartMs;
            paused = d.paused;
            pauseStartedMs = d.pauseStartedMs;
            autoPaused = d.autoPaused;
            lastActivityMs = d.lastActivityMs;
        } catch (IOException | RuntimeException ignored) {}
    }

    private static void loadDeque(Deque<Entry> q, long[] times, long[] amts) {
        if (times == null || amts == null) return;
        int n = Math.min(times.length, amts.length);
        for (int i = 0; i < n; i++) q.addLast(new Entry(times[i], amts[i]));
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            SaveData d = new SaveData();
            d.mithrilTimes  = mithril.stream().mapToLong(Entry::timeMs).toArray();
            d.mithrilAmts   = mithril.stream().mapToLong(Entry::amount).toArray();
            d.gemstoneTimes = gemstone.stream().mapToLong(Entry::timeMs).toArray();
            d.gemstoneAmts  = gemstone.stream().mapToLong(Entry::amount).toArray();
            d.glaciteTimes  = glacite.stream().mapToLong(Entry::timeMs).toArray();
            d.glaciteAmts   = glacite.stream().mapToLong(Entry::amount).toArray();
            d.sessionStartMs = sessionStartMs;
            d.paused = paused;
            d.pauseStartedMs = pauseStartedMs;
            d.autoPaused = autoPaused;
            d.lastActivityMs = lastActivityMs;
            Files.writeString(SAVE_FILE, GSON.toJson(d));
        } catch (IOException ignored) {}
    }

    public static boolean isInMiningArea() { return inMiningArea(); }

    private static boolean inMiningArea() {
        return Location.in(Location.DWARVEN_MINES)
            || Location.in(Location.CRYSTAL_HOLLOWS)
            || Location.in(Location.MINESHAFT);
    }

    private static void scanScoreboard(Minecraft client) {
        if (paused && !autoPaused) return; // manual pause stops scanning; auto-pause resumes on a powder gain
        if (!inMiningArea()) return;
        Scoreboard sb = client.level.getScoreboard();
        Objective sidebar = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return;

        long now = System.currentTimeMillis();
        long newMith = -1, newGem = -1, newGla = -1;

        Collection<PlayerScoreEntry> entries = sb.listPlayerScores(sidebar);
        for (PlayerScoreEntry entry : entries) {
            String owner = entry.owner();
            PlayerTeam team = sb.getPlayersTeam(owner);
            String raw = team != null
                ? team.getPlayerPrefix().getString() + owner + team.getPlayerSuffix().getString()
                : entry.ownerName().getString();
            String line = raw.replaceAll("§.", "").replaceAll("[^\\x20-\\x7E]", "").trim();
            if (line.isEmpty()) continue;

            Matcher m = MITHRIL_PATTERN.matcher(line);
            if (m.find()) { newMith = parseLong(m.group(1)); continue; }
            m = GEMSTONE_PATTERN.matcher(line);
            if (m.find()) { newGem = parseLong(m.group(1)); continue; }
            m = GLACITE_PATTERN.matcher(line);
            if (m.find()) { newGla = parseLong(m.group(1)); continue; }
        }

        boolean dirty = false;
        if (newMith >= 0) {
            if (lastMithril >= 0 && newMith > lastMithril) {
                mithril.addLast(new Entry(now, newMith - lastMithril));
                dirty = true;
            }
            lastMithril = newMith;
        }
        if (newGem >= 0) {
            if (lastGemstone >= 0 && newGem > lastGemstone) {
                gemstone.addLast(new Entry(now, newGem - lastGemstone));
                dirty = true;
            }
            lastGemstone = newGem;
        }
        if (newGla >= 0) {
            if (lastGlacite >= 0 && newGla > lastGlacite) {
                glacite.addLast(new Entry(now, newGla - lastGlacite));
                dirty = true;
            }
            lastGlacite = newGla;
        }
        if (dirty) {
            if (sessionStartMs < 0) sessionStartMs = now;
            noteActivity();
            save();
        }
    }

    private static String elapsedStr() {
        if (sessionStartMs < 0) return "—";
        long ref = (paused && pauseStartedMs > 0) ? pauseStartedMs : System.currentTimeMillis();
        long s = (ref - sessionStartMs) / 1000;
        long m = s / 60; s %= 60;
        long h = m / 60; m %= 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return -1; }
    }

    private static long perHour(Deque<Entry> deque) {
        long now = (paused && pauseStartedMs > 0) ? pauseStartedMs : System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;
        while (!deque.isEmpty() && deque.peekFirst().timeMs() < cutoff) deque.pollFirst();
        if (deque.isEmpty()) return 0;
        long total = 0;
        for (Entry e : deque) total += e.amount();
        long windowMs = now - deque.peekFirst().timeMs();
        if (windowMs < 1000) return 0;
        return total * 3_600_000L / windowMs;
    }

    private static String[] buildLines() {
        long mith = perHour(mithril);
        long gem  = perHour(gemstone);
        long gla  = perHour(glacite);
        return new String[] {
            "§e§lPowder Tracker" + (paused ? " §e§l(PAUSED)" : ""),
            "§7Mithril: §a"  + (mith == 0 ? "§8—" : fmt(mith) + "/hr"),
            "§7Gemstone: §d" + (gem  == 0 ? "§8—" : fmt(gem)  + "/hr"),
            "§7Glacite: §b"  + (gla  == 0 ? "§8—" : fmt(gla)  + "/hr"),
            "§7Time: §f"     + elapsedStr(),
        };
    }

    public static void renderHud(GuiGraphicsExtractor ctx, DeltaTracker tick) {
        btnVisible = false;
        if (!FishSettings.powderTrackerEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        if (!inMiningArea()) return;

        int x = FishSettings.powderTrackerHudX;
        int y = FishSettings.powderTrackerHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.powderTrackerScale;
        ctx.pose().pushMatrix();
        ctx.pose().translate((float)x, (float)y);
        ctx.pose().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
    }

    public static void renderInScreen(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        btnVisible = false;
        if (!FishSettings.powderTrackerEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?>)) return;
        if (!inMiningArea()) return;

        int x = FishSettings.powderTrackerHudX;
        int y = FishSettings.powderTrackerHudY;
        int lh = Constants.TEXT_HEIGHT + 1;
        String[] lines = buildLines();
        float sc = (float) FishSettings.powderTrackerScale;

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
                if (pauseStartedMs > 0 && sessionStartMs > 0) sessionStartMs += (now - pauseStartedMs);
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
