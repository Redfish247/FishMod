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
 * GSON-backed store of every user-placed dungeon waypoint, keyed by {@link
 * fishmod.utils.dungeon.map.RoomSignature#key()} (rotation-normalized). Same load-on-static-init,
 * save-on-mutation pattern as {@code RoomSignatureDB}.
 *
 * <p>Stored in config/fishmod-dungeon-waypoints.json as {@code { signatureKey: [ StoredWaypoint, ... ] } }.
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

    public static Map<String, List<StoredWaypoint>> allData() {
        return data;
    }

    public static void replaceAll(Map<String, List<StoredWaypoint>> newData) {
        data = newData != null ? newData : new HashMap<>();
        save();
    }

    /** 90-degree rotation of a room-tile-relative point around its tile center. steps in [0,3], applied CCW to match {@code RoomSignature}'s (x,z) -> (z,-x). */
    public static double[] rotate90(double x, double z, int steps) {
        double rx = x, rz = z;
        int n = ((steps % 4) + 4) % 4;
        for (int i = 0; i < n; i++) {
            double nx = rz;
            double nz = -rx;
            rx = nx;
            rz = nz;
        }
        return new double[]{rx, rz};
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
