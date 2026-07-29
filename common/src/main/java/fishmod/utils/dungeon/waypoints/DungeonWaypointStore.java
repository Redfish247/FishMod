package fishmod.utils.dungeon.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GSON-backed store of every user-placed dungeon waypoint, keyed by a per-grid-tile key (see
 * {@code DungeonWaypoints.tileKey}) or a freeform global key outside calibrated dungeons.
 * Load-on-static-init, save-on-mutation.
 *
 * <p>Stored in config/fishmod-dungeon-waypoints.json as {@code { key: [ StoredWaypoint, ... ] } }.
 */
public class DungeonWaypointStore {

    private static final String FILE_PATH = "config/fishmod-dungeon-waypoints.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, List<StoredWaypoint>> data = new HashMap<>();

    static {
        load();
    }

    public static List<StoredWaypoint> get(String roomKey) {
        return data.getOrDefault(roomKey, List.of());
    }

    public static void add(String roomKey, StoredWaypoint waypoint) {
        data.computeIfAbsent(roomKey, k -> new ArrayList<>()).add(waypoint);
        save();
    }

    /** Removes the waypoint whose stored position is within {@code epsilon} of (x,y,z). Returns true if one was removed. */
    public static boolean removeNear(String roomKey, double x, double y, double z, double epsilon) {
        List<StoredWaypoint> list = data.get(roomKey);
        if (list == null) return false;
        boolean removed = list.removeIf(w ->
                Math.abs(w.x - x) < epsilon && Math.abs(w.y - y) < epsilon && Math.abs(w.z - z) < epsilon);
        if (removed) save();
        return removed;
    }

    public static void clearRoom(String roomKey) {
        if (data.remove(roomKey) != null) save();
    }

    /** Removes the waypoint at {@code index} within {@code key}'s list. Returns true if one was removed. */
    public static boolean removeAt(String key, int index) {
        List<StoredWaypoint> list = data.get(key);
        if (list == null || index < 0 || index >= list.size()) return false;
        list.remove(index);
        if (list.isEmpty()) data.remove(key);
        save();
        return true;
    }

    /** Renames the waypoint at {@code index} within {@code key}'s list. */
    public static void setTitle(String key, int index, String title) {
        List<StoredWaypoint> list = data.get(key);
        if (list == null || index < 0 || index >= list.size()) return;
        list.get(index).title = title;
        save();
    }

    /** Removes every waypoint (across every room/global key) tagged with {@code routeId}. Returns the count removed. */
    public static int removeRoute(String routeId) {
        int removed = 0;
        for (List<StoredWaypoint> list : data.values()) {
            int before = list.size();
            list.removeIf(w -> routeId.equals(w.routeId));
            removed += before - list.size();
        }
        data.values().removeIf(List::isEmpty);
        if (removed > 0) save();
        return removed;
    }

    public static Map<String, List<StoredWaypoint>> allData() {
        return data;
    }

    public static void replaceAll(Map<String, List<StoredWaypoint>> newData) {
        data = newData != null ? newData : new HashMap<>();
        save();
    }

    public static String exportBase64() {
        try {
            String json = GSON.toJson(data);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
                gz.write(json.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    /** Decodes base64(gzip(json)) and replaces the whole DB. Returns true on success. */
    public static boolean importBase64(String base64) {
        if (base64 == null || base64.isBlank()) return false;
        try {
            byte[] compressed = Base64.getDecoder().decode(base64.trim());
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(compressed);
            StringBuilder sb = new StringBuilder();
            try (GZIPInputStream gz = new GZIPInputStream(bais)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = gz.read(buf)) > 0) sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            Type type = new TypeToken<Map<String, List<StoredWaypoint>>>() {}.getType();
            Map<String, List<StoredWaypoint>> loaded = GSON.fromJson(sb.toString(), type);
            if (loaded == null) return false;
            replaceAll(loaded);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void load() {
        File file = new File(FILE_PATH);
        if (!file.exists()) return;
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, List<StoredWaypoint>>>() {}.getType();
            Map<String, List<StoredWaypoint>> loaded = GSON.fromJson(reader, type);
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
