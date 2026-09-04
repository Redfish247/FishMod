package fishmod.features.scoreboard

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.HypixelApi
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import java.io.InputStreamReader

/** Overall Bestiary completion for the Custom Scoreboard's "Bestiary" extra. Hypixel doesn't
 *  expose a ready-made percentage, so this computes it: each mob family has a kill-count bracket
 *  (one of 7 shared threshold curves) capped at that family's max kills, its current tier is how
 *  many thresholds its summed kills clear, and the overall score is (sum of current tiers) /
 *  (sum of max tiers). The family/bracket/cap table is `data/bestiary.json` (208 families,
 *  809 kill keys). */
object BestiaryProgress {

    private class Family(val bracket: Int, val cap: Int, val keys: List<String>)

    private val BRACKETS = HashMap<Int, IntArray>()
    private val FAMILIES = ArrayList<Family>()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            javaClass.getResourceAsStream("/data/bestiary.json")?.use { stream ->
                InputStreamReader(stream).use { reader ->
                    val root = JsonParser.parseReader(reader).asJsonObject
                    for ((k, v) in root.getAsJsonObject("brackets").entrySet()) {
                        BRACKETS[k.toInt()] = v.asJsonArray.map { it.asInt }.toIntArray()
                    }
                    for (f in root.getAsJsonArray("families")) {
                        val fo = f.asJsonObject
                        val keys = fo.getAsJsonArray("keys").map { it.asString }
                        FAMILIES.add(Family(fo.get("bracket").asInt, fo.get("cap").asInt, keys))
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    /** How many thresholds in this bracket are cleared by `kills`, capped at the family's max tier
     *  (the family's `cap` truncates the shared 25-entry bracket curve early for easy/common mobs). */
    private fun tierFor(bracket: IntArray, cap: Int, kills: Long): Int {
        var maxTier = 0
        for (t in bracket) if (t <= cap) maxTier++ else break
        var tier = 0
        for (i in 0 until maxTier) if (kills >= bracket[i]) tier = i + 1
        return tier
    }

    private var milestone = 0
    private var maxMilestone = 0
    private var lastFetchAt = 0L
    private var fetchInFlight = false
    private const val REFRESH_MS = 60_000L

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!FishSettings.customScoreboardEnabled || !FishSettings.sbSectionBestiary ||
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
        if (FAMILIES.isEmpty()) return
        try {
            val kills = member.getAsJsonObject("bestiary")?.getAsJsonObject("kills") ?: return
            var ms = 0
            var maxMs = 0
            for (fam in FAMILIES) {
                val bracket = BRACKETS[fam.bracket] ?: continue
                var total = 0L
                for (key in fam.keys) if (kills.has(key)) total += kills.get(key).asLong
                var maxTier = 0
                for (t in bracket) if (t <= fam.cap) maxTier++ else break
                ms += tierFor(bracket, fam.cap, total)
                maxMs += maxTier
            }
            milestone = ms
            maxMilestone = maxMs
        } catch (ignored: Exception) {
        }
    }

    /** "§7Bestiary: §d1234§7/§d5678 §7(21.7%)", empty until the first poll lands. */
    @JvmStatic
    fun lines(): List<String> {
        if (maxMilestone <= 0) return emptyList()
        val pct = 100.0 * milestone / maxMilestone
        return listOf("§7Bestiary: §d$milestone§7/§d$maxMilestone §7(${String.format("%.1f", pct)}%)")
    }
}
