/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object ClassLevelQuery {
	@JvmRecord
	data class Parsed(val classId: String?, val displayName: String, val targetLevel: Int, val classAverage: Boolean) {
		companion object {
			@JvmStatic
			fun ofClass(classId: String, targetLevel: Int): Parsed =
				Parsed(classId, ClassLevelQuery.displayName(classId), targetLevel, false)

			@JvmStatic
			fun ofAverage(targetLevel: Int): Parsed = Parsed(null, "CA", targetLevel, true)
		}
	}

	/** Class targets allow overflow levels (e.g. `M55`); CA stays 1–2 digits. */
	private val CLASS_THEN_LEVEL = Regex("^([a-z]+)\\s*(\\d{1,3})$")
	private val LEVEL_THEN_CLASS = Regex("^(\\d{1,3})\\s*([a-z]+)$")

	/** Longest CA phrases first so `class average` wins over `ca`/`avg`. */
	private val CA_THEN_LEVEL = Regex(
		"^(?:class\\s*average|class\\s*avg|classaverage|classavg|average|avg|ca)\\s*(\\d{1,2})$"
	)
	private val LEVEL_THEN_CA = Regex(
		"^(\\d{1,2})\\s*(?:class\\s*average|class\\s*avg|classaverage|classavg|average|avg|ca)$"
	)
	private val WHITESPACE = Regex("\\s+")

	/** Longer aliases first so `berserker` wins over `bers`/`b`. */
	private val ALIASES: Map<String, String> = linkedMapOf(
		"mage" to "mage",
		"m" to "mage",
		"berserker" to "berserk",
		"berzerker" to "berserk",
		"berserk" to "berserk",
		"berz" to "berserk",
		"bers" to "berserk",
		"b" to "berserk",
		"tank" to "tank",
		"t" to "tank",
		"healer" to "healer",
		"h" to "healer",
		"archer" to "archer",
		"arch" to "archer",
		"a" to "archer"
	)

	private val AVERAGE_ALIASES = setOf("ca", "avg", "average", "classavg", "classaverage")

	@JvmStatic
	fun parse(raw: String?): Parsed? {
		if (raw.isNullOrBlank()) return null
		val cleaned = raw.trim().lowercase(Locale.ROOT).replace(WHITESPACE, " ")

		parseAverage(cleaned)?.let { return it }

		val alias: String
		val level: Int
		val classFirst = CLASS_THEN_LEVEL.matchEntire(cleaned)
		if (classFirst != null) {
			alias = classFirst.groupValues[1]
			level = classFirst.groupValues[2].toInt()
		} else {
			val levelFirst = LEVEL_THEN_CLASS.matchEntire(cleaned) ?: return null
			level = levelFirst.groupValues[1].toInt()
			alias = levelFirst.groupValues[2]
		}
		if (alias in AVERAGE_ALIASES) return Parsed.ofAverage(clampAverageLevel(level))
		val classId = ALIASES[alias] ?: return null
		return Parsed.ofClass(classId, clampClassLevel(level))
	}

	private fun parseAverage(cleaned: String): Parsed? {
		val match = CA_THEN_LEVEL.matchEntire(cleaned) ?: LEVEL_THEN_CA.matchEntire(cleaned) ?: return null
		return Parsed.ofAverage(clampAverageLevel(match.groupValues[1].toInt()))
	}

	/** Class average RTCA stays capped at soft max (50). */
	private fun clampAverageLevel(level: Int): Int = max(1, min(CataXpMath.SOFT_CAP, level))

	/** Individual class targets may use overflow levels from the XP table. */
	private fun clampClassLevel(level: Int): Int = max(1, min(CataXpMath.maxLevel(), level))

	@JvmStatic
	fun displayName(classId: String?): String {
		if (classId.isNullOrBlank()) return ""
		return classId[0].uppercaseChar() + classId.substring(1)
	}
}
