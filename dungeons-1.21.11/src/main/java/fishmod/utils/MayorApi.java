package fishmod.utils;

import fishmod.utils.debug.Debug;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class MayorApi {

    private static final String URL = "https://api.hypixel.net/v2/resources/skyblock/election";
    private static final long CACHE_MS = 10 * 60 * 1000L; // 10 minutes
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static volatile boolean aatroxSlayerBonus = false;
    private static volatile boolean paulDungeonBonus  = false;
    private static volatile long lastFetch = 0;

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> refresh());
    }

    public static boolean isAatroxSlayerBonusActive() {
        if (System.currentTimeMillis() - lastFetch > CACHE_MS) refresh();
        return aatroxSlayerBonus;
    }

    /** True if Paul is mayor (or minister with EZPZ perk), giving +10 dungeon score. */
    public static boolean isPaulDungeonBonusActive() {
        if (System.currentTimeMillis() - lastFetch > CACHE_MS) refresh();
        return paulDungeonBonus;
    }

    public static void refresh() {
        lastFetch = System.currentTimeMillis(); // prevent parallel fetches
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(URL))
                        .GET()
                        .timeout(Duration.ofSeconds(8))
                        .build();
                return HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
            } catch (Exception e) {
                Debug.LOGGER.warn("MayorApi: fetch failed - {}", e.getMessage());
                return null;
            }
        }).thenAccept(body -> {
            if (body == null) return;
            try {
                aatroxSlayerBonus = parseAatrox(body);
                paulDungeonBonus  = parsePaul(body);
            } catch (Exception e) {
                Debug.LOGGER.warn("MayorApi: parse failed - {}", e.getMessage());
            }
        });
    }

    private static boolean parseAatrox(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("mayor")) return false;
        JsonObject mayor = root.getAsJsonObject("mayor");
        if ("aatrox".equalsIgnoreCase(mayor.get("key").getAsString())) return true;
        if (mayor.has("minister")) {
            JsonObject minister = mayor.getAsJsonObject("minister");
            if ("aatrox".equalsIgnoreCase(minister.get("key").getAsString())) {
                if (minister.has("perk")) {
                    String perkName = minister.getAsJsonObject("perk")
                            .get("name").getAsString().toLowerCase();
                    return perkName.contains("slayer");
                }
            }
        }
        return false;
    }

    private static boolean parsePaul(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("mayor")) return false;
        JsonObject mayor = root.getAsJsonObject("mayor");
        if ("paul".equalsIgnoreCase(mayor.get("key").getAsString())) return true;
        if (mayor.has("minister")) {
            JsonObject minister = mayor.getAsJsonObject("minister");
            if ("paul".equalsIgnoreCase(minister.get("key").getAsString())) {
                if (minister.has("perk")) {
                    String perkName = minister.getAsJsonObject("perk")
                            .get("name").getAsString().toLowerCase();
                    // Paul's dungeon bonus perk is "EZPZ" (+10 dungeon score)
                    return perkName.contains("ezpz");
                }
            }
        }
        return false;
    }
}
