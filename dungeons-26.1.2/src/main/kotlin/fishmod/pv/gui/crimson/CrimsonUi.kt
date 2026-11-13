/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.crimson

import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import java.util.Locale
import kotlin.math.pow
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object CrimsonUi {
	const val PAD = 6
	const val GAP = 6
	const val STAT_ROW = 12
	const val SLOT = 18
	const val SLOT_GAP = 3
	const val ITEM_SLOT_BG = 0x1CFFFFFF
	const val ITEM_SLOT_BORDER = 0x00FFFFFF
	const val FLIP_MS = 480
	const val SEP_GAP = 10
	const val PANEL_HOVER = 0x0AFFFFFF

	const val MAGE_COLOR = 0xFFD97FFF.toInt()

	const val BARB_COLOR = 0xFFFF7777.toInt()

	const val KUUDRA_COLOR = 0xFFFF5555.toInt()

	const val DOJO_COLOR = 0xFFFFAA00.toInt()

	const val SHOP_HEADER_COLOR = 0xFFFF5555.toInt()
	const val ENABLED = 0xFF55FF55.toInt()
	const val DISABLED = 0xFF555555.toInt()

	private val SECTION_CODE = Regex("§.")
	private val AMP_CODE = Regex("&[0-9a-fk-or]")

	/**
	 * Draw an item at native 16×16 when possible (avoids fuzzy downscales).
	 * Only scales when the target box is smaller than 16px.
	 */
	@JvmStatic
	fun drawItemIcon(g: GuiGraphicsExtractor, icon: ItemStack?, x: Int, y: Int, size: Int) {
		if (icon == null || icon.isEmpty) {
			return
		}
		if (size >= 16) {
			val pad = (size - 16) / 2
			g.item(icon, x + pad, y + pad)
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
	fun sectionSeparator(g: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") font: Font, panelX: Int, y: Int, panelW: Int): Int {
		val lineInset = PAD + 4
		val lineW = maxOf(0, panelW - lineInset * 2)
		val lineY = y + (SEP_GAP - 1) / 2
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF)
		}
		return y + SEP_GAP
	}

	@JvmStatic
	fun statLine(g: GuiGraphicsExtractor, font: Font, label: String, value: String?, x: Int, y: Int, w: Int, valueColor: Int): Int {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED)
		PvDraw.textRight(g, font, if (value.isNullOrBlank()) "-" else value, x + w, y, valueColor)
		return y + STAT_ROW
	}

	@JvmStatic
	fun drawHover(g: GuiGraphicsExtractor, font: Font, zones: List<HoverZone>, mx: Int, my: Int, screenW: Int, screenH: Int) {
		val zone = zones.firstOrNull { mx >= it.x && mx < it.x + it.w && my >= it.y && my < it.y + it.h } ?: return
		if (!zone.components.isNullOrEmpty()) {
			PvTooltip.drawComponents(g, font, zone.components, mx, my, screenW, screenH)
		} else if (zone.lines != null) {
			PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH)
		}
	}

	@JvmStatic
	fun easeInOutCubic(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).toDouble().pow(3).toFloat() / 2f

	@JvmStatic
	fun formatAgo(ms: Long): String {
		if (ms <= 0L) {
			return "-"
		}
		val ago = maxOf(0L, System.currentTimeMillis() - ms)
		return FormatUtil.prettySpan(ago) + " ago"
	}

	@JvmStatic
	fun prettyRingtone(raw: String?): String {
		if (raw.isNullOrBlank()) {
			return "-"
		}
		return raw.replace('-', '_').split("_")
			.filter { it.isNotBlank() }
			.joinToString(" ") { it[0].uppercaseChar() + it.substring(1).lowercase(Locale.ROOT) }
	}

	@JvmStatic
	fun trim(font: Font, text: String?, maxW: Int): String {
		if (text == null) {
			return ""
		}
		if (font.width(text) <= maxW) {
			return text
		}
		val ellipsis = "..."
		val budget = maxOf(0, maxW - font.width(ellipsis))
		val sb = StringBuilder()
		for (c in text) {
			if (font.width(sb.toString() + c) > budget) {
				break
			}
			sb.append(c)
		}
		return "$sb$ellipsis"
	}

	@JvmStatic
	fun stripFormatting(text: String?): String {
		if (text.isNullOrEmpty()) {
			return ""
		}
		return text.replace(SECTION_CODE, "").replace(AMP_CODE, "")
	}

	@JvmStatic
	fun visible(y: Int, h: Int, clipTop: Int, clipBottom: Int): Boolean = y + h > clipTop && y < clipBottom

	@JvmRecord
	data class HoverZone(
		val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<PvTooltip.Line>?, val components: List<Component>?,
	) {
		companion object {
			@JvmStatic
			fun of(x: Int, y: Int, w: Int, h: Int, lines: List<PvTooltip.Line>?): HoverZone = HoverZone(x, y, w, h, lines, null)

			@JvmStatic
			fun ofComponents(x: Int, y: Int, w: Int, h: Int, components: List<Component>?): HoverZone =
				HoverZone(x, y, w, h, null, components)
		}
	}
}
