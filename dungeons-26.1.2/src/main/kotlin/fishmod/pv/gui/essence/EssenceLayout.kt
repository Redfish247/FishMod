/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.essence

/** Geometry for Home → Essence. Kept free of rendering so bounds can be unit tested. */
object EssenceLayout {
	const val PAD = 6
	const val GAP = 6
	const val SELECTOR_MIN_W = 104
	const val SELECTOR_ROW_MAX = 18
	const val SELECTOR_ROW = 16
	const val SELECTOR_ROW_MIN = 14
	const val GROUP_GAP = 2
	const val DIVIDER_H = 5
	const val DETAIL_HEADER_H = 18
	const val STAT_ROW = 12
	const val BAR_H = 6
	const val PERK_ROW = 20
	const val PERK_ROW_MIN = 18
	const val PERK_COL_MIN = 140
	const val PERK_COL_GAP = 10

	@JvmRecord
	data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
		fun contains(mx: Double, my: Double): Boolean = mx >= x && mx < x + w && my >= y && my < y + h

		fun right(): Int = x + w

		fun bottom(): Int = y + h
	}

	/**
	 * Selector rows. `rowY` and `headerY` are offsets from `list.y()` before
	 * scrolling; `headerY` is empty when there is only room for dividers between groups.
	 */
	@JvmRecord
	data class Selector(
		val panel: Rect,
		val list: Rect,
		val headers: Boolean,
		val rowH: Int,
		val headerY: IntArray,
		val dividerY: IntArray,
		val rowY: IntArray,
		val contentH: Int,
	) {
		fun maxScroll(): Int = maxOf(0, contentH - list.h)
	}

	/** Column-major perk grid inside the detail list area. */
	@JvmRecord
	data class Perks(val list: Rect, val cols: Int, val rows: Int, val rowH: Int, val colW: Int, val contentH: Int) {
		fun maxScroll(): Int = maxOf(0, contentH - list.h)

		fun col(index: Int): Int = if (rows <= 0) 0 else index / rows

		fun row(index: Int): Int = if (rows <= 0) 0 else index % rows
	}

	@JvmStatic
	fun selectorWidth(bodyW: Int, widestRow: Int): Int {
		val wanted = widestRow + PAD * 2
		val cap = maxOf(SELECTOR_MIN_W, bodyW * 34 / 100)
		return maxOf(SELECTOR_MIN_W, minOf(cap, wanted))
	}

	@JvmStatic
	fun detail(body: Rect, selectorW: Int): Rect {
		val x = body.x + selectorW + GAP
		return Rect(x, body.y, maxOf(1, body.right() - x), body.h)
	}

	@JvmStatic
	fun selector(panel: Rect, lineHeight: Int, groupSizes: List<Int>): Selector {
		val list = Rect(panel.x + PAD, panel.y + PAD, maxOf(1, panel.w - PAD * 2), maxOf(1, panel.h - PAD * 2))
		val groups = groupSizes.size
		val rows = maxOf(1, groupSizes.sum())
		val headerH = lineHeight + 3
		val headerSpace = groups * headerH + maxOf(0, groups - 1) * GROUP_GAP
		val withHeaders = (list.h - headerSpace) / rows
		val headers = withHeaders >= SELECTOR_ROW
		val rowH = if (headers) {
			minOf(SELECTOR_ROW_MAX, withHeaders)
		} else {
			maxOf(SELECTOR_ROW_MIN, minOf(SELECTOR_ROW, (list.h - maxOf(0, groups - 1) * DIVIDER_H) / rows))
		}

		val headerY = IntArray(if (headers) groups else 0)
		val dividerY = IntArray(if (headers) 0 else maxOf(0, groups - 1))
		val rowY = IntArray(rows)
		var y = 0
		var r = 0
		for (gi in 0 until groups) {
			if (headers) {
				if (gi > 0) {
					y += GROUP_GAP
				}
				headerY[gi] = y
				y += headerH
			} else if (gi > 0) {
				dividerY[gi - 1] = y + DIVIDER_H / 2
				y += DIVIDER_H
			}
			repeat(groupSizes[gi]) {
				rowY[r++] = y
				y += rowH
			}
		}
		return Selector(panel, list, headers, rowH, headerY, dividerY, rowY, y)
	}

	/** Top of the perk list: header row, two stat rows, the level bar and the "Perks" section header. */
	@JvmStatic
	fun perkListTop(detail: Rect, lineHeight: Int): Int =
		detail.y + PAD + DETAIL_HEADER_H + 4 + STAT_ROW * 2 + 4 + BAR_H + 6 + lineHeight + 3

	@JvmStatic
	fun perks(detail: Rect, lineHeight: Int, perkCount: Int): Perks {
		val top = perkListTop(detail, lineHeight)
		val list = Rect(
			detail.x + PAD, top,
			maxOf(1, detail.w - PAD * 2), maxOf(1, detail.bottom() - PAD - top),
		)
		val cols = if (list.w >= PERK_COL_MIN * 2 + PERK_COL_GAP) 2 else 1
		val rows = if (perkCount <= 0) 0 else (perkCount + cols - 1) / cols
		val rowH = if (rows == 0) PERK_ROW else maxOf(PERK_ROW_MIN, minOf(PERK_ROW, list.h / rows))
		val colW = (list.w - PERK_COL_GAP * (cols - 1)) / cols
		return Perks(list, cols, rows, rowH, colW, rows * rowH)
	}

	@JvmStatic
	fun clampScroll(value: Int, max: Int): Int = maxOf(0, minOf(maxOf(0, max), value))
}
