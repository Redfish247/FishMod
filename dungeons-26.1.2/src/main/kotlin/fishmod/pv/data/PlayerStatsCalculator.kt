/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import fishmod.pv.dungeons.CataXpMath
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.PetWorth
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.OptionalDouble
import kotlin.math.abs
import kotlin.math.floor

/**
 * Hypixel does not expose final stats - values are reconstructed from NEU constants,
 * skills/slayers, SkyBlock level, bestiary, essence permanents, consumables, Maxwell power, tuning,
 * active pet, and item lore on armor / equipment / accessories.
 */
object PlayerStatsCalculator {
    private class StatDef(val id: String, val label: String, val regex: Regex)

    private class AttributeFlat(val stackId: String, val statId: String, val perLevel: Double)

    private const val STAT_END = ": ((?:\\+|-)([0-9]+(?:\\.[0-9]+)?))%?"

    private fun stat(id: String, label: String, prefix: String) = StatDef(id, label, Regex("^$prefix$STAT_END"))

    private val DISPLAY = listOf(
        stat("health", "HP", "Health"),
        stat("defense", "Def", "(?:Defence|Defense)"),
        stat("strength", "STR", "Strength"),
        stat("speed", "SPD", "Speed"),
        stat("critical_chance", "CC", "Crit Chance"),
        stat("critical_damage", "CD", "Crit Damage"),
        stat("attack_speed", "AS", "(?:Bonus Attack Speed|Attack Speed)"),
        stat("intelligence", "INT", "Intelligence"),
        stat("ferocity", "Fero", "Ferocity"),
        stat("ability_damage", "AD", "Ability Damage"),
        stat("magic_find", "MF", "Magic Find"),
        stat("pet_luck", "PL", "Pet Luck"),
        stat("true_defense", "TD", "(?:True Defence|True Defense)"),
        stat("health_regen", "RG", "Health Regen"),
        stat("vitality", "VIT", "Vitality"),
        stat("mending", "MD", "Mending"),
        stat("swing_range", "Swing", "Swing Range"),
        stat("mining_fortune", "MiF", "Mining Fortune"),
        stat("farming_fortune", "FaF", "Farming Fortune"),
        stat("foraging_fortune", "FoF", "Foraging Fortune"),
        stat("timber", "Timb", "Timber"),
        stat("mining_speed", "MS", "Mining Speed"),
        stat("mining_spread", "Spr", "Mining Spread"),
        stat("pristine", "Pris", "Pristine"),
        stat("sea_creature_chance", "SCC", "Sea Creature Chance"),
        stat("fishing_speed", "FS", "Fishing Speed"),
    )
    private val DISPLAY_IDS = DISPLAY.mapTo(HashSet()) { it.id }

    /** Stats Hypixel always starts at these values; NEU misc.base_stats omits healing stats. */
    private val FALLBACK_BASE = mapOf(
        "vitality" to 100.0,
        "health_regen" to 100.0,
        "mending" to 100.0,
        "swing_range" to 3.0,
    )

    /**
     * Unconditional Hunting attribute → flat player stats (per level).
     * `attributes.stacks.mending` is the Vitality shard (API id ≠ combat Mending).
     */
    private val ATTRIBUTE_FLAT = listOf(
        AttributeFlat("life_regeneration", "health_regen", 1.25),
        AttributeFlat("mending", "vitality", 2.0),
        AttributeFlat("magic_find", "magic_find", 0.5),
        AttributeFlat("speed", "speed", 1.0),
        AttributeFlat("attack_speed", "attack_speed", 1.0),
        AttributeFlat("veil", "true_defense", 1.0),
        AttributeFlat("fishing_speed", "fishing_speed", 3.0),
        AttributeFlat("rotten_pickaxe", "mining_speed", 3.0),
        AttributeFlat("earth_elemental", "health", 2.0),
        AttributeFlat("forest_elemental", "health", 2.0),
        AttributeFlat("nature_elemental", "health", 2.0),
        AttributeFlat("shadow_elemental", "health", 2.0),
        AttributeFlat("wood_elemental", "health", 2.0),
        AttributeFlat("light_elemental", "strength", 1.0),
        AttributeFlat("lightning_elemental", "strength", 1.0),
        AttributeFlat("stone_elemental", "strength", 1.0),
        AttributeFlat("storm_elemental", "strength", 1.0),
        AttributeFlat("wind_elemental", "strength", 1.0),
        AttributeFlat("fog_elemental", "intelligence", 1.0),
        AttributeFlat("frost_elemental", "intelligence", 1.0),
        AttributeFlat("snow_elemental", "intelligence", 1.0),
        AttributeFlat("torrent_elemental", "intelligence", 1.0),
        AttributeFlat("water_elemental", "intelligence", 1.0),
        AttributeFlat("ultimate_dna", "mining_fortune", 1.0),
        AttributeFlat("ultimate_dna", "farming_fortune", 1.0),
        AttributeFlat("ultimate_dna", "foraging_fortune", 1.0),
        AttributeFlat("rare_bird", "pet_luck", 1.0),
    )

    /** Wither Essence Forbidden perks - cumulative total at each level (wiki). */
    private val FORBIDDEN = mapOf(
        "permanent_health" to intArrayOf(0, 2, 4, 6, 8, 10),
        "permanent_defense" to intArrayOf(0, 1, 2, 3, 4, 5),
        "permanent_speed" to intArrayOf(0, 1, 2),
        "permanent_intelligence" to intArrayOf(0, 1, 2, 3, 4, 5),
        "permanent_strength" to intArrayOf(0, 1, 2, 3, 4, 5),
    )

    private val FORBIDDEN_STAT = mapOf(
        "permanent_health" to "health",
        "permanent_defense" to "defense",
        "permanent_speed" to "speed",
        "permanent_intelligence" to "intelligence",
        "permanent_strength" to "strength",
    )

    private val SLAYERS = listOf("zombie", "spider", "wolf", "enderman", "blaze", "vampire")
    private val SKILLS = listOf(
        "combat", "mining", "farming", "foraging", "fishing", "enchanting", "alchemy", "taming", "carpentry",
        "runecrafting", "social",
    )

    @Volatile
    private var miscCache: JsonObject? = null

    @Volatile
    private var bonusesCache: JsonObject? = null

    @Volatile
    private var petnumsCache: JsonObject? = null

    @Volatile
    private var attributeShardsCache: JsonObject? = null

    // Worn gear only: parseCategories also merges every saved equipment loadout into "equipment".
    @JvmStatic
    fun fromMember(member: JsonObject?): PlayerStatsSnapshot =
        fromMember(member, if (member == null) emptyMap() else InventoryDecoder.parseHomeGear(member))

    @JvmStatic
    fun fromMember(member: JsonObject?, categories: Map<String, List<InventoryDecoder.Stack?>?>?): PlayerStatsSnapshot {
        if (member == null) return PlayerStatsSnapshot.empty()
        val totals = LinkedHashMap<String, Double>()
        var anySource = false

        val misc = misc()
        if (misc != null && misc.has("base_stats") && misc.get("base_stats").isJsonObject) {
            val base = misc.getAsJsonObject("base_stats")
            for (def in DISPLAY) {
                val baseValue = readBaseStat(base, def.id) ?: continue
                add(totals, def.id, baseValue)
                anySource = true
            }
        }
        for ((id, value) in FALLBACK_BASE) {
            if (!totals.containsKey(id)) {
                add(totals, id, value)
                anySource = true
            }
        }

        anySource = addBonusStats(totals, member) or anySource
        anySource = addZombieSlayerRegen(totals, member) or anySource
        anySource = addHuntingAttributes(totals, member) or anySource
        anySource = addSkyBlockLevel(totals, member) or anySource
        anySource = addBestiary(totals, member) or anySource
        anySource = addCatacombs(totals, member) or anySource
        anySource = addEssencePermanents(totals, member) or anySource
        anySource = addConsumables(totals, member) or anySource
        anySource = addHotm(totals, member) or anySource
        anySource = addMaxwellAndTuning(totals, member) or anySource
        anySource = addPetScore(totals, member) or anySource
        anySource = addActivePet(totals, member) or anySource

        if (categories != null) {
            anySource = addItemLore(totals, categories["armor"], false) or anySource
            anySource = addItemLore(totals, categories["equipment"], false) or anySource
            anySource = addItemLore(totals, categories["accessories"], true) or anySource
        }

        anySource = applyDragonGreed(totals, member) or anySource

        val entries = DISPLAY.map { def ->
            val total = totals[def.id]
            val value = if (anySource && total != null) OptionalDouble.of(total) else OptionalDouble.empty()
            PlayerStatsSnapshot.Entry(def.id, def.label, value)
        }
        return PlayerStatsSnapshot(entries)
    }

    /** Skill + slayer milestone bonuses from NEU `bonuses.json`. */
    private fun addBonusStats(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        val bonuses = bonuses()
        if (bonuses == null || !bonuses.has("bonus_stats") || !bonuses.get("bonus_stats").isJsonObject) return false
        val bonusStats = bonuses.getAsJsonObject("bonus_stats")
        var added = false

        for ((skill, level) in skillLevels(member)) {
            added = applyMilestoneBonuses(totals, bonusStats, "skill_$skill", level) or added
        }
        for (slayer in SLAYERS) {
            val level = slayerLevel(member, slayer)
            if (level > 0) added = applyMilestoneBonuses(totals, bonusStats, "slayer_$slayer", level) or added
        }
        return added
    }

    private fun slayerLevel(member: JsonObject, slayer: String): Int {
        val xp = Leveling.readSlayerXp(member, slayer)
        val table = RepoData.slayerXp(slayer)
        val cap = if (table == null || table.isEmpty) 9 else table.size()
        val progress: Leveling.Progress? = Leveling.getLevel(table, xp, cap, true)
        return if (progress == null) 0 else floor(progress.level).toInt()
    }

    /**
     * NEU milestone tables only list breakpoints; the last defined bonus sticks and is applied
     * once per level up to the player's level (combat CC is +0.5 each level, etc.).
     */
    private fun applyMilestoneBonuses(totals: MutableMap<String, Double>, bonusStats: JsonObject, key: String, level: Int): Boolean {
        if (level <= 0 || !bonusStats.has(key) || !bonusStats.get(key).isJsonObject) return false
        val perLevel = bonusStats.getAsJsonObject(key)
        var current: Map<String, Double> = emptyMap()
        var added = false
        for (lvl in 1..level) {
            val k = lvl.toString()
            if (perLevel.has(k) && perLevel.get(k).isJsonObject) current = readStatMap(perLevel.getAsJsonObject(k))
            for ((id, value) in current) {
                if (wanted(id)) {
                    add(totals, id, value)
                    added = true
                }
            }
        }
        return added
    }

    /** Zombie Slayer VIII+ permanently grants +50 Health Regen (not in NEU bonuses.json). */
    private fun addZombieSlayerRegen(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        if (slayerLevel(member, "zombie") < 8) return false
        add(totals, "health_regen", 50.0)
        return true
    }

    /**
     * Hunting Box attribute levels from `attributes.stacks` + NEU shard costs.
     * Only unconditional flat stat grants (no island/mob-conditional bonuses).
     */
    private fun addHuntingAttributes(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        val attributes = obj(member.get("attributes"))
        val stacks = attributes?.let { obj(it.get("stacks")) }
        if (stacks == null || stacks.entrySet().isEmpty()) return false
        val shardsFile = attributeShards() ?: return false
        val costsByRarity = readAttributeLevelling(shardsFile)
        val rarityById = readAttributeRarities(shardsFile)
        var added = false
        for (flat in ATTRIBUTE_FLAT) {
            val shards = intOr(stacks, flat.stackId)
            if (shards <= 0) continue
            val rarity = rarityById[flat.stackId] ?: "COMMON"
            val level = attributeLevel(costsByRarity[rarity], shards)
            if (level <= 0) continue
            add(totals, flat.statId, flat.perLevel * level)
            added = true
        }
        return added
    }

    /** Attribute levels for the given stack ids; empty when the NEU shard table isn't available. */
    @JvmStatic
    fun attributeLevels(member: JsonObject?, stackIds: List<String>): Map<String, Int> {
        val attributes = member?.let { obj(it.get("attributes")) }
        val stacks = attributes?.let { obj(it.get("stacks")) }
        val shardsFile = (if (stacks == null) null else attributeShards()) ?: return emptyMap()
        val costsByRarity = readAttributeLevelling(shardsFile)
        val rarityById = readAttributeRarities(shardsFile)
        val out = HashMap<String, Int>()
        for (id in stackIds) {
            val level = attributeLevel(costsByRarity[rarityById[id] ?: "COMMON"], intOr(stacks, id))
            if (level > 0) out[id] = level
        }
        return out
    }

    private fun readAttributeLevelling(shardsFile: JsonObject): Map<String, IntArray> {
        val levelling = obj(shardsFile.get("attribute_levelling")) ?: return emptyMap()
        val out = HashMap<String, IntArray>()
        for ((key, value) in levelling.entrySet()) {
            if (!value.isJsonArray) continue
            val arr = value.asJsonArray
            out[key.uppercase()] = IntArray(arr.size()) { i -> soft(0) { arr.get(i).asInt } }
        }
        return out
    }

    private fun readAttributeRarities(shardsFile: JsonObject): Map<String, String> {
        if (!shardsFile.has("attributes") || !shardsFile.get("attributes").isJsonArray) return emptyMap()
        val out = HashMap<String, String>()
        for (el in shardsFile.getAsJsonArray("attributes")) {
            val row = obj(el) ?: continue
            val internal = if (row.has("internalName") && row.get("internalName").isJsonPrimitive) row.get("internalName").asString else ""
            val rarity = if (row.has("rarity") && row.get("rarity").isJsonPrimitive) row.get("rarity").asString.uppercase() else "COMMON"
            val id = attributeStackId(internal)
            if (id.isNotEmpty()) out[id] = rarity
        }
        return out
    }

    private fun attributeStackId(internalName: String?): String {
        if (internalName.isNullOrBlank()) return ""
        return internalName.removePrefix("ATTRIBUTE_SHARD_").substringBefore(';').lowercase()
    }

    private fun attributeLevel(costs: IntArray?, shards: Int): Int {
        if (costs == null || costs.isEmpty() || shards <= 0) return 0
        var level = 0
        var remaining = shards
        for (cost in costs) {
            if (cost <= 0 || remaining < cost) break
            remaining -= cost
            level++
        }
        return level
    }

    /** +5 HP per SB level, +5 extra every 10 levels (wiki → 2750 at 500). */
    private fun addSkyBlockLevel(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        val leveling = obj(member.get("leveling"))
        val exp = leveling?.let { Leveling.num(it.get("experience")) }
        if (exp == null || exp <= 0f) return false
        val level = floor(exp / 100f).toInt()
        if (level <= 0) return false
        add(totals, "health", 5.0 * level + 5.0 * (level / 10))
        return true
    }

    private fun addBestiary(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        val bestiary = obj(member.get("bestiary"))
        val milestone = bestiary?.let { obj(it.get("milestone")) }
        val claimed = intOr(milestone, "last_claimed_milestone")
        if (claimed <= 0) return false
        add(totals, "health", 1.0 * claimed)
        return true
    }

    private fun addCatacombs(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        val xp = Leveling.readCatacombsXp(member)
        if (xp <= 0f) return false
        val progress: Leveling.Progress? = CataXpMath.progress(xp)
        // Stat bonuses stop at the soft cap; mastery levels are cosmetic only.
        val level = if (progress == null) 0 else minOf(CataXpMath.SOFT_CAP, progress.displayLevel())
        if (level <= 0) return false
        add(totals, "health", 2.0 * level)
        return true
    }

    private fun addEssencePermanents(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        val playerData = obj(member.get("player_data"))
        val perks = playerData?.let { obj(it.get("perks")) }
            ?: obj(member.get("dungeons"))?.let { obj(it.get("perks")) }
            ?: return false
        var added = false
        for ((key, table) in FORBIDDEN) {
            val level = intOr(perks, key).coerceIn(0, table.size - 1)
            if (level <= 0) continue
            add(totals, FORBIDDEN_STAT.getValue(key), table[level].toDouble())
            added = true
        }
        return added
    }

    // Each consumable caps at 5 eaten.
    private fun addConsumables(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        val playerData = obj(member.get("player_data")) ?: return false
        val peppers = intOr(playerData, "reaper_peppers_eaten").coerceIn(0, 5)
        val saliva = intOr(playerData, "bee_saliva_eaten").coerceIn(0, 5)
        val husks = intOr(playerData, "isopod_husks_eaten").coerceIn(0, 5)
        if (peppers > 0) {
            add(totals, "health", peppers.toDouble())
            add(totals, "cold_resistance", peppers.toDouble())
        }
        if (saliva > 0) add(totals, "foraging_fortune", saliva.toDouble())
        if (husks > 0) add(totals, "respiration", husks.toDouble())
        return peppers + saliva + husks > 0
    }

    private fun addHotm(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        val miningLevel = skillLevels(member)["mining"] ?: 0
        val hotmNodes = hotmPerkNodes(member)
        if (hotmNodes == null && miningLevel <= 0) return false
        var added = false
        val fortune = 4.0 * miningLevel +
            5.0 * intOr(hotmNodes, "mining_fortune") +
            5.0 * intOr(hotmNodes, "mining_fortune_2") +
            5.0 * intOr(hotmNodes, "fortunate_mineman")
        if (fortune > 0) {
            add(totals, "mining_fortune", fortune)
            added = true
        }
        val miningSpeed = 20.0 * intOr(hotmNodes, "mining_speed") +
            40.0 * intOr(hotmNodes, "mining_speed_2") +
            40.0 * intOr(hotmNodes, "speedy_mineman")
        if (miningSpeed > 0) {
            add(totals, "mining_speed", miningSpeed)
            added = true
        }
        return added
    }

    private fun addMaxwellAndTuning(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        val storage = obj(member.get("accessory_bag_storage")) ?: return false
        var added = false
        var mp = MagicalPowerCalculator.fromMember(member)
        if (mp <= 0) mp = intOr(storage, "highest_magical_power")
        val power = if (storage.has("selected_power") && storage.get("selected_power").isJsonPrimitive) storage.get("selected_power").asString else ""
        for ((id, value) in MaxwellPowers.statsFor(power, mp)) {
            if (wanted(id)) {
                add(totals, id, value)
                added = true
            }
        }

        val tuning = obj(storage.get("tuning"))
        if (tuning != null) {
            // Hypixel does not expose which template is equipped. Prefer the lowest
            // slot index with points (slot_0 is the primary Maxwell template). Picking
            // "most points" wrongly selects an alternate preset (e.g. CD instead of SPD).
            var best: JsonObject? = null
            var bestIndex = Int.MAX_VALUE
            for ((key, value) in tuning.entrySet()) {
                if (key == null || !key.startsWith("slot_")) continue
                val index = tryParseInt(key.substring("slot_".length))
                if (index == null || index >= bestIndex) continue
                val slot = obj(value) ?: continue
                var points = 0
                for ((_, stat) in slot.entrySet()) {
                    if (stat.isJsonPrimitive) points += soft(0) { abs(stat.asInt) }
                }
                if (points > 0) {
                    bestIndex = index
                    best = slot
                }
            }
            if (best != null) {
                for ((id, value) in readStatMap(best)) {
                    add(totals, id, value)
                    added = true
                }
            }
        }
        return added
    }

    private fun addPetScore(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        val bonuses = bonuses()
        if (bonuses == null || !bonuses.has("pet_rewards") || !bonuses.get("pet_rewards").isJsonObject) return false
        val leveling = obj(member.get("leveling"))
        val score = intOr(leveling, "highest_pet_score")
        if (score <= 0) return false
        var best: Map<String, Double> = emptyMap()
        var bestKey = -1
        for ((rawKey, value) in bonuses.getAsJsonObject("pet_rewards").entrySet()) {
            val key = tryParseInt(rawKey)
            if (key == null || key > score || key < bestKey || !value.isJsonObject) continue
            bestKey = key
            best = readStatMap(value.asJsonObject)
        }
        var added = false
        for ((id, value) in best) {
            if (wanted(id)) {
                add(totals, id, value)
                added = true
            }
        }
        return added
    }

    private fun addActivePet(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        val active = activePet(member) ?: return false
        val stats = HashMap(petStatNums(active))
        val type = petType(active)
        val level = maxOf(1, PetWorth.levelInfo(active).level)

        if (type == "GOLDEN_DRAGON") applyGoldenDragonAbilities(stats, member, level)

        applyPetHeldItem(stats, active)

        var added = false
        for ((id, value) in stats) {
            if (wanted(id)) {
                add(totals, id, value)
                added = true
            }
        }
        return added
    }

    private fun petType(pet: JsonObject): String =
        if (pet.has("type") && pet.get("type").isJsonPrimitive) pet.get("type").asString else ""

    private fun activePet(member: JsonObject): JsonObject? {
        val petsData = obj(member.get("pets_data"))
        val pets: JsonArray = when {
            petsData != null && petsData.has("pets") && petsData.get("pets").isJsonArray -> petsData.getAsJsonArray("pets")
            member.has("pets") && member.get("pets").isJsonArray -> member.getAsJsonArray("pets")
            else -> return null
        }
        for (el in pets) {
            if (el == null || !el.isJsonObject) continue
            val pet = el.asJsonObject
            if (pet.has("active") && pet.get("active").isJsonPrimitive && pet.get("active").asBoolean) return pet
        }
        return null
    }

    /**
     * GDRAG Shining Scales: +11.1 STR / +2.2 MF per digit of personal Gold collection (cap 100M).
     * Dragon's Greed is applied later once total Magic Find is known.
     */
    private fun applyGoldenDragonAbilities(stats: MutableMap<String, Double>, member: JsonObject, level: Int) {
        if (level < 100) return
        val capped = goldCollection(member).coerceIn(0L, 100_000_000L)
        val digits = minOf(if (capped <= 0L) 1 else capped.toString().length, 9)
        add(stats, "strength", digits * 11.1)
        add(stats, "magic_find", digits * 2.2)
    }

    /** Percent Strength from GDRAG Dragon's Greed using final Magic Find. */
    private fun applyDragonGreed(totals: MutableMap<String, Double>, member: JsonObject): Boolean {
        val active = activePet(member) ?: return false
        if (petType(active) != "GOLDEN_DRAGON") return false
        val level = maxOf(1, PetWorth.levelInfo(active).level)
        if (level < 100) return false
        val t = ((level - 100) / 100.0).coerceIn(0.0, 1.0)
        val perFive = 0.25 + 0.25 * t
        val maxPct = 2.5 + 2.5 * t
        val mf = totals["magic_find"] ?: 0.0
        val pct = minOf(maxPct, (mf / 5.0) * perFive)
        if (pct <= 0) return false
        val strength = totals["strength"] ?: 0.0
        if (strength <= 0) return false
        add(totals, "strength", strength * (pct / 100.0))
        return true
    }

    private fun applyPetHeldItem(stats: MutableMap<String, Double>, pet: JsonObject) {
        if (!pet.has("heldItem") || !pet.get("heldItem").isJsonPrimitive) return
        when (pet.get("heldItem").asString) {
            "MINOS_RELIC" -> stats.replaceAll { _, v -> v * 1.333 }
            "HEPHAESTUS_REMEDIES" -> stats["strength"]?.let { stats["strength"] = it * 2.0 }
            "ANTIQUE_REMEDIES" -> stats["strength"]?.let { stats["strength"] = it * 1.8 }
        }
    }

    private fun goldCollection(member: JsonObject): Long {
        val collection = obj(member.get("collection"))
        if (collection == null || !collection.has("GOLD_INGOT") || !collection.get("GOLD_INGOT").isJsonPrimitive) return 0L
        return soft(0L) { maxOf(0L, collection.get("GOLD_INGOT").asLong) }
    }

    private fun petStatNums(pet: JsonObject): Map<String, Double> {
        val petnums = petnums()
        if (petnums == null || !pet.has("type") || !pet.get("type").isJsonPrimitive) return emptyMap()
        val type = pet.get("type").asString
        if (!petnums.has(type) || !petnums.get(type).isJsonObject) return emptyMap()
        val byTier = petnums.getAsJsonObject(type)
        val tier = if (pet.has("tier") && pet.get("tier").isJsonPrimitive) pet.get("tier").asString.uppercase() else "COMMON"
        if (!byTier.has(tier) || !byTier.get(tier).isJsonObject) return emptyMap()
        val tierObj = byTier.getAsJsonObject(tier)
        val level = maxOf(1, PetWorth.levelInfo(pet).level)

        val keys = tierObj.entrySet().mapNotNull { (rawKey, value) ->
            val key = tryParseInt(rawKey)
            val node = if (value.isJsonObject) value.asJsonObject else null
            if (key != null && node != null && node.has("statNums") && node.get("statNums").isJsonObject) key else null
        }.sorted()
        if (keys.isEmpty()) return emptyMap()
        var lo = keys.first()
        var hi = keys.last()
        for (key in keys) {
            if (key <= level) lo = key
            if (key >= level) {
                hi = key
                break
            }
        }
        val lowStats = readPetStatNums(tierObj.getAsJsonObject(lo.toString()))
        if (lo == hi || level <= lo) return lowStats
        if (level >= hi) {
            val highStats = readPetStatNums(tierObj.getAsJsonObject(hi.toString()))
            if (level <= hi) return highStats
            // Extrapolate past the last key using the gain from first→last (GDRAG 101-200).
            val first = keys.first()
            val last = keys.last()
            if (last <= first) return highStats
            val firstStats = readPetStatNums(tierObj.getAsJsonObject(first.toString()))
            val t = (level - last) / (last - first).toDouble()
            return highStats.mapValues { (id, v) -> v + (v - (firstStats[id] ?: 0.0)) * t }
        }
        val highStats = readPetStatNums(tierObj.getAsJsonObject(hi.toString()))
        val t = (level - lo) / (hi - lo).toDouble()
        return (lowStats.keys + highStats.keys).associateWith { id ->
            val a = lowStats[id] ?: 0.0
            val b = highStats[id] ?: 0.0
            a + (b - a) * t
        }
    }

    private fun readPetStatNums(node: JsonObject?): Map<String, Double> {
        if (node == null || !node.has("statNums") || !node.get("statNums").isJsonObject) return emptyMap()
        val out = HashMap<String, Double>()
        for ((rawKey, value) in node.getAsJsonObject("statNums").entrySet()) {
            if (!value.isJsonPrimitive) continue
            val key = normalizeStatKey(rawKey)
            val n = Leveling.numDouble(value)
            if (wanted(key) && n != null) out[key] = n
        }
        return out
    }

    private fun addItemLore(totals: MutableMap<String, Double>, stacks: List<InventoryDecoder.Stack?>?, uniqueById: Boolean): Boolean {
        if (stacks.isNullOrEmpty()) return false
        val seen = if (uniqueById) HashSet<String>() else null
        var added = false
        for (stack in stacks) {
            if (stack == null || stack.id.isBlank()) continue
            if (seen != null && !seen.add(stack.id)) continue
            val lore = stack.lore
            if (lore.isEmpty()) continue
            for (line in lore) {
                val clean = stripFormatting(line)
                if (clean.isBlank()) continue
                for (def in DISPLAY) {
                    val value = def.regex.find(clean)?.groupValues?.get(1)?.toDoubleOrNull() ?: continue
                    add(totals, def.id, value)
                    added = true
                }
            }
        }
        return added
    }

    /** Sum of one stat's lore lines on a single item, matched the same way as worn gear. */
    @JvmStatic
    fun loreStat(lore: List<String?>?, id: String): Double {
        if (lore.isNullOrEmpty()) return 0.0
        var total = 0.0
        for (def in DISPLAY) {
            if (def.id != id) continue
            for (line in lore) {
                total += def.regex.find(stripFormatting(line))?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            }
        }
        return total
    }

    private fun skillLevels(member: JsonObject): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (skill in SKILLS) {
            val xp = Leveling.readSkillXpDouble(member, skill)
            if (xp <= 0f) continue
            val cap = Leveling.skillCap(skill, member)
            val progress: Leveling.Progress? = Leveling.getLevel(Leveling.skillTable(skill), xp, cap, false)
            val level = if (progress == null) 0 else floor(progress.level).toInt()
            if (level > 0) out[skill] = level
        }
        return out
    }

    private fun readStatMap(obj: JsonObject): Map<String, Double> {
        val out = HashMap<String, Double>()
        for ((rawKey, value) in obj.entrySet()) {
            if (!value.isJsonPrimitive) continue
            val key = normalizeStatKey(rawKey)
            if (!wanted(key)) continue
            soft(null) { value.asDouble }?.let { out[key] = it }
        }
        return out
    }

    private fun normalizeStatKey(key: String?): String = when (val k = key?.lowercase() ?: return "") {
        "defence" -> "defense"
        "walk_speed" -> "speed"
        "crit_chance", "critchance", "criticalchance" -> "critical_chance"
        "crit_damage", "critdamage", "criticaldamage" -> "critical_damage"
        "true_defence" -> "true_defense"
        "bonus_attack_speed" -> "attack_speed"
        else -> k
    }

    /** NEU misc.base_stats uses defence / crit_chance naming. */
    private fun readBaseStat(base: JsonObject, id: String): Double? =
        baseStatKeys(id).firstNotNullOfOrNull { Leveling.numDouble(base.get(it)) }

    private fun baseStatKeys(id: String): List<String> = when (id) {
        "defense" -> listOf("defense", "defence")
        "speed" -> listOf("speed", "walk_speed")
        "critical_chance" -> listOf("critical_chance", "crit_chance")
        "critical_damage" -> listOf("critical_damage", "crit_damage")
        "true_defense" -> listOf("true_defense", "true_defence")
        "attack_speed" -> listOf("attack_speed", "bonus_attack_speed")
        else -> listOf(id)
    }

    private fun wanted(id: String): Boolean = id in DISPLAY_IDS

    private fun add(totals: MutableMap<String, Double>, id: String, value: Double) {
        totals.merge(id, value, Double::plus)
    }

    @JvmStatic
    fun stripFormatting(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == '§' && i + 1 < text.length) {
                i += 2
                continue
            }
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp in 0xE000..0xF8FF) continue
            out.appendCodePoint(cp)
        }
        return out.toString().trim()
    }

    private fun misc(): JsonObject? = miscCache ?: loadConstant("misc.json").also { miscCache = it }

    private fun bonuses(): JsonObject? = bonusesCache ?: loadConstant("bonuses.json").also { bonusesCache = it }

    private fun petnums(): JsonObject? = petnumsCache ?: loadConstant("petnums.json").also { petnumsCache = it }

    private fun attributeShards(): JsonObject? =
        attributeShardsCache ?: loadConstant("attribute_shards.json").also { attributeShardsCache = it }

    private fun loadConstant(fileName: String): JsonObject? {
        var path = ProfileViewer.DIR.resolve("neu-repo").resolve("repo").resolve("constants").resolve(fileName)
        if (!Files.isRegularFile(path)) path = ProfileViewer.DIR.resolve("neu-repo").resolve("constants").resolve(fileName)
        if (!Files.isRegularFile(path)) return null
        return try {
            Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                val el = JsonParser.parseReader(reader)
                if (el != null && el.isJsonObject) el.asJsonObject else null
            }
        } catch (exception: IOException) {
            ProfileViewer.LOGGER.debug("Failed loading NEU {}", fileName, exception)
            null
        } catch (exception: RuntimeException) {
            if (!SoftDataFailure.isSoft(exception)) throw exception
            ProfileViewer.LOGGER.debug("Failed loading NEU {}", fileName, exception)
            null
        }
    }

    /** HOTM perk levels: the selected `skill_tree` preset, else legacy `mining_core.nodes`. */
    private fun hotmPerkNodes(member: JsonObject): JsonObject? {
        val selected = SkillTreePresets.selectedNodes(obj(member.get("skill_tree")), "mining")
        if (selected != null) {
            val perks = JsonObject()
            for ((id, value) in selected.entrySet()) {
                if (id == null || id.startsWith("toggle_")) continue
                perks.add(id, value)
            }
            if (perks.entrySet().isNotEmpty()) return perks
        }
        return obj(member.get("mining_core"))?.let { obj(it.get("nodes")) }
    }

    private fun obj(element: JsonElement?): JsonObject? =
        if (element != null && element.isJsonObject) element.asJsonObject else null

    private inline fun <T> soft(fallback: T, block: () -> T): T = try {
        block()
    } catch (_: IllegalStateException) {
        fallback
    } catch (_: ClassCastException) {
        fallback
    } catch (_: NumberFormatException) {
        fallback
    } catch (_: UnsupportedOperationException) {
        fallback
    }

    private fun intOr(obj: JsonObject?, key: String?): Int {
        if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) return 0
        return soft(0) { obj.get(key).asInt }
    }

    private fun tryParseInt(text: String?): Int? = if (text.isNullOrBlank()) null else text.trim().toIntOrNull()
}
