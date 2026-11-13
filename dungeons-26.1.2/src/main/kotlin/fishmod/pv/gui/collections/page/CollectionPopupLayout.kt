/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections.page

import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.collections.CollectionsUi

/**
 * Collection popup geometry. Fitted inside the board it covers with a margin, and sized from the
 * collection's widest content and busiest tier so switching tiers never moves anything.
 */
internal data class CollectionPopupLayout(
	val x: Int, val y: Int, val w: Int, val h: Int,
	val innerX: Int, val innerW: Int,
	val headerY: Int, val statsY: Int, val barY: Int, val nextY: Int,
	val tiersY: Int, val trackY: Int,
	val selectedY: Int, val selectedBarY: Int,
	val columnsY: Int, val bottom: Int,
	val rewardsW: Int, val recipeX: Int, val recipeW: Int,
) {
	data class Content(
		val tierCount: Int, val maxRewards: Int, val recipes: Boolean,
		val headerW: Int, val statsW: Int, val rewardW: Int, val recipeTitleW: Int,
	)

	fun hasTiersHeader(): Boolean = tiersY >= 0

	fun nextInline(): Boolean = nextY < 0

	fun columnsBodyY(lineH: Int): Int = columnsY + lineH + 3

	companion object {
		const val MARGIN = 10
		const val PAD = 8
		const val MAX_W = 460
		const val MIN_INNER_W = 240
		const val HEADER_H = 16
		const val CELL = 18
		const val ROW_H = 18
		const val COL_GAP = 12
		const val RECIPE_GRID_W = CELL * 3 + 16 + CELL
		const val RECIPE_MAX_W = 180
		const val MIN_TRACK_GAP = 12
		private const val GAP = 8
		private const val TIGHT_GAP = 5

		fun recipeBodyH(lineH: Int, recipes: Boolean): Int = if (recipes) lineH + 4 + CELL * 3 else lineH + 2

		fun compute(px: Int, py: Int, pw: Int, ph: Int, lineH: Int, c: Content): CollectionPopupLayout {
			val maxW = maxOf(120, pw - MARGIN * 2)
			val maxH = maxOf(100, ph - MARGIN * 2)
			var recipeW = if (c.recipes) maxOf(RECIPE_GRID_W, minOf(RECIPE_MAX_W, c.recipeTitleW)) else RECIPE_GRID_W
			val trackW = c.tierCount * CollectionsUi.SLOT + maxOf(0, c.tierCount - 1) * MIN_TRACK_GAP
			val wantInner = maxOf(
				maxOf(MIN_INNER_W, trackW),
				maxOf(maxOf(c.headerW, c.statsW), c.rewardW + COL_GAP + recipeW),
			)
			val w = minOf(minOf(MAX_W, maxW), wantInner + PAD * 2)
			val innerW = w - PAD * 2
			recipeW = minOf(recipeW, maxOf(RECIPE_GRID_W, innerW - COL_GAP - 80))
			val rewardsW = maxOf(40, innerW - COL_GAP - recipeW)
			val nextInline = c.statsW <= innerW

			val body = maxOf(ROW_H, maxOf(c.maxRewards * ROW_H, recipeBodyH(lineH, c.recipes)))
			val tight = height(lineH, nextInline, false, body) > maxH
			val h = minOf(maxH, height(lineH, nextInline, tight, body))
			val x = px + (pw - w) / 2
			val y = py + (ph - h) / 2
			val gap = if (tight) TIGHT_GAP else GAP

			var cy = y + PAD
			val headerY = cy
			cy += HEADER_H + 4
			val statsY = cy
			cy += lineH + 3
			val barY = cy
			cy += PvDraw.BAR_HEIGHT
			var nextY = -1
			if (!nextInline) {
				nextY = cy + 3
				cy = nextY + lineH
			}
			cy += gap
			var tiersY = -1
			if (!tight) {
				tiersY = cy
				cy += lineH + 3
			}
			val trackY = cy
			cy += CollectionsUi.SLOT + 2 + lineH + gap
			val selectedY = cy
			cy += lineH + 3
			val selectedBarY = cy
			cy += lineH + 6
			val innerX = x + PAD
			return CollectionPopupLayout(
				x, y, w, h, innerX, innerW, headerY, statsY, barY, nextY, tiersY, trackY, selectedY, selectedBarY,
				cy, y + h - PAD, rewardsW, innerX + innerW - recipeW, recipeW,
			)
		}

		private fun height(lineH: Int, nextInline: Boolean, tight: Boolean, body: Int): Int {
			val gap = if (tight) TIGHT_GAP else GAP
			return (PAD + HEADER_H + 4
				+ lineH + 3 + PvDraw.BAR_HEIGHT + (if (nextInline) 0 else 3 + lineH) + gap
				+ (if (tight) 0 else lineH + 3) + CollectionsUi.SLOT + 2 + lineH + gap
				+ lineH + 3 + lineH + 6
				+ lineH + 3 + body + PAD)
		}
	}
}
