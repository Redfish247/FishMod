package fishmod.features.croesus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent store of claimed Croesus chests. */
public class CroesusStore {

    private static final Path FILE = Paths.get("config/fishmod/croesus_loot.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class Entry {
        public long timestamp;
        public String floor = "?";
        public String chestType = "";
        public long runCompletedAgoSec = -1;
        public long claimCost = 0;
        public List<Item> items = new ArrayList<>();
    }

    public static class Item {
        public String id = "";
        public String name = "";
        public int count = 1;
        public double priceAtClaim = 0;
    }

    private static final List<Entry> entries = new ArrayList<>();
    private static boolean loaded = false;

    public static synchronized List<Entry> all() {
        ensureLoaded();
        return entries;
    }

    public static synchronized void add(Entry e) {
        ensureLoaded();
        entries.add(e);
        save();
    }

    /** Fill in any item priceAtClaim that is 0 using the supplied lookup. */
    public static synchronized void clear() {
        ensureLoaded();
        entries.clear();
        save();
    }

    public static synchronized int backfillPrices(java.util.function.Function<String, Double> lookup) {
        ensureLoaded();
        int updated = 0;
        for (Entry e : entries) {
            for (Item it : e.items) {
                if (it.priceAtClaim > 0) continue;
                if (it.id == null || it.id.isEmpty()) continue;
                double p = lookup.apply(it.id);
                if (p > 0) { it.priceAtClaim = p; updated++; }
            }
        }
        if (updated > 0) save();
        return updated;
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            String json = Files.readString(FILE);
            Type type = new TypeToken<List<Entry>>(){}.getType();
            List<Entry> read = GSON.fromJson(json, type);
            if (read != null) entries.addAll(read);
        } catch (Exception ignored) {}
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(entries));
        } catch (IOException ignored) {}
    }

    /** Aggregate value across all entries, using priceAtClaim. */
    public static double totalValue() {
        double sum = 0;
        for (Entry e : all()) for (Item it : e.items) sum += it.priceAtClaim * it.count;
        return sum;
    }

    private static final java.util.regex.Pattern TRAILING_COUNT =
            java.util.regex.Pattern.compile("(?i)\\s*x\\s*\\d+\\s*$");

    /** Aggregate count + value per item id. Normalises old entries that stored counts in the name. */
    public static Map<String, Agg> aggregateByItem() {
        Map<String, Agg> map = new LinkedHashMap<>();
        for (Entry e : all()) {
            for (Item it : e.items) {
                String normName = TRAILING_COUNT.matcher(
                        it.name == null ? "" : it.name).replaceAll("").trim();
                String key = (it.id == null || it.id.isEmpty()) ? normName : it.id;
                Agg a = map.computeIfAbsent(key, k -> { Agg x = new Agg(); x.name = normName; return x; });
                if (a.name.isEmpty()) a.name = normName;
                a.count += it.count;
                a.totalValue += it.priceAtClaim * it.count;
            }
        }
        return map;
    }

    public static class Agg {
        public String name = "";
        public long count = 0;
        public double totalValue = 0;
    }
}
