/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import fishmod.pv.gui.inventories.SkyBlockIconRenderer
import java.util.Optional
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.ItemStack

/**
 * Shared profile-viewer tooltip template.
 *
 * Use [Line.title], [Line.divider], [Line.row], [Line.meta],
 * and [Line.action] so Networth / Weight / Username History (and future tips)
 * share title rules, right-aligned values, muted secondary text, and section dividers.
 */
object PvTooltip {
	private const val PAD = 8
	private const val ROW_GAP = 10
	private const val META_GAP = 4
	private const val DIVIDER_H = 7
	private const val ICON_SIZE = 10
	private const val ICON_W = ICON_SIZE + 2
	private const val PAN_STEP = 20
	private const val PAN_ACTIVE_MS = 250L

	// Tips bigger than the screen can be panned with the wheel; reset whenever a different tip shows.
	private var panLines: List<Line>? = null
	private var panX = 0
	private var panY = 0
	private var panMaxX = 0
	private var panMaxY = 0
	private var panDrawnAt = 0L

	enum class Kind {
		TEXT,
		DIVIDER,
		ROW,
		META,
		ACTION,
		BLANK
	}

	/** Null text becomes "". */
	class Span @JvmOverloads constructor(
		text: String?,
		@get:JvmName("color") val color: Int,
		@get:JvmName("bold") val bold: Boolean,
		@get:JvmName("italic") val italic: Boolean = false,
		@get:JvmName("obfuscated") val obfuscated: Boolean = false
	) {
		@get:JvmName("text")
		val text: String = text ?: ""

		fun toComponent(): Component {
			val style = Style.EMPTY
				.withItalic(italic)
				.withBold(if (bold) true else null)
				.withObfuscated(if (obfuscated) true else null)
				.withColor(TextColor.fromRgb(color and 0xFFFFFF))
			return Component.literal(text).setStyle(style)
		}

		companion object {
			@JvmStatic
			fun of(text: String?, color: Int): Span = Span(text, color, false, false, false)

			@JvmStatic
			fun bold(text: String?, color: Int): Span = Span(text, color, true, false, false)

			@JvmStatic
			fun italic(text: String?, color: Int): Span = Span(text, color, false, true, false)

			@JvmStatic
			fun obfuscated(text: String?, color: Int, bold: Boolean): Span = Span(text, color, bold, false, true)
		}
	}

	/** Null kind/lists normalise to TEXT/empty; empty icon and blank iconId to null. */
	class Line @JvmOverloads constructor(
		kind: Kind?,
		left: List<Span>?,
		value: List<Span>?,
		meta: List<Span>?,
		icon: ItemStack? = null,
		iconId: String? = null
	) {
		@get:JvmName("kind")
		val kind: Kind = kind ?: Kind.TEXT

		@get:JvmName("left")
		val left: List<Span> = if (left == null) listOf() else java.util.List.copyOf(left)

		@get:JvmName("value")
		val value: List<Span> = if (value == null) listOf() else java.util.List.copyOf(value)

		@get:JvmName("meta")
		val meta: List<Span> = if (meta == null) listOf() else java.util.List.copyOf(meta)

		@get:JvmName("icon")
		val icon: ItemStack? = if (icon == null || icon.isEmpty) null else icon

		@get:JvmName("iconId")
		val iconId: String? = if (iconId.isNullOrBlank()) null else iconId

		constructor(spans: List<Span>?) : this(Kind.TEXT, spans, listOf(), listOf())

		/** Small item icon drawn before the label (ROW / TEXT lines). */
		fun withIcon(stack: ItemStack?): Line = Line(kind, left, value, meta, stack, null)

		/** Icon drawn by SkyBlock id, so items NEU only has as paper still get their real texture. */
		fun withIcon(stack: ItemStack?, skyblockId: String?): Line = Line(kind, left, value, meta, stack, skyblockId)

		fun hasIcon(): Boolean = icon != null || iconId != null

		fun spans(): List<Span> = if (kind == Kind.ROW) left + value + meta else left

		fun toComponent(): Component {
			val out = Component.empty()
			for (span in spans()) {
				out.append(span.toComponent())
			}
			return out
		}

		fun isBlank(): Boolean =
			kind == Kind.BLANK || (kind != Kind.DIVIDER && spans().all { it.text.isEmpty() })

		fun isDivider(): Boolean = kind == Kind.DIVIDER

		companion object {
			@JvmStatic
			fun of(text: String?, color: Int): Line = text(listOf(Span.of(text, color)))

			@JvmStatic
			fun bold(text: String?, color: Int): Line = text(listOf(Span.bold(text, color)))

			@JvmStatic
			fun plain(text: String?): Line = of(text, PvDraw.COLOR_TEXT)

			@JvmStatic
			fun text(spans: List<Span>?): Line = Line(Kind.TEXT, spans, listOf(), listOf())

			@JvmStatic
			fun title(text: String?, color: Int): Line = bold(text, color)

			@JvmStatic
			fun divider(): Line = Line(Kind.DIVIDER, listOf(), listOf(), listOf())

			@JvmStatic
			fun blank(): Line = Line(Kind.BLANK, listOf(), listOf(), listOf())

			@JvmStatic
			fun meta(text: String?): Line = Line(Kind.META, listOf(Span.of(text, PvDraw.COLOR_MUTED)), listOf(), listOf())

			// Match inventory "Click to view value breakdown" (dark gray italic).
			@JvmStatic
			fun action(text: String?): Line = Line(Kind.ACTION, listOf(Span.italic(text, 0xFF555555.toInt())), listOf(), listOf())

			@JvmStatic
			fun row(label: String?, labelColor: Int, value: String?, valueColor: Int): Line =
				row(listOf(Span.of(label, labelColor)), listOf(Span.of(value, valueColor)), listOf())

			@JvmStatic
			@JvmOverloads
			fun row(label: List<Span>?, value: List<Span>?, meta: List<Span>? = listOf()): Line =
				Line(Kind.ROW, label, value, meta)
		}
	}

	@JvmStatic
	fun drawComponents(
		g: GuiGraphicsExtractor,
		font: Font,
		lines: List<Component?>?,
		mouseX: Int,
		mouseY: Int,
		screenW: Int,
		screenH: Int
	) {
		if (lines.isNullOrEmpty()) {
			return
		}
		drawStyled(g, font, lines.map { lineFromComponent(it) }, mouseX, mouseY, screenW, screenH)
	}

	/** Expand a lore/name Component into coloured spans (never flatten with getString). */
	private fun lineFromComponent(line: Component?): Line {
		if (line == null) {
			return Line.blank()
		}
		val spans = spansFromComponent(line)
		if (spans.all { it.text.isEmpty() }) {
			return Line.blank()
		}
		return Line.text(spans)
	}

	private fun spansFromComponent(line: Component): List<Span> {
		val spans = ArrayList<Span>()
		line.visit<Unit>({ style, text ->
			if (!text.isNullOrEmpty()) {
				val styleColor = style?.color
				val color = if (styleColor != null) 0xFF000000.toInt() or styleColor.value else PvDraw.COLOR_TEXT
				val bold = style != null && style.isBold
				val italic = style != null && style.isItalic
				val obfuscated = style != null && style.isObfuscated
				spans.add(Span(text, color, bold, italic, obfuscated))
			}
			Optional.empty()
		}, Style.EMPTY)
		return spans
	}

	@JvmStatic
	@JvmOverloads
	fun draw(
		g: GuiGraphicsExtractor,
		font: Font,
		lines: List<String?>?,
		mouseX: Int,
		mouseY: Int,
		screenW: Int,
		screenH: Int,
		textColor: Int = PvDraw.COLOR_TEXT
	) {
		if (lines.isNullOrEmpty()) {
			return
		}
		drawStyled(g, font, lines.map { Line.of(it, textColor) }, mouseX, mouseY, screenW, screenH)
	}

	@JvmStatic
	fun drawStyled(
		g: GuiGraphicsExtractor,
		font: Font,
		lines: List<Line>?,
		mouseX: Int,
		mouseY: Int,
		screenW: Int,
		screenH: Int
	) {
		drawStyled(g, font, lines, mouseX, mouseY, screenW, screenH, 0, Int.MAX_VALUE, null)
	}

	/**
	 * Draw a styled tip; when `maxBodyH` is finite, content after the sticky header
	 * (title + optional divider) scrolls by `scrollY`.
	 */
	@JvmStatic
	fun drawStyled(
		g: GuiGraphicsExtractor,
		font: Font,
		lines: List<Line>?,
		mouseX: Int,
		mouseY: Int,
		screenW: Int,
		screenH: Int,
		scrollY: Int,
		maxBodyH: Int,
		maxScrollOut: IntArray?
	): Int {
		if (lines.isNullOrEmpty()) {
			if (maxScrollOut != null && maxScrollOut.isNotEmpty()) {
				maxScrollOut[0] = 0
			}
			return 0
		}

		val lineH = font.lineHeight + 3
		val blankH = maxOf(4, font.lineHeight / 2)
		val metrics = measure(font, lines, lineH, blankH)
		val boxW = metrics.contentW + PAD * 2

		val stickyCount = stickyHeaderCount(lines)
		var stickyH = 0
		var bodyH = 0
		for (i in lines.indices) {
			val h = metrics.heights[i]
			if (i < stickyCount) {
				stickyH += h
			} else {
				bodyH += h
			}
		}
		val scrolling = maxBodyH < Int.MAX_VALUE / 4 && bodyH > maxBodyH
		val visibleBodyH = if (scrolling) minOf(maxBodyH, bodyH) else bodyH
		val maxScroll = maxOf(0, bodyH - visibleBodyH)
		val scroll = maxOf(0, minOf(maxScroll, scrollY))
		if (maxScrollOut != null && maxScrollOut.isNotEmpty()) {
			maxScrollOut[0] = maxScroll
		}

		val footerH = if (scrolling) font.lineHeight + 2 else 0
		val boxH = PAD * 2 + stickyH + visibleBodyH + footerH

		var x = mouseX + 12
		var y = mouseY - 12
		if (x + boxW > screenW - 4) {
			x = mouseX - boxW - 8
		}
		if (y + boxH > screenH - 4) {
			y = screenH - boxH - 4
		}
		if (x < 4) {
			x = 4
		}
		if (y < 4) {
			y = 4
		}
		if (!sameTip(lines, panLines)) {
			panLines = lines
			panX = 0
			panY = 0
		}
		panMaxX = maxOf(0, boxW - (screenW - 8))
		panMaxY = maxOf(0, boxH - (screenH - 8))
		panX = minOf(panX, panMaxX)
		panY = minOf(panY, panMaxY)
		panDrawnAt = System.currentTimeMillis()
		x -= panX
		y -= panY

		PvDraw.popupSurface(g, x, y, boxW, boxH)

		var ty = y + PAD
		for (i in 0 until stickyCount) {
			drawLine(g, font, lines[i], metrics, x, ty, boxW)
			ty += metrics.heights[i]
		}

		if (scrolling) {
			val bodyTop = ty
			g.enableScissor(x + 1, bodyTop, x + boxW - 1, bodyTop + visibleBodyH)
			var drawY = bodyTop - scroll
			for (i in stickyCount until lines.size) {
				drawLine(g, font, lines[i], metrics, x, drawY, boxW)
				drawY += metrics.heights[i]
			}
			g.disableScissor()
			PvDraw.text(g, font, "Scroll for more",
				x + PAD, y + boxH - PAD - font.lineHeight, PvDraw.COLOR_MUTED)
		} else {
			for (i in stickyCount until lines.size) {
				drawLine(g, font, lines[i], metrics, x, ty, boxW)
				ty += metrics.heights[i]
			}
		}
		return maxScroll
	}

	/**
	 * Pans the tooltip drawn last frame when it doesn't fit on screen. Returns false (so the page
	 * scrolls as usual) when no tooltip is showing or it already fits along that axis.
	 */
	@JvmStatic
	fun panOverflow(scrollY: Double, horizontal: Boolean): Boolean {
		if (scrollY == 0.0 || panLines == null || System.currentTimeMillis() - panDrawnAt > PAN_ACTIVE_MS) {
			return false
		}
		val delta = Math.round(-scrollY * PAN_STEP).toInt()
		if (horizontal) {
			if (panMaxX <= 0) {
				return false
			}
			panX = maxOf(0, minOf(panMaxX, panX + delta))
		} else {
			if (panMaxY <= 0) {
				return false
			}
			panY = maxOf(0, minOf(panMaxY, panY + delta))
		}
		return true
	}

	// Pages rebuild their tips every frame and rainbow lore recolours per frame, so compare text only.
	private fun sameTip(a: List<Line>?, b: List<Line>?): Boolean {
		if (a === b) {
			return true
		}
		if (a == null || b == null || a.size != b.size) {
			return false
		}
		for (i in a.indices) {
			val x = a[i]
			val y = b[i]
			if (x.kind != y.kind ||
				plain(x.left) != plain(y.left) ||
				plain(x.value) != plain(y.value) ||
				plain(x.meta) != plain(y.meta)
			) {
				return false
			}
		}
		return true
	}

	private fun plain(spans: List<Span>): String =
		if (spans.size == 1) spans[0].text else spans.joinToString("") { it.text }

	@JvmStatic
	fun drawCenteredAbove(
		g: GuiGraphicsExtractor,
		font: Font,
		line: Component?,
		cx: Int,
		topY: Int,
		screenW: Int,
		screenH: Int
	) {
		if (line == null) {
			return
		}
		val pad = 6
		val lineH = font.lineHeight + 2
		val boxW = maxOf(8, font.width(line)) + pad * 2
		val boxH = lineH + pad * 2
		val x = cx - boxW / 2
		val y = topY - boxH - 3
		drawLegacyBox(g, font, listOf(line), x, y, boxW, boxH, pad, lineH, screenW, screenH)
	}

	@JvmStatic
	fun drawCenteredLeft(
		g: GuiGraphicsExtractor,
		font: Font,
		line: Component?,
		leftX: Int,
		cy: Int,
		screenW: Int,
		screenH: Int
	) {
		if (line == null) {
			return
		}
		val pad = 6
		val lineH = font.lineHeight + 2
		val boxW = maxOf(8, font.width(line)) + pad * 2
		val boxH = lineH + pad * 2
		val x = leftX - boxW - 3
		val y = cy - boxH / 2
		drawLegacyBox(g, font, listOf(line), x, y, boxW, boxH, pad, lineH, screenW, screenH)
	}

	private fun stickyHeaderCount(lines: List<Line>): Int {
		if (lines.isEmpty()) {
			return 0
		}
		return if (lines.size > 1 && lines[1].isDivider()) 2 else 1
	}

	private fun measure(font: Font, lines: List<Line>, lineH: Int, blankH: Int): Metrics {
		var maxLabel = 0
		var maxValue = 0
		var maxMeta = 0
		var maxText = 8
		val heights = IntArray(lines.size)
		lines.forEachIndexed { i, line ->
			when (line.kind) {
				Kind.DIVIDER -> heights[i] = DIVIDER_H
				Kind.BLANK -> heights[i] = blankH
				Kind.ROW -> {
					heights[i] = lineH
					maxLabel = maxOf(maxLabel, iconOffset(line) + widthOf(font, line.left))
					maxValue = maxOf(maxValue, widthOf(font, line.value))
					maxMeta = maxOf(maxMeta, widthOf(font, line.meta))
				}
				Kind.TEXT, Kind.META, Kind.ACTION -> {
					heights[i] = if (line.isBlank()) blankH else lineH
					maxText = maxOf(maxText, iconOffset(line) + widthOf(font, line.left))
				}
			}
		}
		val rowW = maxLabel + ROW_GAP + maxValue + (if (maxMeta > 0) META_GAP + maxMeta else 0)
		val contentW = maxOf(maxText, rowW)
		return Metrics(contentW, maxMeta, heights)
	}

	private fun drawLine(
		g: GuiGraphicsExtractor,
		font: Font,
		line: Line,
		metrics: Metrics,
		boxX: Int,
		ty: Int,
		boxW: Int
	) {
		val innerLeft = boxX + PAD
		val innerRight = boxX + boxW - PAD
		when (line.kind) {
			Kind.DIVIDER -> {
				val mid = ty + DIVIDER_H / 2
				PvDraw.fill(g, innerLeft, mid, boxW - PAD * 2, 1, PvDraw.COLOR_DIVIDER)
			}
			Kind.BLANK -> {}
			Kind.ROW -> {
				drawIcon(g, font, line, innerLeft, ty)
				drawSpans(g, font, line.left, innerLeft + iconOffset(line), ty)
				val valueW = widthOf(font, line.value)
				val valueColRight = innerRight - (if (metrics.maxMeta > 0) META_GAP + metrics.maxMeta else 0)
				val valueX = valueColRight - valueW
				drawSpans(g, font, line.value, valueX, ty)
				if (line.meta.isNotEmpty()) {
					drawSpans(g, font, line.meta, valueColRight + META_GAP, ty)
				}
			}
			// Slight indent so instructions don't read as data rows.
			Kind.ACTION -> drawSpans(g, font, line.left, innerLeft + 4, ty)
			Kind.TEXT, Kind.META -> {
				drawIcon(g, font, line, innerLeft, ty)
				drawSpans(g, font, line.left, innerLeft + iconOffset(line), ty)
			}
		}
	}

	private fun iconOffset(line: Line): Int = if (line.hasIcon()) ICON_W else 0

	private fun drawIcon(g: GuiGraphicsExtractor, font: Font, line: Line, x: Int, ty: Int) {
		if (!line.hasIcon()) {
			return
		}
		val iy = ty + (font.lineHeight - ICON_SIZE) / 2 - 1
		if (line.iconId != null) {
			SkyBlockIconRenderer.draw(g, line.icon, line.iconId, x, iy, ICON_SIZE)
			return
		}
		val icon = line.icon ?: return
		val scale = ICON_SIZE / 16f
		g.pose().pushMatrix()
		g.pose().translate(x.toFloat(), iy.toFloat())
		g.pose().scale(scale, scale)
		g.item(icon, 0, 0)
		g.pose().popMatrix()
	}

	private fun drawSpans(g: GuiGraphicsExtractor, font: Font, spans: List<Span?>, x: Int, y: Int) {
		var cx = x
		for (span in spans) {
			if (span == null || span.text.isEmpty()) {
				continue
			}
			// Obfuscated / italic need Component rendering so the font applies §k scramble.
			if (span.obfuscated || span.italic) {
				val c = span.toComponent()
				PvDraw.text(g, font, c, cx, y)
				cx += font.width(c)
			} else if (span.bold) {
				PvDraw.textBold(g, font, span.text, cx, y, span.color)
				cx += PvDraw.widthBold(font, span.text)
			} else {
				PvDraw.text(g, font, span.text, cx, y, span.color)
				cx += font.width(span.text)
			}
		}
	}

	private fun widthOf(font: Font, spans: List<Span>): Int = spans.sumOf { font.width(it.toComponent()) }

	private fun drawLegacyBox(
		g: GuiGraphicsExtractor,
		font: Font,
		rendered: List<Component>,
		boxX: Int,
		boxY: Int,
		boxW: Int,
		boxH: Int,
		pad: Int,
		lineH: Int,
		screenW: Int,
		screenH: Int
	) {
		var x = boxX
		var y = boxY
		if (x + boxW > screenW - 4) {
			x = screenW - boxW - 4
		}
		if (y + boxH > screenH - 4) {
			y = screenH - boxH - 4
		}
		if (x < 4) {
			x = 4
		}
		if (y < 4) {
			y = 4
		}
		PvDraw.popupSurface(g, x, y, boxW, boxH)
		var ty = y + pad
		for (line in rendered) {
			if (line.string.isNotEmpty()) {
				PvDraw.text(g, font, line, x + pad, ty)
			}
			ty += lineH
		}
	}

	private class Metrics(val contentW: Int, val maxMeta: Int, val heights: IntArray)
}
