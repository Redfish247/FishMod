/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page

import fishmod.pv.data.FarmingFortuneEstimate
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.data.PlayerStatsSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.SkyBlockStats
import fishmod.pv.gui.overview.SummaryCards
import fishmod.pv.networth.InventoryDecoder
import java.util.Locale
import kotlin.math.abs

/** Garden overview Farming Fortune row built from the best farming setup on the profile. */
object FarmingFortuneTip {
	const val LABEL = "Farming Fortune (est.)"
	private val FORMAT_CODE = Regex("§.")

	/** Null until inventories are parsed; callers fall back to the worn-gear stat. */
	@JvmStatic
	fun row(stats: PlayerStatsSnapshot?, snap: GardenSnapshot?): SummaryCards.Stat? {
		val est = stats?.farmingFortune ?: return null
		val garden = FarmingFortuneEstimate.Garden.of(snap)
		val style = SkyBlockStats.stat("farming_fortune")
		val value = format(est.general(garden))
		return SummaryCards.Stat(LABEL, value, style.color, PvDraw.COLOR_WHITE, tooltip(est, garden, value))
	}

	@JvmStatic
	fun tooltip(est: FarmingFortuneEstimate, garden: FarmingFortuneEstimate.Garden, value: String): List<PvTooltip.Line> {
		val tip = ArrayList(SkyBlockStats.tooltipLines("farming_fortune", value))
		tip.add(PvTooltip.Line.meta("Estimate from the best farming setup"))
		tip.add(PvTooltip.Line.meta("on the profile, not a live stat."))
		tip.add(PvTooltip.Line.blank())
		tip.add(row("Permanent bonuses", est.permanentTotal(garden)))
		tip.add(row("Armour and set bonus", est.armourTotal()))
		tip.add(row("Equipment", est.equipmentTotal()))
		val pet = est.bestPet(null, garden)
		tip.add(row(if (pet == null) "Pet" else "Pet: " + pet.pet.displayName, est.petTotal(null, garden)))
		tip.add(PvTooltip.Line.divider())
		tip.add(PvTooltip.Line.row("General", PvDraw.COLOR_TEXT, value, PvDraw.COLOR_WHITE))
		tip.add(PvTooltip.Line.blank())
		tip.add(PvTooltip.Line.meta("Excludes tools and crop-specific"))
		tip.add(PvTooltip.Line.meta("fortune." + (if (garden.loaded) "" else " Garden not loaded yet.")))
		tip.add(PvTooltip.Line.of("Click for the full breakdown", PvDraw.COLOR_ACCENT))
		return tip
	}

	private fun row(label: String, value: Double): PvTooltip.Line =
		PvTooltip.Line.row(label, PvDraw.COLOR_MUTED, "+" + format(value), PvDraw.COLOR_GOLD)

	@JvmStatic
	fun name(stack: InventoryDecoder.Stack): String {
		val raw = stack.displayName?.replace(FORMAT_CODE, "")?.trim() ?: ""
		return raw.ifEmpty { stack.id }
	}

	@JvmStatic
	fun format(value: Double): String {
		if (abs(value - Math.rint(value)) < 0.005) {
			return FormatUtil.commas(Math.round(value))
		}
		val tenths = Math.round(value * 10.0) / 10.0
		if (abs(value - tenths) < 0.005) {
			return FormatUtil.oneDecimal(value)
		}
		return String.format(Locale.ROOT, "%.2f", value)
	}
}
