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
import fishmod.pv.dungeons.EssenceShopData
import fishmod.pv.networth.InventoryDecoder
import java.util.Locale

class ForagingSnapshot private constructor(
	@get:JvmName("foragingLevel") val foragingLevel: Int,
	foragingFill: Float,
	@get:JvmName("foragingMaxed") val foragingMaxed: Boolean,
	foragingHover: String?,
	@get:JvmName("huntingLevel") val huntingLevel: Int,
	huntingFill: Float,
	@get:JvmName("huntingMaxed") val huntingMaxed: Boolean,
	huntingHover: String?,
	raceBestMs: Long,
	collections: List<CollectionRow>?,
	attributes: List<AttrStack>?,
	treeGifts: List<TreeGift>?,
	starlynBests: List<StarlynBest>?,
	whisperPools: List<WhisperPool>?,
	fishFamily: List<String>?,
	hinaTier: Int,
	hinaProgress: Map<String, Long>?,
	hinaCompleted: List<String>?,
	hinaClaimed: List<String>?,
	@get:JvmName("harpTalisman") val harpTalisman: Boolean,
	harpSelected: String?,
	harpSongs: List<HarpSong>?,
	@get:JvmName("galateaBeacon") val galateaBeacon: Int,
	hotfXp: Double,
	forestTokensSpent: Int,
	hotfLastResetMs: Long,
	dailyEffect: String?,
	@get:JvmName("dailyEffectChanged") val dailyEffectChanged: Int,
	dailyTreesCut: Long,
	dailyTreesDay: Long,
	dailyGifts: Long,
	dailyLogs: List<String>?,
	dailyLogsDay: Long,
	hotfNodes: List<HotfNode>?,
	centerPages: Map<String, Int>?,
	selectedAbility: String?,
	@get:JvmName("refundAbilityFree") val refundAbilityFree: Boolean,
	fusedShards: Long,
	ownedShards: List<OwnedShard>?,
	huntStats: Map<String, Long>?,
	@get:JvmName("toolkitUnlocked") val toolkitUnlocked: Boolean,
	toolkitSlots: List<ToolkitSlot>?,
	attributeShards: List<AttributeShardRow>?,
	forestShop: DungeonSnapshot.EssenceShop?,
	safari: SafariInfo?,
	uniqueShards: Long,
	safariShop: DungeonSnapshot.EssenceShop?,
	peltCount: Long,
	honey: HoneyInfo?
) {
	@JvmRecord
	data class CollectionRow(val id: String, val name: String, val amount: Long)

	@JvmRecord
	data class AttrStack(val id: String, val name: String, val level: Int)

	@JvmRecord
	data class TreeGift(val id: String, val name: String, val count: Long, val milestoneTier: Int)

	@JvmRecord
	data class StarlynBest(val id: String, val name: String, val amount: Long)

	@JvmRecord
	data class HarpSong(val id: String, val name: String, val best: Double, val completions: Int, val perfects: Int)

	@JvmRecord
	data class HotfNode(val id: String, val name: String, val level: Int, val maxLevel: Int, val enabled: Boolean, val ability: Boolean)

	@JvmRecord
	data class OwnedShard(val type: String, val name: String, val amount: Long, val capturedMs: Long)

	@JvmRecord
	data class ToolkitSlot(val group: String, val index: Int, val inUse: Boolean, val item: InventorySnapshot.Slot?)

	@JvmRecord
	data class AttributeShardRow(
		val stackId: String, val name: String, val iconId: String, val rarity: String, val priceId: String,
		val level: Int, val maxLevel: Int, val shardsOwned: Int, val shardsForMax: Int, val unlocked: Boolean
	) {
		companion object {
			@JvmStatic
			fun byRarity(rows: List<AttributeShardRow>): List<AttributeShardRow> = java.util.List.copyOf(
				rows.sortedWith(
					compareByDescending<AttributeShardRow> { AttributeShardsData.rarityRank(it.rarity) }
						.thenComparing({ it.name }, String.CASE_INSENSITIVE_ORDER)
				)
			)
		}
	}

	@JvmRecord
	data class SafariInfo(
		val discoveredCritters: List<String>,
		val biomeCaptures: Map<String, Long>,
		val tickets: Map<String, Long>,
		val milestoneTiers: Map<String, Int>,
		val sparklingDiscovered: List<String>,
		val sparklingCaptured: Long
	) {
		fun present(): Boolean =
			discoveredCritters.isNotEmpty() || biomeCaptures.isNotEmpty() || tickets.isNotEmpty() || milestoneTiers.isNotEmpty()

		fun totalCaptures(): Long = biomeCaptures.values.sumOf { maxOf(0L, it) }

		fun totalTickets(): Long = tickets.values.sumOf { maxOf(0L, it) }

		companion object {
			@JvmStatic
			fun empty(): SafariInfo = SafariInfo(listOf(), mapOf(), mapOf(), mapOf(), listOf(), 0L)
		}
	}

	@JvmRecord
	data class HoneyInfo(val smearedTrees: Int, val refills: Int, val treeNames: List<String>) {
		fun present(): Boolean = smearedTrees > 0 || refills > 0

		companion object {
			@JvmStatic
			fun empty(): HoneyInfo = HoneyInfo(0, 0, listOf())
		}
	}

	@JvmRecord
	data class WhisperPool(
		val id: String,
		val label: String,
		val total: Long,
		val spent: Long,
		val loadout: Int,
		val spentByLoadout: Map<Int, Long>
	) {
		fun balance(): Long = maxOf(0L, total - spent)

		fun present(): Boolean = total > 0L || spent > 0L || spentByLoadout.isNotEmpty()

		companion object {
			internal fun of(id: String, label: String?, total: Long, spent: Long, loadout: Int, spentByLoadout: Map<Int, Long>): WhisperPool =
				WhisperPool(
					id, if (label.isNullOrBlank()) prettyWhisperLabel(id) else label, maxOf(0L, total), maxOf(0L, spent),
					maxOf(1, loadout), java.util.Map.copyOf(spentByLoadout)
				)

			internal fun prettyWhisperLabel(id: String?): String {
				if (id.isNullOrBlank()) {
					return "Whispers"
				}
				return when (id.lowercase(Locale.ROOT)) {
					"forest" -> "Forest"
					"desert" -> "Desert"
					else -> InventoryDecoder.prettyWords(id)
				}
			}
		}
	}

	@get:JvmName("foragingFill") val foragingFill: Float = foragingFill.coerceIn(0f, 1f)
	@get:JvmName("foragingHover") val foragingHover: String = foragingHover ?: ""
	@get:JvmName("huntingFill") val huntingFill: Float = huntingFill.coerceIn(0f, 1f)
	@get:JvmName("huntingHover") val huntingHover: String = huntingHover ?: ""
	@get:JvmName("raceBestMs") val raceBestMs: Long = maxOf(0L, raceBestMs)
	@get:JvmName("collections") val collections: List<CollectionRow> = copyList(collections)
	@get:JvmName("attributes") val attributes: List<AttrStack> = copyList(attributes)
	@get:JvmName("treeGifts") val treeGifts: List<TreeGift> = copyList(treeGifts)
	@get:JvmName("starlynBests") val starlynBests: List<StarlynBest> = copyList(starlynBests)
	@get:JvmName("whisperPools") val whisperPools: List<WhisperPool> = copyList(whisperPools)
	@get:JvmName("fishFamily") val fishFamily: List<String> = copyList(fishFamily)
	@get:JvmName("hinaTier") val hinaTier: Int = maxOf(0, hinaTier)
	@get:JvmName("hinaProgress") val hinaProgress: Map<String, Long> = java.util.Map.copyOf(hinaProgress ?: mapOf())
	@get:JvmName("hinaCompleted") val hinaCompleted: List<String> = copyList(hinaCompleted)
	@get:JvmName("hinaClaimed") val hinaClaimed: List<String> = copyList(hinaClaimed)
	@get:JvmName("harpSelected") val harpSelected: String = harpSelected ?: ""
	@get:JvmName("harpSongs") val harpSongs: List<HarpSong> = copyList(harpSongs)
	@get:JvmName("hotfXp") val hotfXp: Double = maxOf(0.0, hotfXp)
	@get:JvmName("forestTokensSpent") val forestTokensSpent: Int = maxOf(0, forestTokensSpent)
	@get:JvmName("hotfLastResetMs") val hotfLastResetMs: Long = maxOf(0L, hotfLastResetMs)
	@get:JvmName("dailyEffect") val dailyEffect: String = dailyEffect ?: ""
	@get:JvmName("dailyTreesCut") val dailyTreesCut: Long = maxOf(0L, dailyTreesCut)
	@get:JvmName("dailyTreesDay") val dailyTreesDay: Long = maxOf(0L, dailyTreesDay)
	@get:JvmName("dailyGifts") val dailyGifts: Long = maxOf(0L, dailyGifts)
	@get:JvmName("dailyLogs") val dailyLogs: List<String> = copyList(dailyLogs)
	@get:JvmName("dailyLogsDay") val dailyLogsDay: Long = maxOf(0L, dailyLogsDay)
	@get:JvmName("hotfNodes") val hotfNodes: List<HotfNode> = copyList(hotfNodes)
	@get:JvmName("centerPages") val centerPages: Map<String, Int> = java.util.Map.copyOf(centerPages ?: mapOf())
	@get:JvmName("selectedAbility") val selectedAbility: String = selectedAbility ?: ""
	@get:JvmName("fusedShards") val fusedShards: Long = maxOf(0L, fusedShards)
	@get:JvmName("ownedShards") val ownedShards: List<OwnedShard> = copyList(ownedShards)
	@get:JvmName("huntStats") val huntStats: Map<String, Long> = java.util.Map.copyOf(huntStats ?: mapOf())
	@get:JvmName("toolkitSlots") val toolkitSlots: List<ToolkitSlot> = copyList(toolkitSlots)
	@get:JvmName("attributeShards") val attributeShards: List<AttributeShardRow> = copyList(attributeShards)
	@get:JvmName("forestShop") val forestShop: DungeonSnapshot.EssenceShop = forestShop ?: DungeonSnapshot.EssenceShop.empty("forest", "Forest")
	@get:JvmName("safari") val safari: SafariInfo = safari ?: SafariInfo.empty()
	@get:JvmName("uniqueShards") val uniqueShards: Long = maxOf(0L, uniqueShards)
	@get:JvmName("safariShop") val safariShop: DungeonSnapshot.EssenceShop = safariShop ?: DungeonSnapshot.EssenceShop.empty("safari", "Safari")
	@get:JvmName("peltCount") val peltCount: Long = maxOf(0L, peltCount)
	@get:JvmName("honey") val honey: HoneyInfo = honey ?: HoneyInfo.empty()

	fun whispers(): Long = whisperBalance("forest")

	fun whispersSpent(): Long = whisperSpent("forest")

	fun whisperBalance(id: String?): Long = whisperPool(id)?.balance() ?: 0L

	fun whisperSpent(id: String?): Long = whisperPool(id)?.spent ?: 0L

	fun whisperPool(id: String?): WhisperPool? {
		if (id.isNullOrBlank()) {
			return null
		}
		return whisperPools.firstOrNull { id.equals(it.id, ignoreCase = true) }
	}

	fun hasGalateaBeacon(): Boolean = galateaBeacon >= 0

	/** Trees cut in the current daily period only; 0 when {@link #dailyTreesDay()} is stale. */
	fun currentDailyTreesCut(): Long = if (dailyTreesDay > 0L && !isCurrentDailyDay(dailyTreesDay)) 0L else dailyTreesCut

	/**
	 * Tree gifts for the current daily period.
	 * {@code daily_gifts} has no own day field; it shares the foraging daily with
	 * {@code daily_trees_cut_day}. When that day is present and stale, gifts are 0.
	 */
	fun currentDailyGifts(): Long = if (dailyTreesDay > 0L && !isCurrentDailyDay(dailyTreesDay)) 0L else dailyGifts

	/** Log types cut in the current daily period; empty when {@link #dailyLogsDay()} is stale. */
	fun currentDailyLogs(): List<String> = if (dailyLogsDay > 0L && !isCurrentDailyDay(dailyLogsDay)) listOf() else dailyLogs

	fun unplacedHotfNodes(): List<HotfNode> = hotfNodes.filter { ForagingHotfData.perk(it.id) == null }

	fun safariEssence(): Long = safariShop.balance

	fun hotfNodeLevel(id: String?): Int {
		if (id.isNullOrBlank()) {
			return 0
		}
		return hotfNodes.firstOrNull { id == it.id }?.level ?: 0
	}

	fun hotfNodeEnabled(id: String?): Boolean {
		if (id.isNullOrBlank()) {
			return true
		}
		return hotfNodes.firstOrNull { id == it.id }?.enabled ?: true
	}

	companion object {
		private val COLLECTION_PRIORITY = arrayOf(
			"FIG_LOG", "MANGROVE_LOG", "LOG", "LOG:1", "LOG:2", "LOG:3", "LOG_2", "LOG_2:1",
			"TENDER_WOOD", "CADUCOUS_STEM", "VINESAP"
		)
		private val ATTR_KEYS = arrayOf(
			"foraging_wisdom", "fig_collector", "fig_sharpening", "mangrove_collector", "mangrove_sharpening",
			"forest_elemental", "forest_essence", "forest_fishing", "forest_strength", "forest_trap",
			"forest_speed", "berry_mogul", "berry_enjoyer", "spirit_axe"
		)
		private val HUNT_ORDER = arrayOf(
			"shard_fishing_hunts", "shard_combat_hunts", "shard_salt_hunts",
			"shard_forest_hunts", "shard_trap_hunts"
		)
		private val BEACON_KEYS = arrayOf(
			"beacon_signal_strength", "moonglade_beacon", "moonglade_beacon_level",
			"galatea_beacon", "galatea_beacon_level", "signal_strength", "beacon_level"
		)

		private fun <T : Any> copyList(list: List<T>?): List<T> = java.util.List.copyOf(list ?: listOf())

		@JvmStatic
		fun empty(): ForagingSnapshot = ForagingSnapshot(
			0, 0f, false, "", 0, 0f, false, "",
			0L, listOf(), listOf(),
			listOf(), listOf(), listOf(),
			listOf(), 0, mapOf(), listOf(), listOf(),
			false, "", listOf(), -1,
			0.0, 0, 0L, "", 0, 0L, 0L, 0L, listOf(), 0L,
			listOf(), mapOf(), "", false,
			0L, listOf(), mapOf(), false, listOf(), listOf(),
			DungeonSnapshot.EssenceShop.empty("forest", "Forest"),
			SafariInfo.empty(),
			0L,
			DungeonSnapshot.EssenceShop.empty("safari", "Safari"),
			0L,
			HoneyInfo.empty()
		)

		@JvmStatic
		fun fromMember(member: JsonObject?): ForagingSnapshot {
			if (member == null) {
				return empty()
			}
			ForagingHotfData.ensureLoaded()
			AttributeShardsData.ensureLoaded()
			RepoData.ensureLoaded()

			val foragingXp = Leveling.readSkillXpDouble(member, "foraging")
			val foraging = Leveling.getLevel(Leveling.skillTable("foraging"), foragingXp, Leveling.skillCap("foraging", member), false)

			val huntingXp = Leveling.readSkillXpDouble(member, "hunting")
			val hunting = Leveling.getLevel(Leveling.skillTable("hunting"), huntingXp, Leveling.skillCap("hunting", member), false)

			val stats = Leveling.obj(member.get("player_stats"))
			val races = Leveling.obj(stats?.get("races"))
			val raceBest = longOf(races, "foraging_race_best_time")

			val collections = parseCollections(Leveling.obj(member.get("collection")))
			val attributesRoot = Leveling.obj(member.get("attributes"))
			val stacks = Leveling.obj(attributesRoot?.get("stacks"))
			val attributes = parseAttributes(stacks)
			val attributeShards = parseAttributeShards(stacks)

			val foragingObj = Leveling.obj(member.get("foraging"))
			val core = Leveling.obj(member.get("foraging_core"))
			val skillTree = Leveling.obj(member.get("skill_tree"))

			val gifts = parseTreeGifts(Leveling.obj(foragingObj?.get("tree_gifts")))
			val starlynRoot = Leveling.obj(foragingObj?.get("starlyn"))
			val starlyn = parseStarlyn(Leveling.obj(starlynRoot?.get("personal_bests")))

			val whispers = longOf(core, "forests_whispers")
			val whispersSpent = longOf(core, "forests_whispers_spent")
			val whisperPools = parseWhisperPools(core, whispers, whispersSpent, SkillTreePresets.selectedSlot(skillTree, "foraging"))

			val fish = stringList(foragingObj?.get("fish_family"))
			val hina = Leveling.obj(foragingObj?.get("hina"))
			val hinaTasks = Leveling.obj(hina?.get("tasks"))
			val hinaTier = longOf(hinaTasks, "tier_claimed").toInt()
			val hinaProgress = longMap(Leveling.obj(hinaTasks?.get("task_progress")))
			val hinaCompleted = stringList(hinaTasks?.get("completed_tasks"))
			val hinaClaimed = stringList(hinaTasks?.get("claimed_rewards"))

			val songsRoot = Leveling.obj(foragingObj?.get("songs"))
			val harp = Leveling.obj(songsRoot?.get("harp")) ?: Leveling.obj(foragingObj?.get("harp"))
			val quests = Leveling.obj(member.get("quests"))
			val harpQuest = Leveling.obj(quests?.get("harp_quest"))
			val talisman = boolOf(harp, "claimed_talisman") ||
				boolOf(songsRoot, "claimed_talisman") ||
				boolOf(harpQuest, "claimed_talisman") ||
				objectiveComplete(member, "talk_to_melody_2") ||
				memberOwnsItem(member, "MELODY_HAIR") ||
				memberJsonContains(member, "MELODY_HAIR")
			val selectedSong = str(harp, "selected_song").ifBlank { str(harpQuest, "selected_song") }
			val harpSongs = parseHarpSongs(harp ?: harpQuest)
			val galateaBeacon = parseGalateaBeacon(member, foragingObj, core)

			val treeXp = Leveling.obj(skillTree?.get("experience"))
			val hotfXp = doubleOf(treeXp, "foraging")
			val forestTokens = SkillTreePresets.selectedTokensSpent(skillTree, "foraging") ?: 0
			val lastReset = Leveling.obj(skillTree?.get("last_reset"))
			val resetMs = longOf(lastReset, "foraging")
			val selectedAbility = Leveling.obj(skillTree?.get("selected_ability"))
			val ability = str(selectedAbility, "foraging").ifBlank { str(selectedAbility, "forest") }
			val refundFree = boolOf(skillTree, "refund_ability_free")

			val dailyEffect = str(core, "current_daily_effect")
			val dailyChanged = longOf(core, "current_daily_effect_last_changed").toInt()
			val dailyTrees = longOf(core, "daily_trees_cut")
			val dailyTreesDay = longOf(core, "daily_trees_cut_day")
			val dailyGifts = longOf(core, "daily_gifts")
			val dailyLogs = stringList(core?.get("daily_log_cut"))
			val dailyLogsDay = longOf(core, "daily_log_cut_day")

			val nodesRoot = Leveling.obj(skillTree?.get("nodes"))
			val nodes = parseHotfNodes(SkillTreePresets.selectedNodes(skillTree, "foraging"))
			val centers = LinkedHashMap<String, Int>()
			for (i in 2..5) {
				val page = Leveling.obj(nodesRoot?.get("foraging_$i"))
				val center = longOf(page, "center_of_the_forest").toInt()
				if (center > 0) {
					centers["Page $i"] = center
				}
			}

			val shards = Leveling.obj(member.get("shards"))
			val fused = longOf(shards, "fused")
			val owned = parseOwnedShards(shards?.get("owned"))
			val hunts = parseHuntStats(stats)

			val toolkit = Leveling.obj(foragingObj?.get("hunting_toolkit"))
			val toolkitUnlocked = boolOf(toolkit, "IS_UNLOCKED")
			val toolkitSlots = parseToolkit(toolkit)
			val safari = parseSafari(Leveling.obj(member.get("safari")))
			val trapper = Leveling.obj(quests?.get("trapper_quest"))
			val pelts = longOf(trapper, "pelt_count")
			val honey = parseHoney(Leveling.obj(foragingObj?.get("honey")))

			return ForagingSnapshot(
				foraging.cappedLevel(), foraging.fill(), foraging.maxed, foraging.skillHoverText("Foraging"),
				hunting.cappedLevel(), hunting.fill(), hunting.maxed, hunting.skillHoverText("Hunting"),
				raceBest, collections, attributes,
				gifts, starlyn, whisperPools,
				fish, hinaTier, hinaProgress, hinaCompleted, hinaClaimed,
				talisman, selectedSong, harpSongs, galateaBeacon,
				hotfXp, forestTokens, resetMs,
				dailyEffect, dailyChanged, dailyTrees, dailyTreesDay, dailyGifts, dailyLogs, dailyLogsDay,
				nodes, centers, ability, refundFree,
				fused, owned, hunts, toolkitUnlocked, toolkitSlots, attributeShards,
				EssenceShopData.forest(member),
				safari,
				longOf(stats, "unique_shards"),
				EssenceShopData.safari(member),
				pelts,
				honey
			)
		}

		@JvmStatic
		fun currentUtcDayId(): Long = System.currentTimeMillis() / 86_400_000L

		@JvmStatic
		fun isCurrentDailyDay(dayId: Long): Boolean = dayId > 0L && dayId == currentUtcDayId()

		@JvmStatic
		fun parseWhisperPools(core: JsonObject?, legacyBalance: Long, legacySpent: Long, loadout: Int): List<WhisperPool> {
			val root = Leveling.obj(core?.get("whispers"))
			if (root != null && root.entrySet().isNotEmpty()) {
				val ordered = LinkedHashMap<String, JsonObject>()
				for (id in listOf("forest", "desert")) {
					Leveling.obj(root.get(id))?.let { ordered[id] = it }
				}
				for ((key, value) in root.entrySet()) {
					if (key == null || ordered.containsKey(key)) {
						continue
					}
					Leveling.obj(value)?.let { ordered[key] = it }
				}
				val out = ordered.mapNotNull { (id, pool) -> parseWhisperPool(id, pool, loadout) }
				if (out.isNotEmpty()) {
					return java.util.List.copyOf(out)
				}
			}
			if (legacyBalance > 0L || legacySpent > 0L) {
				return listOf(WhisperPool.of("forest", "Forest", legacyBalance + legacySpent, legacySpent, 1, mapOf()))
			}
			return listOf()
		}

		private fun parseWhisperPool(id: String, pool: JsonObject?, loadout: Int): WhisperPool? {
			if (pool == null || pool.entrySet().isEmpty()) {
				return null
			}
			val total = firstLong(pool, "total")
			val spentField = firstLong(pool, "spent")
			val bySlot = LinkedHashMap<Int, Long>()
			for ((key, value) in pool.entrySet()) {
				if (key == null || isWhisperMetaKey(key)) {
					continue
				}
				val slot = key.toIntOrNull() ?: continue
				val slotObj = Leveling.obj(value)
				var slotSpent = if (slotObj != null) longOf(slotObj, "spent") else 0L
				if (slotSpent <= 0L && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
					slotSpent = maxOf(0L, value.asDouble.toLong())
				}
				if (slotSpent > 0L) {
					bySlot[slot] = slotSpent
				}
			}
			val spent = if (bySlot.isEmpty()) spentField else bySlot[loadout] ?: 0L
			if (total <= 0L && spent <= 0L && bySlot.isEmpty()) {
				return null
			}
			return WhisperPool.of(id, WhisperPool.prettyWhisperLabel(id), total, spent, loadout, bySlot)
		}

		private fun isWhisperMetaKey(key: String): Boolean =
			"total".equals(key, ignoreCase = true) || "current".equals(key, ignoreCase = true) ||
				"balance".equals(key, ignoreCase = true) || "spent".equals(key, ignoreCase = true)

		private fun firstLong(obj: JsonObject?, vararg keys: String): Long {
			if (obj == null) {
				return 0L
			}
			return keys.map { longOf(obj, it) }.firstOrNull { it > 0L } ?: 0L
		}

		private fun parseSafari(root: JsonObject?): SafariInfo {
			if (root == null || root.entrySet().isEmpty()) {
				return SafariInfo.empty()
			}
			val milestones = LinkedHashMap<String, Int>()
			Leveling.obj(root.get("milestone_claimed_tiers"))?.let { tiers ->
				for ((key, value) in tiers.entrySet()) {
					if (key == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
						continue
					}
					val n = maxOf(0, value.asDouble.toInt())
					if (n > 0) {
						milestones[key] = n
					}
				}
			}
			return SafariInfo(
				java.util.List.copyOf(stringList(root.get("discovered_critters"))),
				java.util.Map.copyOf(longMap(Leveling.obj(root.get("biome_captures")))),
				java.util.Map.copyOf(longMap(Leveling.obj(root.get("tickets")))),
				java.util.Map.copyOf(milestones),
				java.util.List.copyOf(stringList(root.get("discovered_sparkling_critters"))),
				maxOf(0L, longOf(root, "total_captured_sparkling_critters"))
			)
		}

		private fun parseHoney(root: JsonObject?): HoneyInfo {
			if (root == null || root.entrySet().isEmpty()) {
				return HoneyInfo.empty()
			}
			val smeared = root.get("smeared_trees")
			val refills = root.get("refill_times")
			val smearedCount = countEntries(smeared)
			val refillCount = countEntries(refills)
			val names = ArrayList<String>()
			if (smeared != null && smeared.isJsonObject) {
				for (key in smeared.asJsonObject.keySet()) {
					if (!key.isNullOrBlank()) {
						names.add(InventoryDecoder.prettyWords(key))
					}
				}
			} else if (smeared != null && smeared.isJsonArray) {
				for (el in smeared.asJsonArray) {
					if (el != null && el.isJsonPrimitive) {
						try {
							val id: String? = el.asString
							if (!id.isNullOrBlank()) {
								names.add(InventoryDecoder.prettyWords(id))
							}
						} catch (ignored: RuntimeException) {
						}
					}
				}
			}
			if (smearedCount <= 0 && refillCount <= 0) {
				return HoneyInfo.empty()
			}
			return HoneyInfo(maxOf(0, smearedCount), maxOf(0, refillCount), java.util.List.copyOf(names))
		}

		private fun countEntries(el: JsonElement?): Int = when {
			el == null || el.isJsonNull -> 0
			el.isJsonObject -> el.asJsonObject.size()
			el.isJsonArray -> el.asJsonArray.size()
			else -> 0
		}

		private fun parseCollections(collection: JsonObject?): List<CollectionRow> {
			if (collection == null) {
				return listOf()
			}
			val out = ArrayList<CollectionRow>()
			for (id in COLLECTION_PRIORITY) {
				val n = longOf(collection, id)
				if (n > 0L) {
					out.add(CollectionRow(id, prettyId(id), n))
				}
			}
			for (id in collection.keySet()) {
				if (id.isNullOrBlank() || out.any { it.id == id }) {
					continue
				}
				val upper = id.uppercase(Locale.ROOT)
				if (!listOf("LOG", "WOOD", "SAP", "STEM", "VINE", "FIG", "MANGROVE").any { upper.contains(it) }) {
					continue
				}
				val n = longOf(collection, id)
				if (n > 0L) {
					out.add(CollectionRow(id, prettyId(id), n))
				}
			}
			return out
		}

		private fun parseAttributeShards(stacks: JsonObject?): List<AttributeShardRow> {
			AttributeShardsData.ensureLoaded()
			val out = ArrayList<AttributeShardRow>()
			for (def in AttributeShardsData.all()) {
				val owned = if (stacks == null) 0 else longOf(stacks, def.stackId).toInt()
				val level = AttributeShardsData.levelFromShards(def.rarity, owned)
				val maxLevel = AttributeShardsData.maxLevel(def.rarity)
				val forMax = AttributeShardsData.shardsForMax(def.rarity)
				val unlocked = level > 0 || owned > 0
				val name = def.abilityName.ifBlank { def.displayName }
				out.add(AttributeShardRow(
					def.stackId, name, def.iconId(), def.rarity, def.priceId(),
					level, maxOf(1, maxLevel), owned, forMax, unlocked
				))
			}
			out.sortWith(compareByDescending<AttributeShardRow> { it.unlocked }.thenComparing({ it.name }, String.CASE_INSENSITIVE_ORDER))
			return out
		}

		private fun parseAttributes(stacks: JsonObject?): List<AttrStack> {
			if (stacks == null) {
				return listOf()
			}
			val out = ArrayList<AttrStack>()
			for (id in ATTR_KEYS) {
				val n = longOf(stacks, id).toInt()
				if (n > 0) {
					out.add(AttrStack(id, prettyId(id), n))
				}
			}
			return out
		}

		@JvmStatic
		fun parseTreeGifts(gifts: JsonObject?): List<TreeGift> {
			if (gifts == null) {
				return listOf()
			}
			val milestones = Leveling.obj(gifts.get("milestone_tier_claimed"))
			val out = ArrayList<TreeGift>()
			for (id in listOf("FIG", "MANGROVE", "HELIX")) {
				val count = longOf(gifts, id)
				val tier = longOf(milestones, id).toInt()
				if (count > 0L || tier > 0) {
					out.add(TreeGift(id, prettyId(id), count, tier))
				}
			}
			return out
		}

		private fun parseStarlyn(pbs: JsonObject?): List<StarlynBest> {
			if (pbs == null) {
				return listOf()
			}
			val out = ArrayList<StarlynBest>()
			for (key in pbs.keySet()) {
				val n = longOf(pbs, key)
				if (n <= 0L) {
					continue
				}
				val name = if ("agatha".equals(key, ignoreCase = true)) "Agatha" else prettyId(key)
				out.add(StarlynBest(key, name, n))
			}
			out.sortByDescending { it.amount }
			return out
		}

		private fun parseHarpSongs(harp: JsonObject?): List<HarpSong> {
			if (harp == null) {
				return listOf()
			}
			val map = LinkedHashMap<String, HarpSong>()
			for (key in harp.keySet()) {
				if (key == null || !key.startsWith("song_")) {
					continue
				}
				val rest = key.substring("song_".length)
				val id: String
				val kind: String
				when {
					rest.endsWith("_perfect_completions") -> {
						id = rest.removeSuffix("_perfect_completions")
						kind = "perfect"
					}
					rest.endsWith("_best_completion") -> {
						id = rest.removeSuffix("_best_completion")
						kind = "best"
					}
					rest.endsWith("_completions") -> {
						id = rest.removeSuffix("_completions")
						kind = "completions"
					}
					else -> continue
				}
				val prev = map[id] ?: HarpSong(id, prettyId(id), 0.0, 0, 0)
				map[id] = when (kind) {
					"best" -> prev.copy(best = doubleOf(harp, key))
					"completions" -> prev.copy(completions = longOf(harp, key).toInt())
					else -> prev.copy(perfects = longOf(harp, key).toInt())
				}
			}
			return map.values.sortedWith(Comparator.comparing({ s: HarpSong -> s.name }, String.CASE_INSENSITIVE_ORDER))
		}

		private fun toggleEnabled(nodes: JsonObject, id: String): Boolean {
			val toggleKey = "toggle_$id"
			if (!nodes.has(toggleKey) || !nodes.get(toggleKey).isJsonPrimitive) {
				return true
			}
			return try {
				nodes.get(toggleKey).asBoolean
			} catch (ignored: RuntimeException) {
				true
			}
		}

		private fun parseHotfNodes(nodes: JsonObject?): List<HotfNode> {
			if (nodes == null) {
				return listOf()
			}
			val out = ArrayList<HotfNode>()
			for (perk in ForagingHotfData.perks()) {
				val level = longOf(nodes, perk.id).toInt()
				if (level <= 0) {
					continue
				}
				out.add(HotfNode(perk.id, perk.name, level, perk.maxLevel, toggleEnabled(nodes, perk.id), perk.ability))
			}
			for (id in nodes.keySet()) {
				if (id == null || id.startsWith("toggle_") || out.any { it.id == id }) {
					continue
				}
				val level = longOf(nodes, id).toInt()
				if (level <= 0) {
					continue
				}
				out.add(HotfNode(id, ForagingHotfData.displayName(id), level, ForagingHotfData.maxLevel(id), toggleEnabled(nodes, id), false))
			}
			out.sortWith(compareByDescending<HotfNode> { it.ability }.thenComparing({ it.name }, String.CASE_INSENSITIVE_ORDER))
			return out
		}

		private fun parseOwnedShards(ownedEl: JsonElement?): List<OwnedShard> {
			if (ownedEl == null || !ownedEl.isJsonArray) {
				return listOf()
			}
			val out = ArrayList<OwnedShard>()
			for (el in ownedEl.asJsonArray) {
				if (el == null || !el.isJsonObject) {
					continue
				}
				val o = el.asJsonObject
				val type = str(o, "type")
				if (type.isBlank()) {
					continue
				}
				out.add(OwnedShard(type, prettyId(type), longOf(o, "amount_owned"), longOf(o, "captured")))
			}
			out.sortByDescending { it.amount }
			return out
		}

		private fun parseHuntStats(stats: JsonObject?): Map<String, Long> {
			val out = LinkedHashMap<String, Long>()
			if (stats == null) {
				return out
			}
			for (key in HUNT_ORDER) {
				val n = longOf(stats, key)
				if (n > 0L) {
					out[key] = n
				}
			}
			for (key in stats.keySet()) {
				if (key == null || !key.startsWith("shard_") || !key.endsWith("_hunts") || out.containsKey(key)) {
					continue
				}
				val n = longOf(stats, key)
				if (n > 0L) {
					out[key] = n
				}
			}
			return out
		}

		private fun parseGalateaBeacon(member: JsonObject?, foraging: JsonObject?, core: JsonObject?): Int {
			val playerData = Leveling.obj(member?.get("player_data"))
			val perks = Leveling.obj(playerData?.get("perks"))
			val stats = Leveling.obj(member?.get("player_stats"))
			for (source in listOf(core, foraging, playerData, perks, stats)) {
				val found = firstPresentInt(source)
				if (found >= 0) {
					return minOf(10, found)
				}
			}
			return -1
		}

		private fun firstPresentInt(obj: JsonObject?): Int {
			if (obj == null) {
				return -1
			}
			for (key in BEACON_KEYS) {
				if (obj.has(key) && !obj.get(key).isJsonNull) {
					return maxOf(0, longOf(obj, key).toInt())
				}
			}
			return -1
		}

		private fun objectiveComplete(member: JsonObject?, id: String?): Boolean {
			if (member == null || id.isNullOrBlank()) {
				return false
			}
			val objectives = Leveling.obj(member.get("objectives"))
			val row = Leveling.obj(objectives?.get(id)) ?: return false
			return "COMPLETE".equals(str(row, "status"), ignoreCase = true)
		}

		private val TOOLKIT_GROUPS = listOf(
			"TRAP" to 5, "FISHING_NET" to 1, "LASSO" to 1, "POCKET_BLACK_HOLE" to 1, "HUNTING_TOOLKIT" to 1, "HUNTING_SCYTHE" to 1
		)

		private fun parseToolkit(toolkit: JsonObject?): List<ToolkitSlot> {
			if (toolkit == null) {
				return listOf()
			}
			val inUse = Leveling.obj(toolkit.get("IN_USE"))
			val out = ArrayList<ToolkitSlot>()
			for ((group, expected) in TOOLKIT_GROUPS) {
				addToolkitGroup(out, toolkit, inUse, group, expected)
			}
			return out
		}

		private fun addToolkitGroup(out: MutableList<ToolkitSlot>, toolkit: JsonObject, inUseRoot: JsonObject?, group: String, expected: Int) {
			val el = toolkit.get(group)
			val useGroup = Leveling.obj(inUseRoot?.get(group))
			if (el != null && el.isJsonArray) {
				val arr = el.asJsonArray
				for (i in 0 until maxOf(expected, arr.size())) {
					var slot: InventorySnapshot.Slot? = null
					if (i < arr.size()) {
						val item = arr.get(i)
						if (item != null && !item.isJsonNull) {
							slot = InventoryDecoder.slotFromItemBytes(item)
						}
					}
					out.add(ToolkitSlot(group, i, toolkitInUse(useGroup, i), slot))
				}
				return
			}
			val map = Leveling.obj(el)
			if (map != null) {
				var maxIdx = expected - 1
				for (key in map.keySet()) {
					// Cap so a bogus huge key can't spin a giant slot loop.
					key.toIntOrNull()?.let { maxIdx = maxOf(maxIdx, minOf(63, it)) }
				}
				for (i in 0..maxIdx) {
					val item = map.get(i.toString())
					val slot = if (item != null && !item.isJsonNull) InventoryDecoder.slotFromItemBytes(item) else null
					out.add(ToolkitSlot(group, i, toolkitInUse(useGroup, i), slot))
				}
				return
			}
			for (i in 0 until expected) {
				out.add(ToolkitSlot(group, i, toolkitInUse(useGroup, i), null))
			}
		}

		private fun toolkitInUse(useGroup: JsonObject?, index: Int): Boolean {
			val el = useGroup?.get(index.toString()) ?: return false
			return el.isJsonPrimitive && el.asBoolean
		}

		private fun boolOf(obj: JsonObject?, key: String?): Boolean {
			if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull) {
				return false
			}
			val el = obj.get(key)
			if (!el.isJsonPrimitive) {
				return false
			}
			return try {
				val primitive = el.asJsonPrimitive
				when {
					primitive.isBoolean -> el.asBoolean
					primitive.isNumber -> el.asDouble != 0.0
					else -> {
						val s = el.asString
						"true".equals(s, ignoreCase = true) || s == "1" || "yes".equals(s, ignoreCase = true)
					}
				}
			} catch (ignored: RuntimeException) {
				false
			}
		}

		private fun memberJsonContains(member: JsonObject?, needle: String?): Boolean {
			if (member == null || needle.isNullOrBlank()) {
				return false
			}
			return try {
				member.toString().contains(needle)
			} catch (ignored: RuntimeException) {
				false
			}
		}

		private fun memberOwnsItem(member: JsonObject?, skyblockId: String?): Boolean {
			if (member == null || skyblockId.isNullOrBlank()) {
				return false
			}
			val want = skyblockId.uppercase(Locale.ROOT)
			return try {
				InventoryDecoder.parseCategories(member, null).values.any { stacks ->
					stacks.any { stack ->
						var id = stack.id.uppercase(Locale.ROOT)
						val semi = id.indexOf(';')
						if (semi > 0) {
							id = id.substring(0, semi)
						}
						want == id
					}
				}
			} catch (ignored: IllegalStateException) {
				false
			} catch (ignored: ClassCastException) {
				false
			} catch (ignored: NumberFormatException) {
				false
			} catch (ignored: UnsupportedOperationException) {
				false
			}
		}

		private fun longMap(obj: JsonObject?): Map<String, Long> {
			val out = LinkedHashMap<String, Long>()
			if (obj == null) {
				return out
			}
			for (key in obj.keySet()) {
				val n = longOf(obj, key)
				if (n > 0L) {
					out[key] = n
				}
			}
			return out
		}

		private fun stringList(el: JsonElement?): List<String> {
			if (el == null || !el.isJsonArray) {
				return listOf()
			}
			val out = ArrayList<String>()
			for (item in el.asJsonArray) {
				if (item != null && item.isJsonPrimitive) {
					val s: String? = item.asString
					if (!s.isNullOrBlank()) {
						out.add(s)
					}
				}
			}
			return out
		}

		private fun prettyId(id: String?): String = if (id.isNullOrBlank()) "" else InventoryDecoder.prettyWords(id.replace(':', '_'))

		private fun str(obj: JsonObject?, key: String?): String {
			if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull) {
				return ""
			}
			return try {
				obj.get(key).asString
			} catch (ignored: RuntimeException) {
				""
			}
		}

		private fun longOf(obj: JsonObject?, key: String?): Long {
			if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull) {
				return 0L
			}
			return try {
				obj.get(key).asLong
			} catch (ignored: RuntimeException) {
				try {
					obj.get(key).asDouble.toLong()
				} catch (ignored2: RuntimeException) {
					0L
				}
			}
		}

		private fun doubleOf(obj: JsonObject?, key: String?): Double {
			if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull) {
				return 0.0
			}
			return try {
				obj.get(key).asDouble
			} catch (ignored: RuntimeException) {
				0.0
			}
		}
	}
}
