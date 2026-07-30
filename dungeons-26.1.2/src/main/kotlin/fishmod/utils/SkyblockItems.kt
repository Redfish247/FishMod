package fishmod.utils

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Resolves display names -> Skyblock item IDs by fetching the canonical list from
 * https://api.hypixel.net/v2/resources/skyblock/items at startup.
 * Falls back silently to a null/empty map if the fetch fails — callers should keep their
 * hardcoded fallback maps for that case.
 */
object SkyblockItems {
    private val HTTP: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val nameToId = HashMap<String, String>()
    private val npcSellPrice = HashMap<String, Double>()
    private val loading = AtomicBoolean(false)
    @Volatile
    private var loaded = false

    @JvmStatic
    fun initAsync() {
        if (loaded || !loading.compareAndSet(false, true)) return
        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.hypixel.net/v2/resources/skyblock/items"))
            .timeout(Duration.ofSeconds(15))
            .GET().build()
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { r ->
                try {
                    if (r.statusCode() != 200) return@thenAccept
                    val obj = JsonParser.parseString(r.body()).asJsonObject
                    val items = obj.getAsJsonArray("items")
                    val nextNames = HashMap<String, String>()
                    val nextNpc = HashMap<String, Double>()
                    for (i in 0 until items.size()) {
                        val it = items[i].asJsonObject
                        if (!it.has("id") || !it.has("name")) continue
                        val id = it.get("id").asString
                        val name = it.get("name").asString.replace(Regex("§."), "").trim()
                        if (name.isEmpty()) continue
                        nextNames[name] = id
                        if (it.has("npc_sell_price")) {
                            try {
                                nextNpc[id] = it.get("npc_sell_price").asDouble
                            } catch (ignored: Exception) {
                            }
                        }
                    }
                    synchronized(nameToId) { nameToId.clear(); nameToId.putAll(nextNames) }
                    synchronized(npcSellPrice) { npcSellPrice.clear(); npcSellPrice.putAll(nextNpc) }
                    loaded = true
                    fishmod.utils.debug.Debug.LOGGER.info("[SkyblockItems] loaded {} items ({} with NPC sell price)", nextNames.size, nextNpc.size)
                    // Re-apply price mode in case CroesusPrices already loaded the bazaar
                    fishmod.features.croesus.CroesusPrices.applyPriceMode()
                } catch (ignored: Exception) {
                } finally {
                    loading.set(false)
                }
            }
            .exceptionally { loading.set(false); null }
    }

    /**
     * Case-insensitive name search for autocomplete. Prefix matches rank before substring
     * matches; both are sorted alphabetically and the combined result is capped at `limit`.
     * Returns display names (resolve to ids via [idFor]).
     */
    @JvmStatic
    fun searchNames(query: String?, limit: Int): List<String> {
        if (query == null || query.isBlank()) return listOf()
        val q = query.lowercase()
        val prefix = ArrayList<String>()
        val contains = ArrayList<String>()
        synchronized(nameToId) {
            for (name in nameToId.keys) {
                val ln = name.lowercase()
                if (ln.startsWith(q)) prefix.add(name)
                else if (ln.contains(q)) contains.add(name)
            }
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

    /** @return canonical id for the given display name, or null if not loaded/unknown */
    @JvmStatic
    fun idFor(name: String?): String? {
        if (name == null) return null
        synchronized(nameToId) {
            return nameToId[name]
        }
    }

    @JvmStatic
    fun isLoaded(): Boolean = loaded

    @JvmStatic
    fun npcSellPriceFor(id: String?): Double {
        if (id == null) return 0.0
        synchronized(npcSellPrice) {
            val v = npcSellPrice[id]
            return v ?: 0.0
        }
    }

    @JvmStatic
    fun npcSellPriceMap(): Map<String, Double> {
        synchronized(npcSellPrice) { return HashMap(npcSellPrice) }
    }
}
