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

object CataXpCalculator {
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
	data class Result(val xpNeeded: Float, val targetLevel: Int, val floors: List<FloorEstimate>, val mayorLabel: String)

	@JvmStatic
	fun calculate(data: DungeonSnapshot, targetLevel: Int): Result {
		DungeonXpData.ensureLoaded()
		val target = max(1, min(CataXpMath.maxLevel(), targetLevel))
		val needed = CataXpMath.xpNeeded(data.cataXp(), target)
		if (needed <= 0f) return Result(0f, target, emptyList(), mayorDisplay(data))

		val cataLevel = data.cataLevel()
		val masterUnlocked = completions(data.normal(), "7") > 0L
		val estimates = ArrayList<FloorEstimate>()

		for (floor in DungeonXpData.floors()) {
			if (cataLevel < floor.unlockCata) continue
			if (floor.master && !masterUnlocked) continue
			if (!chainUnlocked(data, floor)) continue
			val comps = completions(if (floor.master) data.master() else data.normal(), floor.floor.toString())
			val xpPerRun = max(1L, Math.round(cataXpPerRun(
				floor.baseXp,
				comps,
				data.expertRing(),
				data.hecatombLevel(),
				data.mayorFactor(),
				0.0
			)))
			val runs = ceil(needed / xpPerRun.toDouble()).toLong()
			estimates.add(FloorEstimate(floor.id, floor.label, floor.master, xpPerRun, runs, comps))
		}

		val top = estimates
			.sortedWith(compareByDescending<FloorEstimate> { it.xpPerRun }.thenBy { it.label })
			.take(5)
		return Result(needed, target, java.util.List.copyOf(top), mayorDisplay(data))
	}

	/**
	 * SkyHelper-style Catacombs XP for an S+ run:
	 * `base * (1 + ring + hecatomb) * (1 + experienced?) * mayor * (1 + global)`.
	 * Experienced Floor Bonus (+50%) applies at 5+ completions on that floor.
	 */
	@JvmStatic
	fun cataXpPerRun(
		baseXp: Double,
		completions: Long,
		expertRing: Boolean,
		hecatombLevel: Int,
		mayorXpFactor: Double,
		globalBoost: Double
	): Double {
		val ring = if (expertRing) DungeonXpData.expertRingBonus() else 0.0
		val hec = DungeonXpData.hecatombBonus(hecatombLevel)
		val bonus = 1.0 + ring + hec
		val experienced = if (DungeonXpData.isExperiencedFloor(completions)) 1.0 + DungeonXpData.experiencedFloorBonus() else 1.0
		val mayor = if (mayorXpFactor > 0) mayorXpFactor else 1.0
		return baseXp * bonus * experienced * mayor * (1.0 + globalBoost)
	}

	private fun mayorDisplay(data: DungeonSnapshot): String {
		val name = data.mayorName()
		if (name.isNullOrBlank()) return ""
		return if (data.mayorFactor() <= 1.0) "$name (no XP buff)" else name
	}

	private fun chainUnlocked(data: DungeonSnapshot, floor: DungeonXpData.FloorDef): Boolean {
		val own = completions(if (floor.master) data.master() else data.normal(), floor.floor.toString())
		if (own > 0L) return true
		if (floor.master) {
			if (floor.floor <= 1) return completions(data.normal(), "7") > 0L
			return completions(data.master(), (floor.floor - 1).toString()) > 0L
		}
		if (floor.floor <= 1) return true
		return completions(data.normal(), (floor.floor - 1).toString()) > 0L
	}

	private fun completions(mode: DungeonSnapshot.ModeStats, id: String): Long =
		mode.floors().firstOrNull { it.id() == id }?.completions() ?: 0L
}
