/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons

import com.google.gson.JsonObject
import fishmod.pv.data.Leveling
import fishmod.pv.data.RepoData
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object CataXpMath {
	/**
	 * Soft / stat cap. Levels above this are cosmetic mastery levels (200M XP each in the
	 * NEU table) and do not grant dungeon item stat bonuses.
	 */
	const val SOFT_CAP = 50

	/** Highest level the catacombs XP table supports (includes mastery steps). */
	@JvmStatic
	fun maxLevel(): Int {
		val table = RepoData.catacombsXp()
		if (table == null || table.isEmpty) return SOFT_CAP
		return table.size()
	}

	/** Catacombs / class progress using the full XP table (not soft-capped at 50). */
	@JvmStatic
	fun progress(xp: Float): Leveling.Progress = Leveling.getLevel(RepoData.catacombsXp(), xp, maxLevel(), false)

	/**
	 * Total XP required to reach `level`. Levels above [SOFT_CAP] use overflow
	 * steps from the repo table (typically 200M XP each).
	 */
	@JvmStatic
	fun xpForLevel(level: Int): Float {
		if (level <= 0) return 0f
		return Leveling.xpRequiredForLevel(RepoData.catacombsXp(), min(level, maxLevel()), false)
	}

	@JvmStatic
	fun xpNeeded(currentXp: Float, targetLevel: Int): Float = max(0f, xpForLevel(targetLevel) - max(0f, currentXp))

	@JvmStatic
	fun mayorName(electionRoot: JsonObject?): String {
		val mayor = electionRoot?.let { Leveling.obj(it.get("mayor")) } ?: return ""
		return primitiveString(mayor, "name") ?: primitiveString(mayor, "key") ?: ""
	}

	@JvmStatic
	fun mayorXpFactor(electionRoot: JsonObject?): Double {
		val name = mayorName(electionRoot).lowercase(Locale.ROOT)
		val mayor = electionRoot?.let { Leveling.obj(it.get("mayor")) }
		val key = mayor?.let { primitiveString(it, "key") }?.lowercase(Locale.ROOT) ?: ""
		val id = key.ifBlank { name }
		if (id.contains("derpy")) return 1.5
		if (id.contains("aura")) return 1.59
		val perks = mayor?.get("perks")?.takeIf { it.isJsonArray }?.asJsonArray ?: return 1.0
		for (el in perks) {
			if (!el.isJsonObject) continue
			val perk = el.asJsonObject
			val desc = primitiveString(perk, "description")?.lowercase(Locale.ROOT) ?: ""
			val perkName = primitiveString(perk, "name")?.lowercase(Locale.ROOT) ?: ""
			val xpPerk = (desc.contains("catacombs") && desc.contains("experi")) ||
				(desc.contains("dungeon") && desc.contains("experi")) ||
				perkName.contains("derpy") ||
				perkName.contains("aura")
			if (!xpPerk) continue
			if (desc.contains("59%") || id.contains("aura") || perkName.contains("aura")) return 1.59
			if (desc.contains("50%") || id.contains("derpy") || perkName.contains("derpy")) return 1.5
		}
		return 1.0
	}

	private fun primitiveString(obj: JsonObject, key: String): String? {
		val el = obj.get(key)
		return if (el != null && el.isJsonPrimitive) el.asString else null
	}
}
