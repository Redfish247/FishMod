package blade.addon.utils;

import blade.addon.utils.config.values.FishSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class HypixelApi {

    /** Cumulative XP required for each catacombs / class level (index = level 0-50) */
    public static final long[] CATA_XP_TABLE = {
        0L, 50L, 125L, 235L, 395L, 625L, 955L, 1_425L, 2_095L, 3_045L,
        4_385L, 6_275L, 8_940L, 12_700L, 17_960L, 25_340L, 35_640L, 50_040L, 70_040L, 97_640L,
        135_640L, 188_140L, 259_640L, 356_640L, 488_640L, 668_640L, 911_640L, 1_239_640L, 1_684_640L, 2_284_640L,
        3_084_640L, 4_149_640L, 5_559_640L, 7_459_640L, 9_959_640L, 13_259_640L, 17_559_640L, 23_159_640L, 30_359_640L, 39_559_640L,
        51_559_640L, 66_559_640L, 85_559_640L, 109_559_640L, 139_559_640L, 177_559_640L, 225_559_640L, 285_559_640L, 360_559_640L, 453_559_640L,
        569_809_640L
    };

    public static final long XP_FOR_50 = CATA_XP_TABLE[50];

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    // ─── persistent cache ──────────────────────────────────────────────────────
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L; // 30 minutes
    /** player name (case-sensitive as returned by Mojang) → UUID without dashes */
    public  static final Map<String, String> uuidByName    = new ConcurrentHashMap<>();
    /** player name → epoch-ms when DungeonData was last fetched */
    public  static final Map<String, Long>   dataTimestamp = new ConcurrentHashMap<>();

    public static void loadPfCache(Map<String, DungeonData> liveCache) {
        try {
            var file = FabricLoader.getInstance().getConfigDir().resolve("fishmod_pf_cache.json");
            if (!Files.exists(file)) return;
            JsonObject root    = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonObject entries = root.getAsJsonObject("entries");
            long now = System.currentTimeMillis();
            for (Map.Entry<String, JsonElement> e : entries.entrySet()) {
                String     name = e.getKey();
                JsonObject obj  = e.getValue().getAsJsonObject();
                long ts = obj.get("timestamp").getAsLong();
                if (now - ts > CACHE_TTL_MS) continue; // stale — skip
                if (obj.has("uuid")) uuidByName.put(name, obj.get("uuid").getAsString());
                dataTimestamp.put(name, ts);
                DungeonData d = new DungeonData();
                if (obj.has("cataXp"))       d.cataXp       = obj.get("cataXp").getAsLong();
                if (obj.has("cataLevel"))    d.cataLevel    = obj.get("cataLevel").getAsInt();
                if (obj.has("totalSecrets")) d.totalSecrets = obj.get("totalSecrets").getAsLong();
                if (obj.has("totalRuns"))    d.totalRuns    = obj.get("totalRuns").getAsLong();
                if (obj.has("secretAverage") && !obj.get("secretAverage").isJsonNull())
                    d.secretAverage = obj.get("secretAverage").getAsString();
                if (obj.has("cataPbs")) {
                    JsonArray arr = obj.getAsJsonArray("cataPbs");
                    for (int i = 0; i < Math.min(arr.size(), 8); i++)
                        d.cataPbs[i] = arr.get(i).isJsonNull() ? null : arr.get(i).getAsString();
                }
                if (obj.has("masterPbs")) {
                    JsonArray arr = obj.getAsJsonArray("masterPbs");
                    for (int i = 0; i < Math.min(arr.size(), 8); i++)
                        d.masterPbs[i] = arr.get(i).isJsonNull() ? null : arr.get(i).getAsString();
                }
                liveCache.put(name, d);
            }
        } catch (Exception ignored) {}
    }

    public static void savePfCacheAsync(Map<String, DungeonData> liveCache) {
        CompletableFuture.runAsync(() -> {
            try {
                var file = FabricLoader.getInstance().getConfigDir().resolve("fishmod_pf_cache.json");
                JsonObject root    = new JsonObject();
                JsonObject entries = new JsonObject();
                for (Map.Entry<String, DungeonData> e : liveCache.entrySet()) {
                    String     name = e.getKey();
                    DungeonData d   = e.getValue();
                    Long ts = dataTimestamp.get(name);
                    if (ts == null) continue;
                    JsonObject obj = new JsonObject();
                    String uuid = uuidByName.get(name);
                    if (uuid != null) obj.addProperty("uuid", uuid);
                    obj.addProperty("timestamp",    ts);
                    obj.addProperty("cataXp",       d.cataXp);
                    obj.addProperty("cataLevel",    d.cataLevel);
                    obj.addProperty("totalSecrets", d.totalSecrets);
                    obj.addProperty("totalRuns",    d.totalRuns);
                    if (d.secretAverage != null) obj.addProperty("secretAverage", d.secretAverage);
                    else obj.add("secretAverage", JsonNull.INSTANCE);
                    JsonArray cataPbs = new JsonArray();
                    for (String pb : d.cataPbs)   { if (pb != null) cataPbs.add(pb); else cataPbs.add(JsonNull.INSTANCE); }
                    obj.add("cataPbs", cataPbs);
                    JsonArray masterPbs = new JsonArray();
                    for (String pb : d.masterPbs) { if (pb != null) masterPbs.add(pb); else masterPbs.add(JsonNull.INSTANCE); }
                    obj.add("masterPbs", masterPbs);
                    entries.add(name, obj);
                }
                root.addProperty("version", 1);
                root.add("entries", entries);
                Files.writeString(file, root.toString());
            } catch (Exception ignored) {}
        });
    }

    public static class DungeonData {
        public long cataXp;
        public int  cataLevel;       // computed from cataXp
        public long totalSecrets;
        public long totalRuns;
        public String secretAverage; // "9.5", null if no runs
        public Map<String, Long> classXp = new HashMap<>();
        // Index 0-7: 0=Entrance/E, 1-7=F1-F7 for cata; 1-7=M1-M7 for master. null = no PB.
        public String[] cataPbs   = new String[8];
        public String[] masterPbs = new String[8];
    }

    public interface DungeonDataCallback {
        void onData(DungeonData data);
    }

    // ─── entry points ─────────────────────────────────────────────────────────

    /**
     * Silent Party Finder lookup — requires API key.
     * Flow: Ashcon (UUID, fast/cached) → Hypixel profiles (stats).
     * Falls back to Mojang for UUID if Ashcon fails.
     * Always calls callback so pending is never stuck.
     */
    public static void getByNameSilent(String ign, DungeonDataCallback callback) {
        String key = FishSettings.hypixelApiKey;
        if (key == null || key.isBlank()) { callback.onData(new DungeonData()); return; }

        // Fast path: UUID already known — skip Ashcon/Mojang lookup entirely
        String cachedUuid = uuidByName.get(ign);
        if (cachedUuid != null) { fetchProfilesSilent(cachedUuid, callback); return; }

        fetchAsync("https://api.ashcon.app/mojang/v2/user/" + ign)
            .thenAccept(body -> {
                try {
                    JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
                    if (obj.has("uuid")) {
                        String uuid = obj.get("uuid").getAsString().replace("-", "");
                        uuidByName.put(ign, uuid);
                        fetchProfilesSilent(uuid, callback); return;
                    }
                } catch (Exception ignored) {}
                // Ashcon failed — fall back to Mojang
                fetchAsync("https://api.mojang.com/users/profiles/minecraft/" + ign)
                    .thenAccept(body2 -> {
                        try {
                            JsonObject obj2 = JsonParser.parseString(body2).getAsJsonObject();
                            if (obj2.has("id")) {
                                String uuid = obj2.get("id").getAsString();
                                uuidByName.put(ign, uuid);
                                fetchProfilesSilent(uuid, callback); return;
                            }
                        } catch (Exception ignored) {}
                        callback.onData(new DungeonData());
                    })
                    .exceptionally(e -> { callback.onData(new DungeonData()); return null; });
            })
            .exceptionally(e -> {
                fetchAsync("https://api.mojang.com/users/profiles/minecraft/" + ign)
                    .thenAccept(body2 -> {
                        try {
                            JsonObject obj2 = JsonParser.parseString(body2).getAsJsonObject();
                            if (obj2.has("id")) {
                                String uuid = obj2.get("id").getAsString();
                                uuidByName.put(ign, uuid);
                                fetchProfilesSilent(uuid, callback); return;
                            }
                        } catch (Exception ignored) {}
                        callback.onData(new DungeonData());
                    })
                    .exceptionally(e2 -> { callback.onData(new DungeonData()); return null; });
                return null;
            });
    }

    private static void fetchProfilesSilent(String uuidStr, DungeonDataCallback callback) {
        String key = FishSettings.hypixelApiKey;
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.hypixel.net/v2/skyblock/profiles?uuid=" + uuidStr))
                .header("API-Key", key)
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        } catch (Exception e) { callback.onData(new DungeonData()); return; }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept(resp -> {
                try {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (!root.get("success").getAsBoolean()) { callback.onData(new DungeonData()); return; }
                    for (JsonElement profileEl : root.getAsJsonArray("profiles")) {
                        JsonObject profile = profileEl.getAsJsonObject();
                        if (!profile.has("selected") || !profile.get("selected").getAsBoolean()) continue;
                        JsonObject members = profile.getAsJsonObject("members");
                        if (!members.has(uuidStr)) continue;
                        JsonObject member = members.getAsJsonObject(uuidStr);
                        if (!member.has("dungeons")) continue;
                        callback.onData(parseDungeonData(uuidStr, member.getAsJsonObject("dungeons")));
                        return;
                    }
                } catch (Exception ignored) {}
                callback.onData(new DungeonData());
            })
            .exceptionally(e -> { callback.onData(new DungeonData()); return null; });
    }

    private static java.util.concurrent.CompletableFuture<String> fetchAsync(String url) {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Mozilla/5.0")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body);
    }


    /** Look up by IGN: Mojang UUID lookup → Hypixel profiles. */
    public static void getByName(MinecraftClient mc, String ign, DungeonDataCallback callback) {
        if (!checkKey(mc)) return;
        mc.send(() -> Misc.addChatMessage(Text.literal("§7Looking up " + ign + "...")));

        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + ign))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        } catch (Exception e) { return; }

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept(resp -> {
                try {
                    JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (!obj.has("id")) {
                        mc.send(() -> Misc.addChatMessage(Text.literal("§cPlayer not found: " + ign)));
                        return;
                    }
                    String uuid = obj.get("id").getAsString(); // no dashes
                    fetchProfiles(mc, uuid, callback);
                } catch (Exception e) {
                    mc.send(() -> Misc.addChatMessage(Text.literal("§cMojang lookup failed.")));
                }
            })
            .exceptionally(e -> { mc.send(() -> Misc.addChatMessage(Text.literal("§cMojang lookup failed."))); return null; });
    }

    /** Look up by local player's own UUID (no Mojang step needed). */
    public static void getPlayerDungeonData(MinecraftClient mc, DungeonDataCallback callback) {
        if (!checkKey(mc)) return;
        if (mc.player == null) return;
        String uuid = mc.player.getUuid().toString().replace("-", "");
        mc.send(() -> Misc.addChatMessage(Text.literal("§7Fetching Hypixel data...")));
        fetchProfiles(mc, uuid, callback);
    }

    // ─── internal ─────────────────────────────────────────────────────────────

    private static DungeonData parseDungeonData(String uuidStr, JsonObject dungeons) {
        DungeonData result = new DungeonData();

        if (dungeons.has("dungeon_types")) {
            JsonObject types = dungeons.getAsJsonObject("dungeon_types");
            if (types.has("catacombs")) {
                JsonObject cata = types.getAsJsonObject("catacombs");
                if (cata.has("experience"))
                    result.cataXp = cata.get("experience").getAsLong();
                for (int f = 0; f <= 7; f++)
                    result.cataPbs[f] = extractFloorPb(cata, f);
            }

            // Secrets per run
            long totalRuns = 0;
            for (String t : new String[]{"catacombs", "master_catacombs"}) {
                if (types.has(t)) {
                    JsonObject dt = types.getAsJsonObject(t);
                    if (dt.has("times_played")) {
                        for (Map.Entry<String, JsonElement> e : dt.getAsJsonObject("times_played").entrySet()) {
                            if (!e.getKey().matches("\\d+")) continue; // only count floor number keys
                            totalRuns += e.getValue().getAsLong();
                        }
                    }
                }
            }
            long totalSecrets = dungeons.has("secrets") ? dungeons.get("secrets").getAsLong() : 0;
            result.totalSecrets = totalSecrets;
            result.totalRuns    = totalRuns;
            if (totalRuns > 0) {
                result.secretAverage = String.format("%.1f", (double) totalSecrets / totalRuns);
            }
            result.cataLevel = calcCataLevel(result.cataXp);

            if (types.has("master_catacombs")) {
                JsonObject mc = types.getAsJsonObject("master_catacombs");
                for (int f = 1; f <= 7; f++)
                    result.masterPbs[f] = extractFloorPb(mc, f);
            }
        }

        if (dungeons.has("player_classes")) {
            JsonObject classes = dungeons.getAsJsonObject("player_classes");
            for (String cls : new String[]{"healer", "mage", "berserk", "archer", "tank"}) {
                if (classes.has(cls)) {
                    JsonObject c = classes.getAsJsonObject(cls);
                    if (c.has("experience"))
                        result.classXp.put(cls, c.get("experience").getAsLong());
                }
            }
        }

        return result;
    }

    public static int calcCataLevel(long xp) {
        for (int i = CATA_XP_TABLE.length - 1; i >= 0; i--) {
            if (xp >= CATA_XP_TABLE[i]) {
                if (i == CATA_XP_TABLE.length - 1)
                    return (int)(i + (xp - CATA_XP_TABLE[i]) / 200_000_000L);
                return i;
            }
        }
        return 0;
    }

    private static String extractFloorPb(JsonObject dungeonType, int floor) {
        String floorKey = String.valueOf(floor); // Hypixel API uses "7" not "floor_7"
        for (String[] pair : new String[][]{{"fastest_time_s_plus", "S+"}, {"fastest_time_s", "S"}}) {
            if (dungeonType.has(pair[0])) {
                JsonObject times = dungeonType.getAsJsonObject(pair[0]);
                if (times.has(floorKey)) {
                    long ms = times.get(floorKey).getAsLong();
                    long s = ms / 1000;
                    return String.format("%d:%02d %s", s / 60, s % 60, pair[1]);
                }
            }
        }
        return null;
    }

    private static boolean checkKey(MinecraftClient mc) {
        String key = FishSettings.hypixelApiKey;
        if (key == null || key.isBlank()) {
            mc.send(() -> Misc.addChatMessage(Text.literal("§cSet your Hypixel API key in /fm → Party Commands.")));
            return false;
        }
        return true;
    }

    private static void fetchProfiles(MinecraftClient mc, String uuidStr, DungeonDataCallback callback) {
        String key = FishSettings.hypixelApiKey;
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.hypixel.net/v2/skyblock/profiles?uuid=" + uuidStr))
                .header("API-Key", key)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        } catch (Exception e) { return; }

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept(resp -> {
                try {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (!root.get("success").getAsBoolean()) {
                        mc.send(() -> Misc.addChatMessage(Text.literal("§cHypixel API error — check your key.")));
                        return;
                    }
                    for (JsonElement profileEl : root.getAsJsonArray("profiles")) {
                        JsonObject profile = profileEl.getAsJsonObject();
                        if (!profile.has("selected") || !profile.get("selected").getAsBoolean()) continue;
                        JsonObject members = profile.getAsJsonObject("members");
                        if (!members.has(uuidStr)) continue;
                        JsonObject member = members.getAsJsonObject(uuidStr);
                        if (!member.has("dungeons")) continue;
                        JsonObject dungeons = member.getAsJsonObject("dungeons");
                        callback.onData(parseDungeonData(uuidStr, dungeons));
                        return;
                    }
                    mc.send(() -> Misc.addChatMessage(Text.literal("§cNo active Skyblock profile found.")));
                } catch (Exception e) {
                    mc.send(() -> Misc.addChatMessage(Text.literal("§cAPI parse error: " + e.getMessage())));
                }
            })
            .exceptionally(e -> { mc.send(() -> Misc.addChatMessage(Text.literal("§cAPI request failed."))); return null; });
    }

    /** Returns XP needed to reach the next whole cata level, formatted like "142.3k" or "1.23m". */
    public static String xpToNextLevel(long xp) {
        for (int i = 0; i < CATA_XP_TABLE.length - 1; i++) {
            if (xp < CATA_XP_TABLE[i + 1]) {
                long needed = CATA_XP_TABLE[i + 1] - xp;
                if (needed >= 1_000_000) return String.format("%.2fm", needed / 1_000_000.0);
                if (needed >= 1_000)     return String.format("%.1fk", needed / 1_000.0);
                return String.valueOf(needed);
            }
        }
        // Overflow (level 50+): each extra level = 200,000,000 XP
        long overflow = xp - CATA_XP_TABLE[CATA_XP_TABLE.length - 1];
        long needed = 200_000_000L - (overflow % 200_000_000L);
        if (needed >= 1_000_000) return String.format("%.2fm", needed / 1_000_000.0);
        if (needed >= 1_000)     return String.format("%.1fk", needed / 1_000.0);
        return String.valueOf(needed);
    }

    /** Returns level as a formatted string like "42.75" or "51.30" for overflow cata. */
    public static String formatLevel(long xp) {
        for (int i = CATA_XP_TABLE.length - 1; i >= 0; i--) {
            if (xp >= CATA_XP_TABLE[i]) {
                if (i == CATA_XP_TABLE.length - 1) {
                    // Overflow: each extra level = 200,000,000 XP (from adjectils)
                    double overflow = (double)(xp - CATA_XP_TABLE[i]) / 200_000_000.0;
                    return String.format("%.2f", i + overflow);
                }
                double progress = (double)(xp - CATA_XP_TABLE[i]) / (CATA_XP_TABLE[i + 1] - CATA_XP_TABLE[i]);
                return String.format("%d.%02d", i, (int)(progress * 100));
            }
        }
        return "0.00";
    }
}
