package fishmod.features.scoreboard

import com.google.gson.JsonObject
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

/** Active Fire Sales for the Custom Scoreboard's "Fire Sales" extra. `/skyblock/firesales` is a
 *  public Hypixel endpoint (confirmed live, no API key) called directly, same as [ElectionInfo].
 *  Hypixel doesn't publicly document the "sales" entry field names, so parsing tries the common
 *  candidates defensively and only shows an item name -- never a guessed price/time -- since a
 *  silently-empty section is a safer failure mode here than a made-up number. */
object FireSaleInfo {

    private val HTTP: HttpClient = HttpClient.newHttpClient()
    private const val REFRESH_MS = 300_000L // 5 minutes -- sales are short-lived

    private var saleLines: List<String> = emptyList()
    private var lastFetchAt = 0L
    private var fetchInFlight = false

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!FishSettings.customScoreboardEnabled || !FishSettings.sbSectionFireSales ||
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
                    .uri(URI.create("https://api.hypixel.net/v2/skyblock/firesales"))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(10)).GET().build()
                val body = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body()
                val root = JsonParser.parseString(body).asJsonObject
                val sales = root.getAsJsonArray("sales") ?: com.google.gson.JsonArray()
                val lines = ArrayList<String>()
                for (el in sales) {
                    val name = itemName(el.asJsonObject) ?: continue
                    lines.add("§7Fire Sale: §6${prettify(name)}")
                }
                mc.execute { saleLines = lines; fetchInFlight = false }
            } catch (ignored: Exception) {
                mc.execute { fetchInFlight = false }
            }
        }
    }

    private fun itemName(sale: JsonObject): String? {
        for (key in arrayOf("item", "itemId", "product_id", "productId")) {
            if (sale.has(key)) return sale.get(key).asString
        }
        return null
    }

    private fun prettify(id: String): String =
        id.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

    @JvmStatic
    fun lines(): List<String> = saleLines
}
