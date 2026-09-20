package fishmod.features.scoreboard

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.HypixelApi
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import java.io.InputStreamReader

object CollectionsProgress {

    private val MAX_TIER = HashMap<String, Long>()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            javaClass.getResourceAsStream("/data/collections_max.json")?.use { stream ->
                InputStreamReader(stream).use { reader ->
                    val root = JsonParser.parseReader(reader).asJsonObject
                    for ((k, v) in root.entrySet()) MAX_TIER[k] = v.asLong
                }
            }
        } catch (ignored: Exception) {
        }
    }

    private var maxed = 0
    private var total = 0
    private var lastFetchAt = 0L
    private var fetchInFlight = false
    private const val REFRESH_MS = 60_000L

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!FishSettings.customScoreboardEnabled || !FishSettings.sbSectionCollections ||
                client.player == null || !Location.inSkyblock()
            ) return@register
            val now = System.currentTimeMillis()
            if (fetchInFlight || now - lastFetchAt < REFRESH_MS) return@register
            lastFetchAt = now
            fetchInFlight = true
            HypixelApi.getLocalMember(client) { member ->
                fetchInFlight = false
                apply(member)
            }
        }
    }

    private fun apply(member: JsonObject?) {
        if (member == null) return
        ensureLoaded()
        if (MAX_TIER.isEmpty()) return
        try {
            val collection = member.getAsJsonObject("collection") ?: return
            var maxedCount = 0
            for ((id, threshold) in MAX_TIER) {
                val amount = if (collection.has(id)) collection.get(id).asLong else 0L
                if (amount >= threshold) maxedCount++
            }
            maxed = maxedCount
            total = MAX_TIER.size
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun lines(): List<String> {
        if (total <= 0) return emptyList()
        return listOf("§7Collections: §b$maxed§7/§b$total §7maxed")
    }
}
