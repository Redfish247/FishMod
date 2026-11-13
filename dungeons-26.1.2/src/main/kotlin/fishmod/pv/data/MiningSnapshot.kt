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
import fishmod.pv.dungeons.EssenceShopData
import kotlin.math.floor

class MiningSnapshot private constructor(
    @get:JvmName("hotmLevel") val hotmLevel: Int,
    @get:JvmName("hotmFill") val hotmFill: Float,
    @get:JvmName("hotmMaxed") val hotmMaxed: Boolean,
    @get:JvmName("hotmHover") val hotmHover: String,
    @get:JvmName("hotmXp") val hotmXp: Double,
    @get:JvmName("miningLevel") val miningLevel: Int,
    miningFill: Float,
    @get:JvmName("miningMaxed") val miningMaxed: Boolean,
    miningHover: String?,
    @get:JvmName("mithril") val mithril: Powder,
    @get:JvmName("gemstone") val gemstone: Powder,
    @get:JvmName("glacite") val glacite: Powder,
    @get:JvmName("tokensSpent") val tokensSpent: Int,
    @get:JvmName("selectedAbilityId") val selectedAbilityId: String,
    @get:JvmName("selectedAbilityName") val selectedAbilityName: String,
    @get:JvmName("skyMallEffect") val skyMallEffect: String,
    @get:JvmName("nodes") val nodes: Map<String, Int>,
    @get:JvmName("crystals") val crystals: List<Crystal>,
    @get:JvmName("forge") val forge: List<ForgeProcess>,
    @get:JvmName("fossilsDonated") val fossilsDonated: List<String>,
    @get:JvmName("fossilDust") val fossilDust: Long,
    @get:JvmName("mineshaftsEntered") val mineshaftsEntered: Long,
    @get:JvmName("corpses") val corpses: CorpseCounts,
    @get:JvmName("corpseMilestone") val corpseMilestone: Int,
    @get:JvmName("commissionMilestone") val commissionMilestone: Int,
    coleWeight: ColeWeight.Result?,
    goldShop: DungeonSnapshot.EssenceShop?,
    diamondShop: DungeonSnapshot.EssenceShop?,
    fossilShop: DungeonSnapshot.EssenceShop?,
    goblinKingQuests: Int,
    @get:JvmName("jungleTempleOpen") val jungleTempleOpen: Boolean,
    @get:JvmName("precursorTalked") val precursorTalked: Boolean,
    miningFiestaOres: Long,
    apiNucleusRuns: Long,
) {
    @JvmRecord
    data class Powder(val available: Long, val spent: Long) {
        fun total(): Long = available + spent

        companion object {
            @JvmStatic
            fun zero(): Powder = Powder(0L, 0L)
        }
    }

    @JvmRecord
    data class Crystal(val id: String, val name: String, val state: String, val totalPlaced: Long) {
        fun placed(): Boolean = "PLACED".equals(state, ignoreCase = true)

        fun found(): Boolean = placed() || "FOUND".equals(state, ignoreCase = true)
    }

    @JvmRecord
    data class ForgeProcess(val id: String, val name: String, val slot: Int, val startTimeMs: Long)

    @JvmRecord
    data class CorpseCounts(val lapis: Long, val umber: Long, val tungsten: Long, val vanguard: Long) {
        fun total(): Long = lapis + umber + tungsten + vanguard

        companion object {
            @JvmStatic
            fun zero(): CorpseCounts = CorpseCounts(0L, 0L, 0L, 0L)
        }
    }

    @JvmRecord
    data class CorpseMilestone(val tier: Int, val needLapis: Long, val needUmber: Long, val needTungsten: Long, val needVanguard: Long) {
        fun met(counts: CorpseCounts): Boolean =
            counts.lapis >= needLapis && counts.umber >= needUmber && counts.tungsten >= needTungsten && counts.vanguard >= needVanguard

        fun fill(counts: CorpseCounts, previous: CorpseMilestone?): Float {
            val need = longArrayOf(
                needLapis - (previous?.needLapis ?: 0L),
                needUmber - (previous?.needUmber ?: 0L),
                needTungsten - (previous?.needTungsten ?: 0L),
                needVanguard - (previous?.needVanguard ?: 0L),
            )
            val have = longArrayOf(
                counts.lapis - (previous?.needLapis ?: 0L),
                counts.umber - (previous?.needUmber ?: 0L),
                counts.tungsten - (previous?.needTungsten ?: 0L),
                counts.vanguard - (previous?.needVanguard ?: 0L),
            )
            var worst = 1f
            var any = false
            for (i in need.indices) {
                if (need[i] <= 0L) continue
                any = true
                worst = minOf(worst, (have[i] / need[i].toFloat()).coerceIn(0f, 1f))
            }
            return if (any) worst else if (met(counts)) 1f else 0f
        }
    }

    @get:JvmName("miningFill") val miningFill: Float = miningFill.coerceIn(0f, 1f)
    @get:JvmName("miningHover") val miningHover: String = miningHover ?: ""
    @get:JvmName("coleWeight") val coleWeight: ColeWeight.Result = coleWeight ?: ColeWeight.Result.empty()
    @get:JvmName("goldShop") val goldShop: DungeonSnapshot.EssenceShop = goldShop ?: DungeonSnapshot.EssenceShop.empty("gold", "Gold")
    @get:JvmName("diamondShop") val diamondShop: DungeonSnapshot.EssenceShop =
        diamondShop ?: DungeonSnapshot.EssenceShop.empty("diamond", "Diamond")
    @get:JvmName("fossilShop") val fossilShop: DungeonSnapshot.EssenceShop = fossilShop ?: DungeonSnapshot.EssenceShop.empty("fossil", "Fossil")
    @get:JvmName("goblinKingQuests") val goblinKingQuests: Int = maxOf(0, goblinKingQuests)
    @get:JvmName("miningFiestaOres") val miningFiestaOres: Long = maxOf(0L, miningFiestaOres)
    @get:JvmName("apiNucleusRuns") val apiNucleusRuns: Long = maxOf(0L, apiNucleusRuns)

    fun withColeWeight(coleWeight: ColeWeight.Result?): MiningSnapshot = MiningSnapshot(
        hotmLevel, hotmFill, hotmMaxed, hotmHover, hotmXp,
        miningLevel, miningFill, miningMaxed, miningHover,
        mithril, gemstone, glacite, tokensSpent,
        selectedAbilityId, selectedAbilityName, skyMallEffect,
        nodes, crystals, forge,
        fossilsDonated, fossilDust, mineshaftsEntered,
        corpses, corpseMilestone, commissionMilestone,
        coleWeight,
        goldShop,
        diamondShop,
        fossilShop,
        goblinKingQuests,
        jungleTempleOpen,
        precursorTalked,
        miningFiestaOres,
        apiNucleusRuns,
    )

    fun nodeLevel(layoutId: String?): Int {
        if (layoutId.isNullOrBlank()) return 0
        nodes[layoutId]?.let { return it }
        for ((key, level) in nodes) {
            if (layoutId == MiningHotmData.layoutId(key)) return level
        }
        return 0
    }

    fun forgeEndMs(process: ForgeProcess?): Long {
        if (process == null || process.startTimeMs <= 0L) return 0L
        val duration = ForgeTimes.durationMs(process.id, nodeLevel("forge_time"))
        return if (duration <= 0L) 0L else process.startTimeMs + duration
    }

    /**
     * Nucleus completions: minimum `total_placed` among the five Crystal Hollows
     * gemstone crystals (SkyCrypt-style). Jade alone can over-count when other gems lag.
     */
    fun nucleusRuns(): Long {
        var min = Long.MAX_VALUE
        var any = false
        for (c in crystals) {
            val id = c.id.lowercase()
            if (!(id.contains("jade") || id.contains("amber") || id.contains("amethyst") ||
                    id.contains("sapphire") || id.contains("topaz"))
            ) {
                continue
            }
            val placed = maxOf(0L, c.totalPlaced)
            if (placed <= 0L) continue
            any = true
            min = minOf(min, placed)
        }
        return if (any) min else 0L
    }

    fun nextCorpseMilestone(): CorpseMilestone? = CORPSE_MILESTONES.getOrNull(corpseMilestone)

    fun currentCorpseMilestoneDef(): CorpseMilestone? =
        if (corpseMilestone <= 0) null else CORPSE_MILESTONES[corpseMilestone - 1]

    companion object {
        @JvmField
        val CORPSE_MILESTONES: List<CorpseMilestone> = listOf(
            CorpseMilestone(1, 10, 0, 0, 0),
            CorpseMilestone(2, 25, 1, 1, 0),
            CorpseMilestone(3, 50, 5, 5, 0),
            CorpseMilestone(4, 100, 10, 10, 1),
            CorpseMilestone(5, 250, 25, 25, 5),
            CorpseMilestone(6, 500, 50, 50, 10),
            CorpseMilestone(7, 1000, 100, 100, 20),
        )

        @JvmStatic
        fun empty(): MiningSnapshot = MiningSnapshot(
            0, 0f, false, "", 0.0,
            0, 0f, false, "",
            Powder.zero(), Powder.zero(), Powder.zero(), 0,
            "", "", "",
            emptyMap(), emptyList(), emptyList(),
            emptyList(), 0L, 0L,
            CorpseCounts.zero(), 0, 0,
            ColeWeight.Result.empty(),
            DungeonSnapshot.EssenceShop.empty("gold", "Gold"),
            DungeonSnapshot.EssenceShop.empty("diamond", "Diamond"),
            DungeonSnapshot.EssenceShop.empty("fossil", "Fossil"),
            0, false, false, 0L, 0L,
        )

        @JvmStatic
        fun fromMember(member: JsonObject?): MiningSnapshot {
            if (member == null) return empty()
            RepoData.ensureLoaded()
            MiningHotmData.ensureLoaded()

            val core = Leveling.obj(member.get("mining_core"))
            val skillTree = Leveling.obj(member.get("skill_tree"))

            val treeXp = Leveling.obj(skillTree?.get("experience"))
            val xp = if (treeXp != null && treeXp.has("mining")) doubleOf(treeXp, "mining") else doubleOf(core, "experience")

            var nodes = parseSkillTreeMiningNodes(skillTree)
            if (nodes.isEmpty()) nodes = parseNodes(Leveling.obj(core?.get("nodes")))

            val hotmTable = RepoData.leveling()?.get("HOTM") as? JsonArray
            val cap = maxOf(1, RepoData.skillCap("HOTM"))
            val hotmLevel: Int
            val hotmFill: Float
            val hotmMaxed: Boolean
            val hotmHover: String
            if (xp <= 0 && nodes.isEmpty()) {
                hotmLevel = 0
                hotmFill = 0f
                hotmMaxed = false
                hotmHover = "HOTM 0"
            } else {
                val hotm = Leveling.getLevel(hotmTable, xp.toFloat(), cap, false)
                hotmLevel = floor(hotm.level).toInt()
                hotmFill = hotm.fill()
                hotmMaxed = hotm.maxed
                hotmHover = hotm.skillHoverText("HOTM")
            }

            val mithril = powder(core, "mithril")
            val gemstone = powder(core, "gemstone")
            val glacite = powder(core, "glacite")

            // Prefer skill_tree even when 0 so we do not resurrect stale mining_core.tokens_spent.
            val tokensSpent = SkillTreePresets.selectedTokensSpent(skillTree, "mining") ?: intOf(core, "tokens_spent")

            val treeAbility = Leveling.obj(skillTree?.get("selected_ability"))
            val abilityId = if (treeAbility != null && treeAbility.has("mining")) {
                stringOf(treeAbility, "mining")
            } else {
                stringOf(core, "selected_pickaxe_ability")
            }
            val abilityName = if (abilityId.isBlank()) "-" else MiningHotmData.displayName(abilityId)
            val skyMall = prettyEffect(stringOf(core, "current_daily_effect"))

            val crystals = parseCrystals(Leveling.obj(core?.get("crystals")))
            val forge = parseForge(Leveling.obj(member.get("forge")))

            val glaciteData = Leveling.obj(member.get("glacite_player_data"))
            val fossils = stringList(glaciteData?.get("fossils_donated"))
            val dust = longOf(glaciteData, "fossil_dust")
            val shafts = longOf(glaciteData, "mineshafts_entered")
            val corpses = parseCorpses(Leveling.obj(glaciteData?.get("corpses_looted")))
            val corpseMs = highestCorpseMilestone(corpses)
            val commissionMs = parseCommissionMilestone(member)

            val miningXp = Leveling.readSkillXpDouble(member, "mining")
            val miningCap = Leveling.skillCap("mining", member)
            val mining = Leveling.getLevel(Leveling.skillTable("mining"), miningXp, miningCap, false)

            val biomes = Leveling.obj(core?.get("biomes"))
            val goblin = Leveling.obj(biomes?.get("goblin"))
            val jungle = Leveling.obj(biomes?.get("jungle"))
            val precursor = Leveling.obj(biomes?.get("precursor"))
            val leveling = Leveling.obj(member.get("leveling"))
            val completions = Leveling.obj(leveling?.get("completions"))

            return MiningSnapshot(
                hotmLevel,
                hotmFill,
                hotmMaxed,
                hotmHover,
                xp,
                floor(mining.level).toInt(),
                mining.fill(),
                mining.maxed,
                mining.skillHoverText("Mining"),
                mithril,
                gemstone,
                glacite,
                tokensSpent,
                abilityId,
                abilityName,
                skyMall,
                nodes,
                crystals,
                forge,
                fossils,
                dust,
                shafts,
                corpses,
                corpseMs,
                commissionMs,
                ColeWeight.Result.empty(),
                EssenceShopData.gold(member),
                EssenceShopData.diamond(member),
                EssenceShopData.fossil(member),
                intOf(goblin, "king_quests_completed"),
                boolOf(jungle, "jungle_temple_open"),
                boolOf(precursor, "talked_to_professor"),
                longOf(leveling, "mining_fiesta_ores_mined"),
                longOf(completions, "NUCLEUS_RUNS"),
            )
        }

        private fun highestCorpseMilestone(counts: CorpseCounts): Int {
            var best = 0
            for (m in CORPSE_MILESTONES) {
                if (!m.met(counts)) break
                best = m.tier
            }
            return best
        }

        private fun powder(core: JsonObject?, kind: String): Powder {
            val lifetime = maxOf(0L, longOf(core, "powder_$kind"))
            val spent = maxOf(0L, longOf(core, "powder_spent_$kind"))
            return Powder(maxOf(0L, lifetime - spent), spent)
        }

        private fun parseSkillTreeMiningNodes(skillTree: JsonObject?): Map<String, Int> =
            parseNodes(SkillTreePresets.selectedNodes(skillTree, "mining"))

        private fun parseNodes(nodes: JsonObject?): Map<String, Int> {
            val out = LinkedHashMap<String, Int>()
            mergeNodes(out, nodes)
            return out.toMap()
        }

        private fun mergeNodes(out: MutableMap<String, Int>, nodes: JsonObject?) {
            if (nodes == null) return
            for ((id, v) in nodes.entrySet()) {
                if (id == null || id.startsWith("toggle_") || v == null) continue
                val level = if (v.isJsonPrimitive && v.asJsonPrimitive.isBoolean) {
                    if (v.asBoolean) 1 else 0
                } else {
                    Leveling.num(v)?.let { Math.round(it) } ?: 0
                }
                if (level <= 0) continue
                val layout = MiningHotmData.layoutId(id)
                if (level > (out[layout] ?: 0)) out[layout] = level
            }
        }

        private fun parseCrystals(crystals: JsonObject?): List<Crystal> {
            if (crystals == null) return emptyList()
            val out = ArrayList<Crystal>()
            for ((id, value) in crystals.entrySet()) {
                val o = Leveling.obj(value)
                val state = (if (o == null) "NOT_FOUND" else stringOf(o, "state")).ifBlank { "NOT_FOUND" }
                out.add(Crystal(id, titleCrystal(id), state.uppercase(), longOf(o, "total_placed")))
            }
            out.sortBy { it.name }
            return out.toList()
        }

        private fun parseForge(forge: JsonObject?): List<ForgeProcess> {
            if (forge == null) return emptyList()
            val processes = Leveling.obj(forge.get("forge_processes"))
            val forge1 = processes?.let { Leveling.obj(it.get("forge_1")) } ?: return emptyList()
            val out = ArrayList<ForgeProcess>()
            for ((key, value) in forge1.entrySet()) {
                val o = Leveling.obj(value) ?: continue
                val id = stringOf(o, "id").ifBlank { key }
                out.add(ForgeProcess(id, titleId(id), intOf(o, "slot"), longOf(o, "startTime")))
            }
            out.sortBy { it.slot }
            return out.toList()
        }

        private fun parseCorpses(corpses: JsonObject?): CorpseCounts {
            if (corpses == null) return CorpseCounts.zero()
            return CorpseCounts(
                longOf(corpses, "lapis"),
                longOf(corpses, "umber"),
                longOf(corpses, "tungsten"),
                longOf(corpses, "vanguard"),
            )
        }

        private fun parseCommissionMilestone(member: JsonObject): Int {
            val objectives = Leveling.obj(member.get("objectives"))
            val tutorial = objectives?.get("tutorial")
            if (tutorial == null || !tutorial.isJsonArray) return 0
            var best = 0
            val prefix = "commission_milestone_reward_mining_xp_tier_"
            for (el in tutorial.asJsonArray) {
                if (!el.isJsonPrimitive) continue
                val key = softString(el) ?: continue
                if (!key.startsWith(prefix)) continue
                key.substring(prefix.length).toIntOrNull()?.let { best = maxOf(best, it) }
            }
            return best
        }

        private fun stringList(el: JsonElement?): List<String> {
            if (el == null || !el.isJsonArray) return emptyList()
            return el.asJsonArray.filter { it != null && it.isJsonPrimitive }.mapNotNull { softString(it) }
        }

        private fun softString(el: JsonElement): String? = try {
            el.asString
        } catch (_: IllegalStateException) {
            null
        } catch (_: ClassCastException) {
            null
        } catch (_: NumberFormatException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }

        private fun prettyEffect(raw: String?): String = if (raw.isNullOrBlank()) "-" else titleId(raw)

        private fun titleCrystal(id: String?): String = titleId((id ?: "").removeSuffix("_crystal"))

        private fun titleId(id: String?): String {
            if (id.isNullOrBlank()) return ""
            return id.replace('-', '_').split('_').filter { it.isNotEmpty() }
                .joinToString(" ") { it.first().uppercaseChar() + it.substring(1).lowercase() }
        }

        private fun longOf(obj: JsonObject?, key: String): Long {
            if (obj == null) return 0L
            val n = Leveling.numDouble(obj.get(key))
            return if (n == null || n.isNaN()) 0L else Math.round(n)
        }

        private fun doubleOf(obj: JsonObject?, key: String): Double {
            if (obj == null) return 0.0
            return Leveling.numDouble(obj.get(key)) ?: 0.0
        }

        private fun intOf(obj: JsonObject?, key: String): Int = longOf(obj, key).toInt()

        private fun stringOf(obj: JsonObject?, key: String): String {
            val el = obj?.get(key)
            if (el == null || !el.isJsonPrimitive) return ""
            return softString(el) ?: ""
        }

        private fun boolOf(obj: JsonObject?, key: String?): Boolean {
            if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull) return false
            val el = obj.get(key)
            if (!el.isJsonPrimitive) return false
            return try {
                val p = el.asJsonPrimitive
                when {
                    p.isBoolean -> el.asBoolean
                    p.isNumber -> el.asDouble != 0.0
                    else -> {
                        val s = el.asString
                        "true".equals(s, ignoreCase = true) || s == "1" || "yes".equals(s, ignoreCase = true)
                    }
                }
            } catch (_: IllegalStateException) {
                false
            } catch (_: ClassCastException) {
                false
            } catch (_: NumberFormatException) {
                false
            } catch (_: UnsupportedOperationException) {
                false
            }
        }
    }
}
