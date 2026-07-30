package fishmod.utils.networth

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.MinecraftClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Lazily fetches and caches the public Hypixel SkyBlock items resource
 * (https://api.hypixel.net/v2/resources/skyblock/items — no API key required) and indexes
 * each entry by its `id`. The DB provides per-item metadata that SkyHelper's networth
 * pipeline relies on: `category`, `gemstone_slots`, `upgrade_costs`, `prestige`.
 *
 * Caching: kept in-memory and refreshed if older than 12h. Also persisted to
 * `<gameDir>/fishmod-networth/items.json` so the first calculation after a restart isn't
 * blocked on the network. Fetches run on a background thread; if the DB isn't loaded yet, callers
 * get null from [get] and the metadata-dependent handlers simply contribute 0.
 */
object ItemsDb {

    private const val ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items"
    private const val REFRESH_MS = 12L * 60 * 60 * 1000L

    private val HTTP: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20)).build()

    private val ITEMS: MutableMap<String, JsonObject> = ConcurrentHashMap()
    @Volatile private var loadedAt: Long = 0
    @Volatile private var loadedFromDisk = false
    @Volatile private var fetching = false

    /** Returns the metadata for an item id, or null if unknown / not yet loaded. */
    @JvmStatic
    fun get(id: String?): JsonObject? {
        if (id == null) return null
        return ITEMS[id]
    }

    /**
     * Ensures the items DB is being loaded. Loads from disk once (fast, non-blocking-ish) and kicks
     * off a background refresh if the in-memory copy is empty or stale. Never blocks on the network.
     */
    @JvmStatic
    fun ensureLoaded() {
        if (!loadedFromDisk) {
            loadedFromDisk = true
            try {
                loadFromDisk()
            } catch (ignored: Exception) {
            }
        }
        val stale = ITEMS.isEmpty() || (System.currentTimeMillis() - loadedAt) > REFRESH_MS
        if (stale && !fetching) {
            fetching = true
            Thread(ItemsDb::fetch, "FishMod-ItemsDb").start()
        }
    }

    private fun file(): Path {
        val dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("fishmod-networth")
        return dir.resolve("items.json")
    }

    @Throws(Exception::class)
    private fun loadFromDisk() {
        val f = file()
        if (!Files.exists(f)) return
        val json = Files.readString(f, StandardCharsets.UTF_8)
        index(JsonParser.parseString(json).asJsonObject)
        // disk copy counts as loaded but we still allow a background refresh if older than 12h
        loadedAt = Files.getLastModifiedTime(f).toMillis()
    }

    private fun fetch() {
        try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create(ITEMS_URL))
                .header("User-Agent", "FishMod")
                .timeout(Duration.ofSeconds(20)).GET().build()
            val r = HTTP.send(req, HttpResponse.BodyHandlers.ofString())
            if (r.statusCode() == 200) {
                val root = JsonParser.parseString(r.body()).asJsonObject
                index(root)
                loadedAt = System.currentTimeMillis()
                try {
                    val f = file()
                    Files.createDirectories(f.parent)
                    Files.writeString(f, r.body(), StandardCharsets.UTF_8)
                } catch (ignored: Exception) {
                }
            }
        } catch (e: Exception) {
            fishmod.utils.debug.Debug.LOGGER.warn("[ItemsDb] fetch: {}", e.toString())
        } finally {
            fetching = false
        }
    }

    /** Indexes the `items` array (from the resource response) by each item's `id`. */
    private fun index(root: JsonObject?) {
        if (root == null || !root.has("items") || !root.get("items").isJsonArray) return
        val next: MutableMap<String, JsonObject> = ConcurrentHashMap()
        for (el: JsonElement in root.getAsJsonArray("items")) {
            if (!el.isJsonObject) continue
            val o = el.asJsonObject
            if (o.has("id") && o.get("id").isJsonPrimitive) {
                next[o.get("id").asString] = o
            }
        }
        if (next.isNotEmpty()) {
            ITEMS.clear()
            ITEMS.putAll(next)
        }
    }
}
