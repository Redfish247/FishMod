/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.overview

import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

/** Masonry grid of titled cards, used by the trial skill overviews. Scrolls when it doesn't fit. */
class SummaryCards {
	sealed interface Item

	@JvmRecord
	data class Stat(
		val label: String?,
		val value: String?,
		val labelColor: Int,
		val valueColor: Int,
		val tip: List<PvTooltip.Line>?
	) : Item {
		companion object {
			@JvmStatic
			fun of(label: String?, value: String?, valueColor: Int): Stat =
				Stat(label, value, PvDraw.COLOR_MUTED, valueColor, null)

			@JvmStatic
			fun of(label: String?, value: String?, valueColor: Int, tip: List<PvTooltip.Line>?): Stat =
				Stat(label, value, PvDraw.COLOR_MUTED, valueColor, tip)
		}
	}

	@JvmRecord
	data class Bar(
		val label: String?,
		val value: String?,
		val fill: Float,
		val maxed: Boolean,
		val color: Int,
		val tip: List<PvTooltip.Line>?
	) : Item

	@JvmRecord
	data class Note(val text: String?) : Item

	@JvmRecord
	data class Card(val title: String, val items: List<Item>)

	private data class Zone(val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<PvTooltip.Line>)

	private val zones = ArrayList<Zone>()
	private var scroll = 0
	private var maxScroll = 0
	private var viewX = 0
	private var viewY = 0
	private var viewW = 0
	private var viewH = 0

	fun resetScroll() {
		scroll = 0
	}

	fun render(g: GuiGraphicsExtractor, font: Font, cards: List<Card>, x: Int, y: Int, w: Int, h: Int) {
		zones.clear()
		viewX = x
		viewY = y
		viewW = w
		viewH = h
		val cols = columnsFor(w)
		val colW = (w - GAP * (cols - 1)) / cols
		val heights = IntArray(cards.size) { cardHeight(font, cards[it]) }
		val columnOf = assignColumns(heights, cols)
		val colY = IntArray(cols)
		var contentH = 0
		for (i in cards.indices) {
			colY[columnOf[i]] += heights[i] + GAP
			contentH = maxOf(contentH, colY[columnOf[i]] - GAP)
		}
		maxScroll = maxOf(0, contentH - h)
		scroll = maxOf(0, minOf(scroll, maxScroll))

		g.enableScissor(x, y, x + w + 3, y + h)
		val drawY = IntArray(cols)
		for (i in cards.indices) {
			val col = columnOf[i]
			val cx = x + col * (colW + GAP)
			val cy = y + drawY[col] - scroll
			drawY[col] += heights[i] + GAP
			if (cy + heights[i] < y || cy > y + h) {
				continue
			}
			drawCard(g, font, cards[i], cx, cy, colW, heights[i])
		}
		g.disableScissor()

		if (maxScroll > 0) {
			val trackX = x + w - 2
			val thumbH = maxOf(12, h * h / (h + maxScroll))
			val thumbY = y + ((h - thumbH) * (scroll / maxScroll.toFloat())).roundToInt()
			PvDraw.fill(g, trackX, y, 2, h, PvDraw.COLOR_DIVIDER)
			PvDraw.fill(g, trackX, thumbY, 2, thumbH, PvDraw.COLOR_DIVIDER)
		}
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		if (mouseX < viewX || mouseX >= viewX + viewW || mouseY < viewY || mouseY >= viewY + viewH) {
			return
		}
		for (zone in zones) {
			if (mouseX >= zone.x && mouseX < zone.x + zone.w && mouseY >= zone.y && mouseY < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH)
				return
			}
		}
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		if (maxScroll <= 0 || mouseX < viewX || mouseX >= viewX + viewW
			|| mouseY < viewY || mouseY >= viewY + viewH
		) {
			return false
		}
		val next = maxOf(0, minOf(maxScroll, scroll - Math.round(scrollY * SCROLL_STEP).toInt()))
		if (next == scroll) {
			return false
		}
		scroll = next
		return true
	}

	private fun drawCard(g: GuiGraphicsExtractor, font: Font, card: Card, x: Int, y: Int, w: Int, h: Int) {
		PvDraw.innerPanel(g, x, y, w, h)
		val ix = x + PAD
		val iw = w - PAD * 2
		var ly = y + PAD
		ly += PvDraw.sectionHeader(g, font, card.title, ix, ly, iw)
		for (item in card.items) {
			val ih = itemHeight(font, item)
			when (item) {
				is Stat -> {
					val value = item.value ?: ""
					val labelMax = maxOf(8, iw - font.width(value) - 6)
					PvDraw.text(g, font, trim(font, item.label, labelMax), ix, ly, item.labelColor)
					PvDraw.textRight(g, font, value, ix + iw, ly, item.valueColor)
					addZone(ix, ly, iw, ih, item.tip)
				}
				is Bar -> {
					val value = item.value ?: ""
					val label = trim(font, item.label, maxOf(16, iw - font.width(value) - 8))
					PvDraw.labeledBar(g, font, label, value, item.fill, ix, ly, iw, item.color, item.maxed)
					addZone(ix, ly, iw, ih, item.tip)
				}
				is Note -> PvDraw.text(g, font, trim(font, item.text, iw), ix, ly, PvDraw.COLOR_MUTED)
			}
			ly += ih
		}
	}

	private fun addZone(x: Int, y: Int, w: Int, h: Int, lines: List<PvTooltip.Line>?) {
		if (lines.isNullOrEmpty()) {
			return
		}
		val top = maxOf(y, viewY)
		val bottom = minOf(y + h, viewY + viewH)
		if (bottom > top) {
			zones.add(Zone(x, top, w, bottom - top, lines))
		}
	}

	companion object {
		private const val PAD = 6
		private const val GAP = 6
		private const val MIN_COL_W = 150
		private const val MAX_COLS = 3
		private const val BAR_LABEL_GAP = 2
		private const val SCROLL_STEP = 14

		@JvmStatic
		fun columnsFor(w: Int): Int {
			val cols = (w + GAP) / (MIN_COL_W + GAP)
			return maxOf(1, minOf(MAX_COLS, cols))
		}

		/** Places each card, in order, into the currently shortest column. */
		@JvmStatic
		fun assignColumns(heights: IntArray, cols: Int): IntArray {
			val n = maxOf(1, cols)
			val colH = IntArray(n)
			val out = IntArray(heights.size)
			for (i in heights.indices) {
				var best = 0
				for (c in 1 until n) {
					if (colH[c] < colH[best]) {
						best = c
					}
				}
				out[i] = best
				colH[best] += heights[i] + GAP
			}
			return out
		}

		private fun cardHeight(font: Font, card: Card): Int {
			var h = PAD * 2 + font.lineHeight + 3
			for (item in card.items) {
				h += itemHeight(font, item)
			}
			return h
		}

		private fun itemHeight(font: Font, item: Item): Int = when (item) {
			is Stat -> font.lineHeight + 3
			is Bar -> font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT + 4
			is Note -> font.lineHeight + 2
		}

		private fun trim(font: Font, text: String?, maxW: Int): String {
			if (text == null) {
				return ""
			}
			if (font.width(text) <= maxW) {
				return text
			}
			val ellipsis = "..."
			val budget = maxW - font.width(ellipsis)
			if (budget <= 0) {
				return ellipsis
			}
			val out = StringBuilder()
			for (c in text) {
				if (font.width(out.toString() + c) > budget) {
					break
				}
				out.append(c)
			}
			return out.toString() + ellipsis
		}
	}
}
