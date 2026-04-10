package blade.addon.utils.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Tracks your personal split times across runs and provides averages
 * for the EST display in the split timer.
 *
 * Stored in config/fishmod-runs.json as:
 * { "F7": { "Entrance": [45.2, 42.1, ...], "Blood Open": [...] }, ... }
 */
public class RunHistory {

    private static final int MAX_RUNS = 30;
    private static final String FILE_PATH = "config/fishmod-runs.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // floor → split name → list of real times (seconds), newest last
    private static Map<String, Map<String, List<Double>>> data = new HashMap<>();

    static {
        load();
    }

    /**
     * Save raw split times (name → seconds) without depending on Split class.
     * Used by FishEstTotal which avoids blade's Split to prevent classloader conflicts.
     */
    public static void saveSplitTimes(String floor, Map<String, Double> times) {
        if (floor == null || times == null || times.isEmpty()) return;
        Map<String, List<Double>> floorData = data.computeIfAbsent(floor, k -> new HashMap<>());
        boolean anyRecorded = false;
        for (Map.Entry<String, Double> entry : times.entrySet()) {
            List<Double> list = floorData.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
            list.add(entry.getValue());
            if (list.size() > MAX_RUNS) list.remove(0);
            anyRecorded = true;
        }
        if (anyRecorded) save();
    }

    /**
     * Call this when a run completes. Saves every ended split's real time.
     */
    public static void saveSplits(String floor, List<Split> splits) {
        if (floor == null || splits == null || splits.isEmpty()) return;

        Map<String, List<Double>> floorData = data.computeIfAbsent(floor, k -> new HashMap<>());
        boolean anyRecorded = false;

        for (Split split : splits) {
            if (!split.ended()) continue;
            if (split.getAvg() < 0) continue; // skip cumulative/total splits

            List<Double> times = floorData.computeIfAbsent(split.getName(), k -> new ArrayList<>());
            times.add(split.getRealTime());
            if (times.size() > MAX_RUNS) times.remove(0);
            anyRecorded = true;
        }

        if (anyRecorded) save();
    }

    /**
     * Returns the personal average for a split, or -1 if no data yet.
     */
    public static double getPersonalAvg(String floor, String splitName) {
        if (floor == null || splitName == null) return -1;
        Map<String, List<Double>> floorData = data.get(floor);
        if (floorData == null) return -1;
        List<Double> times = floorData.get(splitName);
        if (times == null || times.isEmpty()) return -1;
        return times.stream().mapToDouble(Double::doubleValue).average().orElse(-1);
    }

    /** How many recorded runs exist for a given split. */
    public static int runCount(String floor, String splitName) {
        Map<String, List<Double>> floorData = data.get(floor);
        if (floorData == null) return 0;
        List<Double> times = floorData.get(splitName);
        return times == null ? 0 : times.size();
    }

    private static void load() {
        File file = new File(FILE_PATH);
        if (!file.exists()) return;
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Map<String, List<Double>>>>() {}.getType();
            Map<String, Map<String, List<Double>>> loaded = GSON.fromJson(reader, type);
            if (loaded != null) data = loaded;
        } catch (Exception ignored) {}
    }

    private static void save() {
        try {
            File file = new File(FILE_PATH);
            file.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(file)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception ignored) {}
    }
}
