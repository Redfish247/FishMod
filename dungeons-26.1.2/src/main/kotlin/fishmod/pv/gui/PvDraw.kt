/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import fishmod.features.extras.ExtrasSettings
import java.awt.Color
import kotlin.math.abs
import kotlin.math.sqrt
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

object PvDraw {
	const val COLOR_PANEL = 0x8A10162A.toInt()
	const val COLOR_PANEL_OUTER = 0xE00A0A10.toInt()
	const val COLOR_PANEL_INNER = 0xC0181824.toInt()
	const val COLOR_BORDER = 0x00FFFFFF
	const val COLOR_TEXT = 0xFFE8E8F0.toInt()
	const val COLOR_MUTED = 0xFF9A9AAC.toInt()
	const val COLOR_GOLD = 0xFFFFAA00.toInt()
	const val COLOR_WHITE = 0xFFFFFFFF.toInt()
	@JvmField
	var COLOR_ACCENT = 0xFF5B8CFF.toInt()
	const val COLOR_DIVIDER = 0x26FFFFFF
	const val COLOR_BAR_BG = 0x38FFFFFF
	const val COLOR_BAR_FILL = 0xFF3D8B5A.toInt()
	const val COLOR_BAR_PROGRESS = 0xFF8E8E9C.toInt()
	const val COLOR_BAR_FILL_SLAYER = 0xFF8B4A3D.toInt()
	const val COLOR_MAXED_BAR_LEFT = 0xFF9278C5.toInt()
	const val COLOR_MAXED_BAR_RIGHT = 0xFFF3E6FD.toInt()
	const val BAR_HEIGHT = 6

	@JvmField
	var CELL = 0x38000000
	@JvmField
	var GLASS_TOP = 0x8C141B30.toInt()
	@JvmField
	var GLASS_BOTTOM = 0x960A0F1E.toInt()
	@JvmField
	var GLASS_INNER_TOP = 0x22FFFFFF
	@JvmField
	var GLASS_INNER_BOTTOM = 0x12FFFFFF
	const val GLASS_EDGE = 0x00FFFFFF
	const val GLASS_GLOSS = 0x00FFFFFF
	const val C_TL = 1
	const val C_TR = 2
	const val C_BR = 4
	const val C_BL = 8
	const val C_ALL = 15

	private val INSETS_CACHE = arrayOfNulls<IntArray>(128)
	private val NO_INSETS = IntArray(0)

	@JvmStatic
	fun fill(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, argb: Int) {
		g.fill(x, y, x + w, y + h, argb)
	}

	private fun cornerInsets(r: Int): IntArray {
		if (r < INSETS_CACHE.size) {
			return INSETS_CACHE[r] ?: computeInsets(r).also { INSETS_CACHE[r] = it }
		}
		return computeInsets(r)
	}

	private fun computeInsets(r: Int): IntArray = IntArray(r) { i ->
		val dy = r - i - 0.5
		Math.round(r - sqrt(maxOf(0.0, r * r.toDouble() - dy * dy))).toInt()
	}

	private fun deviceScale(g: GuiGraphicsExtractor): Float {
		val guiScale = Minecraft.getInstance().window.guiScale.toFloat()
		val poseScale = abs(g.pose().m00())
		return maxOf(1f, guiScale * (if (poseScale <= 0f) 1f else poseScale))
	}

	@JvmStatic
	@JvmOverloads
	fun roundedFill(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, argb: Int, mask: Int = C_ALL) {
		val k = deviceScale(g)
		if (k < 1.2f) {
			roundedFillDev(g, x, y, w, h, r, argb, mask)
			return
		}
		g.pose().pushMatrix()
		g.pose().scale(1f / k, 1f / k)
		val dx = Math.round(x * k)
		val dy = Math.round(y * k)
		val dw = Math.round((x + w) * k) - dx
		val dh = Math.round((y + h) * k) - dy
		roundedFillDev(g, dx, dy, dw, dh, Math.round(r * k), argb, mask)
		g.pose().popMatrix()
	}

	private fun roundedFillDev(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Int, argb: Int, mask: Int) {
		val r = minOf(radius, minOf(w, h) / 2)
		if (r <= 1) {
			fill(g, x, y, w, h, argb)
			return
		}
		val ins = cornerInsets(r)
		for (i in 0 until r) {
			val l = if (mask and C_TL != 0) ins[i] else 0
			val rt = if (mask and C_TR != 0) ins[i] else 0
			fill(g, x + l, y + i, w - l - rt, 1, argb)
			val bl = if (mask and C_BL != 0) ins[i] else 0
			val br = if (mask and C_BR != 0) ins[i] else 0
			fill(g, x + bl, y + h - 1 - i, w - bl - br, 1, argb)
		}
		fill(g, x, y + r, w, h - 2 * r, argb)
	}

	@JvmStatic
	fun roundedGradient(
		g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, top: Int, bottom: Int, mask: Int
	) {
		val k = deviceScale(g)
		if (k < 1.2f) {
			roundedGradientDev(g, x, y, w, h, r, top, bottom, mask, 4)
			return
		}
		g.pose().pushMatrix()
		g.pose().scale(1f / k, 1f / k)
		val dx = Math.round(x * k)
		val dy = Math.round(y * k)
		val dw = Math.round((x + w) * k) - dx
		val dh = Math.round((y + h) * k) - dy
		roundedGradientDev(g, dx, dy, dw, dh, Math.round(r * k), top, bottom, mask, maxOf(2, Math.round(k)))
		g.pose().popMatrix()
	}

	private fun roundedGradientDev(
		g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Int, top: Int, bottom: Int, mask: Int, minBand: Int
	) {
		val r = minOf(radius, minOf(w, h) / 2)
		if (h <= 0 || w <= 0) {
			return
		}
		val ins = if (r > 1) cornerInsets(r) else NO_INSETS
		for (i in ins.indices) {
			val l = if (mask and C_TL != 0) ins[i] else 0
			val rt = if (mask and C_TR != 0) ins[i] else 0
			fill(g, x + l, y + i, w - l - rt, 1, lerpArgb(top, bottom, (i + 0.5f) / h))
			val row = h - 1 - i
			val bl = if (mask and C_BL != 0) ins[i] else 0
			val br = if (mask and C_BR != 0) ins[i] else 0
			fill(g, x + bl, y + row, w - bl - br, 1, lerpArgb(top, bottom, (row + 0.5f) / h))
		}
		val start = ins.size
		val end = h - ins.size
		val band = maxOf(minBand, (end - start + 11) / 12)
		var yy = start
		while (yy < end) {
			val bh = minOf(band, end - yy)
			fill(g, x, y + yy, w, bh, lerpArgb(top, bottom, (yy + bh / 2f) / h))
			yy += band
		}
	}

	@JvmStatic
	fun roundedRing(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, argb: Int, mask: Int) {
		val k = deviceScale(g)
		g.pose().pushMatrix()
		g.pose().scale(1f / k, 1f / k)
		val dx = Math.round(x * k)
		val dy = Math.round(y * k)
		val dw = Math.round((x + w) * k) - dx
		val dh = Math.round((y + h) * k) - dy
		val dr = minOf(Math.round(r * k), minOf(dw, dh) / 2)
		val t = maxOf(1, Math.round(k * 0.5f))
		val outer = if (dr > 1) cornerInsets(dr) else NO_INSETS
		val ri = maxOf(0, dr - t)
		val inner = if (ri > 1) cornerInsets(ri) else NO_INSETS
		val cornerRows = minOf(dr, dh / 2)
		for (i in 0 until cornerRows) {
			for (half in 0..1) {
				val top = half == 0
				val row = if (top) i else dh - 1 - i
				val leftBit = mask and (if (top) C_TL else C_BL)
				val rightBit = mask and (if (top) C_TR else C_BR)
				val lo = if (leftBit != 0 && i < outer.size) outer[i] else 0
				val ro = if (rightBit != 0 && i < outer.size) outer[i] else 0
				if (i < t) {
					fill(g, dx + lo, dy + row, dw - lo - ro, 1, argb)
					continue
				}
				val irow = i - t
				val ii = if (irow < inner.size) inner[irow] else 0
				val li = if (leftBit != 0) ii else 0
				val rin = if (rightBit != 0) ii else 0
				val leftEnd = dx + t + li
				val rightStart = dx + dw - t - rin
				if (leftEnd > dx + lo) {
					fill(g, dx + lo, dy + row, leftEnd - (dx + lo), 1, argb)
				}
				if (dx + dw - ro > rightStart) {
					fill(g, rightStart, dy + row, dx + dw - ro - rightStart, 1, argb)
				}
			}
		}
		val midH = dh - 2 * cornerRows
		if (midH > 0) {
			fill(g, dx, dy + cornerRows, t, midH, argb)
			fill(g, dx + dw - t, dy + cornerRows, t, midH, argb)
		}
		g.pose().popMatrix()
	}

	@JvmStatic
	fun popupSurface(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
		roundedFill(g, x, y, w, h, 4, 0xFA090B11.toInt(), C_ALL)
		roundedRing(g, x, y, w, h, 4, 0x00FFFFFF, C_ALL)
	}

	@JvmStatic
	fun overlayPanel(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
		glassShadow(g, x, y, w, h, 8)
		val base = (GLASS_BOTTOM and 0x00FFFFFF) or 0xF6000000.toInt()
		val top = (GLASS_TOP and 0x00FFFFFF) or 0xF6000000.toInt()
		roundedGradient(g, x, y, w, h, 8, top, base, C_ALL)
	}

	@JvmStatic
	fun glassShadow(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int) {
		roundedFill(g, x - 3, y - 2, w + 6, h + 6, r + 3, 0x08000000)
		roundedFill(g, x - 2, y - 1, w + 4, h + 4, r + 2, 0x0C000000)
		roundedFill(g, x - 1, y, w + 2, h + 2, r + 1, 0x12000000)
	}

	@JvmStatic
	@JvmOverloads
	fun glassSurface(
		g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, top: Int, bottom: Int, mask: Int, edge: Int = GLASS_EDGE
	) {
		roundedGradient(g, x, y, w, h, r, top, bottom, mask)
		roundedRing(g, x, y, w, h, r, edge, mask)
		if (mask and (C_TL or C_TR) != 0 && w > 2 * r + 2) {
			fill(g, x + r, y + 1, w - 2 * r, 1, GLASS_GLOSS)
		}
	}

	@JvmStatic
	fun panel(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
		glassShadow(g, x, y, w, h, 8)
		glassSurface(g, x, y, w, h, 8, GLASS_TOP, GLASS_BOTTOM, C_TR or C_BR or C_BL)
	}

	@JvmStatic
	fun slideOffset(eased: Float, w: Int): Float =
		if (eased < 0.5f) -w * (eased * 2f) else w * (1f - (eased - 0.5f) * 2f)

	@JvmStatic
	fun innerPanel(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
		glassSurface(g, x, y, w, h, 5, GLASS_INNER_TOP, GLASS_INNER_BOTTOM, C_ALL)
	}

	@JvmStatic
	fun text(g: GuiGraphicsExtractor, font: Font, value: String?, x: Int, y: Int, color: Int) {
		g.text(font, value, x, y, color, false)
	}

	/** Draw text scaled about its top-left corner (e.g. footer credits). */
	@JvmStatic
	fun textScaled(g: GuiGraphicsExtractor, font: Font, value: String?, x: Int, y: Int, color: Int, scale: Float) {
		if (scale == 1.0f) {
			text(g, font, value, x, y, color)
			return
		}
		g.pose().pushMatrix()
		g.pose().translate(x.toFloat(), y.toFloat())
		g.pose().scale(scale, scale)
		g.text(font, value, 0, 0, color, false)
		g.pose().popMatrix()
	}

	@JvmStatic
	fun text(g: GuiGraphicsExtractor, font: Font, value: Component?, x: Int, y: Int) {
		if (value == null) {
			return
		}
		g.text(font, value, x, y, COLOR_WHITE, false)
	}

	@JvmStatic
	@JvmOverloads
	@Suppress("UNUSED_PARAMETER")
	fun styled(value: String, rgb: Int, bold: Boolean, italic: Boolean = false): Component {
		var style = Style.EMPTY.withColor(TextColor.fromRgb(rgb and 0xFFFFFF))
		if (italic) {
			style = style.withItalic(true)
		}
		return Component.literal(value).setStyle(style)
	}

	@JvmStatic
	fun textBold(g: GuiGraphicsExtractor, font: Font, value: String, x: Int, y: Int, color: Int) {
		text(g, font, styled(value, color, true), x, y)
	}

	@JvmStatic
	fun textBoldScaled(g: GuiGraphicsExtractor, font: Font, value: String, x: Int, y: Int, color: Int, scale: Float) {
		if (scale == 1.0f) {
			textBold(g, font, value, x, y, color)
			return
		}
		g.pose().pushMatrix()
		g.pose().translate(x.toFloat(), y.toFloat())
		g.pose().scale(scale, scale)
		text(g, font, styled(value, color, true), 0, 0)
		g.pose().popMatrix()
	}

	@JvmRecord
	data class IconTextAlign(val iconY: Int, val textY: Int) {
		companion object {
			@JvmStatic
			fun of(rowY: Int, rowH: Int, iconSize: Int, lineHeight: Int): IconTextAlign {
				val pairH = maxOf(maxOf(1, iconSize), maxOf(1, lineHeight))
				val pairTop = rowY + maxOf(0, (maxOf(1, rowH) - pairH) / 2)
				val iconY = pairTop + maxOf(0, (pairH - iconSize) / 2)
				val textY = pairTop + maxOf(0, (pairH - lineHeight) / 2)
				return IconTextAlign(iconY, textY)
			}
		}
	}

	@JvmStatic
	fun width(font: Font, value: Component): Int = font.width(value)

	@JvmStatic
	fun widthBold(font: Font, value: String): Int = font.width(styled(value, 0xFFFFFF, true))

	@JvmStatic
	fun textCentered(g: GuiGraphicsExtractor, font: Font, value: String, cx: Int, y: Int, color: Int) {
		val x = cx - font.width(value) / 2
		text(g, font, value, x, y, color)
	}

	@JvmStatic
	fun textCentered(g: GuiGraphicsExtractor, font: Font, value: Component, cx: Int, y: Int) {
		val x = cx - font.width(value) / 2
		text(g, font, value, x, y)
	}

	@JvmStatic
	fun textRight(g: GuiGraphicsExtractor, font: Font, value: String, rightX: Int, y: Int, color: Int) {
		text(g, font, value, rightX - font.width(value), y, color)
	}

	@JvmStatic
	fun sectionHeader(g: GuiGraphicsExtractor, font: Font, label: String, x: Int, y: Int, w: Int): Int {
		text(g, font, label, x, y, COLOR_MUTED)
		val lineX = x + font.width(label) + 4
		val lineY = y + font.lineHeight / 2
		val lineW = x + w - lineX
		if (lineW > 0) {
			fill(g, lineX, lineY, lineW, 1, COLOR_BORDER)
		}
		return font.lineHeight + 3
	}

	@JvmStatic
	@JvmOverloads
	@Suppress("UNUSED_PARAMETER")
	fun progressBar(
		g: GuiGraphicsExtractor,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		progress: Float,
		fillColor: Int,
		maxedShiny: Boolean = false
	) {
		if (w <= 0 || h <= 0) {
			return
		}
		val clamped = maxOf(0.0f, minOf(1.0f, progress))
		roundedFill(g, x, y, w, h, h / 2, COLOR_BAR_BG)
		val filled = maxOf(0, Math.round(w * clamped))
		if (filled <= 0) {
			return
		}
		// Always round both ends of the fill, including incomplete bars.
		// One scheme everywhere: gold when maxed, gray while still filling.
		val maxed = maxedShiny || clamped >= 0.999f
		val fw = maxOf(filled, minOf(w, h))
		if (maxed && ExtrasSettings.pvMaxedRainbow) {
			chromaSlices(g, x, y, fw, h)
		} else {
			roundedFill(g, x, y, fw, h, h / 2, if (maxed) maxedColor() else COLOR_BAR_PROGRESS)
		}
	}

	private const val CHROMA_PERIOD_MS = 6000L
	private const val CHROMA_SPAN_PX = 240f
	private const val CHROMA_STEP_PX = 4
	private const val CHROMA_BAND_ROWS = 3

	private fun chromaSlices(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
		val r = h / 2
		val end = w - r
		val t = (System.currentTimeMillis() % CHROMA_PERIOD_MS) / CHROMA_PERIOD_MS.toFloat()
		val mid = h / (2 * CHROMA_BAND_ROWS)
		roundedFill(g, x, y, minOf(2 * r, w), h, r, rainbowArgb(positiveModulo((r + mid) / CHROMA_SPAN_PX - t, 1f)))
		roundedFill(g, x + maxOf(0, w - 2 * r), y, minOf(2 * r, w), h, r, rainbowArgb(positiveModulo((end + mid) / CHROMA_SPAN_PX - t, 1f)))
		var band = 0
		while (band * CHROMA_BAND_ROWS < h) {
			val y0 = y + band * CHROMA_BAND_ROWS
			val y1 = minOf(y0 + CHROMA_BAND_ROWS, y + h)
			var sx = r
			while (sx < end) {
				val hue = (sx + band) / CHROMA_SPAN_PX - t
				g.fill(x + sx, y0, x + minOf(sx + CHROMA_STEP_PX, end), y1, rainbowArgb(positiveModulo(hue, 1f)))
				sx += CHROMA_STEP_PX
			}
			band++
		}
	}

	@JvmStatic
	fun maxedColor(): Int {
		if (ExtrasSettings.pvMaxedRainbow) {
			return rainbowArgb((System.currentTimeMillis() % CHROMA_PERIOD_MS) / CHROMA_PERIOD_MS.toFloat())
		}
		return ExtrasSettings.pvMaxedColor
	}

	@JvmStatic
	fun chromaText(text: String?, bold: Boolean, start: Int = 0, periodMs: Long = 2800L, step: Float = 0.07f): Component {
		if (text.isNullOrEmpty()) {
			return Component.empty()
		}
		val t = (System.currentTimeMillis() % periodMs) / periodMs.toFloat()
		val out = Component.empty()
		for (i in text.indices) {
			val rgb = rainbowArgb(positiveModulo(t + (start + i) * step, 1.0f)) and 0xFFFFFF
			var style = Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false)
			if (bold) {
				style = style.withBold(true)
			}
			out.append(Component.literal(text[i].toString()).setStyle(style))
		}
		return out
	}

	private fun rainbowArgb(hue: Float): Int {
		val h = positiveModulo(hue, 1.0f)
		val rgb = Color.HSBtoRGB(h, 0.85f, 1.0f) and 0xFFFFFF
		val cosmetics = COLOR_MAXED_BAR_LEFT and 0xFFFFFF
		return lerpArgb(rgb or 0xFF000000.toInt(), cosmetics or 0xFF000000.toInt(), 0.18f)
	}

	private fun positiveModulo(value: Float, modulus: Float): Float {
		val remainder = value % modulus
		return if (remainder < 0.0f) remainder + modulus else remainder
	}

	private fun lerpArgb(from: Int, to: Int, amount: Float): Int {
		val t = maxOf(0f, minOf(1f, amount))
		val a1 = (from ushr 24) and 0xFF
		val r1 = (from ushr 16) and 0xFF
		val g1 = (from ushr 8) and 0xFF
		val b1 = from and 0xFF
		val a2 = (to ushr 24) and 0xFF
		val r2 = (to ushr 16) and 0xFF
		val g2 = (to ushr 8) and 0xFF
		val b2 = to and 0xFF
		val a = Math.round(a1 + (a2 - a1) * t)
		val r = Math.round(r1 + (r2 - r1) * t)
		val g = Math.round(g1 + (g2 - g1) * t)
		val b = Math.round(b1 + (b2 - b1) * t)
		return (a shl 24) or (r shl 16) or (g shl 8) or b
	}

	@JvmStatic
	@JvmOverloads
	fun labeledBar(
		g: GuiGraphicsExtractor,
		font: Font,
		label: String?,
		value: String?,
		progress: Float,
		x: Int,
		y: Int,
		w: Int,
		fillColor: Int,
		maxedShiny: Boolean = false,
		labelColor: Int = COLOR_TEXT,
		labelBold: Boolean = false
	) {
		drawLabelValue(g, font, label, value, x, y, w, labelColor, labelBold)
		val barY = y + font.lineHeight + 2
		progressBar(g, x, barY, w, BAR_HEIGHT, progress, fillColor, maxedShiny)
	}

	@JvmStatic
	fun labeledBar(
		g: GuiGraphicsExtractor,
		font: Font,
		labelLine: Component,
		progress: Float,
		x: Int,
		y: Int,
		w: Int,
		fillColor: Int,
		maxedShiny: Boolean
	) {
		g.text(font, labelLine, x, y, COLOR_WHITE, false)
		val barY = y + font.lineHeight + 2
		progressBar(g, x, barY, w, BAR_HEIGHT, progress, fillColor, maxedShiny)
	}

	@JvmStatic
	fun drawLabelValue(
		g: GuiGraphicsExtractor,
		font: Font,
		label: String?,
		value: String?,
		x: Int,
		y: Int,
		w: Int,
		labelColor: Int,
		labelBold: Boolean
	) {
		val right = value ?: ""
		val leftComp = styled(label ?: "", labelColor, labelBold)
		g.text(font, leftComp, x, y, COLOR_WHITE, false)
		if (right.isNotEmpty()) {
			val vw = font.width(right)
			g.text(font, styled(right, COLOR_MUTED, false), x + maxOf(0, w - vw), y, COLOR_WHITE, false)
		}
	}
}
