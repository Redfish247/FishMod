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
import fishmod.pv.networth.InventoryDecoder
import java.util.Locale

class GardenSnapshot(
	@get:JvmName("islandLoaded") val islandLoaded: Boolean,
	@get:JvmName("islandLoading") val islandLoading: Boolean,
	islandError: String?,
	@get:JvmName("contestsLoaded") val contestsLoaded: Boolean,
	@get:JvmName("contestsLoading") val contestsLoading: Boolean,
	contestsError: String?,
	@get:JvmName("gardenLevel") val gardenLevel: Int,
	gardenFill: Float,
	@get:JvmName("gardenMaxed") val gardenMaxed: Boolean,
	gardenHover: String?,
	private val gardenXp: Double,
	@get:JvmName("farmingLevel") val farmingLevel: Int,
	farmingFill: Float,
	@get:JvmName("farmingMaxed") val farmingMaxed: Boolean,
	farmingHover: String?,
	copper: Long,
	larvaConsumed: Long,
	unlockedPlotIds: List<String>?,
	plotsMax: Int,
	visitorsCompleted: Long,
	uniqueVisitors: Long,
	totalVisits: Long,
	totalRejected: Long,
	@get:JvmName("visitorMilestone") val visitorMilestone: Int,
	visitorMilestoneFill: Float,
	@get:JvmName("visitorMilestoneMaxed") val visitorMilestoneMaxed: Boolean,
	visitorMilestoneHover: String?,
	@get:JvmName("uniqueVisitorMilestone") val uniqueVisitorMilestone: Int,
	uniqueVisitorMilestoneFill: Float,
	@get:JvmName("uniqueVisitorMilestoneMaxed") val uniqueVisitorMilestoneMaxed: Boolean,
	uniqueVisitorMilestoneHover: String?,
	crops: List<CropRow>?,
	visitors: List<VisitorRow>?,
	activeVisitors: List<ActiveVisitor>?,
	composter: Composter?,
	medals: MedalCounts?,
	uniqueBrackets: List<BracketCount>?,
	personalBests: List<PersonalBest>?,
	perks: Map<String, Int>?,
	contests: List<ContestEntry>?,
	gardenChips: List<ChipEntry>?,
	greenhouse: List<GreenhouseRow>?,
	greenhouseMeta: GreenhouseMeta?,
	farmingWeight: FarmingWeightInfo?,
	@get:JvmName("farmingToolkitUnlocked") val farmingToolkitUnlocked: Boolean,
	farmingToolkitSlots: List<FarmingToolkitSlot>?
) {
	@JvmRecord
	data class CropRow(
		val id: String, val name: String, val iconId: String, val collected: Long, val milestone: Int,
		val milestoneFill: Float, val milestoneMaxed: Boolean, val upgradeLevel: Int, val hover: String
	) {
		fun upgradeCopper(): GardenCosts.Progress = GardenCosts.cropUpgradeCopper(upgradeLevel)
	}

	enum class VisitorState { ACCEPTED, VISITED, NEVER }

	@JvmRecord
	data class VisitorRow(
		val id: String, val name: String, val npcItemId: String, val visits: Long, val completed: Long, val rejected: Long
	) {
		fun visited(): Boolean = visits > 0L

		fun state(): VisitorState = when {
			completed > 0L -> VisitorState.ACCEPTED
			visits > 0L -> VisitorState.VISITED
			else -> VisitorState.NEVER
		}
	}

	@JvmRecord
	data class ActiveVisitor(val id: String, val name: String, val status: String, val detail: String)

	@JvmRecord
	data class ComposterUpgrade(val id: String, val name: String, val iconId: String, val level: Int, val maxLevel: Int) {
		fun fill(): Float = if (maxLevel <= 0) 0f else minOf(1f, level / maxLevel.toFloat())

		fun maxed(): Boolean = level >= maxLevel

		fun missing(): Int = maxOf(0, maxLevel - level)
	}

	@JvmRecord
	data class Composter(
		val organicMatter: Long, val fuelUnits: Long, val compostUnits: Long, val compostItems: Long,
		val upgrades: List<ComposterUpgrade>
	) {
		fun totalUpgradeLevels(): Int = upgrades.sumOf { it.level }
	}

	@JvmRecord
	data class MedalCounts(val bronze: Int, val silver: Int, val gold: Int) {
		fun total(): Int = bronze + silver + gold
	}

	@JvmRecord
	data class BracketCount(val bracket: String, val crops: Int, val cropIds: List<String>) {
		constructor(bracket: String, crops: Int) : this(bracket, crops, listOf())
	}

	/** {@code filled} is the highest unique bracket: 0 none … 5 diamond. */
	@JvmRecord
	data class CropMedal(val id: String, val name: String, val iconId: String, val filled: Int)

	@JvmRecord
	data class PersonalBest(val id: String, val name: String, val amount: Long)

	/** {@code position} is Hypixel's 0-based claimed position; -1 when the contest was never claimed. */
	@JvmRecord
	data class ContestEntry(
		val crop: String, val cropName: String, val iconId: String, val collected: Long, val medal: String,
		val position: Int, val participants: Int, val timestampSeconds: Long
	) {
		/** 1-based placement, or 0 when none was recorded. */
		fun placement(): Int = if (position >= 0) position + 1 else 0
	}

	/** Best recorded 1-based placement (0 = none); unplaced contests were never claimed, so their rank is unknown. */
	@JvmRecord
	data class CropPlacement(val best: Int, val participants: Int, val contests: Int, val unplaced: Int)

	@JvmRecord
	data class ChipEntry(val id: String, val name: String, val iconId: String, val level: Int) {
		fun sowdust(): GardenCosts.Progress = GardenCosts.chipSowdust(level)
	}

	@JvmRecord
	data class PlotCell(val plot: GardenCosts.Plot, val unlocked: Boolean, val unlockCost: GardenCosts.Cost?)

	@JvmRecord
	data class GreenhouseRow(val id: String, val name: String, val iconId: String, val analyzed: Boolean) {
		fun discoveredOnly(): Boolean = !analyzed
	}

	@JvmRecord
	data class GreenhouseMeta(
		val slotsUnlocked: Int, val yieldLevel: Int, val plotLimitLevel: Int, val growthSpeedLevel: Int, val lastGrowthStageMs: Long
	) {
		companion object {
			@JvmStatic
			fun empty(): GreenhouseMeta = GreenhouseMeta(0, 0, 0, 0, 0L)
		}
	}

	@JvmRecord
	data class FarmingWeightInfo(
		val loaded: Boolean, val loading: Boolean, val error: String, val total: Double, val bonus: Double,
		val cropWeight: Map<String, Double>, val bonusWeight: Map<String, Double>
	) {
		/** Display total: Elite totalWeight, or crop+bonus if total looks incomplete. */
		fun displayTotal(): Double {
			val parts = cropWeight.values.sum() + bonus
			return if (total + 0.05 < parts) parts else total
		}

		companion object {
			@JvmStatic
			fun empty(): FarmingWeightInfo = FarmingWeightInfo(false, false, "", 0.0, 0.0, mapOf(), mapOf())

			@JvmStatic
			fun pending(): FarmingWeightInfo = FarmingWeightInfo(false, true, "", 0.0, 0.0, mapOf(), mapOf())

			@JvmStatic
			fun failed(error: String?): FarmingWeightInfo =
				FarmingWeightInfo(false, false, error ?: "Weight unavailable", 0.0, 0.0, mapOf(), mapOf())

			@JvmStatic
			fun fromElite(obj: JsonObject?): FarmingWeightInfo {
				if (obj == null) {
					return failed("Weight unavailable")
				}
				var total = 0.0
				val totalEl = obj.get("totalWeight")
				if (totalEl != null && totalEl.isJsonPrimitive) {
					try {
						total = totalEl.asDouble
					} catch (ignored: RuntimeException) {
					}
				}
				val crops = LinkedHashMap<String, Double>()
				Leveling.obj(obj.get("cropWeight"))?.let { cropWeight ->
					for ((key, value) in cropWeight.entrySet()) {
						Leveling.num(value)?.let { crops[key] = it.toDouble() }
					}
				}
				val bonuses = LinkedHashMap<String, Double>()
				var bonus = 0.0
				Leveling.obj(obj.get("bonusWeight"))?.let { bonusWeight ->
					for ((key, value) in bonusWeight.entrySet()) {
						val n = Leveling.num(value) ?: continue
						bonuses[key] = n.toDouble()
						bonus += n
					}
				}
				return FarmingWeightInfo(true, false, "", total, bonus, java.util.Map.copyOf(crops), java.util.Map.copyOf(bonuses))
			}
		}
	}

	@JvmRecord
	data class FarmingToolkitSlot(val cropId: String, val label: String, val index: Int, val item: InventorySnapshot.Slot?)

	@get:JvmName("islandError") val islandError: String = islandError ?: ""
	@get:JvmName("contestsError") val contestsError: String = contestsError ?: ""
	@get:JvmName("gardenFill") val gardenFill: Float = gardenFill.coerceIn(0f, 1f)
	@get:JvmName("gardenHover") val gardenHover: String = gardenHover ?: ""
	@get:JvmName("farmingFill") val farmingFill: Float = farmingFill.coerceIn(0f, 1f)
	@get:JvmName("farmingHover") val farmingHover: String = farmingHover ?: ""
	@get:JvmName("copper") val copper: Long = maxOf(0L, copper)
	@get:JvmName("larvaConsumed") val larvaConsumed: Long = maxOf(0L, larvaConsumed)
	@get:JvmName("unlockedPlotIds") val unlockedPlotIds: List<String> = copyList(unlockedPlotIds)
	@get:JvmName("plotsMax") val plotsMax: Int = maxOf(1, plotsMax)
	@get:JvmName("visitorsCompleted") val visitorsCompleted: Long = maxOf(0L, visitorsCompleted)
	@get:JvmName("uniqueVisitors") val uniqueVisitors: Long = maxOf(0L, uniqueVisitors)
	@get:JvmName("totalVisits") val totalVisits: Long = maxOf(0L, totalVisits)
	@get:JvmName("totalRejected") val totalRejected: Long = maxOf(0L, totalRejected)
	@get:JvmName("visitorMilestoneFill") val visitorMilestoneFill: Float = visitorMilestoneFill.coerceIn(0f, 1f)
	@get:JvmName("visitorMilestoneHover") val visitorMilestoneHover: String = visitorMilestoneHover ?: ""
	@get:JvmName("uniqueVisitorMilestoneFill") val uniqueVisitorMilestoneFill: Float = uniqueVisitorMilestoneFill.coerceIn(0f, 1f)
	@get:JvmName("uniqueVisitorMilestoneHover") val uniqueVisitorMilestoneHover: String = uniqueVisitorMilestoneHover ?: ""
	@get:JvmName("crops") val crops: List<CropRow> = copyList(crops)
	@get:JvmName("visitors") val visitors: List<VisitorRow> = copyList(visitors)
	@get:JvmName("activeVisitors") val activeVisitors: List<ActiveVisitor> = copyList(activeVisitors)
	@get:JvmName("composter") val composter: Composter = composter ?: emptyComposter()
	@get:JvmName("medals") val medals: MedalCounts = medals ?: MedalCounts(0, 0, 0)
	@get:JvmName("uniqueBrackets") val uniqueBrackets: List<BracketCount> = copyList(uniqueBrackets)
	@get:JvmName("personalBests") val personalBests: List<PersonalBest> = copyList(personalBests)
	@get:JvmName("perks") val perks: Map<String, Int> = java.util.Map.copyOf(perks ?: mapOf())
	@get:JvmName("contests") val contests: List<ContestEntry> = copyList(contests)
	@get:JvmName("gardenChips") val gardenChips: List<ChipEntry> = copyList(gardenChips)
	@get:JvmName("greenhouse") val greenhouse: List<GreenhouseRow> = copyList(greenhouse)
	@get:JvmName("greenhouseMeta") val greenhouseMeta: GreenhouseMeta = greenhouseMeta ?: GreenhouseMeta.empty()
	@get:JvmName("farmingWeight") val farmingWeight: FarmingWeightInfo = farmingWeight ?: FarmingWeightInfo.empty()
	@get:JvmName("farmingToolkitSlots") val farmingToolkitSlots: List<FarmingToolkitSlot> = copyList(farmingToolkitSlots)

	@Volatile
	private var placements: Map<String, CropPlacement>? = null

	fun withIslandLoading(): GardenSnapshot = copy(false, true, "", contestsLoaded, contestsLoading, contestsError)

	fun withIslandError(error: String?): GardenSnapshot =
		copy(false, false, error ?: "Couldn't load garden", contestsLoaded, contestsLoading, contestsError)

	fun withContestsLoading(): GardenSnapshot = copy(islandLoaded, islandLoading, islandError, false, true, "")

	fun withContestsError(error: String?): GardenSnapshot =
		copy(islandLoaded, islandLoading, islandError, false, false, error ?: "Contests unavailable")

	fun withContestsReady(): GardenSnapshot = copy(islandLoaded, islandLoading, islandError, true, false, "")

	fun withEliteContests(array: JsonArray?): GardenSnapshot {
		val list = parseEliteContests(array)
		// Prefer Elite history when present; keep Hypixel contests if Elite returned nothing useful.
		if (list.isEmpty() && contests.isNotEmpty()) {
			return withContestsReady()
		}
		return copy(islandLoaded, islandLoading, islandError, true, false, "", contestsList = list)
	}

	fun withFarmingWeight(info: FarmingWeightInfo?): GardenSnapshot =
		copy(islandLoaded, islandLoading, islandError, contestsLoaded, contestsLoading, contestsError,
			weight = info ?: FarmingWeightInfo.empty())

	fun withIsland(garden: JsonObject?): GardenSnapshot {
		GardenData.ensureLoaded()
		if (garden == null) {
			return withIslandError("No garden data")
		}
		val xp = doubleOf(garden, "garden_experience")
		val gl = GardenData.gardenLevel(xp)
		val gardenHover = gardenHover(gl, GardenData.gardenOverflow(xp), xp)

		val cropRows = buildCrops(Leveling.obj(garden.get("resources_collected")), Leveling.obj(garden.get("crop_upgrade_levels")))

		val plots = ArrayList(LinkedHashSet(rawStringList(garden.get("unlocked_plots_ids"))))

		val commission = Leveling.obj(garden.get("commission_data"))
		val totalCompleted = longOf(commission, "total_completed")
		val unique = longOf(commission, "unique_npcs_served")
		val visitorRows = parseVisitors(commission)
		val visitsSum = visitorRows.sumOf { it.visits }
		val rejectedSum = visitorRows.sumOf { it.rejected }
		val apiVisits = longOf(commission, "total_visits")
		val apiRejected = longOf(commission, "total_rejected")
		val totalVisits = if (apiVisits > 0L) apiVisits else visitsSum
		val totalRejected = if (apiRejected > 0L) apiRejected else rejectedSum

		val vl = GardenData.visitorMilestone(totalCompleted)
		val visitorHover = if (vl.maxed) {
			"Offers accepted milestone " + vl.level + " - MAX"
		} else {
			"Offers accepted milestone " + vl.level + " - " + FormatUtil.commas(vl.intoLevel) + " / " + FormatUtil.commas(vl.needForNext)
		}

		val uvl = GardenData.uniqueVisitorMilestone(unique)
		val uniqueHover = if (uvl.maxed) {
			"Unique visitors milestone " + uvl.level + " - MAX"
		} else {
			"Unique visitors milestone " + uvl.level + " - " + FormatUtil.commas(uvl.intoLevel) + " / " + FormatUtil.commas(uvl.needForNext)
		}

		val ghMeta = parseGreenhouseMeta(garden)

		return GardenSnapshot(
			true, false, "", contestsLoaded, contestsLoading, contestsError,
			gl.level, gl.fill, gl.maxed, gardenHover, xp,
			farmingLevel, farmingFill, farmingMaxed, farmingHover,
			copper, larvaConsumed, plots, GardenData.maxPlots(),
			totalCompleted, unique, totalVisits, totalRejected,
			vl.level, vl.fill, vl.maxed, visitorHover,
			uvl.level, uvl.fill, uvl.maxed, uniqueHover,
			cropRows, visitorRows, parseActive(garden.get("active_commissions")),
			parseComposter(Leveling.obj(garden.get("composter_data"))),
			medals, uniqueBrackets, personalBests, perks, contests, gardenChips,
			greenhouse, ghMeta, farmingWeight,
			farmingToolkitUnlocked, farmingToolkitSlots
		)
	}

	private fun copy(
		islLoaded: Boolean, islLoading: Boolean, islError: String?,
		cLoaded: Boolean, cLoading: Boolean, cError: String?,
		contestsList: List<ContestEntry> = contests,
		weight: FarmingWeightInfo = farmingWeight
	): GardenSnapshot = GardenSnapshot(
		islLoaded, islLoading, islError, cLoaded, cLoading, cError,
		gardenLevel, gardenFill, gardenMaxed, gardenHover, gardenXp,
		farmingLevel, farmingFill, farmingMaxed, farmingHover,
		copper, larvaConsumed, unlockedPlotIds, plotsMax,
		visitorsCompleted, uniqueVisitors, totalVisits, totalRejected,
		visitorMilestone, visitorMilestoneFill, visitorMilestoneMaxed, visitorMilestoneHover,
		uniqueVisitorMilestone, uniqueVisitorMilestoneFill, uniqueVisitorMilestoneMaxed, uniqueVisitorMilestoneHover,
		crops, visitors, activeVisitors, composter,
		medals, uniqueBrackets, personalBests, perks, contestsList, gardenChips,
		greenhouse, greenhouseMeta, weight,
		farmingToolkitUnlocked, farmingToolkitSlots
	)

	fun gardenOverflowLevel(): Int = GardenData.gardenOverflow(gardenXp)?.level ?: gardenLevel

	fun plotsUnlocked(): Int = unlockedPlotIds.size

	/** Static plot layout with this profile's unlock state; locked plots carry their tier's next price. */
	fun plotCells(): List<PlotCell> {
		val unlocked = unlockedPlotIds.mapTo(LinkedHashSet()) { it.lowercase(Locale.ROOT) }
		val unlockedPerTier = LinkedHashMap<String, Int>()
		for (plot in GardenCosts.plots()) {
			if (plot.id in unlocked) {
				unlockedPerTier.merge(plot.tier, 1) { a, b -> a + b }
			}
		}
		return GardenCosts.plots().map { plot ->
			val open = plot.id in unlocked
			val cost = if (open) null else GardenCosts.nextPlotCost(plot.tier, unlockedPerTier[plot.tier] ?: 0)
			PlotCell(plot, open, cost)
		}
	}

	fun composterCostToMax(): GardenCosts.ComposterRemaining {
		val levels = LinkedHashMap<String, Int?>()
		for (upgrade in composter.upgrades) {
			levels[upgrade.id] = upgrade.level
		}
		return GardenCosts.composterRemaining(levels)
	}

	/** Crop ids that have earned a unique GOLD Jacob bracket. */
	fun uniqueGoldCrops(): List<String> =
		uniqueBrackets.firstOrNull { it.bracket.equals("Gold", ignoreCase = true) && it.cropIds.isNotEmpty() }?.cropIds ?: listOf()

	/**
	 * Per-crop highest unique medal as filled orb count (bronze→silver→gold→platinum→diamond).
	 * Prefers Hypixel {@code unique_brackets} crop lists; merges Elite/Hypixel contest medals
	 * when bracket crop ids are missing so the Jacob crop-medal row is not stuck on "None yet".
	 */
	fun cropMedals(): List<CropMedal> {
		val filledByCrop = LinkedHashMap<String, Int>()
		for (b in uniqueBrackets) {
			val rank = bracketRank(b.bracket)
			if (rank <= 0) {
				continue
			}
			for (cropId in b.cropIds) {
				if (cropId.isBlank()) {
					continue
				}
				val key = GardenData.normalizeCropKey(cropId)
				filledByCrop.merge(key.ifBlank { cropId }, rank) { a, c -> maxOf(a, c) }
			}
		}
		// Always allow contest medals to fill gaps (Elite async / count-only unique_brackets).
		for (contest in contests) {
			val rank = bracketRank(contest.medal)
			if (rank <= 0) {
				continue
			}
			val cropId = contest.crop
			if (cropId.isBlank()) {
				continue
			}
			val key = GardenData.normalizeCropKey(cropId)
			filledByCrop.merge(key.ifBlank { cropId }, rank) { a, c -> maxOf(a, c) }
		}
		val out = ArrayList<CropMedal>(filledByCrop.size)
		for ((id, filled) in filledByCrop) {
			out.add(CropMedal(id, GardenData.prettyCrop(id), GardenData.cropIconId(id), filled.coerceIn(0, 5)))
		}
		out.sortWith(Comparator.comparing({ m: CropMedal -> m.name }, String.CASE_INSENSITIVE_ORDER))
		return out
	}

	fun hasUniqueGold(cropId: String?): Boolean {
		if (cropId.isNullOrBlank()) {
			return false
		}
		val key = GardenData.normalizeCropKey(cropId)
		return uniqueGoldCrops().any { key == GardenData.normalizeCropKey(it) }
	}

	/** Keyed by {@link GardenData#normalizeCropKey}. */
	fun placements(): Map<String, CropPlacement> {
		placements?.let { return it }
		return placementsByCrop(contests).also { placements = it }
	}

	fun placement(cropId: String?): CropPlacement? = placements()[GardenData.normalizeCropKey(cropId)]

	companion object {
		@JvmField
		val CROP_ORDER: List<String> = listOf(
			"WHEAT", "CARROT_ITEM", "POTATO_ITEM", "PUMPKIN", "SUGAR_CANE", "MELON", "CACTUS",
			"INK_SACK:3", "MUSHROOM_COLLECTION", "NETHER_STALK", "DOUBLE_PLANT", "MOONFLOWER", "WILD_ROSE"
		)

		const val COMPOSTER_UPGRADE_MAX = 25
		private const val COMPOSTER_ATTRIBUTE_MAX = 10
		private val COMPOSTER_UPGRADE_ORDER = listOf("speed", "multi_drop", "fuel_cap", "organic_matter_cap", "cost_reduction")

		private fun <T : Any> copyList(list: List<T>?): List<T> = java.util.List.copyOf(list ?: listOf())

		@JvmStatic
		fun empty(): GardenSnapshot {
			GardenData.ensureLoaded()
			return GardenSnapshot(
				false, false, "", false, false, "",
				0, 0f, false, "", 0.0,
				0, 0f, false, "",
				0, 0, listOf(), GardenData.maxPlots(),
				0, 0, 0, 0, 0, 0f, false, "", 0, 0f, false, "",
				listOf(), listOf(), listOf(), null,
				MedalCounts(0, 0, 0), listOf(), listOf(), mapOf(), listOf(), listOf(),
				listOf(), GreenhouseMeta.empty(), FarmingWeightInfo.empty(),
				false, listOf()
			)
		}

		@JvmStatic
		fun fromMember(member: JsonObject?): GardenSnapshot {
			GardenData.ensureLoaded()
			if (member == null) {
				return empty()
			}
			val farmingXp = Leveling.readSkillXpDouble(member, "farming")
			val farmingCap = Leveling.skillCap("farming", member)
			val farming = Leveling.getLevel(Leveling.skillTable("farming"), farmingXp, farmingCap, false)

			val gpd = Leveling.obj(member.get("garden_player_data"))
			val copper = longOf(gpd, "copper")
			val larva = longOf(gpd, "larva_consumed")
			val discovered = rawStringList(gpd?.get("discovered_greenhouse_crops"))
			val analyzed = rawStringList(gpd?.get("analyzed_greenhouse_crops"))
			val greenhouse = buildGreenhouse(discovered, analyzed)

			val playerData = Leveling.obj(member.get("player_data"))
			val chips = parseChips(Leveling.obj(playerData?.get("garden_chips")))

			val jacobs = Leveling.obj(member.get("jacobs_contest"))
			val medals = parseMedals(jacobs)
			val brackets = parseBrackets(jacobs)
			val pbs = parsePersonalBests(jacobs)
			val perks = parsePerks(jacobs)
			val contests = parseHypixelContests(jacobs)
			val toolkit = Leveling.obj(gpd?.get("farming_toolkit"))
			val toolkitUnlocked = boolOf(toolkit, "IS_UNLOCKED")
			val toolkitSlots = parseFarmingToolkit(toolkit)

			return GardenSnapshot(
				false, false, "", contests.isNotEmpty(), false, "",
				0, 0f, false, "Open Garden tab to load island", 0.0,
				Math.floor(farming.level.toDouble()).toInt(), farming.fill(), farming.maxed, farming.skillHoverText("Farming"),
				copper, larva, listOf(), GardenData.maxPlots(),
				0, 0, 0, 0, 0, 0f, false, "", 0, 0f, false, "",
				listOf(), listOf(), listOf(), null,
				medals, brackets, pbs, perks, contests, chips,
				greenhouse, GreenhouseMeta.empty(), FarmingWeightInfo.empty(),
				toolkitUnlocked, toolkitSlots
			)
		}

		private fun buildCrops(resources: JsonObject?, upgrades: JsonObject?): List<CropRow> {
			val amounts = LinkedHashMap<String, Long>()
			if (resources != null) {
				for ((key, value) in resources.entrySet()) {
					val amount = Leveling.longAmount(value)
					if (amount > 0L) {
						amounts[key] = amount
					}
				}
			}
			val rows = ArrayList<CropRow>()
			val used = LinkedHashSet<String>()
			for (id in CROP_ORDER) {
				val amount = takeAmount(amounts, id)
				used.add(GardenData.normalizeCropKey(id))
				rows.add(cropRow(id, amount, upgrades))
			}
			for ((key, amount) in amounts.entries.sortedBy { it.key }) {
				if (GardenData.normalizeCropKey(key) in used) {
					continue
				}
				rows.add(cropRow(key, amount, upgrades))
			}
			return rows
		}

		private fun takeAmount(amounts: MutableMap<String, Long>, id: String): Long {
			amounts.remove(id)?.let { return it }
			for (alias in listOf(id.replace("_ITEM", ""), GardenData.normalizeCropKey(id), GardenData.cropIconId(id))) {
				amounts.remove(alias)?.let { return it }
			}
			// DOUBLE_PLANT is sunflower in Hypixel resources
			if (id == "DOUBLE_PLANT") {
				amounts.remove("SUNFLOWER")?.let { return it }
			}
			return 0L
		}

		@JvmStatic
		fun gardenHover(gl: GardenData.LevelProgress, overflow: GardenData.LevelProgress?, xp: Double): String {
			if (!gl.maxed) {
				return "Garden " + gl.level + " - " + FormatUtil.commas(gl.intoLevel) + " / " + FormatUtil.commas(gl.needForNext) +
					" (" + FormatUtil.oneDecimal((gl.fill * 100).toDouble()) + "%)"
			}
			val out = StringBuilder("Garden " + gl.level + " - MAX")
			if (overflow != null && gl.intoLevel > 0L) {
				out.append("\nOverflow Level\t").append(overflow.level)
				out.append("\nNext Level\t").append(FormatUtil.commas(overflow.intoLevel)).append(" / ")
					.append(FormatUtil.commas(overflow.needForNext))
					.append(" (").append(FormatUtil.oneDecimal((overflow.fill * 100).toDouble())).append("%)")
				out.append("\nOverflow XP\t").append(FormatUtil.commas(gl.intoLevel))
			}
			out.append("\nTotal XP\t").append(FormatUtil.commas(Math.round(xp)))
			return out.toString()
		}

		private fun cropRow(id: String, amount: Long, upgrades: JsonObject?): CropRow {
			val ms = GardenData.cropMilestone(id, amount)
			val upgrade = upgradeOf(upgrades, id)
			val hover = if (ms.maxed) {
				"Overflow: " + FormatUtil.commas(ms.intoLevel)
			} else {
				FormatUtil.commas(ms.intoLevel) + " / " + FormatUtil.commas(ms.needForNext)
			}
			return CropRow(id, GardenData.prettyCrop(id), GardenData.cropIconId(id), amount, ms.level, ms.fill, ms.maxed, upgrade, hover)
		}

		private fun upgradeOf(upgrades: JsonObject?, id: String): Int {
			var upgrade = intOf(upgrades, id)
			if (upgrade > 0) {
				return upgrade
			}
			for (alias in listOf(
				GardenData.normalizeCropKey(id),
				id.replace("_ITEM", ""),
				"COCOA_BEANS", "COCOA", "SUNFLOWER", "MUSHROOM", "NETHER_WART"
			)) {
				upgrade = maxOf(upgrade, intOf(upgrades, alias))
			}
			return upgrade
		}

		private fun parseVisitors(commission: JsonObject?): List<VisitorRow> {
			val visits = commission?.let { Leveling.obj(it.get("visits")) }
			val completed = commission?.let { Leveling.obj(it.get("completed")) }
			val stats = LinkedHashMap<String, LongArray>()
			for (id in GardenData.allVisitorIds()) {
				stats[id.lowercase(Locale.ROOT)] = longArrayOf(0L, 0L)
			}
			if (visits != null) {
				for ((key, value) in visits.entrySet()) {
					stats.getOrPut(key.lowercase(Locale.ROOT)) { longArrayOf(0L, 0L) }[0] = Leveling.longAmount(value)
				}
			}
			if (completed != null) {
				for ((key, value) in completed.entrySet()) {
					stats.getOrPut(key.lowercase(Locale.ROOT)) { longArrayOf(0L, 0L) }[1] = Leveling.longAmount(value)
				}
			}
			val rows = ArrayList<VisitorRow>()
			for ((id, counts) in stats) {
				val v = counts[0]
				val c = counts[1]
				rows.add(VisitorRow(id, GardenData.prettyVisitor(id), GardenData.visitorNpcId(id), v, c, maxOf(0L, v - c)))
			}
			rows.sortWith(compareByDescending<VisitorRow> { it.visits }.thenBy { it.name })
			return rows
		}

		private fun parseActive(element: JsonElement?): List<ActiveVisitor> {
			val out = ArrayList<ActiveVisitor>()
			if (element == null || element.isJsonNull) {
				return out
			}
			if (element.isJsonObject) {
				val obj = element.asJsonObject
				var map = false
				for ((key, value) in obj.entrySet()) {
					if (value.isJsonObject) {
						map = true
						out.add(activeFrom(key, value.asJsonObject))
					}
				}
				if (!map && (obj.has("visitor") || obj.has("npc"))) {
					out.add(activeFrom(stringOf(obj, "visitor").ifBlank { stringOf(obj, "npc") }, obj))
				}
			} else if (element.isJsonArray) {
				for (el in element.asJsonArray) {
					if (!el.isJsonObject) {
						continue
					}
					val v = el.asJsonObject
					out.add(activeFrom(stringOf(v, "visitor").ifBlank { stringOf(v, "npc") }, v))
				}
			}
			return out
		}

		private fun activeFrom(id: String, v: JsonObject): ActiveVisitor {
			val status = stringOf(v, "status").ifBlank { stringOf(v, "state") }
			var detail = ""
			val req = Leveling.obj(v.get("requirements"))
			if (req != null && req.entrySet().isNotEmpty()) {
				val sb = StringBuilder()
				for ((key, value) in req.entrySet()) {
					if (sb.isNotEmpty()) {
						sb.append(", ")
					}
					val n = Leveling.num(value)
					sb.append(GardenData.prettyCrop(key))
					if (n != null) {
						sb.append(' ').append(FormatUtil.shortXp(n.toDouble()))
					}
				}
				detail = sb.toString()
			}
			return ActiveVisitor(id, GardenData.prettyVisitor(id), status, detail)
		}

		private fun emptyComposter(): Composter = Composter(0, 0, 0, 0, listOf())

		private fun parseComposter(data: JsonObject?): Composter {
			if (data == null) {
				return emptyComposter()
			}
			val upgrades = Leveling.obj(data.get("upgrades"))
			val rows = ArrayList<ComposterUpgrade>()
			val seen = LinkedHashSet<String>()
			for (key in COMPOSTER_UPGRADE_ORDER) {
				val max = upgradeMax(key)
				val level = clampUpgrade(intOf(upgrades, key), max)
				rows.add(ComposterUpgrade(key, GardenData.prettyComposterUpgrade(key), GardenData.composterUpgradeIconId(key), level, max))
				seen.add(key)
			}
			if (upgrades != null) {
				for ((rawKey, value) in upgrades.entrySet()) {
					val key = rawKey.lowercase(Locale.ROOT)
					if (key in seen) {
						continue
					}
					val n = Leveling.num(value)
					val max = upgradeMax(key)
					val level = clampUpgrade(if (n == null) 0 else Math.round(n), max)
					rows.add(ComposterUpgrade(key, GardenData.prettyComposterUpgrade(key), GardenData.composterUpgradeIconId(key), level, max))
				}
			}
			return Composter(
				longOf(data, "organic_matter"),
				longOf(data, "fuel_units"),
				longOf(data, "compost_unit") + longOf(data, "compost_units"),
				longOf(data, "compost_item") + longOf(data, "compost_items"),
				java.util.List.copyOf(rows)
			)
		}

		private fun upgradeMax(key: String?): Int {
			if (key.isNullOrBlank()) {
				return COMPOSTER_ATTRIBUTE_MAX
			}
			return when (key.lowercase(Locale.ROOT)) {
				"speed", "multi_drop", "fuel_cap", "organic_matter_cap", "cost_reduction" -> COMPOSTER_UPGRADE_MAX
				else -> COMPOSTER_ATTRIBUTE_MAX
			}
		}

		private fun clampUpgrade(level: Int, max: Int): Int = maxOf(0, minOf(max, level))

		private fun parseMedals(jacobs: JsonObject?): MedalCounts {
			val medals = jacobs?.let { Leveling.obj(it.get("medals_inv")) }
			return MedalCounts(intOf(medals, "bronze"), intOf(medals, "silver"), intOf(medals, "gold"))
		}

		private fun parseBrackets(jacobs: JsonObject?): List<BracketCount> {
			val brackets = jacobs?.let { Leveling.obj(it.get("unique_brackets")) } ?: return listOf()
			val out = ArrayList<BracketCount>()
			for (key in listOf("BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND")) {
				// Hypixel stores lowercase keys (bronze/silver/…).
				val el = brackets.get(key) ?: brackets.get(key.lowercase(Locale.ROOT))
				val cropIds = bracketCropIds(el)
				val count = if (cropIds.isEmpty()) bracketCountFallback(el) else cropIds.size
				if (count > 0) {
					out.add(BracketCount(title(key), count, java.util.List.copyOf(cropIds)))
				}
			}
			return out
		}

		private fun bracketCropIds(el: JsonElement?): List<String> {
			if (el == null) {
				return listOf()
			}
			val out = ArrayList<String>()
			if (el.isJsonArray) {
				for (item in el.asJsonArray) {
					if (item != null && item.isJsonPrimitive) {
						val id: String? = item.asString
						if (!id.isNullOrBlank()) {
							out.add(id)
						}
					}
				}
			} else if (el.isJsonObject) {
				out.addAll(el.asJsonObject.keySet())
			}
			out.sortWith(String.CASE_INSENSITIVE_ORDER)
			return out
		}

		private fun bracketCountFallback(el: JsonElement?): Int {
			if (el == null) {
				return 0
			}
			if (el.isJsonArray) {
				return el.asJsonArray.size()
			}
			if (el.isJsonObject) {
				return el.asJsonObject.size()
			}
			val n = Leveling.num(el)
			return if (n == null) 0 else Math.round(n)
		}

		private fun parsePersonalBests(jacobs: JsonObject?): List<PersonalBest> {
			val pbs = jacobs?.let { Leveling.obj(it.get("personal_bests")) } ?: return listOf()
			val out = ArrayList<PersonalBest>()
			for ((key, value) in pbs.entrySet()) {
				val amount = Leveling.longAmount(value)
				if (amount <= 0L) {
					continue
				}
				out.add(PersonalBest(key, GardenData.prettyCrop(key), amount))
			}
			out.sortByDescending { it.amount }
			return out
		}

		private fun parsePerks(jacobs: JsonObject?): Map<String, Int> {
			val perks = jacobs?.let { Leveling.obj(it.get("perks")) } ?: return mapOf()
			val out = LinkedHashMap<String, Int>()
			for ((key, value) in perks.entrySet()) {
				val n = Leveling.num(value)
				out[key] = if (n == null) 0 else Math.round(n)
			}
			return out
		}

		private fun parseChips(chips: JsonObject?): List<ChipEntry> {
			val levels = LinkedHashMap<String, Int>()
			for (id in GardenData.CHIP_ORDER) {
				levels[id] = 0
			}
			if (chips != null) {
				for ((rawKey, value) in chips.entrySet()) {
					val key = GardenData.normalizeChipKey(rawKey)
					if (key.isBlank()) {
						continue
					}
					var n = Leveling.num(value)
					if (n == null && value != null && value.isJsonObject) {
						n = Leveling.num(value.asJsonObject.get("level"))
					}
					levels[key] = if (n == null) 0 else Math.round(n)
				}
			}
			val out = ArrayList<ChipEntry>()
			val seen = LinkedHashSet<String>()
			for (id in GardenData.CHIP_ORDER) {
				seen.add(id)
				out.add(ChipEntry(id, GardenData.prettyChip(id), GardenData.chipIconId(id), levels[id] ?: 0))
			}
			for (id in levels.keys.filter { it !in seen }.sorted()) {
				out.add(ChipEntry(id, GardenData.prettyChip(id), GardenData.chipIconId(id), levels[id] ?: 0))
			}
			return out
		}

		private fun parseGreenhouseMeta(garden: JsonObject): GreenhouseMeta {
			var slots = 0
			val slotsEl = garden.get("greenhouse_slots")
			if (slotsEl != null && slotsEl.isJsonArray) {
				slots = slotsEl.asJsonArray.size()
			}
			val upgrades = Leveling.obj(garden.get("garden_upgrades"))
			var yield = intOf(upgrades, "greenhouse_yield")
			if (yield == 0) {
				yield = intOf(upgrades, "GREENHOUSE_YIELD")
			}
			var plotLimit = intOf(upgrades, "greenhouse_plot_limit")
			if (plotLimit == 0) {
				plotLimit = intOf(upgrades, "GREENHOUSE_PLOT_LIMIT")
			}
			var growth = intOf(upgrades, "greenhouse_growth_speed")
			if (growth == 0) {
				growth = intOf(upgrades, "GREENHOUSE_GROWTH_SPEED")
			}
			val last = longOf(garden, "last_growth_stage_time")
			return GreenhouseMeta(slots, yield, plotLimit, growth, last)
		}

		private fun parseHypixelContests(jacobs: JsonObject?): List<ContestEntry> {
			val contests = jacobs?.let { Leveling.obj(it.get("contests")) } ?: return listOf()
			val out = ArrayList<ContestEntry>()
			for ((key, value) in contests.entrySet()) {
				if (!value.isJsonObject) {
					continue
				}
				val c = value.asJsonObject
				val crop = cropFromContestKey(key)
				val medal = stringOf(c, "claimed_medal").ifBlank { "none" }
				out.add(contestEntry(crop, longOf(c, "collected"), medal.lowercase(Locale.ROOT),
					positionOf(c, "claimed_position"), intOf(c, "claimed_participants"), 0L))
			}
			return out
		}

		private fun parseEliteContests(array: JsonArray?): List<ContestEntry> {
			if (array == null) {
				return listOf()
			}
			val out = ArrayList<ContestEntry>()
			for (el in array) {
				if (!el.isJsonObject) {
					continue
				}
				val c = el.asJsonObject
				out.add(contestEntry(
					eliteCrop(c.get("crop")),
					longOf(c, "collected"),
					eliteMedal(c.get("medal")),
					positionOf(c, "position"),
					intOf(c, "participants"),
					longOf(c, "timestamp")
				))
			}
			out.sortByDescending { it.timestampSeconds }
			return out
		}

		private fun contestEntry(crop: String?, collected: Long, medal: String?, position: Int, participants: Int, timestamp: Long): ContestEntry {
			val cropId = crop ?: ""
			return ContestEntry(
				cropId,
				GardenData.prettyCrop(cropId),
				GardenData.cropIconId(cropId),
				collected,
				if (medal.isNullOrBlank()) "none" else medal.lowercase(Locale.ROOT),
				position,
				participants,
				timestamp
			)
		}

		private fun eliteCrop(el: JsonElement?): String {
			if (el == null || el.isJsonNull || !el.isJsonPrimitive) {
				return ""
			}
			if (el.asJsonPrimitive.isNumber) {
				return GardenData.eliteCropId(el.asInt)
			}
			return GardenData.eliteCropId(el.asString)
		}

		private fun eliteMedal(el: JsonElement?): String {
			if (el == null || el.isJsonNull || !el.isJsonPrimitive) {
				return "none"
			}
			if (el.asJsonPrimitive.isNumber) {
				return GardenData.eliteMedalName(el.asInt)
			}
			val raw: String? = el.asString
			if (raw.isNullOrBlank()) {
				return "none"
			}
			return raw.trim().toIntOrNull()?.let { GardenData.eliteMedalName(it) } ?: raw.lowercase(Locale.ROOT)
		}

		@JvmStatic
		fun cropFromContestKey(key: String?): String {
			if (key.isNullOrBlank()) {
				return ""
			}
			val first = key.indexOf(':')
			if (first < 0) {
				return key
			}
			val second = key.indexOf(':', first + 1)
			return if (second >= 0) key.substring(second + 1) else key.substring(first + 1)
		}

		@JvmStatic
		fun buildGreenhouse(discovered: List<String?>, analyzed: List<String?>): List<GreenhouseRow> {
			val analyzedSet = LinkedHashSet<String>()
			for (id in analyzed) {
				if (!id.isNullOrBlank()) {
					analyzedSet.add(id.uppercase(Locale.ROOT))
				}
			}
			val ids = LinkedHashSet<String>()
			for (id in discovered) {
				if (!id.isNullOrBlank()) {
					ids.add(id.uppercase(Locale.ROOT))
				}
			}
			ids.addAll(analyzedSet)
			// Dead Plant is what a failed mutation leaves behind; the API lists it as discovered but it can't be analyzed.
			ids.remove("DEAD_PLANT")
			val rows = ids.map { id ->
				GreenhouseRow(id, GardenData.prettyGreenhouse(id), GardenData.greenhouseIconId(id), id in analyzedSet)
			}
			return rows.sortedWith(
				compareByDescending<GreenhouseRow> { it.analyzed }.thenComparing({ it.name }, String.CASE_INSENSITIVE_ORDER)
			)
		}

		/** Raw API ids - do not pretty-print (greenhouse crops are not visitors). */
		private fun rawStringList(el: JsonElement?): List<String> {
			if (el == null || !el.isJsonArray) {
				return listOf()
			}
			val out = ArrayList<String>()
			for (item in el.asJsonArray) {
				if (item.isJsonPrimitive) {
					val raw: String? = item.asString
					if (!raw.isNullOrBlank()) {
						out.add(raw)
					}
				}
			}
			return out
		}

		private fun longOf(obj: JsonObject?, key: String): Long {
			if (obj == null || !obj.has(key)) {
				return 0L
			}
			return Leveling.longAmount(obj.get(key))
		}

		private fun doubleOf(obj: JsonObject?, key: String): Double {
			if (obj == null || !obj.has(key)) {
				return 0.0
			}
			return Leveling.numDouble(obj.get(key)) ?: 0.0
		}

		private fun intOf(obj: JsonObject?, key: String): Int = longOf(obj, key).toInt()

		// Elite sends position -1 for unclaimed contests; longOf clamps negatives to 0, which would read as first place.
		@JvmStatic
		fun positionOf(obj: JsonObject?, key: String): Int {
			val n = if (obj == null || !obj.has(key)) null else Leveling.numDouble(obj.get(key))
			if (n == null || n.isNaN() || n.isInfinite() || n < 0) {
				return -1
			}
			return Math.round(n).toInt()
		}

		private fun stringOf(obj: JsonObject?, key: String): String {
			if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) {
				return ""
			}
			return try {
				obj.get(key).asString
			} catch (ignored: RuntimeException) {
				""
			}
		}

		private fun title(raw: String?): String {
			if (raw.isNullOrBlank()) {
				return "?"
			}
			return raw.substring(0, 1) + raw.substring(1).lowercase(Locale.ROOT)
		}

		private fun bracketRank(bracket: String?): Int {
			if (bracket.isNullOrBlank()) {
				return 0
			}
			return when (bracket.trim().lowercase(Locale.ROOT)) {
				"bronze" -> 1
				"silver" -> 2
				"gold" -> 3
				"platinum" -> 4
				"diamond" -> 5
				else -> 0
			}
		}

		@JvmStatic
		fun placementsByCrop(contests: List<ContestEntry>): Map<String, CropPlacement> {
			val out = LinkedHashMap<String, CropPlacement>()
			for (c in contests) {
				val key = GardenData.normalizeCropKey(c.crop)
				if (key.isBlank()) {
					continue
				}
				val prev = out[key] ?: CropPlacement(0, 0, 0, 0)
				val place = c.placement()
				val better = place > 0 && (prev.best == 0 || place < prev.best)
				out[key] = CropPlacement(
					if (better) place else prev.best,
					if (better) c.participants else prev.participants,
					prev.contests + 1,
					prev.unplaced + (if (place > 0) 0 else 1)
				)
			}
			return out
		}

		private fun parseFarmingToolkit(toolkit: JsonObject?): List<FarmingToolkitSlot> {
			if (toolkit == null) {
				return listOf()
			}
			return InventoryDecoder.parseToolkit(toolkit).map { item ->
				val stack = item.stack
				val slot = if (stack == null) {
					null
				} else {
					InventorySnapshot.Slot(stack.id, 1, stack.lore, null, null, null, null, stack.extraAttributes, false)
				}
				val label = GardenData.prettyCrop(item.category).ifBlank { if (item.category.isBlank()) "?" else GardenData.prettyCrop(item.category) }
				FarmingToolkitSlot(item.category, label, maxOf(0, item.index), slot)
			}
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
	}
}
