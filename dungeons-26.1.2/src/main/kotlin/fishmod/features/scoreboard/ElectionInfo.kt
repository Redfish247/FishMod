package fishmod.features.scoreboard

import com.google.gson.JsonParser
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

object ElectionInfo {

    private val HTTP: HttpClient = HttpClient.newHttpClient()
    private const val REFRESH_MS = 3_600_000L

    private var mayorLine: String? = null
    private var lastFetchAt = 0L
    private var fetchInFlight = false

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!FishSettings.customScoreboardEnabled || !FishSettings.sbSectionElection ||
                client.player == null || !Location.inSkyblock()
            ) return@register
            val now = System.currentTimeMillis()
            if (fetchInFlight || now - lastFetchAt < REFRESH_MS) return@register
            lastFetchAt = now
            fetchInFlight = true
            fetch(client)
        }
    }

    private fun fetch(mc: Minecraft) {
        CompletableFuture.runAsync {
            try {
                val req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.hypixel.net/v2/resources/skyblock/election"))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(10)).GET().build()
                val resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString())
                if (resp.statusCode() != 200) { mc.execute { fetchInFlight = false }; return@runAsync }
                val root = JsonParser.parseString(resp.body()).asJsonObject
                val mayor = root.getAsJsonObject("mayor")
                val name = mayor.get("name").asString
                val perk = mayor.getAsJsonArray("perks").firstOrNull()?.asJsonObject?.get("name")?.asString
                val line = if (perk != null) "§7Mayor: §e$name §7($perk)" else "§7Mayor: §e$name"
                mc.execute { mayorLine = line; fetchInFlight = false }
            } catch (ignored: Exception) {
                mc.execute { fetchInFlight = false }
            }
        }
    }

    @JvmStatic
    fun lines(): List<String> = mayorLine?.let { listOf(it) } ?: emptyList()
}
