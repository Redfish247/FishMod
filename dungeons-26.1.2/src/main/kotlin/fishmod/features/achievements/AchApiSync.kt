package fishmod.features.achievements

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.utils.FishMsg
import fishmod.utils.HypixelApi
import net.minecraft.client.Minecraft
import kotlin.math.floor

// One selected-profile pull through the fishmod.dev proxy; values only ever raise counters
object AchApiSync {

    private const val MIN_GAP_MS = 10 * 60_000L
    @Volatile private var running = false

    private val SKILL_CAPS = mapOf(
        "farming" to 60, "combat" to 60, "mining" to 60, "carpentry" to 50, "hunting" to 50, "foraging" to 57,
        "fishing" to 50, "alchemy" to 50, "enchanting" to 60, "taming" to 60,
    )
    private val HOTM_XP = longArrayOf(0, 3_000, 12_000, 37_000, 97_000, 197_000, 347_000, 557_000, 847_000, 1_247_000)
    private val PET_MAX_XP = mapOf("COMMON" to 5_624_785L, "UNCOMMON" to 8_644_220L, "RARE" to 12_626_665L, "EPIC" to 18_608_500L, "LEGENDARY" to 25_353_230L, "MYTHIC" to 25_353_230L)
    private val CROPS = listOf("WHEAT", "CARROT_ITEM", "POTATO_ITEM", "MELON", "PUMPKIN", "INK_SACK:3", "CACTUS", "SUGAR_CANE", "MUSHROOM_COLLECTION", "NETHER_STALK")
    private val LOGS = listOf("LOG", "LOG:1", "LOG:2", "LOG:3", "LOG_2", "LOG_2:1")
    private val KUUDRA_TIERS = listOf("none", "hot", "burning", "fiery", "infernal")

    fun sync(manual: Boolean) {
        val mc = Minecraft.getInstance()
        val uuid = mc.player?.uuid?.toString()?.replace("-", "") ?: return
        val now = System.currentTimeMillis()
        if (running) { if (manual) FishMsg.send("§7Achievement sync already running."); return }
        if (!manual && now - AchStore.data.lastApiSync < MIN_GAP_MS) return
        running = true
        if (manual) FishMsg.send("§7Syncing achievements from the Hypixel API...")
        HypixelApi.fetchSelectedMember(uuid) { member ->
            mc.execute {
                running = false
                if (member == null) { if (manual) { FishMsg.send("§cAchievement sync failed (API unreachable or no profile)."); AchLeaderboard.submit(true) }; return@execute }
                val before = AchStore.data.unlocked.size
                try { apply(member) } catch (t: Throwable) { fishmod.utils.debug.FishDiag.fail("AchApiSync.1", "apply profile failed", t) }
                AchStore.data.lastApiSync = System.currentTimeMillis()
                AchStore.changed()
                AchEngine.evaluate()
                if (manual) { FishMsg.send("§aAchievements synced. §7${AchStore.data.unlocked.size - before} new unlock(s)."); AchLeaderboard.submit(true) }
            }
        }
    }

    private fun el(o: JsonObject?, path: String): JsonElement? {
        var cur: JsonElement = o ?: return null
        for (p in path.split('.')) {
            if (!cur.isJsonObject) return null
            cur = cur.asJsonObject.get(p) ?: return null
        }
        return if (cur.isJsonNull) null else cur
    }

    private fun num(o: JsonObject?, path: String): Double = el(o, path)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: 0.0
    private fun obj(o: JsonObject?, path: String): JsonObject? = el(o, path)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun put(k: String, v: Double) { if (v > 0) AchStore.max(k, v.toLong()) }

    private fun apply(m: JsonObject) {
        val cata = num(m, "dungeons.dungeon_types.catacombs.experience")
        put("cata.level", HypixelApi.calcCataLevel(cata.toLong()).toDouble())
        var classSum = 0.0
        for (c in listOf("healer", "mage", "berserk", "archer", "tank")) {
            val lvl = HypixelApi.calcCataLevel(num(m, "dungeons.player_classes.$c.experience").toLong()).toDouble()
            classSum += minOf(lvl, 50.0); put("class.$c", lvl)
        }
        put("class.avg", floor(classSum / 5))
        put("secrets.total", num(m, "dungeons.secrets"))
        var runs = 0.0; var mm = 0.0; var mmMin = Double.MAX_VALUE
        obj(m, "dungeons.dungeon_types.catacombs.tier_completions")?.entrySet()?.forEach { (k, v) ->
            val n = k.toIntOrNull() ?: return@forEach
            val c = v.asDouble; runs += c
            if (n in 1..7) put("floor.F$n", c)
        }
        val master = obj(m, "dungeons.dungeon_types.master_catacombs.tier_completions")
        for (n in 1..7) {
            val c = master?.get(n.toString())?.asDouble ?: 0.0
            runs += c; mm += c; mmMin = minOf(mmMin, c); put("floor.M$n", c)
        }
        put("dungeon.runs", runs); put("mm.total", mm); put("mm.minAll", mmMin)

        for ((s, cap) in SKILL_CAPS) {
            val xp = num(m, "player_data.experience.SKILL_${s.uppercase()}")
            if (xp > 0) put("skill.$s", floor(HypixelApi.skillLevelFor(xp.toLong(), cap)))
        }
        HypixelApi.computeSkillAverage(m)?.toDoubleOrNull()?.let { put("skill.avg", floor(it)) }

        val bosses = obj(m, "slayer.slayer_bosses")
        var slayerTotal = 0.0; var maxed = 0
        bosses?.entrySet()?.forEach { (name, b) ->
            if (!b.isJsonObject) return@forEach
            for ((k, v) in b.asJsonObject.entrySet()) if (k.startsWith("boss_kills_tier_") && v.isJsonPrimitive) slayerTotal += v.asDouble
            val xp = num(b.asJsonObject, "xp")
            if (xp >= (if (name == "vampire") 2_400.0 else 1_000_000.0)) maxed++
        }
        put("slayer.total", slayerTotal)
        put("slayer.zombieT5", num(bosses, "zombie.boss_kills_tier_4"))
        put("slayer.spiderT5", num(bosses, "spider.boss_kills_tier_4"))
        put("slayer.wolfT4", num(bosses, "wolf.boss_kills_tier_3"))
        put("slayer.endermanT4", num(bosses, "enderman.boss_kills_tier_3"))
        put("slayer.blazeT4", num(bosses, "blaze.boss_kills_tier_3"))
        if (maxed >= 6) put("slayer.maxedAll", 1.0)

        val hotmXp = num(m, "mining_core.experience")
        put("mining.hotm", (HOTM_XP.indexOfLast { hotmXp >= it } + 1).toDouble())
        put("mining.powder", listOf("mithril", "gemstone", "glacite").sumOf { num(m, "mining_core.powder_$it") + num(m, "mining_core.powder_spent_$it") })
        put("mining.corpses", obj(m, "glacite_player_data.corpses_looted")?.entrySet()?.sumOf { it.value.asDouble } ?: 0.0)
        put("mining.mineshafts", num(m, "glacite_player_data.mineshafts_entered"))

        val kills = obj(m, "bestiary.kills")
        put("mining.scatha", num(kills, "scatha_10"))
        put("fish.thunder", num(kills, "thunder_400"))
        put("fish.jawbus", num(kills, "lord_jawbus_600"))
        put("fish.hydra", num(kills, "water_hydra_100"))
        put("diana.inquis", num(kills, "minos_inquisitor_750"))
        put("garden.pests", kills?.entrySet()?.filter { it.key.startsWith("pest_") && it.value.isJsonPrimitive }?.sumOf { it.value.asDouble } ?: 0.0)

        put("fish.seaCreatures", num(m, "player_stats.sea_creature_kills"))
        put("diana.mobs", num(m, "player_stats.mythos.kills"))
        put("diana.burrows", listOf("next", "combat", "treasure").sumOf { num(m, "player_stats.mythos.burrows_dug_$it.total") })
        put("rift.motes", num(m, "player_stats.rift.lifetime_motes_earned"))

        obj(m, "trophy_fish")?.let { tf ->
            val keys = tf.keySet()
            val fish = keys.filter { k -> listOf("_bronze", "_silver", "_gold", "_diamond").none { k.endsWith(it) } && tf.get(k).isJsonPrimitive && tf.get(k).asJsonPrimitive.isNumber && k != "total_caught" }
            if (fish.size >= 18) put("fish.trophyAll", 1.0)
            if (keys.count { it.endsWith("_diamond") } >= 18) put("fish.trophyDiamondAll", 1.0)
        }

        val nether = obj(m, "nether_island_player_data")
        if (el(nether, "selected_faction")?.asString?.isNotBlank() == true) put("crimson.faction", 1.0)
        put("crimson.rep", maxOf(num(nether, "mages_reputation"), num(nether, "barbarians_reputation")))
        val dojo = obj(nether, "dojo")?.entrySet()?.filter { it.key.startsWith("dojo_points_") }?.sumOf { it.value.asDouble } ?: 0.0
        if (dojo >= 7000) put("crimson.dojoBlack", 1.0)
        var kt = 0.0
        KUUDRA_TIERS.forEachIndexed { i, t -> val c = num(nether, "kuudra_completed_tiers.$t"); kt += c; put("kuudra.t${i + 1}", c) }
        put("kuudra.total", kt)

        el(m, "pets_data.pets")?.takeIf { it.isJsonArray }?.asJsonArray?.let { pets ->
            val types = HashSet<String>(); var l100 = 0
            for (p in pets) {
                val o = p.asJsonObject
                val type = el(o, "type")?.asString ?: continue
                types.add(type)
                val exp = num(o, "exp")
                if (exp >= (PET_MAX_XP[el(o, "tier")?.asString ?: ""] ?: Long.MAX_VALUE)) l100++
                if (type == "GOLDEN_DRAGON" && exp >= 210_255_385) put("pets.gdrag200", 1.0)
            }
            put("pets.unique", types.size.toDouble()); put("pets.lvl100", l100.toDouble())
        }

        put("purse", num(m, "currencies.coin_purse"))
        el(m, "player_data.crafted_generators")?.takeIf { it.isJsonArray }?.asJsonArray?.let { gens ->
            if (gens.any { (it.asString.substringAfterLast('_').toIntOrNull() ?: 0) >= 11 }) put("minion.t11", 1.0)
        }
        put("choc.prestige", num(m, "events.easter.chocolate_level") - 1)
        put("choc.rabbits", obj(m, "events.easter.rabbits")?.keySet()?.count { it != "collected_eggs" && it != "collected_locations" }?.toDouble() ?: 0.0)

        var gold = 0; var diamond = 0
        obj(m, "jacobs_contest.contests")?.entrySet()?.forEach { (_, c) ->
            when (el(c.asJsonObject, "claimed_medal")?.asString) { "gold", "platinum" -> gold++; "diamond" -> { gold++; diamond++ } }
        }
        put("jacob.gold", gold.toDouble()); put("jacob.diamond", diamond.toDouble())

        val coll = obj(m, "collection")
        put("farming.crops", CROPS.sumOf { num(coll, it) })
        put("foraging.logs", LOGS.sumOf { coll?.get(it)?.takeIf { e -> e.isJsonPrimitive }?.asDouble ?: 0.0 })
    }
}
