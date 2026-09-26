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

object ItemsDb {

    private const val ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items"
    private const val REFRESH_MS = 12L * 60 * 60 * 1000L
    private val COLOR = Regex("§.")

    private val HTTP: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20)).build()

    @Volatile private var items: Map<String, JsonObject> = emptyMap()
    @Volatile private var nameToId: Map<String, String> = emptyMap()
    @Volatile private var npcSellPrice: Map<String, Double> = emptyMap()
    @Volatile private var loadedAt: Long = 0
    @Volatile private var loadedFromDisk = false
    private val fetching = java.util.concurrent.atomic.AtomicBoolean(false)

    @JvmStatic
    fun get(id: String?): JsonObject? {
        if (id == null) return null
        return items[id]
    }

    @JvmStatic
    fun initAsync() = ensureLoaded()

    @JvmStatic
    fun isLoaded(): Boolean = items.isNotEmpty()

    @JvmStatic
    fun idFor(name: String?): String? {
        if (name == null) return null
        return nameToId[name]
    }

    @JvmStatic
    fun npcSellPriceFor(id: String?): Double {
        if (id == null) return 0.0
        return npcSellPrice[id] ?: 0.0
    }

    @JvmStatic
    fun npcSellPriceMap(): Map<String, Double> = npcSellPrice

    private fun stale(): Boolean = items.isEmpty() || (System.currentTimeMillis() - loadedAt) > REFRESH_MS

    @JvmStatic
    fun ensureLoaded() {
        if ((!loadedFromDisk || stale()) && fetching.compareAndSet(false, true)) {
            try {
                Thread(ItemsDb::loadAndRefresh, "FishMod-ItemsDb").apply { isDaemon = true }.start()
            } catch (e: Throwable) {
                fetching.set(false)
                fishmod.utils.debug.Debug.LOGGER.warn("[ItemsDb] failed to start fetch thread: {}", e.toString())
            }
        }
    }

    private fun loadAndRefresh() {
        try {
            if (!loadedFromDisk) {
                loadedFromDisk = true
                try {
                    loadFromDisk()
                } catch (e: Exception) {
                    fishmod.utils.debug.Debug.LOGGER.warn("[ItemsDb] loadFromDisk failed: {}", e.toString())
                }
            }
            if (stale()) fetch()
        } finally {
            fetching.set(false)
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
        }
    }

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
            fishmod.features.croesus.CroesusPrices.applyPriceMode()
        }
    }
}
