/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.overview

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.PlayerStatsSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.SkyBlockStats
import kotlin.math.abs

/** Calculated stat rows for skill overviews, with what the estimate leaves out spelled out on hover. */
object SkillStatRows {
	@JvmStatic
	fun row(stats: PlayerStatsSnapshot?, id: String, exclusions: List<String>): SummaryCards.Stat {
		val style = SkyBlockStats.stat(id)
		val entry = find(stats, id)
		val present = entry != null && entry.present()
		val value = if (entry != null && present) format(entry.value.asDouble) else "-"
		val tip = ArrayList(SkyBlockStats.tooltipLines(id, value))
		tip.add(PvTooltip.Line.blank())
		if (present) {
			for (line in exclusions) {
				tip.add(PvTooltip.Line.meta(line))
			}
		} else {
			tip.add(PvTooltip.Line.meta("Not in the API"))
		}
		return SummaryCards.Stat(style.fullName, value, style.color, if (present) PvDraw.COLOR_WHITE else PvDraw.COLOR_MUTED, tip)
	}

	private fun find(stats: PlayerStatsSnapshot?, id: String): PlayerStatsSnapshot.Entry? =
		stats?.entries?.firstOrNull { id == it.id }

	private fun format(value: Double): String {
		if (abs(value - Math.rint(value)) < 0.05) {
			return FormatUtil.commas(Math.round(value))
		}
		return FormatUtil.oneDecimal(value)
	}
}
