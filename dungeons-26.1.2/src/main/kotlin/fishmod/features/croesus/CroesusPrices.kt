package fishmod.features.croesus

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.debug.Debug
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object CroesusPrices {

    private val HTTP: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private const val TTL_MS = 5 * 60 * 1000L

    private val bazaar = HashMap<String, Double>()     // selected mode (rebuilt on swap)
    private val bazaarBuy = HashMap<String, Double>()  // buyPrice  = highest buy order  = instasell
    private val bazaarSell = HashMap<String, Double>() // sellPrice = lowest  sell offer = instabuy
    private val lbin = HashMap<String, Double>()
    private val avgLbin = HashMap<String, Double>()
    private val coflnet = ConcurrentHashMap<String, Double>()
    private val fetching: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var lastBazaar = 0L
    @Volatile private var lastLbin = 0L
    @Volatile private var lastAvgLbin = 0L
    @Volatile private var inFlight: CompletableFuture<Void>? = null

    @JvmStatic
    fun applyPriceMode() {
        val mode = fishmod.utils.config.values.FishSettings.trackerPriceModeEnum
        synchronized(bazaar) {
            bazaar.clear()
            if (mode == fishmod.utils.config.values.FishSettings.PriceMode.SELL_OFFER) {
                bazaar.putAll(bazaarSell) // Sell Offer (instabuy cost)
            } else if (mode == fishmod.utils.config.values.FishSettings.PriceMode.NPC_SELL) {
                // NPC Sell: pulled from SkyblockItems.npcSellPriceFor(id)
                for ((key, _) in bazaarBuy) {
                    val npc = fishmod.utils.SkyblockItems.npcSellPriceFor(key)
                    if (npc > 0) bazaar[key] = npc
                }
                // Also include items that aren't bazaarable but have NPC price
                for ((key, value) in fishmod.utils.SkyblockItems.npcSellPriceMap()) {
                    if (!bazaar.containsKey(key) && value > 0) bazaar[key] = value
                }
            } else {
                bazaar.putAll(bazaarBuy) // Instasell (default)
            }
        }
    }

    /** Falls back to an async coflnet lookup (cached for next call) if all bulk sources miss. */
    @JvmStatic
    fun price(id: String?): Double {
        if (id == null || id.isEmpty()) return 0.0
        val b = bazaar[id]
        if (b != null && b > 0) return b
        val l = lbin[id]
        if (l != null && l > 0) return l
        val a = avgLbin[id]
        if (a != null && a > 0) return a
        val c = coflnet[id]
        if (c != null && c > 0) return c
        fetchCoflnetItem(id)
        return 0.0
    }

    @JvmStatic
    fun debugSource(id: String?): String {
        if (id == null) return "null"
        val b = bazaar[id]
        if (b != null && b > 0) return "bazaar=$b"
        val l = lbin[id]
        if (l != null && l > 0) return "lbin=$l"
        val a = avgLbin[id]
        if (a != null && a > 0) return "avglbin=$a"
        val c = coflnet[id]
        if (c != null && c > 0) return "coflnet=$c"
        return "miss (bz=${bazaar.size} lbin=${lbin.size} avg=${avgLbin.size} cf=${coflnet.size})"
    }

    private fun fetchCoflnetItem(id: String) {
        if (!fetching.add(id)) return // already in flight
        val url = "https://sky.coflnet.com/api/item/price/$id"
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET().build()
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { r ->
                fetching.remove(id)
                try {
                    if (r.statusCode() != 200) return@thenAccept
                    val obj = JsonParser.parseString(r.body()).asJsonObject
                    // Response: {min, median, max, mode, volume}
                    var med: JsonElement? = obj.get("min")
                    if (med == null || med.isJsonNull) med = obj.get("median")
                    if (med != null && !med.isJsonNull) {
                        val p = med.asDouble
                        if (p > 0) {
                            coflnet[id] = p
                            Debug.LOGGER.info("[CroesusPrices] coflnet {} = {}", id, p)
                        }
                    }
                } catch (ex: Exception) {
                    Debug.LOGGER.warn("[CroesusPrices] coflnet {} error: {}", id, ex.message)
                }
            }.exceptionally { fetching.remove(id); null }
    }

    @JvmStatic
    @Synchronized
    fun refreshIfStale(): CompletableFuture<Void> {
        val now = System.currentTimeMillis()
        val currentInFlight = inFlight
        if (currentInFlight != null && !currentInFlight.isDone) return currentInFlight
        val needB = now - lastBazaar > TTL_MS
        val needL = now - lastLbin > TTL_MS
        val needA = now - lastAvgLbin > TTL_MS
        if (!needB && !needL && !needA) return CompletableFuture.completedFuture(null)

        val bz = if (needB) fetchBazaar() else CompletableFuture.completedFuture(null)
        val lb = if (needL) fetchLbin() else CompletableFuture.completedFuture(null)
        val av = if (needA) fetchAvgLbin() else CompletableFuture.completedFuture(null)
        val all = CompletableFuture.allOf(bz, lb, av)
        inFlight = all
        return all
    }

    private fun fetchBazaar(): CompletableFuture<Void> {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.hypixel.net/v2/skyblock/bazaar"))
            .timeout(Duration.ofSeconds(15))
            .GET().build()
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { r ->
                try {
                    if (r.statusCode() != 200) {
                        Debug.LOGGER.warn("[CroesusPrices] bazaar status={}", r.statusCode())
                        return@thenAccept
                    }
                    val root = JsonParser.parseString(r.body()).asJsonObject
                    val products = root.getAsJsonObject("products")
                    if (products == null) { Debug.LOGGER.warn("[CroesusPrices] bazaar: no products"); return@thenAccept }
                    synchronized(bazaarBuy) { bazaarBuy.clear() }
                    synchronized(bazaarSell) { bazaarSell.clear() }
                    for ((key, value) in products.entrySet()) {
                        try {
                            if (!value.isJsonObject) continue
                            val product = value.asJsonObject
                            val qs = product.getAsJsonObject("quick_status") ?: continue
                            val bp = qs.get("buyPrice")
                            val sp = qs.get("sellPrice")
                            if (bp != null && !bp.isJsonNull) bazaarBuy[key] = bp.asDouble
                            if (sp != null && !sp.isJsonNull) bazaarSell[key] = sp.asDouble
                        } catch (ignored: Exception) {}
                    }
                    applyPriceMode()
                    lastBazaar = System.currentTimeMillis()
                    Debug.LOGGER.info("[CroesusPrices] bazaar loaded {} entries", bazaar.size)
                } catch (ex: Exception) {
                    Debug.LOGGER.warn("[CroesusPrices] bazaar parse error: {}", ex.message)
                }
            }.exceptionally { t -> Debug.LOGGER.warn("[CroesusPrices] bazaar fetch error: {}", t.message); null }
    }

    private fun fetchAvgLbin(): CompletableFuture<Void> {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://moulberry.codes/auction_averages_lbin/1day.json"))
            .timeout(Duration.ofSeconds(15))
            .GET().build()
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { r ->
                try {
                    if (r.statusCode() != 200) return@thenAccept
                    val root = JsonParser.parseString(r.body()).asJsonObject
                    val next = HashMap<String, Double>()
                    for ((key, value) in root.entrySet()) {
                        if (value.isJsonNull) continue
                        // value is a number directly
                        try { next[key] = value.asDouble } catch (ignored: Exception) {}
                    }
                    synchronized(avgLbin) { avgLbin.clear(); avgLbin.putAll(next) }
                    lastAvgLbin = System.currentTimeMillis()
                } catch (ignored: Exception) {}
            }.exceptionally { null }
    }

    private fun fetchLbin(): CompletableFuture<Void> {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://moulberry.codes/lowestbin.json"))
            .timeout(Duration.ofSeconds(15))
            .GET().build()
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { r ->
                try {
                    if (r.statusCode() != 200) { Debug.LOGGER.warn("[CroesusPrices] lbin status={}", r.statusCode()); return@thenAccept }
                    val root = JsonParser.parseString(r.body()).asJsonObject
                    val next = HashMap<String, Double>()
                    for ((key, value) in root.entrySet()) {
                        if (!value.isJsonNull) next[key] = value.asDouble
                    }
                    synchronized(lbin) {
                        lbin.clear()
                        lbin.putAll(next)
                    }
                    lastLbin = System.currentTimeMillis()
                    Debug.LOGGER.info("[CroesusPrices] lbin loaded {} entries", next.size)
                } catch (ex: Exception) {
                    Debug.LOGGER.warn("[CroesusPrices] lbin parse error: {}", ex.message)
                }
            }.exceptionally { t -> Debug.LOGGER.warn("[CroesusPrices] lbin fetch error: {}", t.message); null }
    }
}
