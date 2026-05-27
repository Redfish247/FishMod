package fishmod.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves display names → Skyblock item IDs by fetching the canonical list from
 * https://api.hypixel.net/v2/resources/skyblock/items at startup.
 * Falls back silently to a null/empty map if the fetch fails — callers should keep their
 * hardcoded fallback maps for that case.
 */
public class SkyblockItems {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<String, String> nameToId = new HashMap<>();
    private static final Map<String, Double> npcSellPrice = new HashMap<>();
    private static final AtomicBoolean loading = new AtomicBoolean(false);
    private static volatile boolean loaded = false;

    public static void initAsync() {
        if (loaded || !loading.compareAndSet(false, true)) return;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.hypixel.net/v2/resources/skyblock/items"))
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> {
                    try {
                        if (r.statusCode() != 200) return;
                        JsonObject obj = JsonParser.parseString(r.body()).getAsJsonObject();
                        JsonArray items = obj.getAsJsonArray("items");
                        Map<String, String> nextNames = new HashMap<>();
                        Map<String, Double> nextNpc = new HashMap<>();
                        for (int i = 0; i < items.size(); i++) {
                            JsonObject it = items.get(i).getAsJsonObject();
                            if (!it.has("id") || !it.has("name")) continue;
                            String id = it.get("id").getAsString();
                            String name = it.get("name").getAsString().replaceAll("§.", "").trim();
                            if (name.isEmpty()) continue;
                            nextNames.put(name, id);
                            if (it.has("npc_sell_price")) {
                                try { nextNpc.put(id, it.get("npc_sell_price").getAsDouble()); } catch (Exception ignored) {}
                            }
                        }
                        synchronized (nameToId) { nameToId.clear(); nameToId.putAll(nextNames); }
                        synchronized (npcSellPrice) { npcSellPrice.clear(); npcSellPrice.putAll(nextNpc); }
                        loaded = true;
                        System.out.println("[SkyblockItems] loaded " + nextNames.size() + " items (" + nextNpc.size() + " with NPC sell price)");
                        // Re-apply price mode in case CroesusPrices already loaded the bazaar
                        fishmod.features.croesus.CroesusPrices.applyPriceMode();
                    } catch (Exception ignored) {
                    } finally {
                        loading.set(false);
                    }
                })
                .exceptionally(t -> { loading.set(false); return null; });
    }

    /** @return canonical id for the given display name, or null if not loaded/unknown */
    public static String idFor(String name) {
        if (name == null) return null;
        synchronized (nameToId) {
            return nameToId.get(name);
        }
    }

    public static boolean isLoaded() { return loaded; }

    public static double npcSellPriceFor(String id) {
        if (id == null) return 0;
        synchronized (npcSellPrice) {
            Double v = npcSellPrice.get(id);
            return v == null ? 0 : v;
        }
    }
    public static Map<String, Double> npcSellPriceMap() {
        synchronized (npcSellPrice) { return new HashMap<>(npcSellPrice); }
    }
}
