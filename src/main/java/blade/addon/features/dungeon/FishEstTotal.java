package blade.addon.features.dungeon;

import blade.addon.utils.Constants;
import blade.addon.utils.dungeon.Phase;
import blade.addon.utils.dungeon.RunHistory;
import blade.addon.utils.events.Events;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import config.practical.hud.HUDComponent;
import config.practical.manager.ConfigValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FishMod-exclusive Est. Total row for the split timer.
 *
 * When blade-addons is installed alongside FishMod, blade's Phase/Split classes
 * load instead of FishMod's. FishEstTotal uses its own inner LocalSplit class so
 * it never touches blade.addon.utils.dungeon.Split, avoiding NoSuchMethodError
 * on blade's different constructor.
 */
public class FishEstTotal {

    // ── LocalSplit — never references blade.addon.utils.dungeon.Split ─────────

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

        boolean ended() { return ended; }

        double getRealTime() {
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

    private static boolean parseGameMessage(Text message) {
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
        // Finalise all running splits and save to RunHistory
        for (LocalSplit s : currentSplits) s.end();
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

    public static boolean display() {
        try {
            return Phase.enableSplits && Phase.runStarted() && currentSplits != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void render(HUDComponent component, DrawContext context) {
        if (currentSplits == null) return;
        MinecraftClient client = MinecraftClient.getInstance();

        // Auto-snap position to the bottom of Phase.splitTimer so they appear as one display.
        int x, y;
        try {
            x = Phase.splitTimer.getScaledX();
            int baseY = Phase.splitTimer.getScaledY();

            // Count how many rows blade is rendering
            int splitCount = Phase.includeTotalTime
                    ? currentSplits.size()
                    : currentSplits.size() - 1;

            int visibleRows;
            if (Phase.onlyShowActivatedSplits) {
                visibleRows = 0;
                for (LocalSplit s : currentSplits) {
                    if (s.started || s.ended()) visibleRows++;
                }
                visibleRows = Math.min(visibleRows, splitCount);
            } else {
                visibleRows = splitCount;
            }

            // sit right after the last split row, at the separator level
            y = baseY + Constants.TEXT_HEIGHT * visibleRows + 4;
        } catch (Throwable t) {
            x = component.getScaledX();
            y = component.getScaledY();
        }

        // Base = sum of all personal averages. Delta = sum of (actual − avg) for ended splits.
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

        double totalSeconds = base + delta;

        int estColor = (personalCount > 0 && fallbackCount == 0) ? 0xFF00AACC
                : (personalCount > 0) ? 0xFFFFAA00 : 0xFF888888;

        String mins = totalSeconds >= 60 ? (int) (totalSeconds / 60) + "m " : "";
        String estTimeStr = mins + Constants.DECIMAL_FORMAT.format(totalSeconds % 60) + "s";

        Text estLabel = Text.literal("Est. Total ").withColor(estColor);
        Text estTime  = Text.literal(estTimeStr).withColor(0xFF55FF55);

        int timeWidth = client.textRenderer.getWidth(estTime);
        context.drawText(client.textRenderer, estLabel, x, y, 0xFFFFFFFF, true);
        context.drawText(client.textRenderer, estTime, x + Phase.SPLIT_LENGTH - timeWidth, y, 0xFFFFFFFF, true);
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
