/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.essence

import fishmod.pv.data.DungeonSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.dungeons.EssencePerkTips
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import kotlin.math.roundToInt
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Essence shop column: icon, name and balance header, then one row per perk.
 * Packs rows into the given height instead of shrinking the font.
 */
object EssenceShopColumn {
	const val MAXED_COLOR = 0xFF7CFF9A.toInt()

	fun interface HoverSink {
		fun add(x: Int, y: Int, w: Int, h: Int, lines: List<PvTooltip.Line>)
	}

	@JvmStatic
	fun draw(
		g: GuiGraphicsExtractor,
		font: Font,
		shop: DungeonSnapshot.EssenceShop?,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		headerColor: Int,
		rowGap: Int,
		hovers: HoverSink?,
	) {
		if (w <= 0 || h <= 0 || shop == null) {
			return
		}
		val perks = shop.perks
		val headerH = maxOf(16, font.lineHeight + 2)
		val n = perks.size
		val bodyH = maxOf(1, h - headerH - 2)
		val packed = if (n <= 0) font.lineHeight + 2 else bodyH / n
		val rowH = minOf(16 + maxOf(0, rowGap), maxOf(font.lineHeight, packed))

		g.enableScissor(x, y, x + w + 3, y + h)

		val name = shop.name
		val bal = balance(shop.balance)
		val headerLabelX = 16 + 3
		val avail = maxOf(1, w - headerLabelX)
		val nameW = PvDraw.widthBold(font, name)
		val balW = PvDraw.widthBold(font, bal)
		val gapNameBal = 4
		var headerScale = 1f
		val needed = (nameW + gapNameBal + balW) * headerScale
		if (needed > avail) {
			headerScale = maxOf(0.7f, avail / (nameW + gapNameBal + balW).toFloat())
		}
		val textH = maxOf(1, (font.lineHeight * headerScale).roundToInt())
		val headerAlign = PvDraw.IconTextAlign.of(0, headerH, 16, textH)
		g.item(EssenceIcons.shop(shop.iconId), x, y + headerAlign.iconY)
		val drawBalW = maxOf(1, (balW * headerScale).roundToInt())
		if (headerScale >= 0.999f) {
			PvDraw.textBold(g, font, name, x + headerLabelX, y + headerAlign.textY, headerColor)
			PvDraw.textBold(g, font, bal, x + w - balW, y + headerAlign.textY, headerColor)
		} else {
			PvDraw.textBoldScaled(g, font, name, x + headerLabelX, y + headerAlign.textY, headerColor, headerScale)
			PvDraw.textBoldScaled(g, font, bal, x + w - drawBalW, y + headerAlign.textY, headerColor, headerScale)
		}

		var ly = y + headerH + 2
		val labelX = 16 + 2
		for (perk in perks) {
			val iconSize = minOf(16, rowH)
			val iconY = ly + maxOf(0, (rowH - iconSize) / 2)
			if (iconSize >= 16) {
				g.item(EssenceIcons.perk(perk.id), x, iconY)
			} else {
				g.pose().pushMatrix()
				g.pose().translate(x.toFloat(), iconY.toFloat())
				val s = iconSize / 16f
				g.pose().scale(s, s)
				g.item(EssenceIcons.perk(perk.id), 0, 0)
				g.pose().popMatrix()
			}
			val right = "${perk.level}/${perk.maxLevel}"
			val rightW = font.width(right)
			var left = perk.name
			val nameAvail = maxOf(4, w - labelX - rightW - 2)
			if (font.width(left) > nameAvail) {
				left = trimToWidth(font, left, nameAvail)
			}
			val textY = ly + maxOf(0, (rowH - font.lineHeight) / 2)
			val valueColor = if (perk.maxed()) MAXED_COLOR else PvDraw.COLOR_TEXT
			PvDraw.text(g, font, left, x + labelX, textY, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, right, x + w, textY, valueColor)
			hovers?.add(x, ly, w, rowH, EssencePerkTips.tip(perk))
			ly += rowH
		}

		g.disableScissor()
	}

	@JvmStatic
	fun balance(balance: Long): String {
		if (balance < 1000L) {
			return FormatUtil.commas(balance)
		}
		return FormatUtil.oneDecimal(balance / 1000.0).removeSuffix(".0") + "k"
	}

	private fun trimToWidth(font: Font, text: String, maxW: Int): String {
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
		return "$out$ellipsis"
	}
}
