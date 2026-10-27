package fishmod.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.debug.Debug
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object MayorApi {

    private const val URL = "https://api.hypixel.net/v2/resources/skyblock/election"
    private const val CACHE_MS = 10 * 60 * 1000L
    private const val FAIL_CACHE_MS = 60 * 1000L
    private const val SWITCH_CACHE_MS = 60 * 1000L
    private const val SWITCH_WINDOW_MS = 15 * 60 * 1000L
    private const val SB_EPOCH_MS = 1560276000_000L
    private const val YEAR_MS = 1200_000L * 372
    private const val ELECTION_OFFSET_MS = 1200_000L * 88
    private val HTTP: HttpClient = fishmod.utils.Http.CLIENT

    @Volatile
    private var paulDungeonBonus = false
    @Volatile
    private var dianaRitual = false
    @Volatile
    private var mayorLine: String? = null
    @Volatile
    private var lastFetch = 0L
    @Volatile
    private var fetching = false

    private fun failStamp(): Long = System.currentTimeMillis() - CACHE_MS + FAIL_CACHE_MS

    // Start of the current mayor term (Late Spring 27, every 5d4h real time)
    private fun lastSwitchMs(now: Long): Long {
        val t = now - SB_EPOCH_MS - ELECTION_OFFSET_MS
        return SB_EPOCH_MS + ELECTION_OFFSET_MS + Math.floorDiv(t, YEAR_MS) * YEAR_MS
    }

    // Refetch at each term switch, then every minute for a while in case the API lags
    private fun stale(): Boolean {
        val now = System.currentTimeMillis()
        val sw = lastSwitchMs(now)
        if (lastFetch < sw) return true
        val ttl = if (now - sw < SWITCH_WINDOW_MS) SWITCH_CACHE_MS else CACHE_MS
        return now - lastFetch > ttl
    }

    private fun refreshIfStale() { if (stale()) refresh() }

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> refreshIfStale() }
    }

    @JvmStatic
    fun isPaulDungeonBonusActive(): Boolean {
        refreshIfStale()
        return paulDungeonBonus
    }

    // Diana as mayor or minister with Mythological Ritual
    @JvmStatic
    fun isDianaRitualActive(): Boolean {
        refreshIfStale()
        return dianaRitual
    }

    @JvmStatic
    fun mayorLine(): String? {
        refreshIfStale()
        return mayorLine
    }

    @JvmStatic
    fun refresh() {
        if (fetching) return
        fetching = true
        val req = HttpRequest.newBuilder()
            .uri(URI.create(URL))
            .header("User-Agent", "Mozilla/5.0")
            .GET()
            .timeout(Duration.ofSeconds(8))
            .build()
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { resp ->
            try {
                FishDiag.check(resp.statusCode() == 200, "MayorApi.1") { "election API returned HTTP ${resp.statusCode()}" }
                val root = JsonParser.parseString(resp.body()).asJsonObject
                paulDungeonBonus = parsePaul(root)
                dianaRitual = hasPerk(root, "diana", "mythological ritual")
                mayorLine = parseMayorLine(root)
                lastFetch = System.currentTimeMillis()
            } catch (e: Exception) {
                FishDiag.fail("MayorApi.2", "election response parse failed", e)
                Debug.LOGGER.warn("MayorApi: parse failed - {}", e.message)
                lastFetch = failStamp()
            } finally {
                fetching = false
            }
        }.exceptionally { e ->
            FishDiag.fail("MayorApi.3", "election request failed", e)
            Debug.LOGGER.warn("MayorApi: fetch failed - {}", e.message)
            lastFetch = failStamp()
            fetching = false
            null
        }
    }

    private fun parseMayorLine(root: JsonObject): String? {
        val mayor = root.get("mayor")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null // null/non-object mayor must not throw
        val name = FishDiag.notNull(mayor.get("name")?.takeIf { it.isJsonPrimitive }?.asString, "MayorApi.5") { "election mayor object has no name" } ?: return null
        val perk = runCatching { mayor.getAsJsonArray("perks")?.firstOrNull()?.asJsonObject?.get("name")?.asString }.getOrNull()
        return if (perk != null) "§7Mayor: §e$name §7($perk)" else "§7Mayor: §e$name"
    }

    private fun parsePaul(root: JsonObject): Boolean {
        val mayor = root.get("mayor")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        if ("paul".equals(mayor.get("key")?.asString, ignoreCase = true) && hasEzpz(mayor.get("perks")?.takeIf { it.isJsonArray }?.asJsonArray)) {
            return true
        }
        val minister = mayor.get("minister")?.takeIf { it.isJsonObject }?.asJsonObject
        if (minister != null) {
            if ("paul".equals(minister.get("key")?.asString, ignoreCase = true)) {
                val perkName = minister.get("perk")?.takeIf { it.isJsonObject }?.asJsonObject?.get("name")?.asString?.lowercase() ?: return false
                return perkName.contains("ezpz")
            }
        }
        return false
    }

    private fun hasPerk(root: JsonObject, key: String, perk: String): Boolean {
        val mayor = root.get("mayor")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        if (key.equals(mayor.get("key")?.asString, ignoreCase = true)) {
            val perks = mayor.get("perks")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
            return perks.any { runCatching { it.asJsonObject.get("name").asString }.getOrNull()?.lowercase()?.contains(perk) == true }
        }
        val minister = mayor.get("minister")?.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        if (!key.equals(minister.get("key")?.asString, ignoreCase = true)) return false
        return minister.get("perk")?.takeIf { it.isJsonObject }?.asJsonObject?.get("name")?.asString?.lowercase()?.contains(perk) == true
    }

    private fun hasEzpz(perks: com.google.gson.JsonArray?): Boolean {
        if (perks == null) return false
        for (p in perks) {
            val n = runCatching { p.asJsonObject.get("name").asString }
                .onFailure { FishDiag.fail("MayorApi.4", "mayor perk without name: $p", it) }.getOrNull() ?: continue
            if (n.lowercase().contains("ezpz")) return true
        }
        return false
    }
}
