package fishmod.utils.networth

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** Lazily fetches/caches the Hypixel SkyBlock items resource, refreshed every 12h, persisted to disk so a restart isn't blocked on the network.
 *  Also the single source of truth for display-name -> item-id resolution and NPC sell prices (formerly duplicated in SkyblockItems). */
object ItemsDb {

    private const val ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items"
    private const val REFRESH_MS = 12L * 60 * 60 * 1000L
    private val COLOR = Regex("§.")

    private val HTTP: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20)).build()

    // Immutable snapshots, swapped atomically via the volatile writes in index(); readers never see a partial map.
    @Volatile private var items: Map<String, JsonObject> = emptyMap()
    @Volatile private var nameToId: Map<String, String> = emptyMap()
    @Volatile private var npcSellPrice: Map<String, Double> = emptyMap()
    @Volatile private var loadedAt: Long = 0
    @Volatile private var loadedFromDisk = false
    @Volatile private var fetching = false

    /** Returns the metadata for an item id, or null if unknown / not yet loaded. */
    @JvmStatic
    fun get(id: String?): JsonObject? {
        if (id == null) return null
        return items[id]
    }

    /** Kicks off the initial load; equivalent to [ensureLoaded]. Kept as an alias for the old SkyblockItems API. */
    @JvmStatic
    fun initAsync() = ensureLoaded()

    @JvmStatic
    fun isLoaded(): Boolean = items.isNotEmpty()

    /** Resolves a cleaned display name (color codes stripped) to an item id. */
    @JvmStatic
    fun idFor(name: String?): String? {
        if (name == null) return null
        return nameToId[name]
    }

    /** Prefix matches rank before substring matches; returns display names, resolve via [idFor]. */
    @JvmStatic
    fun searchNames(query: String?, limit: Int): List<String> {
        if (query == null || query.isBlank()) return listOf()
        val q = query.lowercase()
        val prefix = ArrayList<String>()
        val contains = ArrayList<String>()
        for (name in nameToId.keys) {
            val ln = name.lowercase()
            if (ln.startsWith(q)) prefix.add(name)
            else if (ln.contains(q)) contains.add(name)
        }
        prefix.sortWith(String.CASE_INSENSITIVE_ORDER)
        contains.sortWith(String.CASE_INSENSITIVE_ORDER)
        val out = ArrayList<String>(prefix)
        for (c in contains) {
            if (out.size >= limit) break
            out.add(c)
        }
        return if (out.size > limit) ArrayList(out.subList(0, limit)) else out
    }

    @JvmStatic
    fun npcSellPriceFor(id: String?): Double {
        if (id == null) return 0.0
        return npcSellPrice[id] ?: 0.0
    }

    @JvmStatic
    fun npcSellPriceMap(): Map<String, Double> = npcSellPrice

    /** Loads from disk once, and kicks off a background refresh if stale. Never blocks on the network. */
    @JvmStatic
    fun ensureLoaded() {
        if (!loadedFromDisk) {
            loadedFromDisk = true
            try {
                loadFromDisk()
            } catch (ignored: Exception) {
            }
        }
        val stale = items.isEmpty() || (System.currentTimeMillis() - loadedAt) > REFRESH_MS
        if (stale && !fetching) {
            fetching = true
            Thread(ItemsDb::fetch, "FishMod-ItemsDb").start()
        }
    }

    private fun file(): Path {
        val dir = Minecraft.getInstance().gameDirectory.toPath().resolve("fishmod-networth")
        return dir.resolve("items.json")
    }

    @Throws(Exception::class)
    private fun loadFromDisk() {
        val f = file()
        if (!Files.exists(f)) return
        val json = Files.readString(f, StandardCharsets.UTF_8)
        index(JsonParser.parseString(json).asJsonObject)
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

    /** Indexes the `items` array (from the resource response) by each item's `id`, plus the name->id and NPC sell price lookups. */
    private fun index(root: JsonObject?) {
        if (root == null || !root.has("items") || !root.get("items").isJsonArray) return
        val next = HashMap<String, JsonObject>()
        val nextNames = HashMap<String, String>()
        val nextNpc = HashMap<String, Double>()
        for (el: JsonElement in root.getAsJsonArray("items")) {
            if (!el.isJsonObject) continue
            val o = el.asJsonObject
            if (o.has("id") && o.get("id").isJsonPrimitive) {
                val id = o.get("id").asString
                next[id] = o
                if (o.has("name") && o.get("name").isJsonPrimitive) {
                    val name = o.get("name").asString.replace(COLOR, "").trim()
                    if (name.isNotEmpty()) nextNames[name] = id
                }
                if (o.has("npc_sell_price")) {
                    try {
                        nextNpc[id] = o.get("npc_sell_price").asDouble
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
        if (next.isNotEmpty()) {
            items = next
            nameToId = nextNames
            npcSellPrice = nextNpc
            fishmod.utils.debug.Debug.LOGGER.info("[ItemsDb] indexed {} items ({} names, {} with NPC sell price)", next.size, nextNames.size, nextNpc.size)
            // Re-apply price mode in case CroesusPrices already loaded the bazaar
            fishmod.features.croesus.CroesusPrices.applyPriceMode()
        }
    }
}
