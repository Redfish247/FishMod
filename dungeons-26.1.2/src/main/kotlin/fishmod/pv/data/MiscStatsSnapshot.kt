/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.pv.networth.InventoryDecoder
import java.util.Collections

class MiscStatsSnapshot private constructor(
    kills: List<CountEntry>?,
    killsTotal: Long,
    deaths: List<CountEntry>?,
    deathsTotal: Long,
    highestDamage: Double,
    highestCriticalDamage: Double,
    giftsGiven: Long,
    giftsReceived: Long,
    petOresMined: Long,
    petSeaCreatures: Long,
    petXpTotal: Long,
    seaCreatureKills: Long,
    firstJoinMs: Long,
    fairyCollected: Int,
    fairyExchanges: Int,
    fairyUnspent: Int,
    personalBankUpgrade: Int,
    @get:JvmName("cookieBuffActive") val cookieBuffActive: Boolean,
    soulflow: Long,
    refinedJyrreUses: Int,
    unlockedTemples: List<String>?,
    communityUpgrades: List<CommunityUpgrade>?,
    extraSections: List<Section>?,
    experimentation: ExperimentationStats?,
    dragons: DragonStats?,
    races: List<RaceGroup>?,
    mythos: MythosStats?,
    seasonal: SeasonalStats?,
) {
    @JvmRecord
    data class CountEntry(val id: String, val label: String, val count: Long)

    @JvmRecord
    data class CommunityUpgrade(val upgrade: String, val label: String, val tier: Int, val startedMs: Long)

    class Section(@get:JvmName("id") val id: String, @get:JvmName("title") val title: String, entries: List<CountEntry>?) {
        @get:JvmName("entries") val entries: List<CountEntry> = entries?.toList() ?: emptyList()
    }

    class ExperimentGame(id: String?, label: String?, claims: Long, attempts: Long, bestScore: Long) {
        @get:JvmName("id") val id: String = id ?: ""
        @get:JvmName("label") val label: String = if (label.isNullOrBlank()) this.id else label
        @get:JvmName("claims") val claims: Long = maxOf(0L, claims)
        @get:JvmName("attempts") val attempts: Long = maxOf(0L, attempts)
        @get:JvmName("bestScore") val bestScore: Long = maxOf(0L, bestScore)
    }

    class ExperimentationStats(games: List<ExperimentGame>?, serumsDrank: Long, claimsResets: Long) {
        @get:JvmName("games") val games: List<ExperimentGame> = games?.toList() ?: emptyList()
        @get:JvmName("serumsDrank") val serumsDrank: Long = maxOf(0L, serumsDrank)
        @get:JvmName("claimsResets") val claimsResets: Long = maxOf(0L, claimsResets)

        fun present(): Boolean =
            serumsDrank > 0L || claimsResets > 0L || games.any { it.claims > 0L || it.attempts > 0L || it.bestScore > 0L }

        companion object {
            @JvmStatic
            fun empty(): ExperimentationStats = ExperimentationStats(emptyList(), 0L, 0L)
        }
    }

    @JvmRecord
    data class DragonStat(
        val id: String,
        val label: String,
        val summoned: Long,
        val eyes: Long,
        val mostDamage: Double,
        val fastestKillMs: Long,
        val bestRank: Int,
    )

    class DragonStats(
        dragons: List<DragonStat>?,
        @get:JvmName("summoned") val summoned: Long,
        @get:JvmName("eyesPlaced") val eyesPlaced: Long,
        @get:JvmName("bestDamage") val bestDamage: Double,
        @get:JvmName("fastestKillMs") val fastestKillMs: Long,
        @get:JvmName("eyesCollected") val eyesCollected: Long,
        @get:JvmName("specialZealots") val specialZealots: Long,
    ) {
        @get:JvmName("dragons") val dragons: List<DragonStat> = dragons?.toList() ?: emptyList()

        fun present(): Boolean = dragons.isNotEmpty() || summoned > 0L || eyesCollected > 0L || specialZealots > 0L

        companion object {
            @JvmStatic
            fun empty(): DragonStats = DragonStats(emptyList(), 0L, 0L, 0.0, 0L, 0L, 0L)
        }
    }

    @JvmRecord
    data class RaceTime(val mode: String, val ms: Long)

    /** One race course; Dungeon Hub courses have several modes, the rest have one. */
    class RaceGroup(@get:JvmName("label") val label: String, @get:JvmName("bestMs") val bestMs: Long, modes: List<RaceTime>?) {
        @get:JvmName("modes") val modes: List<RaceTime> = modes?.toList() ?: emptyList()
    }

    class BurrowCount(
        @get:JvmName("key") val key: String,
        @get:JvmName("label") val label: String,
        @get:JvmName("total") val total: Long,
        byRarity: Map<String, Long>?,
    ) {
        @get:JvmName("byRarity")
        val byRarity: Map<String, Long> = if (byRarity == null) emptyMap() else Collections.unmodifiableMap(LinkedHashMap(byRarity))
    }

    class MythosStats(@get:JvmName("kills") val kills: Long, burrows: List<BurrowCount>?) {
        @get:JvmName("burrows") val burrows: List<BurrowCount> = burrows?.toList() ?: emptyList()

        fun present(): Boolean = kills > 0L || burrows.isNotEmpty()

        companion object {
            @JvmStatic
            fun empty(): MythosStats = MythosStats(0L, emptyList())
        }
    }

    @JvmRecord
    data class SeasonalStats(
        val snowballsHit: Long,
        val winterDamage: Long,
        val magmaDamage: Long,
        val candyTotal: Long,
        val greenCandy: Long,
        val purpleCandy: Long,
        val festivals: Int,
        val bestFestivalCandy: Long,
        val bestFestivalYear: Int,
        val batsSpawned: Long,
        val bestFestivalPoints: Long,
        val bestFestivalGreen: Long,
        val bestFestivalPurple: Long,
        val festivalHistoryCandy: Long,
    ) {
        fun festivalHistoryIncomplete(): Boolean = festivalHistoryCandy < candyTotal

        fun winterPresent(): Boolean = snowballsHit > 0L || winterDamage > 0L || magmaDamage > 0L

        fun spookyPresent(): Boolean = candyTotal > 0L || batsSpawned > 0L

        companion object {
            @JvmStatic
            fun empty(): SeasonalStats = SeasonalStats(0L, 0L, 0L, 0L, 0L, 0L, 0, 0L, 0, 0L, 0L, 0L, 0L, 0L)
        }
    }

    @get:JvmName("kills") val kills: List<CountEntry> = kills?.toList() ?: emptyList()
    @get:JvmName("killsTotal") val killsTotal: Long = maxOf(0L, killsTotal)
    @get:JvmName("deaths") val deaths: List<CountEntry> = deaths?.toList() ?: emptyList()
    @get:JvmName("deathsTotal") val deathsTotal: Long = maxOf(0L, deathsTotal)
    @get:JvmName("highestDamage") val highestDamage: Double = maxOf(0.0, highestDamage)
    @get:JvmName("highestCriticalDamage") val highestCriticalDamage: Double = maxOf(0.0, highestCriticalDamage)
    @get:JvmName("giftsGiven") val giftsGiven: Long = maxOf(0L, giftsGiven)
    @get:JvmName("giftsReceived") val giftsReceived: Long = maxOf(0L, giftsReceived)
    @get:JvmName("petOresMined") val petOresMined: Long = maxOf(0L, petOresMined)
    @get:JvmName("petSeaCreatures") val petSeaCreatures: Long = maxOf(0L, petSeaCreatures)
    @get:JvmName("petXpTotal") val petXpTotal: Long = maxOf(0L, petXpTotal)
    @get:JvmName("seaCreatureKills") val seaCreatureKills: Long = maxOf(0L, seaCreatureKills)
    @get:JvmName("firstJoinMs") val firstJoinMs: Long = maxOf(0L, firstJoinMs)
    @get:JvmName("fairyCollected") val fairyCollected: Int = maxOf(0, fairyCollected)
    @get:JvmName("fairyExchanges") val fairyExchanges: Int = maxOf(0, fairyExchanges)
    @get:JvmName("fairyUnspent") val fairyUnspent: Int = maxOf(0, fairyUnspent)
    @get:JvmName("personalBankUpgrade") val personalBankUpgrade: Int = maxOf(0, personalBankUpgrade)
    @get:JvmName("soulflow") val soulflow: Long = maxOf(0L, soulflow)
    @get:JvmName("refinedJyrreUses") val refinedJyrreUses: Int = maxOf(0, refinedJyrreUses)
    @get:JvmName("unlockedTemples") val unlockedTemples: List<String> = unlockedTemples?.toList() ?: emptyList()
    @get:JvmName("communityUpgrades") val communityUpgrades: List<CommunityUpgrade> = communityUpgrades?.toList() ?: emptyList()
    @get:JvmName("extraSections") val extraSections: List<Section> = extraSections?.toList() ?: emptyList()
    @get:JvmName("experimentation") val experimentation: ExperimentationStats = experimentation ?: ExperimentationStats.empty()
    @get:JvmName("dragons") val dragons: DragonStats = dragons ?: DragonStats.empty()
    @get:JvmName("races") val races: List<RaceGroup> = races?.toList() ?: emptyList()
    @get:JvmName("mythos") val mythos: MythosStats = mythos ?: MythosStats.empty()
    @get:JvmName("seasonal") val seasonal: SeasonalStats = seasonal ?: SeasonalStats.empty()

    fun killDeathRatio(): Double = killsTotal / maxOf(1L, deathsTotal).toDouble()

    companion object {
        private val DRAGON_ORDER = listOf("protector", "old", "wise", "unstable", "young", "strong", "superior", "holy")
        private val RARITY_ORDER = listOf("none", "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")
        private val HUB_COURSES = listOf("crystal_core", "giant_mushroom", "precursor_ruins")
        private val BEST_TIME_SUFFIX = Regex("_best_time(_\\d+)?$")
        private val BY_COUNT_THEN_LABEL = compareByDescending<CountEntry> { it.count }.thenBy { it.label.lowercase() }

        @JvmStatic
        fun empty(): MiscStatsSnapshot = MiscStatsSnapshot(
            emptyList(), 0L, emptyList(), 0L,
            0.0, 0.0, 0L, 0L, 0L, 0L, 0L, 0L,
            0L, 0, 0, 0, 0, false, 0L, 0, emptyList(),
            emptyList(), emptyList(), ExperimentationStats.empty(),
            DragonStats.empty(), emptyList(), MythosStats.empty(), SeasonalStats.empty(),
        )

        @JvmStatic
        fun from(profileRoot: JsonObject?, member: JsonObject?): MiscStatsSnapshot {
            if (member == null) return empty()
            val stats = Leveling.obj(member.get("player_stats"))
            val killsObj = Leveling.obj(stats?.get("kills"))
            val deathsObj = Leveling.obj(stats?.get("deaths"))
            val kills = countMap(killsObj, true)
            val deaths = countMap(deathsObj, true)
            val killsTotal = longOf(killsObj, "total").takeIf { it > 0L } ?: kills.sumOf { it.count }
            val deathsTotal = longOf(deathsObj, "total").takeIf { it > 0L } ?: deaths.sumOf { it.count }

            val gifts = Leveling.obj(stats?.get("gifts"))
            val pets = Leveling.obj(stats?.get("pets"))
            val milestones = Leveling.obj(pets?.get("milestone"))

            val profile = Leveling.obj(member.get("profile"))
            val fairy = Leveling.obj(member.get("fairy_soul"))
            val itemData = Leveling.obj(member.get("item_data"))
            val winter = Leveling.obj(member.get("winter_player_data"))
            val temples = Leveling.obj(member.get("temples"))

            val upgrades = parseCommunity(profileRoot)
            val extras = ArrayList<Section>()
            addSection(extras, "rift_combat", "Rift Combat", Leveling.obj(stats?.get("rift")))

            return MiscStatsSnapshot(
                kills,
                killsTotal,
                deaths,
                deathsTotal,
                doubleOf(stats, "highest_damage"),
                doubleOf(stats, "highest_critical_damage"),
                longOf(gifts, "total_given"),
                longOf(gifts, "total_received"),
                longOf(milestones, "ores_mined"),
                longOf(milestones, "sea_creatures_killed"),
                longOf(pets, "total_exp_gained"),
                longOf(stats, "sea_creature_kills"),
                longOf(profile, "first_join"),
                longOf(fairy, "total_collected").toInt(),
                longOf(fairy, "fairy_exchanges").toInt(),
                longOf(fairy, "unspent_souls").toInt(),
                longOf(profile, "personal_bank_upgrade").toInt(),
                boolOf(profile, "cookie_buff_active"),
                longOf(itemData, "soulflow"),
                longOf(winter, "refined_jyrre_uses").toInt(),
                stringList(temples?.get("unlocked_temples")),
                upgrades,
                extras,
                parseExperimentation(Leveling.obj(member.get("experimentation"))),
                parseDragons(Leveling.obj(stats?.get("end_island"))),
                parseRaces(Leveling.obj(stats?.get("races"))),
                parseMythos(Leveling.obj(stats?.get("mythos"))),
                parseSeasonal(stats),
            )
        }

        @JvmStatic
        fun parseSeasonal(stats: JsonObject?): SeasonalStats {
            if (stats == null) return SeasonalStats.empty()
            val winter = Leveling.obj(stats.get("winter"))
            val candy = Leveling.obj(stats.get("candy_collected"))
            val spooky = Leveling.obj(stats.get("spooky"))
            val bats = Leveling.obj(spooky?.get("bats_spawned"))
            var festivals = 0
            var bestCandy = 0L
            var bestYear = 0
            var bestPoints = 0L
            var bestGreen = 0L
            var bestPurple = 0L
            var historyCandy = 0L
            if (candy != null) {
                for ((key, value) in candy.entrySet()) {
                    if (!key.startsWith("spooky_festival_")) continue
                    val year = Leveling.obj(value)
                    val total = longOf(year, "total")
                    if (total <= 0L) continue
                    festivals++
                    historyCandy += total
                    val green = longOf(year, "green_candy")
                    val purple = longOf(year, "purple_candy")
                    val points = green + purple * 5L
                    if (points > bestPoints) {
                        bestPoints = points
                        bestGreen = green
                        bestPurple = purple
                        bestCandy = total
                        bestYear = key.substring("spooky_festival_".length).toIntOrNull() ?: 0
                    }
                }
            }
            return SeasonalStats(
                longOf(winter, "most_snowballs_hit"),
                longOf(winter, "most_damage_dealt"),
                longOf(winter, "most_magma_damage_dealt"),
                longOf(candy, "total"),
                longOf(candy, "green_candy"),
                longOf(candy, "purple_candy"),
                festivals,
                bestCandy,
                bestYear,
                longOf(bats, "total"),
                bestPoints,
                bestGreen,
                bestPurple,
                historyCandy,
            )
        }

        private fun parseDragons(endIsland: JsonObject?): DragonStats {
            if (endIsland == null) return DragonStats.empty()
            val fight = Leveling.obj(endIsland.get("dragon_fight"))
            val damage = Leveling.obj(fight?.get("most_damage"))
            val fastest = Leveling.obj(fight?.get("fastest_kill"))
            val rank = Leveling.obj(fight?.get("highest_rank"))
            val summoned = Leveling.obj(fight?.get("amount_summoned"))
            val eyes = Leveling.obj(fight?.get("summoning_eyes_contributed"))

            val ids = ArrayList(DRAGON_ORDER)
            for (src in listOf(damage, fastest, rank, summoned, eyes)) {
                if (src == null) continue
                for (key in src.keySet()) {
                    val id = key.lowercase()
                    if (id != "best" && id != "total" && id !in ids) ids.add(id)
                }
            }
            val dragons = ids.map { id ->
                DragonStat(
                    id,
                    InventoryDecoder.prettyWords(id),
                    longOf(summoned, id),
                    longOf(eyes, id),
                    doubleOf(damage, id),
                    longOf(fastest, id),
                    longOf(rank, id).toInt(),
                )
            }.filter { it.summoned > 0L || it.mostDamage > 0.0 || it.fastestKillMs > 0L }
            return DragonStats(
                dragons,
                longOf(summoned, "total"),
                longOf(eyes, "total"),
                doubleOf(damage, "best"),
                longOf(fastest, "best"),
                longOf(endIsland, "summoning_eyes_collected"),
                longOf(endIsland, "special_zealot_loot_collected"),
            )
        }

        private fun parseRaces(races: JsonObject?): List<RaceGroup> {
            if (races == null) return emptyList()
            val out = ArrayList<RaceGroup>()
            val hub = LinkedHashMap<String, MutableList<RaceTime>>()
            for ((key, value) in races.entrySet()) {
                if (key == null) continue
                if (value.isJsonObject) {
                    val obj = value.asJsonObject
                    for (subKey in obj.keySet()) {
                        val ms = longOf(obj, subKey)
                        val id = subKey.replace("_best_time", "")
                        val course = HUB_COURSES.firstOrNull { id.startsWith(it + "_") }
                        if (ms <= 0L || course == null) continue
                        val mode = InventoryDecoder.prettyWords(
                            id.substring(course.length + 1)
                                .replace("_no_return", ", no return")
                                .replace("_with_return", ", with return"),
                        )
                        hub.getOrPut(course) { ArrayList() }.add(RaceTime(mode, ms))
                    }
                    continue
                }
                val ms = longOf(races, key)
                if (ms <= 0L || !key.contains("best_time")) continue
                var label = InventoryDecoder.prettyWords(key.replace(BEST_TIME_SUFFIX, ""))
                if (!label.lowercase().endsWith("race")) label = "$label Race"
                out.add(RaceGroup(label, ms, listOf(RaceTime(label, ms))))
            }
            for ((course, times) in hub) {
                val modes = times.sortedBy { it.ms }
                out.add(RaceGroup(InventoryDecoder.prettyWords(course), modes[0].ms, modes))
            }
            return out
        }

        @JvmStatic
        fun parseMythos(mythos: JsonObject?): MythosStats {
            if (mythos == null) return MythosStats.empty()
            val burrows = ArrayList<BurrowCount>()
            addBurrows(burrows, mythos, "burrows_dug_next", "Arrow")
            addBurrows(burrows, mythos, "burrows_dug_combat", "Mob")
            addBurrows(burrows, mythos, "burrows_dug_treasure", "Treasure")
            addBurrows(burrows, mythos, "burrows_chains_complete", "Chains Completed")
            return MythosStats(longOf(mythos, "kills"), burrows)
        }

        private fun addBurrows(out: MutableList<BurrowCount>, mythos: JsonObject, key: String, label: String) {
            val obj = Leveling.obj(mythos.get(key)) ?: return
            val byRarity = LinkedHashMap<String, Long>()
            for (rarity in RARITY_ORDER) {
                val n = longOf(obj, rarity)
                if (n > 0L) byRarity[if (rarity == "none") "No Griffin" else InventoryDecoder.prettyWords(rarity.lowercase())] = n
            }
            val total = longOf(obj, "total").takeIf { it > 0L } ?: byRarity.values.sum()
            if (total > 0L) out.add(BurrowCount(key, label, total, byRarity))
        }

        private fun parseExperimentation(root: JsonObject?): ExperimentationStats {
            if (root == null || root.entrySet().isEmpty()) return ExperimentationStats.empty()
            val games = ArrayList<ExperimentGame>(3)
            addExperimentGame(games, root, "pairings", "Pairings")
            addExperimentGame(games, root, "simon", "Simon")
            addExperimentGame(games, root, "numbers", "Numbers")
            return ExperimentationStats(games, longOf(root, "serums_drank"), longOf(root, "claims_resets"))
        }

        private fun addExperimentGame(out: MutableList<ExperimentGame>, root: JsonObject, key: String, label: String) {
            val game = Leveling.obj(root.get(key))
            if (game == null || game.entrySet().isEmpty()) return
            var claims = 0L
            var attempts = 0L
            var best = 0L
            for ((id, value) in game.entrySet()) {
                if (id == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) continue
                val n = maxOf(0L, value.asDouble.toLong())
                when {
                    id.startsWith("claims_") -> claims += n
                    id.startsWith("attempts_") -> attempts += n
                    id.startsWith("best_score_") -> best = maxOf(best, n)
                }
            }
            if (claims <= 0L && attempts <= 0L && best <= 0L) return
            out.add(ExperimentGame(key, label, claims, attempts, best))
        }

        private fun addSection(out: MutableList<Section>, id: String, title: String, obj: JsonObject?) {
            if (obj == null || obj.entrySet().isEmpty()) return
            val entries = flattenCounts(obj, "")
            if (entries.isEmpty()) return
            entries.sortWith(BY_COUNT_THEN_LABEL)
            out.add(Section(id, title, entries))
        }

        private fun countMap(obj: JsonObject?, skipTotal: Boolean): List<CountEntry> {
            if (obj == null) return emptyList()
            val merged = LinkedHashMap<String, CountEntry>()
            for ((key, value) in obj.entrySet()) {
                if (key == null || (skipTotal && "total".equals(key, ignoreCase = true))) continue
                if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) continue
                val n = maxOf(0L, value.asDouble.toLong())
                if (n <= 0L) continue
                val label = MobNames.pretty(key)
                val existing = merged[label]
                merged[label] = if (existing == null) CountEntry(key, label, n) else CountEntry(existing.id + "+" + key, label, existing.count + n)
            }
            return merged.values.sortedWith(BY_COUNT_THEN_LABEL)
        }

        private fun flattenCounts(obj: JsonObject?, prefix: String): MutableList<CountEntry> {
            val out = ArrayList<CountEntry>()
            if (obj == null) return out
            for ((key, value) in obj.entrySet()) {
                if (key == null) continue
                val path = if (prefix.isEmpty()) key else prefix + "_" + key
                if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                    val n = maxOf(0L, value.asDouble.toLong())
                    if (n > 0L) out.add(CountEntry(path, InventoryDecoder.prettyWords(path), n))
                } else if (value.isJsonObject) {
                    out.addAll(flattenCounts(value.asJsonObject, path))
                }
            }
            return out
        }

        private fun parseCommunity(profileRoot: JsonObject?): List<CommunityUpgrade> {
            val root = Leveling.obj(profileRoot?.get("community_upgrades"))
            if (root == null || !root.has("upgrade_states") || !root.get("upgrade_states").isJsonArray) return emptyList()
            val byId = LinkedHashMap<String, CommunityUpgrade>()
            for (el in root.getAsJsonArray("upgrade_states")) {
                if (!el.isJsonObject) continue
                val u = el.asJsonObject
                val upgrade = str(u, "upgrade")
                if (upgrade.isBlank()) continue
                val next = CommunityUpgrade(upgrade, InventoryDecoder.prettyWords(upgrade), longOf(u, "tier").toInt(), longOf(u, "started_ms"))
                val key = upgrade.lowercase()
                val prev = byId[key]
                if (prev == null || next.tier >= prev.tier) byId[key] = next
            }
            return byId.values.sortedWith(compareBy<CommunityUpgrade> { it.upgrade.lowercase() }.thenBy { it.tier })
        }

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

        private fun primitive(obj: JsonObject?, key: String?): JsonElement? {
            if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) return null
            return obj.get(key)
        }

        private fun longOf(obj: JsonObject?, key: String?): Long {
            val el = primitive(obj, key) ?: return 0L
            return soft(0L) { maxOf(0L, el.asDouble.toLong()) }
        }

        private fun doubleOf(obj: JsonObject?, key: String?): Double {
            val el = primitive(obj, key) ?: return 0.0
            return soft(0.0) { maxOf(0.0, el.asDouble) }
        }

        private fun boolOf(obj: JsonObject?, key: String?): Boolean {
            val el = primitive(obj, key) ?: return false
            return soft(false) { el.asBoolean }
        }

        private fun str(obj: JsonObject?, key: String?): String {
            val el = primitive(obj, key) ?: return ""
            return soft("") { el.asString ?: "" }
        }

        private fun stringList(el: JsonElement?): List<String> {
            if (el == null || !el.isJsonArray) return emptyList()
            val out = ArrayList<String>()
            for (item in el.asJsonArray) {
                if (item == null || !item.isJsonPrimitive) continue
                val s: String? = soft(null) { item.asString }
                if (!s.isNullOrBlank()) out.add(InventoryDecoder.prettyWords(s))
            }
            return out
        }
    }
}
