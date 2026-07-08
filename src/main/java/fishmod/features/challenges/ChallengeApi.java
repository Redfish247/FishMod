package fishmod.features.challenges;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fishmod.utils.debug.Debug;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Thin Hypixel profile fetcher tailored to challenges. Talks to FishMod's worker proxy
 * (same one HypixelApi uses) and parses skills / pets / slayers / dungeons / collections
 * into a ProfileSnapshot.
 */
public class ChallengeApi {

    private static final String DEFAULT_PROXY_URL = "https://fishmod.redfish2471.workers.dev";
    private static final String MOD_TOKEN = "fishmod123";

    /** Base URL for the /challenges/* endpoints (submit + leaderboard). Falls back to default. */
    private static String challengeBase() {
        String o = fishmod.utils.config.values.FishSettings.challengeWorkerOverride;
        return (o != null && !o.isBlank()) ? o.replaceAll("/+$", "") : DEFAULT_PROXY_URL;
    }
    /** Base URL for the /skyblock/profiles endpoint (always the main worker). */
    private static final String PROXY_URL = DEFAULT_PROXY_URL;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Pet XP table (cumulative) — common levels for COMMON-LEGENDARY pets, level 1-100.
     *  Exact tables vary by rarity offset but the common path is good enough for "what level is this pet". */
    private static final long[] PET_XP_TABLE = buildPetXpTable();
    private static long[] buildPetXpTable() {
        // Cumulative table approximation (legendary, lvl 1-100). Source: SkyBlock wiki.
        long[] t = new long[101];
        long[] diffs = {
            100,110,120,130,145,160,175,190,210,230, 250,275,300,330,360,400,440,490,540,600,
            660,730,800,880,960,1050,1150,1260,1380,1510, 1650,1800,1960,2130,2310,2500,2700,2920,3160,3420,
            3700,4000,4350,4750,5200,5700,6300,7000,7800,8700, 9700,10800,12000,13300,14700,16200,17800,19500,21300,23200,
            25200,27400,29800,32400,35200,38200,41400,44800,48400,52200, 56200,60400,64800,69400,74200,79200,84700,90700,97200,104200,
            111700,119700,128200,137200,146700,156700,167200,178200,189700,201700, 214700,228700,243700,259700,276700,294700,313700,333700,354700,376700
        };
        long acc = 0;
        for (int i = 1; i <= 100; i++) { acc += diffs[i-1]; t[i] = acc; }
        return t;
    }

    /** Convert pet experience to level 1-100 (approximation for legendary scale). */
    public static int petLevelFromXp(long xp) {
        for (int i = PET_XP_TABLE.length - 1; i >= 1; i--) if (xp >= PET_XP_TABLE[i]) return i;
        return 1;
    }

    /**
     * Display name to submit to the leaderboard.
     * Returns the cosmetic /nick raw text (with &-codes intact) when active, else the IGN.
     * Renderers must parse this via {@link #renderName(String)} to get true-color Text.
     */
    public static String displayName() {
        try {
            if (fishmod.cosmetic.NickState.isActive()) {
                String raw = fishmod.cosmetic.NickState.getRaw();
                if (raw != null && !raw.isBlank()) return raw;
            }
        } catch (Throwable ignored) {}
        Minecraft mc = Minecraft.getInstance();
        return (mc.player != null) ? mc.player.getName().getString() : "?";
    }

    /**
     * Build a properly styled Text from a stored leaderboard name. Handles &-codes,
     * &#rrggbb hex, and §-codes via {@link fishmod.cosmetic.NickState#parse(String)}.
     */
    public static net.minecraft.network.chat.Component renderName(String stored) {
        if (stored == null || stored.isEmpty()) return net.minecraft.network.chat.Component.literal("?");
        try { return fishmod.cosmetic.NickState.parse(stored); }
        catch (Throwable ignored) { return net.minecraft.network.chat.Component.literal(stored); }
    }

    public interface SnapshotCallback { void onData(ProfileSnapshot snap); }
    /** Last failure reason, exposed for chat diagnostics. */
    public static volatile String lastFetchError = "";

    /** Fetches the local player's selected profile and parses a ProfileSnapshot. */
    public static void fetchLocal(SnapshotCallback cb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { lastFetchError = "no player"; cb.onData(null); return; }
        String uuid = mc.player.getUUID().toString().replace("-", "");
        fetchByUuid(uuid, cb);
    }

    public static void fetchByUuid(String uuid, SnapshotCallback cb) {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                    .uri(URI.create(PROXY_URL + "/skyblock/profiles?uuid=" + uuid))
                    .header("X-FishMod-Token", MOD_TOKEN)
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12))
                    .GET().build();
        } catch (Exception e) { lastFetchError = "req build: " + e; cb.onData(null); return; }

        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    try {
                        if (resp.statusCode() != 200) {
                            lastFetchError = "http " + resp.statusCode();
                            cb.onData(null); return;
                        }
                        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                        if (!root.has("success") || !root.get("success").getAsBoolean()) {
                            lastFetchError = "proxy success=false" +
                                    (root.has("cause") ? " (" + root.get("cause").getAsString() + ")" : "");
                            cb.onData(null); return;
                        }
                        if (!root.has("profiles") || root.get("profiles").isJsonNull()) {
                            lastFetchError = "no profiles array";
                            cb.onData(null); return;
                        }
                        JsonObject anyProfile = null;
                        for (JsonElement pe : root.getAsJsonArray("profiles")) {
                            JsonObject p = pe.getAsJsonObject();
                            if (anyProfile == null) anyProfile = p;
                            if (!p.has("selected") || !p.get("selected").getAsBoolean()) continue;
                            JsonObject member = p.getAsJsonObject("members").getAsJsonObject(uuid);
                            cb.onData(parse(p, member));
                            return;
                        }
                        // No selected profile — fall back to first one (some accounts have none flagged).
                        if (anyProfile != null && anyProfile.has("members")
                                && anyProfile.getAsJsonObject("members").has(uuid)) {
                            JsonObject member = anyProfile.getAsJsonObject("members").getAsJsonObject(uuid);
                            cb.onData(parse(anyProfile, member));
                            return;
                        }
                        lastFetchError = "no selected profile";
                        cb.onData(null);
                    } catch (Exception e) {
                        lastFetchError = "parse: " + e;
                        Debug.LOGGER.warn("[Challenges] parse fail: {}", e.toString());
                        cb.onData(null);
                    }
                })
                .exceptionally(ex -> { lastFetchError = "net: " + ex; cb.onData(null); return null; });
    }

    private static ProfileSnapshot parse(JsonObject profile, JsonObject member) {
        ProfileSnapshot s = new ProfileSnapshot();
        s.fetchedAtMs = System.currentTimeMillis();

        // ── Skills ───────────────────────────────────────────────────────────
        // Newer profiles use player_data.experience.SKILL_*; legacy used experience_skill_*.
        try {
            JsonObject expSrc = null;
            if (member.has("player_data") && member.getAsJsonObject("player_data").has("experience"))
                expSrc = member.getAsJsonObject("player_data").getAsJsonObject("experience");
            if (expSrc != null) {
                for (Map.Entry<String, JsonElement> e : expSrc.entrySet()) {
                    String k = e.getKey();
                    if (!k.startsWith("SKILL_")) continue;
                    String name = k.substring("SKILL_".length()).toLowerCase();
                    try { s.skillXp.put(name, (long) e.getValue().getAsDouble()); } catch (Exception ignored) {}
                }
            }
            for (Map.Entry<String, JsonElement> e : member.entrySet()) {
                if (!e.getKey().startsWith("experience_skill_")) continue;
                String name = e.getKey().substring("experience_skill_".length()).toLowerCase();
                try { s.skillXp.putIfAbsent(name, (long) e.getValue().getAsDouble()); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // ── Pets ─────────────────────────────────────────────────────────────
        // pets_data.pets[] — each has type, tier, exp, active
        try {
            if (member.has("pets_data") && member.getAsJsonObject("pets_data").has("pets")) {
                JsonArray pets = member.getAsJsonObject("pets_data").getAsJsonArray("pets");
                for (JsonElement pe : pets) {
                    JsonObject p = pe.getAsJsonObject();
                    String type = p.has("type") ? p.get("type").getAsString() : null;
                    long exp = 0; try { exp = (long) p.get("exp").getAsDouble(); } catch (Exception ignored) {}
                    if (type == null) continue;
                    int lvl = petLevelFromXp(exp);
                    // Match across all rarities — leveling any tier of the pet counts.
                    s.petLevels.merge(type, lvl, Math::max);
                }
            }
        } catch (Exception ignored) {}

        // ── Slayers ──────────────────────────────────────────────────────────
        try {
            if (member.has("slayer") && member.getAsJsonObject("slayer").has("slayer_bosses")) {
                JsonObject bosses = member.getAsJsonObject("slayer").getAsJsonObject("slayer_bosses");
                for (Map.Entry<String, JsonElement> e : bosses.entrySet()) {
                    JsonObject b = e.getValue().getAsJsonObject();
                    long xp = b.has("xp") ? b.get("xp").getAsLong() : 0;
                    s.slayerXp.put(e.getKey(), xp);
                }
            } else if (member.has("slayer_bosses")) {
                JsonObject bosses = member.getAsJsonObject("slayer_bosses");
                for (Map.Entry<String, JsonElement> e : bosses.entrySet()) {
                    JsonObject b = e.getValue().getAsJsonObject();
                    long xp = b.has("xp") ? b.get("xp").getAsLong() : 0;
                    s.slayerXp.put(e.getKey(), xp);
                }
            }
        } catch (Exception ignored) {}

        // ── Dungeons ─────────────────────────────────────────────────────────
        try {
            if (member.has("dungeons")) {
                JsonObject d = member.getAsJsonObject("dungeons");
                if (d.has("dungeon_types")) {
                    JsonObject types = d.getAsJsonObject("dungeon_types");
                    if (types.has("catacombs")) {
                        JsonObject cata = types.getAsJsonObject("catacombs");
                        if (cata.has("experience")) s.cataXp = cata.get("experience").getAsLong();
                        if (cata.has("tier_completions"))
                            for (Map.Entry<String, JsonElement> e : cata.getAsJsonObject("tier_completions").entrySet()) {
                                int f = parseFloor(e.getKey());
                                if (f >= 0) s.floorCompletions.merge(f == 0 ? "E" : "F" + f, e.getValue().getAsLong(), Long::sum);
                            }
                    }
                    if (types.has("master_catacombs")) {
                        JsonObject mc = types.getAsJsonObject("master_catacombs");
                        if (mc.has("tier_completions"))
                            for (Map.Entry<String, JsonElement> e : mc.getAsJsonObject("tier_completions").entrySet()) {
                                int f = parseFloor(e.getKey());
                                if (f >= 1) s.floorCompletions.merge("M" + f, e.getValue().getAsLong(), Long::sum);
                            }
                    }
                }
            }
        } catch (Exception ignored) {}

        // ── Pets at 100 (count) ──────────────────────────────────────────────
        for (int lvl : s.petLevels.values()) if (lvl >= 100) s.petsAt100++;

        // ── SkyBlock XP + level ──────────────────────────────────────────────
        try {
            if (member.has("leveling") && member.getAsJsonObject("leveling").has("experience")) {
                s.skyblockXp = (long) member.getAsJsonObject("leveling").get("experience").getAsDouble();
                s.sbLevel = (int) (s.skyblockXp / 100);
            }
        } catch (Exception ignored) {}

        // ── Heart of the Mountain ────────────────────────────────────────────
        try {
            if (member.has("mining_core")) {
                JsonObject mc = member.getAsJsonObject("mining_core");
                if (mc.has("experience")) {
                    long mxp = (long) mc.get("experience").getAsDouble();
                    // HOTM xp table (cumulative): 0,3000,9000,25000,60000,100000,150000,210000,290000,400000,550000
                    long[] hotm = {0L, 3000L, 9000L, 25000L, 60000L, 100000L, 150000L, 210000L, 290000L, 400000L, 550000L};
                    int lvl = 0;
                    for (int i = hotm.length - 1; i >= 0; i--) if (mxp >= hotm[i]) { lvl = i; break; }
                    s.hotmLevel = lvl;
                }
            }
        } catch (Exception ignored) {}

        // ── Magical Power ────────────────────────────────────────────────────
        try {
            if (member.has("accessory_bag_storage")) {
                JsonObject abs = member.getAsJsonObject("accessory_bag_storage");
                if (abs.has("highest_magical_power"))
                    s.magicalPower = (int) abs.get("highest_magical_power").getAsDouble();
                else if (abs.has("magical_power"))
                    s.magicalPower = (int) abs.get("magical_power").getAsDouble();
            }
        } catch (Exception ignored) {}

        // ── Fairy souls ──────────────────────────────────────────────────────
        try {
            if (member.has("fairy_soul") && member.getAsJsonObject("fairy_soul").has("total_collected"))
                s.fairySouls = member.getAsJsonObject("fairy_soul").get("total_collected").getAsInt();
            else if (member.has("fairy_souls_collected"))
                s.fairySouls = member.get("fairy_souls_collected").getAsInt();
        } catch (Exception ignored) {}

        // ── Dungeon class levels ─────────────────────────────────────────────
        try {
            if (member.has("dungeons") && member.getAsJsonObject("dungeons").has("player_classes")) {
                JsonObject cls = member.getAsJsonObject("dungeons").getAsJsonObject("player_classes");
                long[] catalike = fishmod.utils.HypixelApi.CATA_XP_TABLE;
                for (String name : new String[]{"healer","mage","berserk","archer","tank"}) {
                    if (!cls.has(name)) continue;
                    JsonObject c = cls.getAsJsonObject(name);
                    if (!c.has("experience")) continue;
                    long xp = (long) c.get("experience").getAsDouble();
                    int lvl = 0;
                    for (int i = catalike.length - 1; i >= 0; i--) if (xp >= catalike[i]) { lvl = i; break; }
                    s.classLevels.put(name, lvl);
                }
            }
        } catch (Exception ignored) {}

        // ── Purse + bank ─────────────────────────────────────────────────────
        try {
            long purse = 0;
            if (member.has("currencies") && member.getAsJsonObject("currencies").has("coin_purse"))
                purse = (long) member.getAsJsonObject("currencies").get("coin_purse").getAsDouble();
            else if (member.has("coin_purse"))
                purse = (long) member.get("coin_purse").getAsDouble();
            long bank = 0;
            if (profile.has("banking") && profile.getAsJsonObject("banking").has("balance"))
                bank = (long) profile.getAsJsonObject("banking").get("balance").getAsDouble();
            s.purseAndBank = purse + bank;
        } catch (Exception ignored) {}

        // ── Collections (amounts; we treat amount as a rough progress signal) ─
        try {
            if (member.has("collection")) {
                JsonObject c = member.getAsJsonObject("collection");
                for (Map.Entry<String, JsonElement> e : c.entrySet()) {
                    try { s.collections.put(e.getKey(), e.getValue().getAsLong()); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        return s;
    }

    private static int parseFloor(String key) {
        try {
            if (key.matches("\\d+"))      return Integer.parseInt(key);
            if (key.startsWith("floor_")) return Integer.parseInt(key.substring(6));
        } catch (Exception ignored) {}
        return -1;
    }

    // ── Leaderboard worker endpoints ─────────────────────────────────────────

    public interface SubmitCallback { void onResult(boolean ok, long newTotal, int rank); }
    /** Last submit failure detail (HTTP status / body snippet) — for diagnostics. */
    public static volatile String lastSubmitError = "";

    public static void submitScore(String uuid, String name, String challengeId, Tier tier,
                                   int points, long activeMs, SubmitCallback cb) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("uuid", uuid);
            body.addProperty("name", name);
            body.addProperty("challengeId", challengeId);
            body.addProperty("tier", tier.name());
            body.addProperty("points", points);
            body.addProperty("activeMs", activeMs);
            body.addProperty("completedAt", System.currentTimeMillis());
            String url = challengeBase() + "/challenges/submit";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-FishMod-Token", MOD_TOKEN)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> {
                        int code = resp.statusCode();
                        String snippet = resp.body() == null ? "" : resp.body();
                        if (snippet.length() > 180) snippet = snippet.substring(0, 180) + "…";
                        try {
                            JsonObject r = JsonParser.parseString(resp.body()).getAsJsonObject();
                            boolean ok = r.has("ok") && r.get("ok").getAsBoolean();
                            if (!ok) lastSubmitError = "http " + code + " body=" + snippet;
                            cb.onResult(ok,
                                    r.has("newTotal") ? r.get("newTotal").getAsLong() : 0,
                                    r.has("rank") ? r.get("rank").getAsInt() : -1);
                        } catch (Exception e) {
                            lastSubmitError = "http " + code + " (non-JSON) body=" + snippet;
                            cb.onResult(false, 0, -1);
                        }
                    })
                    .exceptionally(ex -> { lastSubmitError = "net: " + ex; cb.onResult(false, 0, -1); return null; });
        } catch (Exception e) { lastSubmitError = "req: " + e; cb.onResult(false, 0, -1); }
    }

    /** Hit the worker's /health endpoint. Returns status code (-1 on network error) and body snippet. */
    public interface PingCallback { void onResult(int statusCode, String bodySnippet); }
    public static void pingWorker(PingCallback cb) {
        String url = challengeBase() + "/health";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> {
                        String b = resp.body() == null ? "" : resp.body();
                        if (b.length() > 200) b = b.substring(0, 200) + "…";
                        cb.onResult(resp.statusCode(), b);
                    })
                    .exceptionally(ex -> { cb.onResult(-1, ex.toString()); return null; });
        } catch (Exception e) { cb.onResult(-1, e.toString()); }
    }

    public static class LbEntry {
        public String uuid; public String name; public long totalPoints; public int dailyCount, weeklyCount, monthlyCount;
    }
    public interface LbCallback { void onData(java.util.List<LbEntry> entries); }

    public static void fetchLeaderboard(int top, LbCallback cb) {
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                    .uri(URI.create(challengeBase() + "/challenges/leaderboard?top=" + top))
                    .header("X-FishMod-Token", MOD_TOKEN)
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();
        } catch (Exception e) { cb.onData(java.util.List.of()); return; }
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    java.util.List<LbEntry> out = new java.util.ArrayList<>();
                    try {
                        JsonObject r = JsonParser.parseString(resp.body()).getAsJsonObject();
                        if (r.has("entries")) for (JsonElement e : r.getAsJsonArray("entries")) {
                            JsonObject o = e.getAsJsonObject();
                            LbEntry x = new LbEntry();
                            x.uuid = o.has("uuid") ? o.get("uuid").getAsString() : "";
                            x.name = o.has("name") ? o.get("name").getAsString() : "?";
                            x.totalPoints = o.has("totalPoints") ? o.get("totalPoints").getAsLong() : 0;
                            x.dailyCount = o.has("dailyCount") ? o.get("dailyCount").getAsInt() : 0;
                            x.weeklyCount = o.has("weeklyCount") ? o.get("weeklyCount").getAsInt() : 0;
                            x.monthlyCount = o.has("monthlyCount") ? o.get("monthlyCount").getAsInt() : 0;
                            out.add(x);
                        }
                    } catch (Exception ignored) {}
                    cb.onData(out);
                })
                .exceptionally(ex -> { cb.onData(java.util.List.of()); return null; });
    }
}
