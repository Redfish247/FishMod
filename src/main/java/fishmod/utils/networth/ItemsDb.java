package fishmod.utils.networth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazily fetches and caches the public Hypixel SkyBlock items resource
 * (https://api.hypixel.net/v2/resources/skyblock/items — no API key required) and indexes
 * each entry by its {@code id}. The DB provides per-item metadata that SkyHelper's networth
 * pipeline relies on: {@code category}, {@code gemstone_slots}, {@code upgrade_costs}, {@code prestige}.
 *
 * Caching: kept in-memory and refreshed if older than 12h. Also persisted to
 * {@code <gameDir>/fishmod-networth/items.json} so the first calculation after a restart isn't
 * blocked on the network. Fetches run on a background thread; if the DB isn't loaded yet, callers
 * get null from {@link #get(String)} and the metadata-dependent handlers simply contribute 0.
 */
public final class ItemsDb {
    private ItemsDb() {}

    private static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final long REFRESH_MS = 12L * 60 * 60 * 1000L;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();

    private static final Map<String, JsonObject> ITEMS = new ConcurrentHashMap<>();
    private static volatile long loadedAt = 0;
    private static volatile boolean loadedFromDisk = false;
    private static volatile boolean fetching = false;

    /** Returns the metadata for an item id, or null if unknown / not yet loaded. */
    public static JsonObject get(String id) {
        if (id == null) return null;
        return ITEMS.get(id);
    }

    /**
     * Ensures the items DB is being loaded. Loads from disk once (fast, non-blocking-ish) and kicks
     * off a background refresh if the in-memory copy is empty or stale. Never blocks on the network.
     */
    public static void ensureLoaded() {
        if (!loadedFromDisk) {
            loadedFromDisk = true;
            try { loadFromDisk(); } catch (Exception ignored) {}
        }
        boolean stale = ITEMS.isEmpty() || (System.currentTimeMillis() - loadedAt) > REFRESH_MS;
        if (stale && !fetching) {
            fetching = true;
            new Thread(ItemsDb::fetch, "FishMod-ItemsDb").start();
        }
    }

    private static Path file() {
        Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("fishmod-networth");
        return dir.resolve("items.json");
    }

    private static void loadFromDisk() throws Exception {
        Path f = file();
        if (!Files.exists(f)) return;
        String json = Files.readString(f, StandardCharsets.UTF_8);
        index(JsonParser.parseString(json).getAsJsonObject());
        // disk copy counts as loaded but we still allow a background refresh if older than 12h
        loadedAt = Files.getLastModifiedTime(f).toMillis();
    }

    private static void fetch() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ITEMS_URL))
                    .header("User-Agent", "FishMod")
                    .timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                index(root);
                loadedAt = System.currentTimeMillis();
                try {
                    Path f = file();
                    Files.createDirectories(f.getParent());
                    Files.writeString(f, r.body(), StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            fishmod.utils.debug.Debug.LOGGER.warn("[ItemsDb] fetch: {}", e.toString());
        } finally {
            fetching = false;
        }
    }

    /** Indexes the {@code items} array (from the resource response) by each item's {@code id}. */
    private static void index(JsonObject root) {
        if (root == null || !root.has("items") || !root.get("items").isJsonArray()) return;
        Map<String, JsonObject> next = new ConcurrentHashMap<>();
        for (JsonElement el : root.getAsJsonArray("items")) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (o.has("id") && o.get("id").isJsonPrimitive()) {
                next.put(o.get("id").getAsString(), o);
            }
        }
        if (!next.isEmpty()) {
            ITEMS.clear();
            ITEMS.putAll(next);
        }
    }
}
