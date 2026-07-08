package fishmod.features.dungeon;

import fishmod.utils.Constants;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.dungeon.RunHistory;
import fishmod.utils.events.Events;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import config.practical.hud.HUDComponent;
import config.practical.manager.ConfigValue;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * FishMod-exclusive Est. Total row for the split timer.
 *
 * When blade-addons is installed alongside FishMod, blade's Phase/Split classes
 * load instead of FishMod's. FishEstTotal uses its own inner LocalSplit class so
 * it never touches fishmod.utils.dungeon.Split, avoiding NoSuchMethodError
 * on blade's different constructor.
 */
public class FishEstTotal {

    // ── LocalSplit — never references fishmod.utils.dungeon.Split ─────────

    private static final class LocalSplit {
        final String name;
        final String startMsg;
        final String endMsg;
        final double avg;          // -1 = cumulative/skip

        private boolean started, ended;
        private long startTime, endTime;
        private int tick;

        LocalSplit(String name, String startMsg, String endMsg, double avg) {
            this.name = name;
            this.startMsg = startMsg;
            this.endMsg = endMsg;
            this.avg = avg;
        }

        void reset() { started = false; ended = false; tick = 0; startTime = 0; endTime = 0; }

        void tick() { if (started && !ended) tick++; }

        void parseMessage(String msg) {
            if (!started && msg.equals(startMsg)) {
                startTime = System.currentTimeMillis();
                started = true;
            } else if (started && !ended && msg.equals(endMsg)) {
                end();
            }
        }

        void end() {
            if (ended) return;
            endTime = System.currentTimeMillis();
            started = false;
            ended = true;
        }

        boolean started() { return started; }
        boolean ended() { return ended; }

        double getRealTime() {
            // startTime == 0 means this split was never started.
            // Without this guard, force-ending a never-started split via endRun()
            // produces (currentTimeMs - 0) / 1000 ≈ 55 years → "infinite time" in the EST.
            if (startTime == 0) return 0;
            if (ended) return (endTime - startTime) / 1000.0;
            if (started) return (System.currentTimeMillis() - startTime) / 1000.0;
            return 0;
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private static final Pattern END_PATTERN =
            Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+)\\s*(\\(NEW RECORD!\\))?$");
    private static final Pattern FLOOR_PATTERN =
            Pattern.compile("^ ⏣ The Catacombs .*$");

    // floor → ordered list of LocalSplits (loaded from FishMod's own jar)
    private static final HashMap<String, ArrayList<LocalSplit>> FLOOR_SPLITS = loadSplits();

    private static ArrayList<LocalSplit> currentSplits = null;
    private static String floor = null;
    private static boolean runOver = false;

    // ── HUD ───────────────────────────────────────────────────────────────────

    @ConfigValue
    public static HUDComponent estTotalHud = new HUDComponent(
            0, 0, Phase.SPLIT_LENGTH, Constants.TEXT_HEIGHT + 4, 1, "Est. Total",
            FishEstTotal::display,
            FishEstTotal::render,
            () -> { try { return Phase.enableSplits; } catch (Throwable t) { return false; } }
    );

    // ── init ─────────────────────────────────────────────────────────────────

    public static void init() {
        Events.ON_TEAM.register(FishEstTotal::detectFloor);
        Events.ON_GAME_MESSAGE.register(FishEstTotal::parseGameMessage);
        Events.ON_LOCATION_CHANGE.register(newLoc -> { reset(); return false; });
        Events.ON_SERVER_TICK.register(() -> {
            if (currentSplits == null || runOver) return false;
            for (LocalSplit s : currentSplits) s.tick();
            return false;
        });
    }

    // ── floor detection ───────────────────────────────────────────────────────

    private static boolean detectFloor(String line) {
        if (floor != null) return false;
        if (!FLOOR_PATTERN.matcher(line).find()) return false;
        int start = line.indexOf("(");
        int end   = line.indexOf(")");
        if (start < 0 || end <= start) return false;
        floor = line.substring(start + 1, end);
        currentSplits = FLOOR_SPLITS.get(floor);
        if (currentSplits != null)
            for (LocalSplit s : currentSplits) s.reset();
        return false;
    }

    // ── message parsing ───────────────────────────────────────────────────────

    private static boolean parseGameMessage(Component message) {
        String string = message.getString();
        if (currentSplits == null || runOver) return false;
        for (LocalSplit s : currentSplits) {
            if (!s.ended()) s.parseMessage(string);
        }
        if (END_PATTERN.matcher(string).find()) endRun();
        return false;
    }

    private static void endRun() {
        runOver = true;
        if (currentSplits == null) return;
        // Only finalise splits that actually started — skipping splits with startTime == 0
        // prevents (currentTime - 0) / 1000 ≈ 55-year getRealTime() blowing up the EST total.
        for (LocalSplit s : currentSplits) {
            if (s.startTime > 0) s.end();
        }
        Map<String, Double> times = new LinkedHashMap<>();
        for (LocalSplit s : currentSplits) {
            if (s.avg < 0) continue; // skip cumulative entries
            if (s.ended()) times.put(s.name, s.getRealTime());
        }
        RunHistory.saveSplitTimes(floor, times);
    }

    private static void reset() {
        currentSplits = null;
        floor = null;
        runOver = false;
    }

    // ── HUD display/render ────────────────────────────────────────────────────

    /**
     * Mirrors Phase.getVisibleRowCount() but works against FishEstTotal's own LocalSplits,
     * so it stays accurate even when blade-addons' Phase is the one rendering. Each call to
     * onPartyCommand splits-list grows as splits start, so this value increases each time a
     * new split begins — which pushes Est. Total down to "stick" to the bottom of the list.
     */
    private static int computeVisibleRowCount() {
        if (currentSplits == null) return 0;
        boolean onlyActivated = true;
        boolean includeTotal  = false;
        try { onlyActivated = Phase.onlyShowActivatedSplits; } catch (Throwable ignored) {}
        try { includeTotal  = Phase.includeTotalTime;        } catch (Throwable ignored) {}
        int count = currentSplits.size();
        if (!includeTotal) count--; // last row is the cumulative "total" split
        if (!onlyActivated) return Math.max(0, count);
        int visible = 0;
        for (int i = 0; i < count; i++) {
            LocalSplit s = currentSplits.get(i);
            if (s.started() || s.ended()) visible++;
        }
        return visible;
    }

    public static boolean display() {
        try {
            return Phase.enableSplits && Phase.runStarted() && currentSplits != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void render(HUDComponent component, GuiGraphicsExtractor context) {
        if (currentSplits == null) return;
        Minecraft client = Minecraft.getInstance();

        // Auto-snap below Phase.splitTimer rows + separator. Compute the visible-row count
        // from our OWN LocalSplits so this works even when blade-addons' Phase wins the
        // classload (its Phase has no getVisibleRowCount()) — otherwise the throwable catch
        // would drop us back to the user's draggable position and the est. total would stop
        // tracking the splits as they're added.
        int x, y;
        try {
            x = Phase.splitTimer.getScaledX();
            int baseY = Phase.splitTimer.getScaledY();
            y = baseY + Constants.TEXT_HEIGHT * computeVisibleRowCount() + 8;
        } catch (Throwable t) {
            x = component.getScaledX();
            y = component.getScaledY();
        }

        // Base = sum of all averages. Delta = (actual − avg) for ended splits only. Subtract accumulated run lag.
        int splitCount = currentSplits.size() - 1;
        double base = 0, delta = 0;
        int personalCount = 0, fallbackCount = 0;

        for (int i = 0; i < splitCount; i++) {
            LocalSplit s = currentSplits.get(i);
            if (s.avg < 0) continue;
            double personal = RunHistory.getPersonalAvg(floor, s.name);
            double avg = personal > 0 ? personal : s.avg;
            if (personal > 0) { base += personal; personalCount++; }
            else               { base += s.avg;    fallbackCount++; }
            if (s.ended()) delta += s.getRealTime() - avg;
        }

        // Lag is already reflected in `delta` (ended splits use wall-clock time, which includes
        // lag). Do NOT subtract it — doing so cancels the penalty and makes the estimate drop
        // during lag spikes. Laggier splits should push the estimate UP via delta.
        double totalSeconds = Math.max(0, base + delta);

        int estColor = (personalCount > 0 && fallbackCount == 0) ? 0xFF00AACC
                : (personalCount > 0) ? 0xFFFFAA00 : 0xFF888888;

        String mins = totalSeconds >= 60 ? (int) (totalSeconds / 60) + "m " : "";
        String estTimeStr = mins + Constants.DECIMAL_FORMAT.format(totalSeconds % 60) + "s";

        Component estLabel = Component.literal("Est. Total ").withColor(estColor);
        Component estTime  = Component.literal(estTimeStr).withColor(0xFF55FF55);

        int timeWidth = client.font.width(estTime);
        context.text(client.font, estLabel, x, y, 0xFFFFFFFF, true);
        context.text(client.font, estTime, x + Phase.SPLIT_LENGTH - timeWidth, y, 0xFFFFFFFF, true);
    }

    /** Standalone render — called from HudRenderCallback when blade-addons is absent. */
    public static void renderStandalone(GuiGraphicsExtractor ctx, int baseX, int baseY) {
        if (!display()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        int x = baseX;
        int y = baseY + Constants.TEXT_HEIGHT * computeVisibleRowCount() + 4;

        int splitCount = currentSplits.size() - 1;
        double base = 0, delta = 0;
        int personalCount = 0, fallbackCount = 0;
        for (int i = 0; i < splitCount; i++) {
            LocalSplit s = currentSplits.get(i);
            if (s.avg < 0) continue;
            double personal = RunHistory.getPersonalAvg(floor, s.name);
            double avg = personal > 0 ? personal : s.avg;
            if (personal > 0) { base += personal; personalCount++; }
            else               { base += s.avg;    fallbackCount++; }
            if (s.ended()) delta += s.getRealTime() - avg;
        }

        // Lag is already reflected in `delta` (ended splits use wall-clock time, which includes
        // lag). Do NOT subtract it — doing so cancels the penalty and makes the estimate drop
        // during lag spikes. Laggier splits should push the estimate UP via delta.
        double totalSeconds = Math.max(0, base + delta);
        int estColor = (personalCount > 0 && fallbackCount == 0) ? 0xFF00AACC
                : (personalCount > 0) ? 0xFFFFAA00 : 0xFF888888;
        String estTimeStr = (totalSeconds >= 60 ? (int)(totalSeconds / 60) + "m " : "")
                + Constants.DECIMAL_FORMAT.format(totalSeconds % 60) + "s";
        Component estLabel = Component.literal("Est. Total ").withColor(estColor);
        Component estTime  = Component.literal(estTimeStr).withColor(0xFF55FF55);
        int timeWidth = client.font.width(estTime);
        ctx.text(client.font, estLabel, x, y, 0xFFFFFFFF, true);
        ctx.text(client.font, estTime, x + Phase.SPLIT_LENGTH - timeWidth, y, 0xFFFFFFFF, true);
    }

    // ── splits.json loader (FishMod's own jar via FishEstTotal.class) ─────────

    private static HashMap<String, ArrayList<LocalSplit>> loadSplits() {
        try (InputStream stream = FishEstTotal.class.getResourceAsStream("/data/fishmod-splits.json")) {
            if (stream == null) return new HashMap<>();
            try (Reader reader = new InputStreamReader(stream)) {
                JsonElement root = JsonParser.parseReader(reader);
                return parseSplits(root.getAsJsonObject());
            }
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private static HashMap<String, ArrayList<LocalSplit>> parseSplits(JsonObject obj) {
        HashMap<String, ArrayList<LocalSplit>> floors = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            ArrayList<LocalSplit> splits = new ArrayList<>();
            for (JsonElement el : entry.getValue().getAsJsonArray()) {
                JsonObject s = el.getAsJsonObject();
                splits.add(new LocalSplit(
                        s.get("name").getAsString(),
                        s.get("start").getAsString(),
                        s.get("end").getAsString(),
                        s.has("avg") ? s.get("avg").getAsDouble() : -1.0
                ));
            }
            floors.put(entry.getKey(), splits);
        }
        return floors;
    }
}
