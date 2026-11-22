/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.weight

import java.util.Locale

object WeightStages {
	@JvmStatic
	fun senitherStage(total: Double): String = when {
		total >= 30_000 -> "No Life"
		total >= 15_000 -> "End Game"
		total >= 10_000 -> "Early End"
		total >= 7_000 -> "Late Game"
		total >= 2_000 -> "Mid Game"
		else -> "Early Game"
	}

	@JvmStatic
	fun lilyRank(total: Double): String = when {
		total >= 50_000 -> "Prestigious"
		total >= 44_500 -> "Grandmaster"
		total >= 37_000 -> "Diamond"
		total >= 24_500 -> "Platinum"
		total >= 17_900 -> "Gold"
		total >= 13_425 -> "Silver"
		else -> "Bronze"
	}

	@JvmStatic
	fun colorFor(stageOrRank: String?): Int {
		if (stageOrRank.isNullOrBlank()) return 0xFF9A9AAC.toInt()
		return when (stageOrRank.lowercase(Locale.ROOT)) {
			"bronze" -> 0xFFCD7F32
			"silver" -> 0xFFC0C0C0
			"gold" -> 0xFFFFAA00
			"platinum" -> 0xFFE5E4E2
			"diamond" -> 0xFF55FFFF
			"grandmaster" -> 0xFFFF55FF
			"prestigious" -> 0xFFFF5555
			"early game" -> 0xFFAAAAAA
			"mid game" -> 0xFF55FF55
			"late game" -> 0xFF5555FF
			"early end" -> 0xFFAA00AA
			"end game" -> 0xFFFFAA00
			"no life" -> 0xFFFF5555
			else -> 0xFFFFAA00
		}.toInt()
	}
}
