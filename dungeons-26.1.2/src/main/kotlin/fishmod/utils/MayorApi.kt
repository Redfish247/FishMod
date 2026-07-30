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
    private const val CACHE_MS = 10 * 60 * 1000L // 10 minutes
    private val HTTP: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    @Volatile
    private var aatroxSlayerBonus = false
    @Volatile
    private var paulDungeonBonus = false
    @Volatile
    private var lastFetch = 0L

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
        lastFetch = System.currentTimeMillis() // prevent parallel fetches
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
            if (body == null) return@thenAccept
            try {
                aatroxSlayerBonus = parseAatrox(body)
                paulDungeonBonus = parsePaul(body)
            } catch (e: Exception) {
                Debug.LOGGER.warn("MayorApi: parse failed - {}", e.message)
            }
        }
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
        if ("paul".equals(mayor.get("key").asString, ignoreCase = true)) return true
        if (mayor.has("minister")) {
            val minister = mayor.getAsJsonObject("minister")
            if ("paul".equals(minister.get("key").asString, ignoreCase = true)) {
                if (minister.has("perk")) {
                    val perkName = minister.getAsJsonObject("perk")
                        .get("name").asString.lowercase()
                    // Paul's dungeon bonus perk is "EZPZ" (+10 dungeon score)
                    return perkName.contains("ezpz")
                }
            }
        }
        return false
    }
}
