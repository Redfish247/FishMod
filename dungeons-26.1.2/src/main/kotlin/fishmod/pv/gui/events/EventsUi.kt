/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.events

import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Shared Events UI helpers and hover zones. */
class EventsUi {
	@JvmField
	val zones = ArrayList<HoverZone>()

	@JvmField
	var contentX = 0

	@JvmField
	var contentY = 0

	@JvmField
	var contentW = 0

	@JvmField
	var contentH = 0

	fun beginFrame(x: Int, y: Int, w: Int, h: Int) {
		zones.clear()
		contentX = x
		contentY = y
		contentW = w
		contentH = h
	}

	fun drawHover(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int, x: Int, y: Int, w: Int, h: Int) {
		val mouseInGui = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
		if (!mouseInGui) {
			return
		}
		val zone = zones.firstOrNull { mouseX >= it.x && mouseX < it.x + it.w && mouseY >= it.y && mouseY < it.y + it.h } ?: return
		PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH)
	}

	fun tipStat(
		g: GuiGraphicsExtractor, font: Font, label: String?, value: String?, valueColor: Int,
		x: Int, y: Int, w: Int, mx: Int, my: Int, tip: List<PvTooltip.Line>?
	): Int {
		val next = statLine(g, font, label, value, x, y, w, valueColor)
		if (!tip.isNullOrEmpty()) {
			addClippedHover(x, y, w, STAT_ROW, contentX, contentY, contentW, contentH, tip)
		}
		return next
	}

	fun addClippedHover(
		bx: Int, by: Int, bw: Int, bh: Int,
		clipX: Int, clipY: Int, clipW: Int, clipH: Int,
		tip: List<PvTooltip.Line>?
	) {
		if (tip.isNullOrEmpty()) {
			return
		}
		val x0 = maxOf(bx, maxOf(clipX, contentX))
		val y0 = maxOf(by, maxOf(clipY, contentY))
		val x1 = minOf(bx + bw, minOf(clipX + clipW, contentX + contentW))
		val y1 = minOf(by + bh, minOf(clipY + clipH, contentY + contentH))
		if (x1 > x0 && y1 > y0) {
			zones.add(HoverZone(x0, y0, x1 - x0, y1 - y0, tip))
		}
	}

	@JvmRecord
	data class HoverZone(val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<PvTooltip.Line>)

	companion object {
		const val PAD = 6
		const val GAP = 6
		const val STAT_ROW = 12
		const val SEP_GAP = 10
		const val BINGO_COLS = 5
		const val BINGO_ROWS = 5
		const val RABBIT_SLOT = 16
		const val RABBIT_ROW = 18
		const val COLOR_COMPLETE = 0xFF55FF55.toInt()
		const val COLOR_COMMUNITY = 0xFF7C9CFF.toInt()
		const val COLOR_CHOCOLATE = 0xFFD4A574.toInt()
		const val SLOT_BG = 0x1CFFFFFF
		const val SLOT_BORDER = 0x30FFFFFF
		const val FLIP_MS = 480
		const val PANEL_HOVER = 0x0AFFFFFF

		private val WORD_SEPARATORS = Regex("[_\\-\\s]+")

		@JvmStatic
		fun statLine(
			g: GuiGraphicsExtractor, font: Font, label: String?, value: String?, x: Int, y: Int, w: Int, valueColor: Int
		): Int {
			val safe = if (value.isNullOrBlank()) "-" else value
			val leftMax = maxOf(8, w - font.width(safe) - 6)
			PvDraw.text(g, font, trim(font, label, leftMax), x, y, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, safe, x + w, y, valueColor)
			return y + STAT_ROW
		}

		@JvmStatic
		fun sectionSeparator(g: GuiGraphicsExtractor, panelX: Int, y: Int, panelW: Int): Int {
			val lineInset = PAD + 4
			val lineW = maxOf(0, panelW - lineInset * 2)
			val lineY = y + (SEP_GAP - 1) / 2
			if (lineW > 0) {
				PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF)
			}
			return y + SEP_GAP
		}

		@JvmStatic
		fun tipTitle(title: String?, titleColor: Int, vararg rows: PvTooltip.Line?): MutableList<PvTooltip.Line> {
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title(title, titleColor))
			tip.add(PvTooltip.Line.divider())
			rows.filterNotNullTo(tip)
			return tip
		}

		@JvmStatic
		fun easeInOutCubic(t: Float): Float =
			if (t < 0.5f) 4f * t * t * t else 1f - Math.pow((-2f * t + 2f).toDouble(), 3.0).toFloat() / 2f

		@JvmStatic
		fun formatAgo(epochMs: Long): String {
			if (epochMs <= 0L) {
				return "-"
			}
			val age = maxOf(0L, System.currentTimeMillis() - epochMs)
			return FormatUtil.prettySpan(age) + " ago"
		}

		@JvmStatic
		fun prettyModifier(raw: String?): String {
			if (raw.isNullOrBlank()) {
				return ""
			}
			return raw.lowercase(Locale.ROOT).split(WORD_SEPARATORS)
				.filter { it.isNotBlank() }
				.joinToString(" ") { it[0].uppercaseChar() + it.substring(1) }
		}

		@JvmStatic
		fun prettyGoalId(id: String?): String = prettyModifier(id ?: "")

		@JvmStatic
		fun wrapLore(lore: String?, maxChars: Int): List<String> {
			val lines = ArrayList<String>()
			if (lore.isNullOrBlank()) {
				return lines
			}
			var cleaned = lore.replace('\n', ' ').trim()
			while (cleaned.isNotEmpty()) {
				if (cleaned.length <= maxChars) {
					lines.add(cleaned)
					break
				}
				var breakAt = cleaned.lastIndexOf(' ', maxChars)
				if (breakAt <= 0) {
					breakAt = maxChars
				}
				lines.add(cleaned.substring(0, breakAt).trim())
				cleaned = cleaned.substring(breakAt).trim()
				if (lines.size >= 8) {
					if (cleaned.isNotEmpty()) {
						lines.add("…")
					}
					break
				}
			}
			return lines
		}

		@JvmStatic
		fun trim(font: Font, text: String?, maxW: Int): String {
			if (text == null) {
				return ""
			}
			if (maxW <= 0 || font.width(text) <= maxW) {
				return text
			}
			val ellipsis = "…"
			val budget = maxW - font.width(ellipsis)
			if (budget <= 0) {
				return ellipsis
			}
			val sb = StringBuilder()
			for (c in text) {
				if (font.width(sb.toString() + c) > budget) {
					break
				}
				sb.append(c)
			}
			return sb.toString() + ellipsis
		}
	}
}
