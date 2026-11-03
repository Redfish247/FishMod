/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons

import fishmod.pv.data.DungeonSnapshot
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object ClassXpCalculator {
	/**
	 * Off-class share used by Adjectils / common RTCA sims (`selectedRate / 4`).
	 * UI tips often say 0.2×; matching Adjectils requires 0.25.
	 */
	private const val OFF_CLASS_RATE = 0.25

	/** Adjectils caps Derpy/Aura class XP at +50% even when Aura is +59%. */
	private const val CLASS_MAYOR_CAP = 1.5
	private const val RTCA_MAX_RUNS = 200_000

	@JvmRecord
	data class FloorEstimate(
		val id: String,
		val label: String,
		val master: Boolean,
		val xpPerRun: Long,
		val runsNeeded: Long,
		val completions: Long
	)

	@JvmRecord
	data class Result(
		val classId: String?,
		val className: String,
		val currentLevel: Int,
		val xpNeeded: Float,
		val targetLevel: Int,
		val floors: List<FloorEstimate>,
		val modsLabel: String
	)

	@JvmRecord
	data class ClassRuns(val classId: String, val className: String, val currentLevel: Int, val selectedRuns: Long)

	@JvmRecord
	data class AverageResult(
		val targetLevel: Int,
		val floorLabel: String,
		val totalRuns: Long,
		val classes: List<ClassRuns>,
		val modsLabel: String,
		val timedOut: Boolean
	)

	@JvmStatic
	fun calculate(data: DungeonSnapshot, query: ClassLevelQuery.Parsed?): Result? {
		if (query == null || query.classAverage) return null
		return calculateForClass(data, query.classId, query.displayName, query.targetLevel)
	}

	/**
	 * Adjectils-style equal-runs planner: each run selects the class with the most XP still needed;
	 * that class gains full XP/run, others gain [OFF_CLASS_RATE]. Stops when every class
	 * has reached the XP for `targetLevel` (Hypixel CA 50 requires all five at 50).
	 */
	@JvmStatic
	fun calculateAverage(data: DungeonSnapshot, targetLevel: Int): AverageResult {
		DungeonXpData.ensureLoaded()
		val target = max(1, min(50, targetLevel))
		val mods = modsLabel(data, null)
		val targetXp = CataXpMath.xpForLevel(target)

		if (data.classes.isEmpty()) return AverageResult(target, "-", 0L, emptyList(), mods, false)

		val floor = pickBestFloor(data)
			?: return AverageResult(target, "-", 0L, classRuns(data) { 0L }, mods, false)

		val remaining = LinkedHashMap<String, Double>()
		val selected = LinkedHashMap<String, Long>()
		val rates = floor.ratesByClass
		var alreadyDone = true
		for (clazz in data.classes) {
			val left = max(0.0, (targetXp - clazz.xp).toDouble())
			remaining[clazz.id] = left
			selected[clazz.id] = 0L
			if (left > 0.0) alreadyDone = false
		}

		if (alreadyDone) return AverageResult(target, floor.label, 0L, classRuns(data) { 0L }, mods, false)

		var total = 0L
		var timedOut = false
		while (remaining.values.any { it > 0.0 }) {
			if (total >= RTCA_MAX_RUNS) {
				timedOut = true
				break
			}
			total++

			var neediest: String? = null
			var mostLeft = -1.0
			for ((id, left) in remaining) {
				if (left > mostLeft) {
					mostLeft = left
					neediest = id
				}
			}
			if (neediest == null) break

			for (entry in remaining.entries) {
				val rate = rates[entry.key] ?: floor.fallbackRate
				val gain = if (entry.key == neediest) rate else rate * OFF_CLASS_RATE
				entry.setValue(entry.value - gain)
			}
			selected[neediest] = (selected[neediest] ?: 0L) + 1L
		}

		return AverageResult(target, floor.label, total, classRuns(data) { selected[it] ?: 0L }, mods, timedOut)
	}

	private inline fun classRuns(data: DungeonSnapshot, runs: (String) -> Long): List<ClassRuns> =
		java.util.List.copyOf(data.classes.map { clazz ->
			ClassRuns(clazz.id, ClassLevelQuery.displayName(clazz.id), clazz.level, runs(clazz.id))
		})

	private fun calculateForClass(data: DungeonSnapshot, classId: String?, className: String, targetLevel: Int): Result {
		DungeonXpData.ensureLoaded()
		val clazz = findClass(data, classId)
		val currentLevel = clazz?.level ?: 0
		val currentXp = clazz?.xp ?: 0f
		val target = max(1, min(CataXpMath.maxLevel(), targetLevel))
		val needed = CataXpMath.xpNeeded(currentXp, target)
		val mods = modsLabel(data, classId)
		if (needed <= 0f) return Result(classId, className, currentLevel, 0f, target, emptyList(), mods)

		val estimates = ArrayList<FloorEstimate>()
		for (floor in availableFloors(data)) {
			val comps = floorCompletions(data, floor)
			val xpPerRun = max(1L, Math.round(classXpPerRun(
				floor.baseXp,
				data.hecatombLevel,
				data.scarfBonus,
				data.catacombsGraduateBonus,
				data.classEssenceBonus(classId),
				data.mayorFactor,
				0.0
			)))
			val runs = ceil(needed / xpPerRun.toDouble()).toLong()
			estimates.add(FloorEstimate(floor.id, floor.label, floor.master, xpPerRun, runs, comps))
		}

		val top = estimates
			.sortedWith(compareByDescending<FloorEstimate> { it.xpPerRun }.thenBy { it.label })
			.take(5)
		return Result(classId, className, currentLevel, needed, target, java.util.List.copyOf(top), mods)
	}

	/**
	 * Class XP for an S+ run while playing that class (full rate), Adjectils-style:
	 * `base * (1 + hecClass + scarf + graduate + essence + (global-1)) * min(1.5, mayor)`.
	 * Expert Ring and Experienced Floor do not affect class XP in this model.
	 */
	@JvmStatic
	fun classXpPerRun(
		baseXp: Double,
		hecatombLevel: Int,
		scarfBonus: Double,
		graduateBonus: Double,
		essenceBonus: Double,
		mayorXpFactor: Double,
		globalBoost: Double
	): Double {
		val hec = 2.0 * DungeonXpData.hecatombBonus(hecatombLevel)
		val bonus = 1.0 +
			hec +
			max(0.0, scarfBonus) +
			max(0.0, graduateBonus) +
			max(0.0, essenceBonus) +
			max(0.0, globalBoost)
		val mayor = if (mayorXpFactor > 0) min(CLASS_MAYOR_CAP, mayorXpFactor) else 1.0
		return baseXp * bonus * mayor
	}

	private data class FloorPick(val label: String, val ratesByClass: Map<String, Double>, val fallbackRate: Double)

	private fun pickBestFloor(data: DungeonSnapshot): FloorPick? {
		var best: FloorPick? = null
		var bestMean = -1.0

		for (floor in availableFloors(data)) {
			val rates = LinkedHashMap<String, Double>()
			var sum = 0.0
			var n = 0
			for (clazz in data.classes) {
				val rate = max(1.0, classXpPerRun(
					floor.baseXp,
					data.hecatombLevel,
					data.scarfBonus,
					data.catacombsGraduateBonus,
					data.classEssenceBonus(clazz.id),
					data.mayorFactor,
					0.0
				))
				rates[clazz.id] = rate
				sum += rate
				n++
			}
			if (n == 0) continue
			val mean = sum / n
			if (mean > bestMean) {
				bestMean = mean
				best = FloorPick(floor.label, rates, mean)
			}
		}
		return best
	}

	// Floors the player can run: cata level, master unlock and previous-floor chain
	private fun availableFloors(data: DungeonSnapshot): List<DungeonXpData.FloorDef> {
		val cataLevel = data.cataLevel
		val masterUnlocked = completions(data.normal, "7") > 0L
		return DungeonXpData.floors().filter { floor ->
			cataLevel >= floor.unlockCata && (!floor.master || masterUnlocked) && chainUnlocked(data, floor)
		}
	}

	private fun modsLabel(data: DungeonSnapshot, classId: String?): String {
		val bits = ArrayList<String>()
		if (data.hecatombLevel > 0) bits.add("Hec " + data.hecatombLevel)
		if (data.scarfBonus > 0) bits.add("Scarf +" + Math.round(data.scarfBonus * 100) + "%")
		if (data.catacombsGraduateBonus > 0) bits.add("Grad +" + Math.round(data.catacombsGraduateBonus * 100) + "%")
		if (classId != null) {
			val essence = data.classEssenceBonus(classId)
			if (essence > 0) bits.add("Essence +" + Math.round(essence * 100) + "%")
		}
		val mayorName = data.mayorName
		if (!mayorName.isNullOrBlank()) bits.add(if (data.mayorFactor > 1.0) mayorName else "$mayorName (no XP buff)")
		return bits.joinToString(" · ")
	}

	private fun findClass(data: DungeonSnapshot, classId: String?): DungeonSnapshot.ClassEntry? =
		data.classes.firstOrNull { it.id.equals(classId, ignoreCase = true) }

	private fun floorCompletions(data: DungeonSnapshot, floor: DungeonXpData.FloorDef): Long =
		completions(if (floor.master) data.master else data.normal, floor.floor.toString())

	private fun chainUnlocked(data: DungeonSnapshot, floor: DungeonXpData.FloorDef): Boolean {
		if (floorCompletions(data, floor) > 0L) return true
		if (floor.master) {
			if (floor.floor <= 1) return completions(data.normal, "7") > 0L
			return completions(data.master, (floor.floor - 1).toString()) > 0L
		}
		if (floor.floor <= 1) return true
		return completions(data.normal, (floor.floor - 1).toString()) > 0L
	}

	private fun completions(mode: DungeonSnapshot.ModeStats, id: String): Long =
		mode.floors.firstOrNull { it.id == id }?.completions ?: 0L
}
