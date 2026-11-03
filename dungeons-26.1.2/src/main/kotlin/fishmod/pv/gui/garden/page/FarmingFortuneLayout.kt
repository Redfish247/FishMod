/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page

/** Positions for the Farming Fortune page, kept free of rendering so the layout can be tested. */
object FarmingFortuneLayout {
	const val CROP_SLOT = 18
	const val CROP_GAP = 2
	const val HERO_H = 22
	const val HERO_CROPS_GAP = 12
	const val HEADER_H = 12
	const val TILE_H = 20
	const val TILE_GAP = 3
	const val SECTION_GAP = 6
	const val COL_GAP = 10
	const val COND_H = 18
	const val COND_GAP = 4
	// Narrowest column that still fits an icon, a short label and a value like "+1,026.5".
	const val MIN_COL_W = 130

	enum class Section { CROP, GEAR, PET, PERMANENT }

	@JvmRecord
	data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
		fun right(): Int = x + w

		fun bottom(): Int = y + h

		fun overlaps(o: Rect): Boolean = x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom()

		fun contains(px: Int, py: Int): Boolean = px >= x && px < right() && py >= y && py < bottom()
	}

	@JvmStatic
	fun cropRowW(count: Int): Int = count * CROP_SLOT + maxOf(0, count - 1) * CROP_GAP

	/** Crop selector beside the hero when the row fits, otherwise wrapped below it. */
	@JvmStatic
	fun cropsBesideHero(w: Int, heroW: Int, count: Int): Boolean = heroW + HERO_CROPS_GAP + cropRowW(count) <= w

	@JvmStatic
	fun cropSlots(x: Int, y: Int, w: Int, heroW: Int, count: Int): List<Rect> {
		if (cropsBesideHero(w, heroW, count)) {
			val sx = x + w - cropRowW(count)
			val sy = y + (HERO_H - CROP_SLOT) / 2
			return List(count) { i -> Rect(sx + i * (CROP_SLOT + CROP_GAP), sy, CROP_SLOT, CROP_SLOT) }
		}
		val perRow = maxOf(1, (w + CROP_GAP) / (CROP_SLOT + CROP_GAP))
		val top = y + HERO_H + SECTION_GAP
		return List(count) { i ->
			Rect(
				x + (i % perRow) * (CROP_SLOT + CROP_GAP),
				top + (i / perRow) * (CROP_SLOT + CROP_GAP),
				CROP_SLOT, CROP_SLOT
			)
		}
	}

	/** Bottom of the hero row plus any wrapped crop rows. */
	@JvmStatic
	fun heroBottom(y: Int, cropSlots: List<Rect>): Int = cropSlots.fold(y + HERO_H) { bottom, r -> maxOf(bottom, r.bottom()) }

	@JvmStatic
	fun columnCount(w: Int): Int {
		for (n in 3 downTo 2) {
			if ((w - (n - 1) * COL_GAP) / n >= MIN_COL_W) {
				return n
			}
		}
		return 1
	}

	/** Column x and width; the last column takes the rounding remainder so the grid ends flush. */
	@JvmStatic
	fun columns(x: Int, w: Int, count: Int): List<Rect> {
		val colW = (w - (count - 1) * COL_GAP) / count
		return List(count) { i ->
			val cx = x + i * (colW + COL_GAP)
			val cw = if (i == count - 1) x + w - cx else colW
			Rect(cx, 0, cw, 0)
		}
	}

	@JvmStatic
	fun sections(columns: Int): List<List<Section>> = when (columns) {
		3 -> listOf(listOf(Section.CROP), listOf(Section.GEAR, Section.PET), listOf(Section.PERMANENT))
		2 -> listOf(listOf(Section.CROP, Section.PET), listOf(Section.PERMANENT, Section.GEAR))
		else -> listOf(listOf(Section.CROP, Section.GEAR, Section.PET, Section.PERMANENT))
	}

	/** Chips flow left to right and wrap; a chip wider than the row is narrowed to fit. */
	@JvmStatic
	fun chips(x: Int, y: Int, w: Int, widths: IntArray): List<Rect> {
		val out = ArrayList<Rect>(widths.size)
		var cx = x
		var cy = y
		for (width in widths) {
			val cw = minOf(width, w)
			if (cx > x && cx + cw > x + w) {
				cx = x
				cy += COND_H + COND_GAP
			}
			out.add(Rect(cx, cy, cw, COND_H))
			cx += cw + COND_GAP
		}
		return out
	}
}
