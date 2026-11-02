/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.weight

import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip

class WeightBreakdown(
	@get:JvmName("system") val system: WeightSystem,
	@get:JvmName("base") val base: Double,
	@get:JvmName("overflow") val overflow: Double,
	@get:JvmName("stageOrRank") val stageOrRank: String,
	categories: List<Category>
) {
	@get:JvmName("categories")
	val categories: List<Category> = java.util.List.copyOf(categories)

	@JvmRecord
	data class Line(val label: String, val value: Double, val overflow: Double) {
		fun total(): Double = value + overflow
	}

	@JvmRecord
	data class Category(val name: String, val value: Double, val overflow: Double, val lines: List<Line>) {
		fun total(): Double = value + overflow
	}

	fun total(): Double = base + overflow

	fun tooltipLines(): List<String> =
		tooltipStyledLines().map { line -> line.spans().joinToString("") { it.text() } }

	fun tooltipStyledLines(): List<PvTooltip.Line> {
		val lines = ArrayList<PvTooltip.Line>()
		lines.add(PvTooltip.Line.bold(system.display + " Weight", PvDraw.COLOR_ACCENT))
		lines.add(PvTooltip.Line(listOf(
			PvTooltip.Span.of("Total: ", PvDraw.COLOR_MUTED),
			PvTooltip.Span.bold(format(total()), COLOR_WEIGHT),
			PvTooltip.Span.of(" (", PvDraw.COLOR_MUTED),
			PvTooltip.Span.of(format(base), PvDraw.COLOR_TEXT),
			PvTooltip.Span.of(" without Overflow)", PvDraw.COLOR_MUTED)
		)))
		val kind = if (system == WeightSystem.SENITHER) "Stage" else "Rank"
		lines.add(PvTooltip.Line(listOf(
			PvTooltip.Span.of("$kind: ", PvDraw.COLOR_MUTED),
			PvTooltip.Span.bold(stageOrRank, WeightStages.colorFor(stageOrRank))
		)))
		for (category in categories) {
			lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED))
			val catColor = when (category.name) {
				"Skills" -> COLOR_SKILLS
				"Slayers" -> COLOR_SLAYERS
				"Dungeons" -> COLOR_DUNGEONS
				else -> PvDraw.COLOR_ACCENT
			}
			val header = ArrayList<PvTooltip.Span>()
			header.add(PvTooltip.Span.bold(category.name + ": ", catColor))
			header.add(PvTooltip.Span.bold(format(category.total()), COLOR_WEIGHT))
			if (category.overflow > 0.05) {
				header.add(PvTooltip.Span.of(" (+" + format(category.overflow) + ")", COLOR_OVERFLOW))
			}
			lines.add(PvTooltip.Line(header))
			for (line in category.lines) {
				val row = ArrayList<PvTooltip.Span>()
				row.add(PvTooltip.Span.of("  " + line.label + ": ", PvDraw.COLOR_MUTED))
				row.add(PvTooltip.Span.of(format(line.total()), PvDraw.COLOR_TEXT))
				if (line.overflow > 0.05) {
					row.add(PvTooltip.Span.of(" (+" + format(line.overflow) + ")", COLOR_OVERFLOW))
				}
				lines.add(PvTooltip.Line(row))
			}
		}
		lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED))
		lines.add(PvTooltip.Line.of("Click to switch to " + system.other().display, PvDraw.COLOR_MUTED))
		return lines
	}

	companion object {
		private const val COLOR_WEIGHT = 0xFF55FFFF.toInt()
		private const val COLOR_OVERFLOW = 0xFF7AD7FF.toInt()
		private const val COLOR_SKILLS = 0xFF55FF55.toInt()
		private const val COLOR_SLAYERS = 0xFFFF5555.toInt()
		private const val COLOR_DUNGEONS = 0xFFAA55FF.toInt()

		@JvmStatic
		fun empty(system: WeightSystem): WeightBreakdown = WeightBreakdown(system, 0.0, 0.0, "-", emptyList())

		private fun format(value: Double): String = FormatUtil.weight(value)
	}
}
