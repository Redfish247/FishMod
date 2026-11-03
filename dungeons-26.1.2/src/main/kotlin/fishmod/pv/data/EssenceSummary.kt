/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import java.util.function.Function

/** Per-shop totals for the Essence overview. Perks without NEU cost data are counted, not guessed. */
@JvmRecord
data class EssenceSummary(
	val loaded: Boolean,
	val perksMaxed: Int,
	val perkCount: Int,
	val levels: Int,
	val maxLevels: Int,
	val toMax: Long,
	val unknownCostPerks: Int
) {
	fun complete(): Boolean = loaded && perkCount > 0 && perksMaxed == perkCount

	companion object {
		@JvmStatic
		fun of(shop: DungeonSnapshot.EssenceShop?, costs: Function<String, IntArray?>?): EssenceSummary {
			val perks = shop?.perks
			if (perks.isNullOrEmpty()) {
				return EssenceSummary(false, 0, 0, 0, 0, 0L, 0)
			}
			var maxed = 0
			var levels = 0
			var maxLevels = 0
			var toMax = 0L
			var unknown = 0
			for (perk in perks) {
				levels += maxOf(0, perk.level)
				maxLevels += maxOf(0, perk.maxLevel)
				if (perk.maxed()) {
					maxed++
					continue
				}
				val tiers = costs?.apply(perk.id)
				if (tiers == null || tiers.size < perk.maxLevel) {
					unknown++
					continue
				}
				for (i in maxOf(0, perk.level) until perk.maxLevel) {
					toMax += tiers[i]
				}
			}
			return EssenceSummary(true, maxed, perks.size, levels, maxLevels, toMax, unknown)
		}
	}
}
