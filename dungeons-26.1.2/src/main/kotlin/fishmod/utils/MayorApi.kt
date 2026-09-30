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
    private val HTTP: HttpClient = fishmod.utils.Http.CLIENT

    @Volatile
    private var paulDungeonBonus = false
    @Volatile
    private var mayorLine: String? = null
    @Volatile
    private var lastFetch = 0L
    @Volatile
    private var fetching = false

    private fun failStamp(): Long = System.currentTimeMillis() - CACHE_MS + FAIL_CACHE_MS

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> refresh() }
    }

    @JvmStatic
    fun isPaulDungeonBonusActive(): Boolean {
        if (System.currentTimeMillis() - lastFetch > CACHE_MS) refresh()
        return paulDungeonBonus
    }

    @JvmStatic
    fun mayorLine(): String? {
        if (System.currentTimeMillis() - lastFetch > CACHE_MS) refresh()
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
        val mayor = root.getAsJsonObject("mayor") ?: return null
        val name = FishDiag.notNull(mayor.get("name")?.asString, "MayorApi.5") { "election mayor object has no name" } ?: return null
        val perk = mayor.getAsJsonArray("perks")?.firstOrNull()?.asJsonObject?.get("name")?.asString
        return if (perk != null) "§7Mayor: §e$name §7($perk)" else "§7Mayor: §e$name"
    }

    private fun parsePaul(root: JsonObject): Boolean {
        if (!root.has("mayor")) return false
        val mayor = root.getAsJsonObject("mayor")
        if ("paul".equals(mayor.get("key")?.asString, ignoreCase = true) && hasEzpz(mayor.getAsJsonArray("perks"))) {
            return true
        }
        if (mayor.has("minister")) {
            val minister = mayor.getAsJsonObject("minister")
            if ("paul".equals(minister.get("key")?.asString, ignoreCase = true) && minister.has("perk")) {
                val perkName = minister.getAsJsonObject("perk").get("name").asString.lowercase()
                return perkName.contains("ezpz")
            }
        }
        return false
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
