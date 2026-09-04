package fishmod.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.debug.Debug
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

object MayorApi {

    private const val URL = "https://api.hypixel.net/v2/resources/skyblock/election"
    private const val CACHE_MS = 10 * 60 * 1000L
    private const val FAIL_CACHE_MS = 60 * 1000L // retry a minute after a failed fetch, not 10
    private val HTTP: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    @Volatile
    private var aatroxSlayerBonus = false
    @Volatile
    private var paulDungeonBonus = false
    @Volatile
    private var lastFetch = 0L
    @Volatile
    private var fetching = false

    /** Stamp on a failed fetch so the next retry is [FAIL_CACHE_MS] out, not [CACHE_MS]. */
    private fun failStamp(): Long = System.currentTimeMillis() - CACHE_MS + FAIL_CACHE_MS

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> refresh() }
    }

    @JvmStatic
    fun isAatroxSlayerBonusActive(): Boolean {
        if (System.currentTimeMillis() - lastFetch > CACHE_MS) refresh()
        return aatroxSlayerBonus
    }

    /** True if Paul is mayor (or minister with EZPZ perk), giving +10 dungeon score. */
    @JvmStatic
    fun isPaulDungeonBonusActive(): Boolean {
        if (System.currentTimeMillis() - lastFetch > CACHE_MS) refresh()
        return paulDungeonBonus
    }

    @JvmStatic
    fun refresh() {
        if (fetching) return
        fetching = true
        CompletableFuture.supplyAsync {
            try {
                val req = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .GET()
                    .timeout(Duration.ofSeconds(8))
                    .build()
                HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body()
            } catch (e: Exception) {
                Debug.LOGGER.warn("MayorApi: fetch failed - {}", e.message)
                null
            }
        }.thenAccept { body ->
            try {
                if (body == null) {
                    lastFetch = failStamp()
                    return@thenAccept
                }
                try {
                    aatroxSlayerBonus = parseAatrox(body)
                    paulDungeonBonus = parsePaul(body)
                    lastFetch = System.currentTimeMillis()
                } catch (e: Exception) {
                    Debug.LOGGER.warn("MayorApi: parse failed - {}", e.message)
                    lastFetch = failStamp()
                }
            } finally {
                fetching = false
            }
        }.exceptionally { fetching = false; null }
    }

    private fun parseAatrox(body: String): Boolean {
        val root = JsonParser.parseString(body).asJsonObject
        if (!root.has("mayor")) return false
        val mayor = root.getAsJsonObject("mayor")
        if ("aatrox".equals(mayor.get("key").asString, ignoreCase = true)) return true
        if (mayor.has("minister")) {
            val minister = mayor.getAsJsonObject("minister")
            if ("aatrox".equals(minister.get("key").asString, ignoreCase = true)) {
                if (minister.has("perk")) {
                    val perkName = minister.getAsJsonObject("perk")
                        .get("name").asString.lowercase()
                    return perkName.contains("slayer")
                }
            }
        }
        return false
    }

    private fun parsePaul(body: String): Boolean {
        val root = JsonParser.parseString(body).asJsonObject
        if (!root.has("mayor")) return false
        val mayor = root.getAsJsonObject("mayor")
        // +10 dungeon score needs the active EZPZ perk, not just Paul being mayor (perks rotate each election)
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
            val n = runCatching { p.asJsonObject.get("name").asString }.getOrNull() ?: continue
            if (n.lowercase().contains("ezpz")) return true
        }
        return false
    }
}
