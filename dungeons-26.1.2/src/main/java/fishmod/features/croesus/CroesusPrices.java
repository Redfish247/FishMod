package fishmod.features.croesus;

import fishmod.utils.debug.Debug;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class CroesusPrices {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final long TTL_MS = 5 * 60 * 1000L;

    private static final Map<String, Double> bazaar     = new HashMap<>(); // selected mode (rebuilt on swap)
    private static final Map<String, Double> bazaarBuy  = new HashMap<>(); // buyPrice  = highest buy order  = instasell
    private static final Map<String, Double> bazaarSell = new HashMap<>(); // sellPrice = lowest  sell offer = instabuy
    private static final Map<String, Double> lbin      = new HashMap<>();
    private static final Map<String, Double> avgLbin   = new HashMap<>();
    private static final Map<String, Double> coflnet   = new ConcurrentHashMap<>();
    private static final Set<String>         fetching  = ConcurrentHashMap.newKeySet();
    private static volatile long lastBazaar  = 0;
    private static volatile long lastLbin    = 0;
    private static volatile long lastAvgLbin = 0;
    private static volatile CompletableFuture<Void> inFlight = null;

    /** Rebuild the active `bazaar` map from the chosen FishSettings.trackerPriceMode. */
    public static void applyPriceMode() {
        fishmod.utils.config.values.FishSettings.PriceMode mode =
                fishmod.utils.config.values.FishSettings.trackerPriceModeEnum;
        synchronized (bazaar) {
            bazaar.clear();
            if (mode == fishmod.utils.config.values.FishSettings.PriceMode.SELL_OFFER) {
                bazaar.putAll(bazaarSell);                // Sell Offer (instabuy cost)
            } else if (mode == fishmod.utils.config.values.FishSettings.PriceMode.NPC_SELL) {
                // NPC Sell: pulled from SkyblockItems.npcSellPriceFor(id)
                for (Map.Entry<String, Double> e : bazaarBuy.entrySet()) {
                    double npc = fishmod.utils.SkyblockItems.npcSellPriceFor(e.getKey());
                    if (npc > 0) bazaar.put(e.getKey(), npc);
                }
                // Also include items that aren't bazaarable but have NPC price
                for (Map.Entry<String, Double> e : fishmod.utils.SkyblockItems.npcSellPriceMap().entrySet()) {
                    if (!bazaar.containsKey(e.getKey()) && e.getValue() > 0) bazaar.put(e.getKey(), e.getValue());
                }
            } else {
                bazaar.putAll(bazaarBuy);                 // Instasell (default)
            }
        }
    }

    /** Returns best estimated price for a SkyBlock item id, or 0 if unknown.
     *  If all bulk sources miss, kicks off an async coflnet lookup for next time. */
    public static double price(String id) {
        if (id == null || id.isEmpty()) return 0;
        Double b = bazaar.get(id);
        if (b != null && b > 0) return b;
        Double l = lbin.get(id);
        if (l != null && l > 0) return l;
        Double a = avgLbin.get(id);
        if (a != null && a > 0) return a;
        Double c = coflnet.get(id);
        if (c != null && c > 0) return c;
        // Nothing found — fetch from coflnet in the background if not already in flight.
        fetchCoflnetItem(id);
        return 0;
    }

    public static String debugSource(String id) {
        if (id == null) return "null";
        Double b = bazaar.get(id);
        if (b != null && b > 0) return "bazaar=" + b;
        Double l = lbin.get(id);
        if (l != null && l > 0) return "lbin=" + l;
        Double a = avgLbin.get(id);
        if (a != null && a > 0) return "avglbin=" + a;
        Double c = coflnet.get(id);
        if (c != null && c > 0) return "coflnet=" + c;
        return "miss (bz=" + bazaar.size() + " lbin=" + lbin.size() + " avg=" + avgLbin.size() + " cf=" + coflnet.size() + ")";
    }

    private static void fetchCoflnetItem(String id) {
        if (!fetching.add(id)) return; // already in flight
        String url = "https://sky.coflnet.com/api/item/price/" + id;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> {
                    fetching.remove(id);
                    try {
                        if (r.statusCode() != 200) return;
                        JsonObject obj = JsonParser.parseString(r.body()).getAsJsonObject();
                        // Response: {min, median, max, mode, volume}
                        JsonElement med = obj.get("min");
                        if (med == null || med.isJsonNull()) med = obj.get("median");
                        if (med != null && !med.isJsonNull()) {
                            double p = med.getAsDouble();
                            if (p > 0) {
                                coflnet.put(id, p);
                                Debug.LOGGER.info("[CroesusPrices] coflnet {} = {}", id, p);
                            }
                        }
                    } catch (Exception ex) {
                        Debug.LOGGER.warn("[CroesusPrices] coflnet {} error: {}", id, ex.getMessage());
                    }
                }).exceptionally(t -> { fetching.remove(id); return null; });
    }

    public static synchronized CompletableFuture<Void> refreshIfStale() {
        long now = System.currentTimeMillis();
        if (inFlight != null && !inFlight.isDone()) return inFlight;
        boolean needB = now - lastBazaar  > TTL_MS;
        boolean needL = now - lastLbin    > TTL_MS;
        boolean needA = now - lastAvgLbin > TTL_MS;
        if (!needB && !needL && !needA) return CompletableFuture.completedFuture(null);

        CompletableFuture<Void> bz = needB ? fetchBazaar()  : CompletableFuture.completedFuture(null);
        CompletableFuture<Void> lb = needL ? fetchLbin()    : CompletableFuture.completedFuture(null);
        CompletableFuture<Void> av = needA ? fetchAvgLbin() : CompletableFuture.completedFuture(null);
        inFlight = CompletableFuture.allOf(bz, lb, av);
        return inFlight;
    }

    private static CompletableFuture<Void> fetchBazaar() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.hypixel.net/v2/skyblock/bazaar"))
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> {
                    try {
                        if (r.statusCode() != 200) {
                            Debug.LOGGER.warn("[CroesusPrices] bazaar status={}", r.statusCode());
                            return;
                        }
                        JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                        JsonObject products = root.getAsJsonObject("products");
                        if (products == null) { Debug.LOGGER.warn("[CroesusPrices] bazaar: no products"); return; }
                        synchronized (bazaarBuy) { bazaarBuy.clear(); }
                        synchronized (bazaarSell) { bazaarSell.clear(); }
                        for (Map.Entry<String, JsonElement> e : products.entrySet()) {
                            try {
                                if (!e.getValue().isJsonObject()) continue;
                                JsonObject product = e.getValue().getAsJsonObject();
                                JsonObject qs = product.getAsJsonObject("quick_status");
                                if (qs == null) continue;
                                JsonElement bp = qs.get("buyPrice");
                                JsonElement sp = qs.get("sellPrice");
                                if (bp != null && !bp.isJsonNull()) bazaarBuy.put(e.getKey(), bp.getAsDouble());
                                if (sp != null && !sp.isJsonNull()) bazaarSell.put(e.getKey(), sp.getAsDouble());
                            } catch (Exception ignored) {}
                        }
                        applyPriceMode();
                        lastBazaar = System.currentTimeMillis();
                        Debug.LOGGER.info("[CroesusPrices] bazaar loaded {} entries", bazaar.size());
                    } catch (Exception ex) {
                        Debug.LOGGER.warn("[CroesusPrices] bazaar parse error: {}", ex.getMessage());
                    }
                }).exceptionally(t -> { Debug.LOGGER.warn("[CroesusPrices] bazaar fetch error: {}", t.getMessage()); return null; });
    }

    private static CompletableFuture<Void> fetchAvgLbin() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://moulberry.codes/auction_averages_lbin/1day.json"))
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> {
                    try {
                        if (r.statusCode() != 200) return;
                        JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                        Map<String, Double> next = new HashMap<>();
                        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                            if (e.getValue().isJsonNull()) continue;
                            // value is a number directly
                            try { next.put(e.getKey(), e.getValue().getAsDouble()); }
                            catch (Exception ignored) {}
                        }
                        synchronized (avgLbin) { avgLbin.clear(); avgLbin.putAll(next); }
                        lastAvgLbin = System.currentTimeMillis();
                    } catch (Exception ignored) {}
                }).exceptionally(t -> null);
    }

    private static CompletableFuture<Void> fetchLbin() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://moulberry.codes/lowestbin.json"))
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> {
                    try {
                        if (r.statusCode() != 200) { Debug.LOGGER.warn("[CroesusPrices] lbin status={}", r.statusCode()); return; }
                        JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                        Map<String, Double> next = new HashMap<>();
                        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                            if (!e.getValue().isJsonNull())
                                next.put(e.getKey(), e.getValue().getAsDouble());
                        }
                        synchronized (lbin) {
                            lbin.clear();
                            lbin.putAll(next);
                        }
                        lastLbin = System.currentTimeMillis();
                        Debug.LOGGER.info("[CroesusPrices] lbin loaded {} entries", next.size());
                    } catch (Exception ex) {
                        Debug.LOGGER.warn("[CroesusPrices] lbin parse error: {}", ex.getMessage());
                    }
                }).exceptionally(t -> { Debug.LOGGER.warn("[CroesusPrices] lbin fetch error: {}", t.getMessage()); return null; });
    }
}
