/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons

import fishmod.pv.data.RepoData
import kotlin.math.min

object DungeonXpData {
	@JvmRecord
	data class FloorDef(
		val id: String,
		val label: String,
		val master: Boolean,
		val floor: Int,
		val baseXp: Double,
		val unlockCata: Int
	)

	private var floorDefs: List<FloorDef> = emptyList()
	private var hecatombSPlus = doubleArrayOf(0.0)
	private var ringBonus = 0.1
	private var experiencedBonus = 0.5
	private var experiencedFloorMinCompletions = 5

	@Volatile
	private var loaded = false

	@JvmStatic
	fun ensureLoaded() {
		if (loaded) return
		synchronized(this) {
			if (!loaded) {
				load()
				loaded = true
			}
		}
	}

	// Fills the fields before loaded is published so other threads never see a half-loaded table.
	private fun load() {
		val root = RepoData.dungeonsXp() ?: return
		root.get("expert_ring_bonus")?.takeIf { it.isJsonPrimitive }?.let { ringBonus = it.asDouble }
		root.get("experienced_floor_bonus")?.takeIf { it.isJsonPrimitive }?.let { experiencedBonus = it.asDouble }
		root.get("experienced_floor_min_completions")?.takeIf { it.isJsonPrimitive }?.let {
			experiencedFloorMinCompletions = it.asInt
		}
		root.get("hecatomb_s_plus")?.takeIf { it.isJsonArray }?.asJsonArray?.let { arr ->
			hecatombSPlus = DoubleArray(arr.size()) { i -> arr.get(i).asDouble }
		}
		val parsed = ArrayList<FloorDef>()
		root.get("floors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { el ->
			if (!el.isJsonObject) return@forEach
			val o = el.asJsonObject
			val id = if (o.has("id")) o.get("id").asString else ""
			val label = if (o.has("label")) o.get("label").asString else id
			val master = o.has("mode") && "master".equals(o.get("mode").asString, ignoreCase = true)
			val floor = if (o.has("floor")) o.get("floor").asInt else 0
			val base = if (o.has("base")) o.get("base").asDouble else 0.0
			val unlock = if (o.has("unlock_cata")) o.get("unlock_cata").asInt else 0
			parsed.add(FloorDef(id, label, master, floor, base, unlock))
		}
		floorDefs = java.util.List.copyOf(parsed)
	}

	@JvmStatic
	fun floors(): List<FloorDef> {
		ensureLoaded()
		return floorDefs
	}

	@JvmStatic
	fun expertRingBonus(): Double {
		ensureLoaded()
		return ringBonus
	}

	@JvmStatic
	fun experiencedFloorBonus(): Double {
		ensureLoaded()
		return experiencedBonus
	}

	@JvmStatic
	fun isExperiencedFloor(completions: Long): Boolean {
		ensureLoaded()
		return completions >= experiencedFloorMinCompletions
	}

	@JvmStatic
	fun hecatombBonus(level: Int): Double {
		ensureLoaded()
		if (level <= 0 || hecatombSPlus.isEmpty()) return 0.0
		return hecatombSPlus[min(level, hecatombSPlus.size - 1)]
	}
}
