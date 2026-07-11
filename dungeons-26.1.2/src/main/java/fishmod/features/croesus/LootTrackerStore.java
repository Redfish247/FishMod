package fishmod.features.croesus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent store for the manual loot/profit tracker shown in the Dungeon-Hub inventory.
 * Holds a run counter and a list of manually-added drop rows. Persists to
 * {@code config/fishmod/loot_tracker.json}.
 */
public class LootTrackerStore {

    private static final Path FILE = Paths.get("config/fishmod/loot_tracker.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Row {
        public String name = "";  // display name as typed/selected
        public String id   = "";  // resolved Skyblock item id (may be "" if unresolved)
        public int    count = 0;
    }

    public static class Data {
        public int runs = 0;
        public List<Row> rows = new ArrayList<>();
    }

    private static Data data;
    private static boolean loaded = false;

    public static synchronized Data get() { ensureLoaded(); return data; }

    public static synchronized int runs() { ensureLoaded(); return data.runs; }

    public static synchronized void setRuns(int r) {
        ensureLoaded();
        data.runs = Math.max(0, r);
        save();
    }

    /** Live list — callers iterate on the render/click thread (single-threaded GUI). */
    public static synchronized List<Row> rows() { ensureLoaded(); return data.rows; }

    /**
     * Adds {@code delta} to an existing row (matched by id when non-empty, else by name),
     * creating the row when needed. Removes the row when its count drops to 0 or below.
     */
    public static synchronized void addOrIncrement(String name, String id, int delta) {
        ensureLoaded();
        Row found = null;
        for (Row r : data.rows) {
            boolean match = (id != null && !id.isEmpty()) ? id.equals(r.id)
                                                          : name.equalsIgnoreCase(r.name);
            if (match) { found = r; break; }
        }
        if (found == null) {
            if (delta <= 0) return;
            found = new Row();
            found.name = name == null ? "" : name;
            found.id   = id == null ? "" : id;
            data.rows.add(found);
        }
        found.count += delta;
        if (found.count <= 0) data.rows.remove(found);
        save();
    }

    /** Sets a row's count directly (removing it at 0), matched by id when set else by name. */
    public static synchronized void setCount(String name, String id, int count) {
        ensureLoaded();
        Row found = null;
        for (Row r : data.rows) {
            boolean match = (id != null && !id.isEmpty()) ? id.equals(r.id)
                                                          : name.equalsIgnoreCase(r.name);
            if (match) { found = r; break; }
        }
        if (count <= 0) {
            if (found != null) data.rows.remove(found);
        } else if (found != null) {
            found.count = count;
        } else {
            found = new Row();
            found.name = name == null ? "" : name;
            found.id   = id == null ? "" : id;
            found.count = count;
            data.rows.add(found);
        }
        save();
    }

    public static synchronized void clear() {
        ensureLoaded();
        data.runs = 0;
        data.rows.clear();
        save();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        data = new Data();
        try {
            if (!Files.exists(FILE)) return;
            Data read = GSON.fromJson(Files.readString(FILE), Data.class);
            if (read != null) {
                data = read;
                if (data.rows == null) data.rows = new ArrayList<>();
            }
        } catch (Exception ignored) {}
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data));
        } catch (IOException ignored) {}
    }
}
