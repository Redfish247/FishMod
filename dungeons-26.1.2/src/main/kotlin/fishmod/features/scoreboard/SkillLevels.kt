package fishmod.features.scoreboard

import com.google.gson.JsonObject
import fishmod.utils.HypixelApi
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/** Per-skill levels for the Custom Scoreboard's "Skills" extra -- not present on Hypixel's actual
 *  sidebar (only the aggregate "Skill Average:" line is), so this polls the same Hypixel API proxy
 *  [fishmod.features.CatacombsOverflowOverlay] uses (`HypixelApi.getLocalMember`, 60s refresh) and
 *  computes levels with the same generic skill XP curve `HypixelApi.skillLevelOverflow` uses for
 *  Farming. Only covers the 8 skills that share that curve (Combat/Mining/Farming/Foraging/Fishing/
 *  Enchanting/Alchemy/Carpentry) -- Taming, Runecrafting and Social Skills have different level
 *  curves/caps and are deliberately left out rather than guessed at. */
object SkillLevels {

    // Same table as HypixelApi's private skillLevelOverflow() (SkyBlock's shared skill XP curve).
    private val SKILL_XP = LongArray(61)
    private const val OVERFLOW_PER_LEVEL = 7_000_000L

    init {
        val per = intArrayOf(
            50, 125, 200, 300, 500, 750, 1000, 1500, 2000, 3500, 5000, 7500, 10000, 15000, 20000, 30000,
            50000, 75000, 100000, 200000, 300000, 400000, 500000, 600000, 700000, 800000, 900000, 1000000,
            1100000, 1200000, 1300000, 1400000, 1500000, 1600000, 1700000, 1800000, 1900000, 2000000, 2100000,
            2200000, 2300000, 2400000, 2500000, 2600000, 2750000, 2900000, 3100000, 3400000, 3700000, 4000000,
            4300000, 4600000, 4900000, 5200000, 5500000, 5800000, 6100000, 6400000, 6700000, 7000000
        )
        var c = 0L
        for (i in per.indices) {
            c += per[i]
            SKILL_XP[i + 1] = c
        }
    }

    private val SKILLS: List<Pair<String, String>> = listOf(
        "Farming" to "SKILL_FARMING",
        "Mining" to "SKILL_MINING",
        "Combat" to "SKILL_COMBAT",
        "Foraging" to "SKILL_FORAGING",
        "Fishing" to "SKILL_FISHING",
        "Enchanting" to "SKILL_ENCHANTING",
        "Alchemy" to "SKILL_ALCHEMY",
        "Carpentry" to "SKILL_CARPENTRY"
    )

    private var levels: List<Pair<String, Double>> = emptyList()
    private var lastFetchAt = 0L
    private var fetchInFlight = false
    private const val REFRESH_MS = 60_000L

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!FishSettings.customScoreboardEnabled || !FishSettings.sbSectionSkills ||
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
        try {
            val exp = member.getAsJsonObject("player_data")?.getAsJsonObject("experience") ?: return
            val out = ArrayList<Pair<String, Double>>()
            for ((label, key) in SKILLS) {
                if (exp.has(key)) out.add(label to levelFor(exp.get(key).asDouble.toLong()))
            }
            if (out.isNotEmpty()) levels = out
        } catch (ignored: Exception) {
        }
    }

    private fun levelFor(xp: Long): Double {
        var lvl = 0
        for (i in SKILL_XP.indices.reversed()) if (xp >= SKILL_XP[i]) {
            lvl = i
            break
        }
        if (lvl >= 60) return 60 + (xp - SKILL_XP[60]) / OVERFLOW_PER_LEVEL.toDouble()
        val into = xp - SKILL_XP[lvl]
        val need = SKILL_XP[lvl + 1] - SKILL_XP[lvl]
        return lvl + (if (need > 0) into.toDouble() / need else 0.0)
    }

    /** One "§7Name: §bXX.X" line per resolved skill, empty until the first API fetch lands. */
    @JvmStatic
    fun lines(): List<String> = levels.map { (name, lvl) -> "§7$name: §b" + String.format("%.1f", lvl) }
}
