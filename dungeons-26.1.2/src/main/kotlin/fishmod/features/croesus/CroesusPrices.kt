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

    private val HTTP: HttpClient = fishmod.utils.Http.CLIENT

    private const val TTL_MS = 5 * 60 * 1000L
    private const val FAIL_TTL_MS = 15 * 60 * 1000L

    @Volatile private var bazaar: Map<String, Double> = emptyMap()
    @Volatile private var bazaarBuy: Map<String, Double> = emptyMap()
    @Volatile private var bazaarSell: Map<String, Double> = emptyMap()
    @Volatile private var lbin: Map<String, Double> = emptyMap()
    private val coflnet = ConcurrentHashMap<String, Double>()
    private val threeDayAvg = ConcurrentHashMap<String, Double>()
    private val coflnetAttempted = ConcurrentHashMap<String, Long>()
    private val lowBinCache = ConcurrentHashMap<String, Pair<Double, Long>>()
    private val fetching: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val fetchingLowBin: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var lastBazaar = 0L
    @Volatile private var lastLbin = 0L
    @Volatile private var lbinFailLogged = false
    @Volatile private var inFlight: CompletableFuture<Void>? = null

    private val qualityBin = ConcurrentHashMap<String, Pair<Double, Long>>()
    private val qualityFetching: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private const val QUALITY_TTL_MS = 10 * 60 * 1000L

    private val dynamicBin = ConcurrentHashMap<String, Pair<Double, Long>>()
    private val dynamicFetching: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun trim(cache: MutableMap<String, Pair<Double, Long>>) {
        if (cache.size < 2048) return
        val now = System.currentTimeMillis()
        cache.values.removeIf { now - it.second > QUALITY_TTL_MS }
        if (cache.size >= 2048) cache.clear()
    }

    private fun failStamp(): Long = System.currentTimeMillis() - TTL_MS + FAIL_TTL_MS

    @JvmStatic
    @Synchronized
    fun applyPriceMode() {
        val mode = fishmod.utils.config.values.FishSettings.trackerPriceModeEnum
        bazaar = when (mode) {
            fishmod.utils.config.values.FishSettings.PriceMode.SELL_OFFER -> bazaarSell
            fishmod.utils.config.values.FishSettings.PriceMode.NPC_SELL -> {
                val next = HashMap<String, Double>()
                for (key in bazaarBuy.keys) {
                    val npc = fishmod.utils.networth.ItemsDb.npcSellPriceFor(key)
                    if (npc > 0) next[key] = npc
                }
                for ((key, value) in fishmod.utils.networth.ItemsDb.npcSellPriceMap()) {
                    if (!next.containsKey(key) && value > 0) next[key] = value
                }
                next
            }
            else -> bazaarBuy
        }
    }

    @JvmStatic
    fun price(id: String?): Double {
        if (id == null || id.isEmpty()) return 0.0
        val known = cachedPrice(id)
        if (known > 0) return known
        fetchCoflnetItemThrottled(id)
        return 0.0
    }

    @JvmStatic
    fun cachedPrice(id: String?): Double {
        if (id == null || id.isEmpty()) return 0.0
        val b = bazaar[id]
        if (b != null && b > 0) return b
        val l = lbin[id]
        if (l != null && l > 0) return l
        val c = coflnet[id]
        if (c != null && c > 0) return c
        return 0.0
    }

    @JvmStatic
    fun debugSource(id: String?): String {
        if (id == null) return "null"
        val b = bazaar[id]
        if (b != null && b > 0) return "bazaar=$b"
        val l = lbin[id]
        if (l != null && l > 0) return "lbin=$l"
        val c = coflnet[id]
        if (c != null && c > 0) return "coflnet=$c"
        return "miss (bz=${bazaar.size} lbin=${lbin.size} cf=${coflnet.size})"
    }

    @JvmStatic
    fun qualityBinPrice(tag: String, boost: Int): Double {
        val key = "$tag:$boost"
        val cached = qualityBin[key]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.second < QUALITY_TTL_MS) return cached.first
        fetchQualityBin(tag, boost, key)
        return cached?.first ?: 0.0
    }

    private fun fetchQualityBin(tag: String, boost: Int, key: String) {
        if (!qualityFetching.add(key)) return
        val url = "https://sky.coflnet.com/api/auctions/tag/$tag/active/bin?query.BaseStatBoost=$boost"
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET().build()
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { r ->
                qualityFetching.remove(key)
                var result = 0.0
                try {
                    if (r.statusCode() == 200) {
                        val arr = JsonParser.parseString(r.body()).asJsonArray
                        var min = Double.MAX_VALUE
                        for (el in arr) {
                            val bid = el.asJsonObject.get("startingBid")
                            if (bid != null && !bid.isJsonNull && bid.asDouble < min) min = bid.asDouble
                        }
                        if (min < Double.MAX_VALUE) result = min
                    }
                } catch (ex: Exception) {
                    Debug.LOGGER.warn("[CroesusPrices] qualityBin {} error: {}", key, ex.message)
                }
                trim(qualityBin)
                qualityBin[key] = result to System.currentTimeMillis()
                if (result > 0.0) Debug.LOGGER.debug("[CroesusPrices] qualityBin {} = {}", key, result)
            }.exceptionally { qualityFetching.remove(key); qualityBin[key] = 0.0 to System.currentTimeMillis(); null }
    }

    @JvmStatic
    fun dynamicBinPrice(
        tag: String,
        cacheKey: String,
        itemName: String,
        enchantments: Map<String, Int>,
        extraAttributes: Map<String, Any>,
        reforge: String? = null
    ): Double {
        val cached = dynamicBin[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.second < QUALITY_TTL_MS) return cached.first
        fetchDynamicBin(tag, cacheKey, itemName, enchantments, extraAttributes, reforge)
        return cached?.first ?: 0.0
    }

    private fun fetchDynamicBin(
        tag: String,
        cacheKey: String,
        itemName: String,
        enchantments: Map<String, Int>,
        extraAttributes: Map<String, Any>,
        reforge: String?
    ) {
        if (!dynamicFetching.add(cacheKey)) return

        val enchObj = JsonObject()
        for ((k, v) in enchantments) enchObj.addProperty(k, v)
        val extraObj = JsonObject()
        for ((k, v) in extraAttributes) {
            when (v) {
                is Int -> extraObj.addProperty(k, v)
                is Long -> extraObj.addProperty(k, v)
                is String -> extraObj.addProperty(k, v)
                else -> extraObj.addProperty(k, v.toString())
            }
        }
        val body = JsonObject()
        body.addProperty("tag", tag)
        body.addProperty("itemName", itemName)
        body.addProperty("count", 1)
        body.add("enchantments", enchObj)
        body.add("extraAttributes", extraObj)

        val filterReq = HttpRequest.newBuilder()
            .uri(URI.create("https://sky.coflnet.com/api/item/filters"))
            .timeout(Duration.ofSeconds(10))
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

        HTTP.sendAsync(filterReq, HttpResponse.BodyHandlers.ofString())
            .thenCompose { r ->
                if (r.statusCode() != 200) return@thenCompose CompletableFuture.completedFuture<HttpResponse<String>?>(null)
                val filters = try { JsonParser.parseString(r.body()).asJsonObject } catch (ex: Exception) { null }
                if ((filters == null || filters.entrySet().isEmpty()) && reforge == null) {
                    return@thenCompose CompletableFuture.completedFuture<HttpResponse<String>?>(null)
                }
                val pairs = ArrayList<Pair<String, String>>()
                filters?.entrySet()?.forEach { (k, v) -> pairs.add(k to v.asString) }
                if (reforge != null) pairs.add("Reforge" to reforge)
                val qs = pairs.joinToString("&") { (k, v) ->
                    "query.${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
                }
                val binReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://sky.coflnet.com/api/auctions/tag/$tag/active/bin?$qs"))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build()
                HTTP.sendAsync(binReq, HttpResponse.BodyHandlers.ofString())
            }
            .thenAccept { r ->
                dynamicFetching.remove(cacheKey)
                var result = 0.0
                if (r != null) {
                    try {
                        if (r.statusCode() == 200) {
                            val arr = JsonParser.parseString(r.body()).asJsonArray
                            var min = Double.MAX_VALUE
                            for (el in arr) {
                                val bid = el.asJsonObject.get("startingBid")
                                if (bid != null && !bid.isJsonNull && bid.asDouble < min) min = bid.asDouble
                            }
                            if (min < Double.MAX_VALUE) result = min
                        }
                    } catch (ex: Exception) {
                        Debug.LOGGER.warn("[CroesusPrices] dynamicBin {} parse error: {}", cacheKey, ex.message)
                    }
                }
                trim(dynamicBin)
                dynamicBin[cacheKey] = result to System.currentTimeMillis()
                if (result > 0.0) Debug.LOGGER.debug("[CroesusPrices] dynamicBin {} = {}", cacheKey, result)
            }
            .exceptionally { dynamicFetching.remove(cacheKey); dynamicBin[cacheKey] = 0.0 to System.currentTimeMillis(); null }
    }

    private fun fetchCoflnetItem(id: String) {
        if (!fetching.add(id)) return
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
                    var med: JsonElement? = obj.get("median")
                    if (med == null || med.isJsonNull) med = obj.get("mode")
                    if (med == null || med.isJsonNull) med = obj.get("min")
                    if (med != null && !med.isJsonNull) {
                        val p = med.asDouble
                        if (p > 0) {
                            coflnet[id] = p
                            Debug.LOGGER.debug("[CroesusPrices] coflnet {} = {}", id, p)
                        }
                    }
                    val mean = obj.get("mean")
                    if (mean != null && !mean.isJsonNull && mean.asDouble > 0) {
                        threeDayAvg[id] = mean.asDouble
                    }
                } catch (ex: Exception) {
                    Debug.LOGGER.warn("[CroesusPrices] coflnet {} error: {}", id, ex.message)
                }
                coflnetAttempted[id] = System.currentTimeMillis()
            }.exceptionally { fetching.remove(id); coflnetAttempted[id] = System.currentTimeMillis(); null }
    }

    @JvmStatic
    fun threeDayAvg(id: String?): Double {
        if (id == null || id.isEmpty()) return 0.0
        val v = threeDayAvg[id]
        if (v != null && v > 0) return v
        fetchCoflnetItemThrottled(id)
        return 0.0
    }

    private fun fetchCoflnetItemThrottled(id: String) {
        val attempted = coflnetAttempted[id]
        if (attempted != null && System.currentTimeMillis() - attempted < QUALITY_TTL_MS) return
        fetchCoflnetItem(id)
    }

    @JvmStatic
    fun currentLowBin(id: String?): Double {
        if (id == null || id.isEmpty()) return 0.0
        val cached = lowBinCache[id]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.second < QUALITY_TTL_MS) return cached.first
        fetchLowBin(id)
        return cached?.first ?: 0.0
    }

    private fun fetchLowBin(id: String) {
        if (!fetchingLowBin.add(id)) return
        val url = "https://sky.coflnet.com/api/auctions/tag/$id/active/bin"
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .GET().build()
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { r ->
                fetchingLowBin.remove(id)
                var result = 0.0
                try {
                    if (r.statusCode() == 200) {
                        val arr = JsonParser.parseString(r.body()).asJsonArray
                        var min = Double.MAX_VALUE
                        for (el in arr) {
                            val obj = el.asJsonObject
                            val bid = obj.get("startingBid") ?: continue
                            if (bid.isJsonNull) continue
                            val count = obj.get("count")?.takeIf { !it.isJsonNull }?.asInt ?: 1
                            val unit = bid.asDouble / count.coerceAtLeast(1)
                            if (unit < min) min = unit
                        }
                        if (min < Double.MAX_VALUE) result = min
                    }
                } catch (ex: Exception) {
                    Debug.LOGGER.warn("[CroesusPrices] lowBin {} error: {}", id, ex.message)
                }
                trim(lowBinCache)
                lowBinCache[id] = result to System.currentTimeMillis()
                if (result > 0.0) Debug.LOGGER.debug("[CroesusPrices] lowBin {} = {}", id, result)
            }.exceptionally { fetchingLowBin.remove(id); lowBinCache[id] = 0.0 to System.currentTimeMillis(); null }
    }

    @JvmStatic
    @Synchronized
    fun refreshIfStale(): CompletableFuture<Void> {
        val now = System.currentTimeMillis()
        val currentInFlight = inFlight
        if (currentInFlight != null && !currentInFlight.isDone) return currentInFlight
        val needB = now - lastBazaar > TTL_MS
        val needLbin = now - lastLbin > TTL_MS
        if (!needB && !needLbin) return CompletableFuture.completedFuture(null)

        val bz = if (needB) fetchBazaar() else CompletableFuture.completedFuture(null)
        val lb = if (needLbin) fetchLbin() else CompletableFuture.completedFuture(null)
        val all = CompletableFuture.allOf(bz, lb)
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
                    val buy = HashMap<String, Double>()
                    val sell = HashMap<String, Double>()
                    for ((key, value) in products.entrySet()) {
                        try {
                            if (!value.isJsonObject) continue
                            val product = value.asJsonObject
                            val qs = product.getAsJsonObject("quick_status") ?: continue
                            val bp = qs.get("buyPrice")
                            val sp = qs.get("sellPrice")
                            if (bp != null && !bp.isJsonNull) buy[key] = bp.asDouble
                            if (sp != null && !sp.isJsonNull) sell[key] = sp.asDouble
                        } catch (ignored: Exception) {}
                    }
                    bazaarBuy = buy
                    bazaarSell = sell
                    applyPriceMode()
                    lastBazaar = System.currentTimeMillis()
                    Debug.LOGGER.debug("[CroesusPrices] bazaar loaded {} entries", bazaar.size)
                } catch (ex: Exception) {
                    Debug.LOGGER.warn("[CroesusPrices] bazaar parse error: {}", ex.message)
                }
            }.exceptionally { t -> Debug.LOGGER.warn("[CroesusPrices] bazaar fetch error: {}", t.message); null }
    }

    private fun fetchLbin(): CompletableFuture<Void> {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://sky.coflnet.com/api/prices/neu"))
            .timeout(Duration.ofSeconds(15))
            .GET().build()
        return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept { r ->
                try {
                    if (r.statusCode() != 200) {
                        if (!lbinFailLogged) { Debug.LOGGER.warn("[CroesusPrices] lbin status={} - backing off {}m", r.statusCode(), FAIL_TTL_MS / 60000); lbinFailLogged = true }
                        lastLbin = failStamp()
                        return@thenAccept
                    }
                    val root = JsonParser.parseString(r.body()).asJsonObject
                    val next = HashMap<String, Double>()
                    for ((key, value) in root.entrySet()) {
                        if (!value.isJsonNull) {
                            try { next[key] = value.asDouble } catch (ignored: Exception) {}
                        }
                    }
                    lbin = next
                    val now = System.currentTimeMillis()
                    lastLbin = now
                    lbinFailLogged = false
                    Debug.LOGGER.debug("[CroesusPrices] coflnet lbin loaded {} entries", next.size)
                } catch (ex: Exception) {
                    if (!lbinFailLogged) { Debug.LOGGER.warn("[CroesusPrices] lbin parse error: {}", ex.message); lbinFailLogged = true }
                    lastLbin = failStamp()
                }
            }.exceptionally { t ->
                if (!lbinFailLogged) { Debug.LOGGER.warn("[CroesusPrices] lbin fetch error: {}", t.message); lbinFailLogged = true }
                lastLbin = failStamp()
                null
            }
    }
}
