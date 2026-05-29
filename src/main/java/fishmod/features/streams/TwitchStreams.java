package fishmod.features.streams;

import fishmod.utils.debug.Debug;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches live Hypixel SkyBlock Twitch streams via Twitch's public web GraphQL
 * endpoint (the same no-auth approach used by streamlink/yt-dlp). Matches streams
 * whose TITLE contains "skyblock".
 */
public class TwitchStreams {

    // FishMod proxy (Cloudflare Worker) — holds the Twitch API keys server-side as
    // encrypted secrets and returns the filtered SkyBlock stream list.
    private static final String PROXY_URL = "https://fishmod.redfish2471.workers.dev/twitch/streams";
    private static final String MOD_TOKEN = "fishmod123";

    // Twitch's well-known public web client id — used only by the no-login GQL fallback.
    private static final String CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko";
    private static final String GQL_URL   = "https://gql.twitch.tv/gql";

    // Twitch's API can't return "all streams whose title contains X", so we cast a wide
    // net: several relevance searches + the top of the Minecraft directory, then dedupe.
    private static final String[] SEARCH_TERMS = {
            "hypixel skyblock", "skyblock", "hypixel sky block", "sky block", "hypixel"
    };

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record Stream(String login, String displayName, String title,
                         int viewers, String game, String profileImageUrl, String previewUrl,
                         String language) {
        public String url() { return "https://twitch.tv/" + login; }
    }

    public enum State { IDLE, LOADING, DONE, ERROR }

    private static volatile State state = State.IDLE;
    private static volatile String error = null;
    private static volatile List<Stream> streams = new ArrayList<>();
    private static volatile long lastFetch = 0;

    public static State state()        { return state; }
    public static String error()       { return error; }
    public static List<Stream> streams() { return streams; }
    public static long lastFetch()     { return lastFetch; }

    // True when the last successful load came from the proxy (full directory coverage);
    // false means we fell back to the no-login GQL search (limited results).
    private static volatile boolean fullCoverage = false;
    public static boolean fullCoverage() { return fullCoverage; }

    public static synchronized void refresh() {
        if (state == State.LOADING) return;
        state = State.LOADING;
        error = null;
        CompletableFuture.runAsync(TwitchStreams::refreshProxy);
    }

    /** Primary path: ask the FishMod proxy for the filtered stream list. Falls back to GQL on failure. */
    private static void refreshProxy() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(PROXY_URL))
                    .header("X-FishMod-Token", MOD_TOKEN)
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(20))
                    .GET().build();
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) { fallbackGql("proxy HTTP " + r.statusCode()); return; }
            JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
            if (root.has("success") && !root.get("success").getAsBoolean()) {
                fallbackGql("proxy: " + (root.has("cause") ? root.get("cause").getAsString() : "error"));
                return;
            }
            List<Stream> next = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("streams")) {
                JsonObject s = e.getAsJsonObject();
                next.add(new Stream(
                        str(s, "login"), str(s, "name"), str(s, "title"),
                        s.has("viewers") && !s.get("viewers").isJsonNull() ? s.get("viewers").getAsInt() : 0,
                        "Minecraft", "", str(s, "preview"), str(s, "language")));
            }
            next.sort(Comparator.comparingInt(Stream::viewers).reversed());
            streams = next;
            lastFetch = System.currentTimeMillis();
            fullCoverage = true;
            state = State.DONE;
            Debug.LOGGER.info("[TwitchStreams] proxy loaded {} skyblock streams", next.size());
        } catch (Exception ex) {
            fallbackGql(ex.getMessage());
        }
    }

    private static void fallbackGql(String why) {
        Debug.LOGGER.warn("[TwitchStreams] proxy failed ({}), falling back to GQL", why);
        fullCoverage = false;
        refreshGql();
    }

    private static void refreshGql() {
        List<CompletableFuture<List<Stream>>> futures = new ArrayList<>();
        for (String term : SEARCH_TERMS) futures.add(send(searchBody(term), TwitchStreams::parseSearch));
        futures.add(send(directoryBody(), TwitchStreams::parseDirectory));

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, t) -> {
                    // Merge & dedupe by login; keep highest reported viewer count.
                    Map<String, Stream> merged = new LinkedHashMap<>();
                    boolean any = false;
                    for (CompletableFuture<List<Stream>> f : futures) {
                        List<Stream> part = f.getNow(null);
                        if (part == null) continue;
                        any = true;
                        for (Stream s : part) {
                            Stream prev = merged.get(s.login());
                            if (prev == null || s.viewers() > prev.viewers()) merged.put(s.login(), s);
                        }
                    }
                    if (!any) {
                        state = State.ERROR;
                        if (error == null) error = "all requests failed";
                        return;
                    }
                    List<Stream> next = new ArrayList<>(merged.values());
                    next.sort(Comparator.comparingInt(Stream::viewers).reversed());
                    streams = next;
                    lastFetch = System.currentTimeMillis();
                    state = State.DONE;
                    Debug.LOGGER.info("[TwitchStreams] loaded {} skyblock streams", next.size());
                });
    }

    // ── GQL (no-login fallback) ─────────────────────────────────────────────────

    private static CompletableFuture<List<Stream>> send(String body, java.util.function.Function<JsonObject, List<Stream>> parser) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(GQL_URL))
                .header("Client-Id", CLIENT_ID)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    if (r.statusCode() != 200) { error = "HTTP " + r.statusCode(); return null; }
                    JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                    JsonElement errs = root.get("errors");
                    if (errs != null && errs.isJsonArray() && !errs.getAsJsonArray().isEmpty()) {
                        Debug.LOGGER.warn("[TwitchStreams] gql errors: {}", errs);
                        return null;
                    }
                    return parser.apply(root);
                })
                .exceptionally(t -> {
                    error = t.getMessage();
                    Debug.LOGGER.warn("[TwitchStreams] request error: {}", t.getMessage());
                    return null;
                });
    }

    private static String searchBody(String term) {
        return "{\"query\":\"query{searchFor(userQuery:\\\"" + term + "\\\",platform:\\\"web\\\")"
                + "{channels{edges{item{... on User{login displayName profileImageURL(width:300) "
                + "broadcastSettings{language} "
                + "stream{id title viewersCount previewImageURL(width:1280,height:720) game{displayName}}}}}}}}\"}";
    }

    private static String directoryBody() {
        return "{\"query\":\"query{game(name:\\\"Minecraft\\\"){streams(first:100,options:{sort:VIEWER_COUNT})"
                + "{edges{node{title viewersCount previewImageURL(width:1280,height:720) game{displayName} "
                + "broadcaster{login displayName profileImageURL(width:300) broadcastSettings{language}}}}}}}\"}";
    }

    private static List<Stream> parseSearch(JsonObject root) {
        List<Stream> out = new ArrayList<>();
        JsonArray edges = root.getAsJsonObject("data").getAsJsonObject("searchFor")
                .getAsJsonObject("channels").getAsJsonArray("edges");
        for (JsonElement e : edges) {
            try {
                JsonObject item = e.getAsJsonObject().getAsJsonObject("item");
                if (item == null || item.isJsonNull()) continue;
                JsonElement st = item.get("stream");
                if (st == null || st.isJsonNull()) continue;
                Stream s = build(item, st.getAsJsonObject());
                if (s != null) out.add(s);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static List<Stream> parseDirectory(JsonObject root) {
        List<Stream> out = new ArrayList<>();
        JsonElement game = root.getAsJsonObject("data").get("game");
        if (game == null || game.isJsonNull()) return out;
        JsonArray edges = game.getAsJsonObject().getAsJsonObject("streams").getAsJsonArray("edges");
        for (JsonElement e : edges) {
            try {
                JsonObject node = e.getAsJsonObject().getAsJsonObject("node");
                JsonObject bc = node.getAsJsonObject("broadcaster");
                Stream s = build(bc, node);
                if (s != null) out.add(s);
            } catch (Exception ignored) {}
        }
        return out;
    }

    /** Builds a Stream from a channel/broadcaster object + its stream/node object, or null if title isn't SkyBlock. */
    private static Stream build(JsonObject channel, JsonObject st) {
        String title = str(st, "title");
        if (!isHypixelSkyblock(title)) return null;
        String game = "";
        JsonElement g = st.get("game");
        if (g != null && g.isJsonObject()) game = str(g.getAsJsonObject(), "displayName");
        int viewers = st.has("viewersCount") && !st.get("viewersCount").isJsonNull()
                ? st.get("viewersCount").getAsInt() : 0;
        String lang = "";
        try {
            JsonElement bs = channel.get("broadcastSettings");
            if (bs != null && bs.isJsonObject()) lang = str(bs.getAsJsonObject(), "language");
        } catch (Exception ignored) {}
        return new Stream(
                str(channel, "login"),
                str(channel, "displayName"),
                title,
                viewers,
                game,
                str(channel, "profileImageURL"),
                str(st, "previewImageURL"),
                lang);
    }

    private static String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return (el == null || el.isJsonNull()) ? "" : el.getAsString();
    }

    /** True only when the title mentions BOTH "hypixel" and "skyblock" (any order/spacing). */
    private static boolean isHypixelSkyblock(String title) {
        if (title == null) return false;
        String t = title.toLowerCase();
        return t.contains("hypixel") && (t.contains("skyblock") || t.contains("sky block"));
    }
}
