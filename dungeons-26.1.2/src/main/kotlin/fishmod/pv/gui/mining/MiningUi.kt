/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.mining

import com.mojang.blaze3d.platform.InputConstants
import fishmod.pv.data.ColeWeight
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.Leveling
import fishmod.pv.data.MiningSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import java.util.Locale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

/** Shared mining GUI helpers and constants. */
object MiningUi {
	const val PAD = 6
	const val GAP = 6
	const val BAR_LABEL_GAP = 2
	const val BAR_AFTER = 4
	const val STAT_ROW = 12
	const val SEP_GAP = 10
	const val BAR_HOTM = 0xFF5B8CFF.toInt()
	const val BAR_MINING = 0xFFAAAAAA.toInt()
	const val BAR_MITHRIL = 0xFF55AA55.toInt()
	const val BAR_GEM = 0xFFCC55CC.toInt()
	const val BAR_GLACITE = 0xFF55CCCC.toInt()
	const val PLACED = 0xFF55FF55.toInt()
	const val FOUND = 0xFFE8C84A.toInt()
	const val MISSING = 0xFF9A9AAC.toInt()
	const val ITEM_SLOT_BG = 0x1CFFFFFF
	const val ITEM_SLOT_BORDER = 0x00FFFFFF

	@JvmStatic
	fun drawHover(
		g: GuiGraphicsExtractor, font: Font, zones: List<HoverZone>, snapshot: MiningSnapshot,
		mx: Int, my: Int, screenW: Int, screenH: Int,
	) {
		val zone = zones.firstOrNull { mx >= it.x && mx < it.x + it.w && my >= it.y && my < it.y + it.h } ?: return
		val tip = if (zone.coleWeight) coleWeightHover(snapshot.coleWeight, leftShiftDown()) else zone.lines
		PvTooltip.drawStyled(g, font, tip, mx, my, screenW, screenH)
	}

	@JvmStatic
	fun drawBar(
		g: GuiGraphicsExtractor, font: Font, label: String, value: String?, fill: Float, maxed: Boolean,
		color: Int, hover: String?, x: Int, y: Int, w: Int, mx: Int, my: Int, zones: MutableList<HoverZone>,
	): Int = drawBar(g, font, label, value, fill, maxed, color, Leveling.hoverTextLines(hover), x, y, w, mx, my, zones)

	@JvmStatic
	fun drawBar(
		g: GuiGraphicsExtractor, font: Font, label: String, value: String?, fill: Float, maxed: Boolean,
		color: Int, hover: List<PvTooltip.Line>?, x: Int, y: Int, w: Int,
		@Suppress("UNUSED_PARAMETER") mx: Int, @Suppress("UNUSED_PARAMETER") my: Int, zones: MutableList<HoverZone>,
	): Int {
		val shown = fitValue(font, label, value ?: "", w)
		PvDraw.labeledBar(g, font, trim(font, label, maxOf(24, w - font.width(shown) - 8)), shown, fill, x, y, w, color, maxed)
		val bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT
		if (!hover.isNullOrEmpty()) {
			zones.add(HoverZone.of(x, y, w, bottom - y, hover))
		}
		return bottom
	}

	@JvmStatic
	fun barRowH(font: Font): Int = font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT + BAR_AFTER

	@JvmStatic
	fun coloredLabelStat(
		g: GuiGraphicsExtractor, font: Font, label: String, value: String?,
		x: Int, y: Int, w: Int, labelColor: Int, valueColor: Int,
	): Int {
		val r = value ?: ""
		val leftMax = maxOf(8, w - font.width(r) - 6)
		PvDraw.text(g, font, trim(font, label, leftMax), x, y, labelColor)
		PvDraw.textRight(g, font, r, x + w, y, valueColor)
		return y + STAT_ROW
	}

	@JvmStatic
	fun statLine(g: GuiGraphicsExtractor, font: Font, label: String, value: String?, x: Int, y: Int, w: Int, valueColor: Int): Int =
		coloredLabelStat(g, font, label, value, x, y, w, PvDraw.COLOR_MUTED, valueColor)

	@JvmStatic
	fun wrapText(g: GuiGraphicsExtractor, font: Font, text: String?, x: Int, y: Int, w: Int, color: Int) {
		val t = if (text.isNullOrBlank()) "-" else text
		PvDraw.text(g, font, if (font.width(t) <= w) t else trim(font, t, w), x, y, color)
	}

	/**
	 * Horizontal rule centered between previous and next text.
	 * `y` is the cursor after a STAT_ROW-based line; corrects for unused row slack.
	 */
	@JvmStatic
	fun sectionSeparator(g: GuiGraphicsExtractor, font: Font, panelX: Int, y: Int, panelW: Int): Int {
		val visualBottom = y - maxOf(0, STAT_ROW - font.lineHeight)
		val pad = SEP_GAP / 2
		val lineY = visualBottom + pad
		val lineInset = PAD + 6
		val lineW = maxOf(0, panelW - lineInset * 2)
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, PvDraw.COLOR_DIVIDER)
		}
		return lineY + 1 + pad
	}

	/** Header + three powder totals (no bars). Returns bottom Y. */
	@JvmStatic
	fun drawPowdersBlock(
		g: GuiGraphicsExtractor, font: Font, snapshot: MiningSnapshot, x: Int, y: Int, w: Int, zones: MutableList<HoverZone>,
	): Int {
		PvDraw.text(g, font, "Powders", x, y, PvDraw.COLOR_MUTED)
		var ly = y + font.lineHeight + 3
		for ((name, powder, color) in listOf(
			Triple("Mithril", snapshot.mithril, BAR_MITHRIL),
			Triple("Gemstone", snapshot.gemstone, BAR_GEM),
			Triple("Glacite", snapshot.glacite, BAR_GLACITE),
		)) {
			ly = coloredLabelStat(g, font, name, FormatUtil.shortXp(powder.total().toDouble()), x, ly, w, color, PvDraw.COLOR_TEXT) + 1
			zones.add(HoverZone.of(x, ly - STAT_ROW, w, STAT_ROW, powderHover(name, powder)))
		}
		return ly
	}

	@JvmStatic
	fun powderHover(name: String, powder: MiningSnapshot.Powder): List<PvTooltip.Line> = listOf(
		PvTooltip.Line.of("$name powder", PvDraw.COLOR_TEXT),
		PvTooltip.Line.of("Total: " + FormatUtil.commas(powder.total()), PvDraw.COLOR_GOLD),
		PvTooltip.Line.of("Available: " + FormatUtil.commas(powder.available), PvDraw.COLOR_MUTED),
		PvTooltip.Line.of("Spent: " + FormatUtil.commas(powder.spent), PvDraw.COLOR_MUTED),
	)

	@JvmStatic
	fun coleWeightHover(weight: ColeWeight.Result, shiftDetails: Boolean): List<PvTooltip.Line> {
		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.of("ColeWeight: " + FormatUtil.oneDecimal(weight.total), PvDraw.COLOR_GOLD))
		tip.add(PvTooltip.Line.blank())

		val byCat = weight.byCategory()
		val miningXp = firstLine(byCat["experience"], "Mining Experience")
		if (miningXp != null) {
			tip.add(PvTooltip.Line.of("Mining XP: " + FormatUtil.oneDecimal(miningXp.weight), PvDraw.COLOR_TEXT))
			tip.add(PvTooltip.Line.blank())
		}

		val powders = byCat["powder"]
		if (!powders.isNullOrEmpty()) {
			tip.add(PvTooltip.Line.of("Powder", PvDraw.COLOR_ACCENT))
			tip.add(PvTooltip.Line.of("Mithril: " + FormatUtil.oneDecimal(lineWeight(powders, "Mithril Powder")), BAR_MITHRIL))
			tip.add(PvTooltip.Line.of("Gemstone: " + FormatUtil.oneDecimal(lineWeight(powders, "Gemstone Powder")), BAR_GEM))
			tip.add(PvTooltip.Line.of("Glacite: " + FormatUtil.oneDecimal(lineWeight(powders, "Glacite Powder")), BAR_GLACITE))
			tip.add(PvTooltip.Line.blank())
		}

		val collections = byCat["collection"]
		tip.add(PvTooltip.Line.of("Collection", PvDraw.COLOR_ACCENT))
		if (shiftDetails && !collections.isNullOrEmpty()) {
			for (line in collections.sortedByDescending { it.weight }) {
				tip.add(PvTooltip.Line.of(line.label + ": " + FormatUtil.oneDecimal(line.weight), PvDraw.COLOR_MUTED))
			}
		} else {
			tip.add(PvTooltip.Line.of("Overall: " + FormatUtil.oneDecimal(sumWeight(collections)), PvDraw.COLOR_TEXT))
		}
		tip.add(PvTooltip.Line.blank())

		val misc = byCat["miscellaneous"]
		tip.add(PvTooltip.Line.of("Misc", PvDraw.COLOR_ACCENT))
		tip.add(PvTooltip.Line.of("Worm kills: " + FormatUtil.oneDecimal(lineWeight(misc, "Worm Kills")), PvDraw.COLOR_MUTED))
		tip.add(PvTooltip.Line.of("Nuc runs: " + FormatUtil.oneDecimal(lineWeight(misc, "Nucleus Runs")), PvDraw.COLOR_MUTED))

		if (!shiftDetails) {
			tip.add(PvTooltip.Line.blank())
			tip.add(PvTooltip.Line.of("Hold L-shift for details", PvDraw.COLOR_MUTED))
		}
		return tip
	}

	@JvmStatic
	fun firstLine(lines: List<ColeWeight.Line>?, label: String): ColeWeight.Line? {
		if (lines == null) {
			return null
		}
		return lines.firstOrNull { it.label == label } ?: lines.firstOrNull()
	}

	@JvmStatic
	fun lineWeight(lines: List<ColeWeight.Line>?, label: String): Double = lines?.firstOrNull { it.label == label }?.weight ?: 0.0

	@JvmStatic
	fun sumWeight(lines: List<ColeWeight.Line>?): Double = lines?.sumOf { it.weight } ?: 0.0

	@JvmStatic
	fun drawItemIcon(g: GuiGraphicsExtractor, icon: ItemStack?, x: Int, y: Int, size: Int) {
		if (icon == null || icon.isEmpty) {
			return
		}
		if (size == 16) {
			g.item(icon, x, y)
			return
		}
		val scale = size / 16f
		g.pose().pushMatrix()
		g.pose().translate(x.toFloat(), y.toFloat())
		g.pose().scale(scale, scale)
		g.item(icon, 0, 0)
		g.pose().popMatrix()
	}

	@JvmStatic
	fun title(raw: String): String = raw.replace('-', '_').split("_")
		.filter { it.isNotEmpty() }
		.joinToString(" ") { it[0].uppercaseChar() + it.substring(1).lowercase(Locale.ROOT) }

	@JvmStatic
	fun fitValue(font: Font, label: String, value: String, w: Int): String = trim(font, value, maxOf(8, w - font.width(label) - 10))

	@JvmStatic
	fun trim(font: Font, text: String?, maxW: Int): String {
		if (text == null) {
			return ""
		}
		if (font.width(text) <= maxW) {
			return text
		}
		val ellipsis = "..."
		val ew = font.width(ellipsis)
		if (maxW <= ew) {
			return ellipsis
		}
		val sb = StringBuilder(text)
		while (sb.isNotEmpty() && font.width(sb.toString()) + ew > maxW) {
			sb.setLength(sb.length - 1)
		}
		return "$sb$ellipsis"
	}

	@JvmStatic
	fun leftShiftDown(): Boolean {
		val window = Minecraft.getInstance()?.window ?: return false
		return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
	}

	@JvmRecord
	data class HoverZone(val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<PvTooltip.Line>, val coleWeight: Boolean) {
		companion object {
			@JvmStatic
			fun of(x: Int, y: Int, w: Int, h: Int, lines: List<PvTooltip.Line>): HoverZone = HoverZone(x, y, w, h, lines, false)

			@JvmStatic
			fun coleWeight(x: Int, y: Int, w: Int, h: Int): HoverZone = HoverZone(x, y, w, h, listOf(), true)
		}
	}
}
