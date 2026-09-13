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
    // back off this long on failure instead of retrying every tick
    private const val FAIL_TTL_MS = 15 * 60 * 1000L

    private val bazaar = HashMap<String, Double>()     // active price mode (rebuilt on swap)
    private val bazaarBuy = HashMap<String, Double>()  // buyPrice  = highest buy order  = instasell
    private val bazaarSell = HashMap<String, Double>() // sellPrice = lowest  sell offer = instabuy
    private val lbin = HashMap<String, Double>()
    private val avgLbin = HashMap<String, Double>()
    private val coflnet = ConcurrentHashMap<String, Double>()
    private val threeDayAvg = ConcurrentHashMap<String, Double>()
    // "TAG" -> (lowest active BIN, fetched timestamp)
    private val lowBinCache = ConcurrentHashMap<String, Pair<Double, Long>>()
    private val fetching: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val fetchingLowBin: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var lastBazaar = 0L
    @Volatile private var lastLbin = 0L
    @Volatile private var lastAvgLbin = 0L
    @Volatile private var lbinFailLogged = false
    @Volatile private var inFlight: CompletableFuture<Void>? = null

    // "TAG:boost" -> (lowest active BIN at that exact quality roll, fetched timestamp)
    private val qualityBin = ConcurrentHashMap<String, Pair<Double, Long>>()
    private val qualityFetching: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private const val QUALITY_TTL_MS = 10 * 60 * 1000L

    // cache key (tag + quality/stars/recomb/enchants) -> (lowest active BIN matching all of it, timestamp)
    private val dynamicBin = ConcurrentHashMap<String, Pair<Double, Long>>()
    private val dynamicFetching: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Timestamp to stamp on a failed fetch so the next retry is [FAIL_TTL_MS] out, not [TTL_MS]. */
    private fun failStamp(): Long = System.currentTimeMillis() - TTL_MS + FAIL_TTL_MS

    @JvmStatic
    fun applyPriceMode() {
        val mode = fishmod.utils.config.values.FishSettings.trackerPriceModeEnum
        synchronized(bazaar) {
            bazaar.clear()
            if (mode == fishmod.utils.config.values.FishSettings.PriceMode.SELL_OFFER) {
                bazaar.putAll(bazaarSell)
            } else if (mode == fishmod.utils.config.values.FishSettings.PriceMode.NPC_SELL) {
                for ((key, _) in bazaarBuy) {
                    val npc = fishmod.utils.networth.ItemsDb.npcSellPriceFor(key)
                    if (npc > 0) bazaar[key] = npc
                }
                // non-bazaar items that still have an NPC price
                for ((key, value) in fishmod.utils.networth.ItemsDb.npcSellPriceMap()) {
                    if (!bazaar.containsKey(key) && value > 0) bazaar[key] = value
                }
            } else {
                bazaar.putAll(bazaarBuy)
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

    /**
     * Lowest active BIN for [tag] filtered to exactly [boost]% dungeon quality (Coflnet's
     * `BaseStatBoost` auction filter — the same "quality" stat [fishmod.features.item.ItemQualityTooltip]
     * shows). Class-specific dungeon armor/weapons swing 10-100x in value across the quality range,
     * so the bulk bazaar/lbin dump (which mixes all rolls together) is useless for these — this hits
     * the live auction list instead. Returns 0 (and kicks off a background fetch) until cached.
     */
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
                try {
                    if (r.statusCode() != 200) return@thenAccept
                    val arr = JsonParser.parseString(r.body()).asJsonArray
                    var min = Double.MAX_VALUE
                    for (el in arr) {
                        val bid = el.asJsonObject.get("startingBid")
                        if (bid != null && !bid.isJsonNull && bid.asDouble < min) min = bid.asDouble
                    }
                    if (min < Double.MAX_VALUE) {
                        qualityBin[key] = min to System.currentTimeMillis()
                        Debug.LOGGER.info("[CroesusPrices] qualityBin {} = {}", key, min)
                    }
                } catch (ex: Exception) {
                    Debug.LOGGER.warn("[CroesusPrices] qualityBin {} error: {}", key, ex.message)
                }
            }.exceptionally { qualityFetching.remove(key); null }
    }

    /**
     * Lowest active BIN matching this exact item's price-relevant attributes (quality roll, stars,
     * recomb, per-enchant levels) — asks Coflnet's own `/item/filters` what auction filters apply to
     * this item, then queries `/active/bin` with all of them at once. [qualityBinPrice] alone (just
     * the quality roll) still lumps together e.g. a 0-star and a 10-star item at the same roll, which
     * for class-specific dungeon gear (Skeleton Master armor, Necron's pieces, ...) is a 10-100x
     * difference — this is the actual apples-to-apples comparison Hypixel's own AH BIN search would
     * show. [cacheKey] must vary with exactly [itemName]/[enchantments]/[extraAttributes] (a plain
     * sorted concatenation is enough; this isn't a real hash, just a stable dedup key). Returns 0
     * (and kicks off a background fetch) until cached; on a miss, callers should fall back to
     * [qualityBinPrice] or [price].
     */
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
                // Reforge swings price heavily (a desirable one vs. an off-meta one on otherwise
                // identical gear) but /item/filters never surfaces it as a filter key itself —
                // Coflnet's auction search still accepts it directly, so it's added by hand.
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
                if (r == null) return@thenAccept
                try {
                    if (r.statusCode() != 200) return@thenAccept
                    val arr = JsonParser.parseString(r.body()).asJsonArray
                    var min = Double.MAX_VALUE
                    for (el in arr) {
                        val bid = el.asJsonObject.get("startingBid")
                        if (bid != null && !bid.isJsonNull && bid.asDouble < min) min = bid.asDouble
                    }
                    if (min < Double.MAX_VALUE) {
                        dynamicBin[cacheKey] = min to System.currentTimeMillis()
                        Debug.LOGGER.info("[CroesusPrices] dynamicBin {} = {}", cacheKey, min)
                    }
                } catch (ex: Exception) {
                    Debug.LOGGER.warn("[CroesusPrices] dynamicBin {} parse error: {}", cacheKey, ex.message)
                }
            }
            .exceptionally { dynamicFetching.remove(cacheKey); null }
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
                    // Response: {min, median, mean, max, mode, volume} over Coflnet's rolling
                    // ~3-day sold-auction window; prefer median, then mode, then min for the
                    // blended price, and mean separately for the "3 Day Avg" tooltip line.
                    var med: JsonElement? = obj.get("median")
                    if (med == null || med.isJsonNull) med = obj.get("mode")
                    if (med == null || med.isJsonNull) med = obj.get("min")
                    if (med != null && !med.isJsonNull) {
                        val p = med.asDouble
                        if (p > 0) {
                            coflnet[id] = p
                            Debug.LOGGER.info("[CroesusPrices] coflnet {} = {}", id, p)
                        }
                    }
                    val mean = obj.get("mean")
                    if (mean != null && !mean.isJsonNull && mean.asDouble > 0) {
                        threeDayAvg[id] = mean.asDouble
                    }
                } catch (ex: Exception) {
                    Debug.LOGGER.warn("[CroesusPrices] coflnet {} error: {}", id, ex.message)
                }
            }.exceptionally { fetching.remove(id); null }
    }

    /** 3-day average sold price (Coflnet's rolling mean). 0 (and kicks off a fetch) until cached. */
    @JvmStatic
    fun threeDayAvg(id: String?): Double {
        if (id == null || id.isEmpty()) return 0.0
        val v = threeDayAvg[id]
        if (v != null && v > 0) return v
        fetchCoflnetItem(id)
        return 0.0
    }

    /** Lowest currently-active BIN listing for [id]. 0 (and kicks off a fetch) until cached. */
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
                try {
                    if (r.statusCode() != 200) return@thenAccept
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
                    if (min < Double.MAX_VALUE) {
                        lowBinCache[id] = min to System.currentTimeMillis()
                        Debug.LOGGER.info("[CroesusPrices] lowBin {} = {}", id, min)
                    }
                } catch (ex: Exception) {
                    Debug.LOGGER.warn("[CroesusPrices] lowBin {} error: {}", id, ex.message)
                }
            }.exceptionally { fetchingLowBin.remove(id); null }
    }

    @JvmStatic
    @Synchronized
    fun refreshIfStale(): CompletableFuture<Void> {
        val now = System.currentTimeMillis()
        val currentInFlight = inFlight
        if (currentInFlight != null && !currentInFlight.isDone) return currentInFlight
        val needB = now - lastBazaar > TTL_MS
        val needLbin = now - lastLbin > TTL_MS || now - lastAvgLbin > TTL_MS
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

    /**
     * Bulk lowest-BIN fetch. moulberry.codes (lowestbin.json / auction_averages_lbin) is dead (HTTP 525
     * for months), so this uses Coflnet's bulk NEU-format price dump instead: a single call returns
     * a {itemTag: price} map for every tracked item (server-cached ~10min on Coflnet's side already).
     * No API key needed. Populates both [lbin] and [avgLbin] since Coflnet doesn't expose the two as
     * separate bulk endpoints - callers already treat lbin/avgLbin as a fallback chain, so this is a
     * harmless drop-in.
     */
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
                        lastAvgLbin = failStamp()
                        return@thenAccept
                    }
                    val root = JsonParser.parseString(r.body()).asJsonObject
                    val next = HashMap<String, Double>()
                    for ((key, value) in root.entrySet()) {
                        if (!value.isJsonNull) {
                            try { next[key] = value.asDouble } catch (ignored: Exception) {}
                        }
                    }
                    synchronized(lbin) { lbin.clear(); lbin.putAll(next) }
                    synchronized(avgLbin) { avgLbin.clear(); avgLbin.putAll(next) }
                    val now = System.currentTimeMillis()
                    lastLbin = now
                    lastAvgLbin = now
                    lbinFailLogged = false
                    Debug.LOGGER.info("[CroesusPrices] coflnet lbin loaded {} entries", next.size)
                } catch (ex: Exception) {
                    if (!lbinFailLogged) { Debug.LOGGER.warn("[CroesusPrices] lbin parse error: {}", ex.message); lbinFailLogged = true }
                    lastLbin = failStamp()
                    lastAvgLbin = failStamp()
                }
            }.exceptionally { t ->
                if (!lbinFailLogged) { Debug.LOGGER.warn("[CroesusPrices] lbin fetch error: {}", t.message); lbinFailLogged = true }
                lastLbin = failStamp()
                lastAvgLbin = failStamp()
                null
            }
    }
}
