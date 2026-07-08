package fishmod.features.dungeon;

import fishmod.features.FishHudEditor;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Pattern;

public class SessionStats {

    private static final Pattern DEATH_PAT = Pattern.compile("☠ \\S+ (?:was|were) killed by|☠ \\S+ (?:died|quit)");

    // Mort's intro line — fires the moment the dungeon run actually starts (same trigger LagTracker uses).
    private static final String MORT_START = "[NPC] Mort: Here, I found this map when I first entered the dungeon.";

    private static final long WINDOW_MS = 3_600_000L; // 1 hour for R/hr
    private static final long IDLE_MS   = 5 * 60_000L; // pause after 5 min idle in-dungeon

    // Auto-pause reason: 0 = none, 1 = location (hub/lobby/pre-Mort), 2 = idle.
    private static int autoPauseReason = 0;
    // Movement tracking for idle detection
    private static double lastX, lastY, lastZ;
    private static boolean havePos = false;

    private static long   sessionStartMs = -1;
    private static int    runs           = 0;
    private static int    deaths         = 0;
    private static final Deque<Long> runTimes = new ArrayDeque<>();

    // Persistence
    private static final Path SAVE_FILE = Paths.get("config/fishmod/session_stats.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Reset button hitbox state (set during inventory render, read during click)
    private static int btnX, btnY, btnW, btnH;
    private static boolean btnVisible = false;
    private static int pauseBtnX, pauseBtnY, pauseBtnW, pauseBtnH;
    private static boolean paused = false;
    private static long pauseStartedMs = 0;
    private static long lastActivityMs = 0;
    private static boolean autoPaused = false;

    private static class SaveData {
        long sessionStartMs;
        int runs;
        int deaths;
        long[] runTimes;
        boolean paused;
        long pauseStartedMs;
        boolean autoPaused;
        long lastActivityMs;
    }

    /** Clears any auto-pause and advances the session start by the paused duration. */
    private static void autoResume() {
        long now = System.currentTimeMillis();
        if (paused && autoPaused) {
            if (pauseStartedMs > 0 && sessionStartMs > 0) sessionStartMs += (now - pauseStartedMs);
            pauseStartedMs = 0;
            paused = false;
            autoPaused = false;
            autoPauseReason = 0;
        }
        lastActivityMs = now;
    }

    /** Player moved / acted in-dungeon: resume only if we were idle-paused (not hub/pre-Mort paused). */
    private static void noteMovement() {
        if (paused && autoPaused && autoPauseReason == 2) autoResume();
        else lastActivityMs = System.currentTimeMillis();
    }

    private static void autoPause(int reason, long freezeAtMs) {
        if (paused) return;
        paused = true;
        autoPaused = true;
        autoPauseReason = reason;
        pauseStartedMs = (freezeAtMs > 0 ? freezeAtMs : System.currentTimeMillis());
    }

    private static void tickAutoPause() {
        if (paused || sessionStartMs <= 0 || lastActivityMs <= 0) return;
        if (System.currentTimeMillis() - lastActivityMs >= IDLE_MS) autoPause(2, lastActivityMs);
    }

    public static void init() {
        load();

        FishHudEditor.register("Session Stats",
                () -> FishSettings.sessionStatsHudX, v -> FishSettings.sessionStatsHudX = v,
                () -> FishSettings.sessionStatsHudY, v -> FishSettings.sessionStatsHudY = v,
                80, 14 * 4,
                () -> FishSettings.sessionStatsScale, v -> FishSettings.sessionStatsScale = v,
                () -> {
                    if (!FishSettings.sessionStatsEnabled) return false;
                    Location loc = Location.getCurrentLocation();
                    return (loc == Location.DUNGEON     && FishSettings.sessionStatsInDungeon)
                        || (loc == Location.DUNGEON_HUB && FishSettings.sessionStatsInDungeonHub);
                });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> autoPause(1, System.currentTimeMillis()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!FishSettings.sessionStatsEnabled) return;
            Location loc = Location.getCurrentLocation();
            // Pause whenever not actively inside a dungeon run (dungeon hub, lobby, etc.).
            if (loc != Location.DUNGEON) {
                havePos = false;
                autoPause(1, System.currentTimeMillis());
                return;
            }
            // Inside the dungeon: track movement so we can pause after 5 min idle (AFK).
            if (client.player != null) {
                double x = client.player.getX(), y = client.player.getY(), z = client.player.getZ();
                if (!havePos) {
                    lastX = x; lastY = y; lastZ = z; havePos = true;
                    if (lastActivityMs <= 0) lastActivityMs = System.currentTimeMillis();
                } else if (Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ) > 0.05) {
                    lastX = x; lastY = y; lastZ = z;
                    noteMovement();
                }
            }
            tickAutoPause();
        });

        Events.ON_WORLD_CHANGE.register(() -> {
            havePos = false;
            if (FishSettings.sessionStatsResetOnRelog) reset();
            else autoPause(1, System.currentTimeMillis());
            return false;
        });

        Events.ON_LOCATION_CHANGE.register(loc -> {
            havePos = false; // recalibrate movement baseline on every location change
            return false;
        });

        Events.ON_RUN_END.register(() -> {
            if (!FishSettings.sessionStatsEnabled) return false;
            if (paused && !autoPaused) return false;
            long now = System.currentTimeMillis();
            if (sessionStartMs < 0) sessionStartMs = now;
            lastActivityMs = now;
            runs++;
            runTimes.addLast(now);
            save();
            return false;
        });

        Events.ON_GAME_MESSAGE.register(message -> {
            if (!FishSettings.sessionStatsEnabled) return false;
            String s = message.getString().replaceAll("§.", "");

            // Dungeon run started (Mort's intro) — start the clock and resume any auto-pause.
            if (s.equals(MORT_START)) {
                if (sessionStartMs < 0) sessionStartMs = System.currentTimeMillis();
                autoResume();
                havePos = false;
                save();
                return false;
            }

            if (paused && !autoPaused) return false;
            Location loc = Location.getCurrentLocation();
            boolean track = (loc == Location.DUNGEON    && FishSettings.sessionStatsInDungeon)
                         || (loc == Location.DUNGEON_HUB && FishSettings.sessionStatsInDungeonHub);
            if (!track) return false;
            if (DEATH_PAT.matcher(s).find()) {
                if (sessionStartMs < 0) sessionStartMs = System.currentTimeMillis();
                lastActivityMs = System.currentTimeMillis();
                deaths++;
                save();
            }
            return false;
        });
    }

    public static int    getRuns()        { return runs; }
    public static int    getDeaths()      { return deaths; }
    public static double getRunsPerHour() { return runsPerHour(); }
    public static long   getSessionStartMs() { return sessionStartMs; }
    public static String formatDuration() {
        if (sessionStartMs < 0) return "—";
        long ref = (paused && pauseStartedMs > 0) ? pauseStartedMs : System.currentTimeMillis();
        // Defensive clamp: if Mort start fires while manually paused, sessionStartMs can be set
        // newer than pauseStartedMs, producing a transient negative duration. Show 0s instead.
        return formatTime(Math.max(0, ref - sessionStartMs));
    }

    public static void reset() {
        sessionStartMs = -1;
        runs           = 0;
        deaths         = 0;
        runTimes.clear();
        paused = false;
        pauseStartedMs = 0;
        autoPaused = false;
        autoPauseReason = 0;
        lastActivityMs = 0;
        havePos = false;
        save();
    }

    private static synchronized void load() {
        try {
            if (!Files.exists(SAVE_FILE)) return;
            String json = Files.readString(SAVE_FILE);
            SaveData d = GSON.fromJson(json, SaveData.class);
            if (d == null) return;
            sessionStartMs = d.sessionStartMs;
            runs = d.runs;
            deaths = d.deaths;
            runTimes.clear();
            if (d.runTimes != null) for (long t : d.runTimes) runTimes.addLast(t);
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
            d.sessionStartMs = sessionStartMs;
            d.runs = runs;
            d.deaths = deaths;
            d.runTimes = runTimes.stream().mapToLong(Long::longValue).toArray();
            d.paused = paused;
            d.pauseStartedMs = pauseStartedMs;
            d.autoPaused = autoPaused;
            d.lastActivityMs = lastActivityMs;
            Files.writeString(SAVE_FILE, GSON.toJson(d));
        } catch (IOException ignored) {}
    }

    private static double runsPerHour() {
        long now    = (paused && pauseStartedMs > 0) ? pauseStartedMs : System.currentTimeMillis();
        long cutoff = now - WINDOW_MS;
        while (!runTimes.isEmpty() && runTimes.peekFirst() < cutoff) runTimes.pollFirst();
        if (runTimes.size() < 2) return runTimes.size() == 1 ? 0 : 0;
        long window = runTimes.peekLast() - runTimes.peekFirst();
        if (window < 1000) return 0;
        return (runTimes.size() - 1) * 3_600_000.0 / window;
    }

    private static String formatTime(long ms) {
        long s = ms / 1000;
        long m = s / 60; s %= 60;
        long h = m / 60; m %= 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    private static String[] buildLines() {
        double rhr = runsPerHour();
        long ref = (paused && pauseStartedMs > 0) ? pauseStartedMs : System.currentTimeMillis();
        String timeStr = sessionStartMs > 0
                ? formatTime(Math.max(0, ref - sessionStartMs))
                : "—";
        return new String[] {
            "§7Runs: §a"    + runs + (paused ? " §e§l(PAUSED)" : ""),
            "§7Deaths: §c"  + deaths,
            "§7R/hr: §e"    + (rhr == 0 ? "§8—" : String.format("%.1f", rhr)),
            "§7Time: §f"    + timeStr,
        };
    }

    public static void renderHud(GuiGraphics ctx, DeltaTracker tick) {
        btnVisible = false;
        if (!FishSettings.sessionStatsEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        Location loc = Location.getCurrentLocation();
        boolean show = (loc == Location.DUNGEON    && FishSettings.sessionStatsInDungeon)
                    || (loc == Location.DUNGEON_HUB && FishSettings.sessionStatsInDungeonHub);
        if (!show) return;

        int x  = FishSettings.sessionStatsHudX;
        int y  = FishSettings.sessionStatsHudY;
        int lh = Constants.TEXT_HEIGHT + 2;
        String[] lines = buildLines();
        float sc = (float) FishSettings.sessionStatsScale;
        ctx.pose().pushMatrix();
        ctx.pose().translate((float)x, (float)y);
        ctx.pose().scale(sc, sc);
        for (int i = 0; i < lines.length; i++)
            ctx.drawString(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
    }

    /** Rendered on top of any HandledScreen (chest/inventory) with a clickable reset button. */
    public static void renderInScreen(GuiGraphics ctx, int mouseX, int mouseY) {
        btnVisible = false;
        if (!FishSettings.sessionStatsEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return;
        Location loc = Location.getCurrentLocation();
        boolean show = (loc == Location.DUNGEON    && FishSettings.sessionStatsInDungeon)
                    || (loc == Location.DUNGEON_HUB && FishSettings.sessionStatsInDungeonHub);
        if (!show) return;

        int x  = FishSettings.sessionStatsHudX;
        int y  = FishSettings.sessionStatsHudY;
        int lh = Constants.TEXT_HEIGHT + 2;
        String[] lines = buildLines();
        float sc = (float) FishSettings.sessionStatsScale;

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
            ctx.drawString(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF, true);
        ctx.drawString(mc.font, shownReset, padX, localBtnY + padY, 0xFFFFFFFF, true);
        ctx.drawString(mc.font, shownPause, localPauseX + padX, localBtnY + padY, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
        btnVisible = true;
    }

    /** Returns true if click landed on the reset button (consume the click). */
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
}
