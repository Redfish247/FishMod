/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home.page

import fishmod.pv.data.CrimsonSnapshot
import fishmod.pv.data.DungeonSnapshot
import fishmod.pv.data.EssenceSummary
import fishmod.pv.data.ForagingSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.MiningSnapshot
import fishmod.pv.dungeons.EssencePerkTips
import fishmod.pv.dungeons.EssenceShopCosts
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.essence.EssenceIcons
import fishmod.pv.gui.essence.EssenceLayout
import fishmod.pv.gui.essence.EssenceShopColumn
import fishmod.pv.gui.essence.EssenceShops
import java.util.function.Function
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

class EssencePage {
	private var entries: List<EssenceShops.Entry> = EssenceShops.resolve(null)
	private var selected = 0
	private var selectorScroll = 0
	private var perkScroll = 0
	private var revealSelected = false
	private var selectorLayout: EssenceLayout.Selector? = null
	private var perkLayout: EssenceLayout.Perks? = null
	private val zones = ArrayList<Zone>()
	private val rowHits = ArrayList<RowHit>()

	fun apply(dungeons: DungeonSnapshot?, mining: MiningSnapshot?, foraging: ForagingSnapshot?, crimson: CrimsonSnapshot?) {
		applyShops(EssenceShops.lookup(dungeons, mining, foraging, crimson))
	}

	fun applyShops(lookup: Function<String, DungeonSnapshot.EssenceShop?>?) {
		entries = EssenceShops.resolve(lookup)
		zones.clear()
		rowHits.clear()
	}

	fun entries(): List<EssenceShops.Entry> = entries

	fun selectedEntry(): EssenceShops.Entry = entries[selected]

	fun select(id: String?): Boolean {
		val index = EssenceShops.indexOf(id)
		if (index < 0) {
			return false
		}
		select(index)
		return true
	}

	fun select(index: Int) {
		val clamped = maxOf(0, minOf(entries.size - 1, index))
		if (clamped != selected) {
			selected = clamped
			perkScroll = 0
		}
		revealSelected = true
	}

	fun render(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int) {
		zones.clear()
		rowHits.clear()
		val body = EssenceLayout.Rect(x, y, w, h)
		val selectorW = EssenceLayout.selectorWidth(w, widestRow(font))
		drawSelector(g, font, EssenceLayout.Rect(x, y, selectorW, h), mouseX, mouseY)
		drawDetail(g, font, EssenceLayout.detail(body, selectorW), mouseX, mouseY)
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		val zone = zones.firstOrNull { it.rect.contains(mouseX.toDouble(), mouseY.toDouble()) } ?: return
		PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH)
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		val hit = rowHits.firstOrNull { it.rect.contains(mx, my) } ?: return false
		select(hit.index)
		return true
	}

	fun mouseScrolled(mx: Double, my: Double, scrollY: Double): Boolean {
		if (scrollY == 0.0) {
			return false
		}
		val dir = if (scrollY > 0.0) -1 else 1
		val sel = selectorLayout
		if (sel != null && sel.panel.contains(mx, my)) {
			if (sel.maxScroll() <= 0) {
				return false
			}
			selectorScroll = EssenceLayout.clampScroll(selectorScroll + dir * sel.rowH, sel.maxScroll())
			return true
		}
		val perks = perkLayout
		if (perks != null && perks.list.contains(mx, my) && perks.maxScroll() > 0) {
			perkScroll = EssenceLayout.clampScroll(perkScroll + dir * perks.rowH, perks.maxScroll())
			return true
		}
		return false
	}

	fun keyPressed(keyCode: Int): Boolean {
		if (keyCode == fishmod.compat.GLFW.GLFW_KEY_DOWN) {
			select(selected + 1)
			return true
		}
		if (keyCode == fishmod.compat.GLFW.GLFW_KEY_UP) {
			select(selected - 1)
			return true
		}
		return false
	}

	private fun widestRow(font: Font): Int =
		entries.maxOfOrNull { 16 + ICON_GAP + font.width(it.def.name) + NAME_BALANCE_GAP + font.width(balanceShort(it.shop)) }
			?.coerceAtLeast(0) ?: 0

	private fun drawSelector(g: GuiGraphicsExtractor, font: Font, panel: EssenceLayout.Rect, mouseX: Int, mouseY: Int) {
		PvDraw.innerPanel(g, panel.x, panel.y, panel.w, panel.h)
		val layout = EssenceLayout.selector(panel, font.lineHeight, GROUP_SIZES)
		selectorLayout = layout
		val list = layout.list
		if (revealSelected) {
			val top = layout.rowY[selected]
			if (top < selectorScroll) {
				selectorScroll = top
			} else if (top + layout.rowH > selectorScroll + list.h) {
				selectorScroll = top + layout.rowH - list.h
			}
			revealSelected = false
		}
		selectorScroll = EssenceLayout.clampScroll(selectorScroll, layout.maxScroll())
		val scroll = selectorScroll
		val mx = mouseX.toDouble()
		val my = mouseY.toDouble()
		val mouseInList = list.contains(mx, my)

		g.enableScissor(panel.x + 1, list.y, panel.right() - 1, list.bottom())
		for (gi in layout.headerY.indices) {
			PvDraw.sectionHeader(g, font, EssenceShops.Group.entries[gi].label(), list.x, list.y + layout.headerY[gi] - scroll, list.w)
		}
		for (dy in layout.dividerY) {
			PvDraw.fill(g, list.x, list.y + dy - scroll, list.w, 1, PvDraw.COLOR_DIVIDER)
		}
		val rowH = layout.rowH
		val iconSize = minOf(16, rowH)
		for ((i, entry) in entries.withIndex()) {
			val rowTop = list.y + layout.rowY[i] - scroll
			if (rowTop + rowH <= list.y || rowTop >= list.bottom()) {
				continue
			}
			val row = EssenceLayout.Rect(panel.x + 1, rowTop, panel.w - 2, rowH)
			val hovered = mouseInList && row.contains(mx, my)
			if (i == selected) {
				PvDraw.fill(g, row.x, row.y, row.w, row.h, ROW_SELECTED)
			} else if (hovered) {
				PvDraw.fill(g, row.x, row.y, row.w, row.h, ROW_HOVER)
			}
			val align = PvDraw.IconTextAlign.of(rowTop, rowH, iconSize, font.lineHeight)
			drawIcon(g, EssenceIcons.shop(entry.shop.iconId), list.x, align.iconY, iconSize)
			val balance = balanceShort(entry.shop)
			val balanceW = font.width(balance)
			val nameX = list.x + iconSize + ICON_GAP
			val name = trim(font, entry.def.name, maxOf(4, list.right() - balanceW - NAME_BALANCE_GAP - nameX))
			PvDraw.text(g, font, name, nameX, align.textY, entry.def.color)
			PvDraw.textRight(g, font, balance, list.right(), align.textY, if (loaded(entry.shop)) PvDraw.COLOR_TEXT else PvDraw.COLOR_MUTED)
			val hitTop = maxOf(rowTop, list.y)
			val hitBottom = minOf(rowTop + rowH, list.bottom())
			rowHits.add(RowHit(i, EssenceLayout.Rect(row.x, hitTop, row.w, hitBottom - hitTop)))
		}
		g.disableScissor()

		if (layout.maxScroll() > 0) {
			drawScrollCue(g, panel.right() - 3, list.y, list.h, scroll, layout.maxScroll())
		}
	}

	private fun drawDetail(g: GuiGraphicsExtractor, font: Font, detail: EssenceLayout.Rect, mouseX: Int, mouseY: Int) {
		PvDraw.innerPanel(g, detail.x, detail.y, detail.w, detail.h)
		val entry = selectedEntry()
		val shop = entry.shop
		val color = entry.def.color
		val cx = detail.x + EssenceLayout.PAD
		val cw = detail.w - EssenceLayout.PAD * 2
		val top = detail.y + EssenceLayout.PAD

		val headerAlign = PvDraw.IconTextAlign.of(top, EssenceLayout.DETAIL_HEADER_H, 16, font.lineHeight)
		g.item(EssenceIcons.shop(shop.iconId), cx, headerAlign.iconY)
		val balance = if (loaded(shop)) FormatUtil.commas(shop.balance) else "..."
		val balanceLabel = "Balance "
		val balanceW = PvDraw.widthBold(font, balance)
		val titleX = cx + 16 + ICON_GAP
		var title = entry.def.name + " Essence"
		val titleRoom = cx + cw - titleX - balanceW - NAME_BALANCE_GAP
		val showLabel = PvDraw.widthBold(font, title) + font.width(balanceLabel) <= titleRoom
		if (!showLabel && PvDraw.widthBold(font, title) > titleRoom) {
			while (title.length > 1 && PvDraw.widthBold(font, "$title...") > titleRoom) {
				title = title.substring(0, title.length - 1)
			}
			title += "..."
		}
		PvDraw.textBold(g, font, title, titleX, headerAlign.textY, color)
		PvDraw.textBold(g, font, balance, cx + cw - balanceW, headerAlign.textY, PvDraw.COLOR_TEXT)
		if (showLabel) {
			PvDraw.textRight(g, font, balanceLabel, cx + cw - balanceW, headerAlign.textY, PvDraw.COLOR_MUTED)
		}
		PvDraw.fill(g, cx, top + EssenceLayout.DETAIL_HEADER_H + 1, cw, 1, PvDraw.COLOR_DIVIDER)

		val summary = EssenceSummary.of(shop) { EssenceShopCosts.costs(it) }
		val statY = top + EssenceLayout.DETAIL_HEADER_H + 4
		val colW = (cw - 12) / 2
		val rightX = cx + cw - colW
		if (summary.loaded) {
			val maxedColor = if (summary.complete()) EssenceShopColumn.MAXED_COLOR else PvDraw.COLOR_TEXT
			stat(g, font, "Perks maxed", "${summary.perksMaxed}/${summary.perkCount}", maxedColor, cx, statY, colW)
			stat(g, font, "Perk levels", "${summary.levels}/${summary.maxLevels}", maxedColor, cx, statY + EssenceLayout.STAT_ROW, colW)
			if (summary.complete()) {
				stat(g, font, "Essence to max", "Maxed", EssenceShopColumn.MAXED_COLOR, rightX, statY, colW)
			} else {
				val toMax = FormatUtil.commas(summary.toMax) + (if (summary.unknownCostPerks > 0) "+" else "")
				stat(g, font, "Essence to max", toMax, PvDraw.COLOR_GOLD, rightX, statY, colW)
				val y2 = statY + EssenceLayout.STAT_ROW
				if (summary.unknownCostPerks > 0) {
					val n = summary.unknownCostPerks
					stat(g, font, "Unknown cost", n.toString() + (if (n == 1) " perk" else " perks"), PvDraw.COLOR_MUTED, rightX, y2, colW)
				} else {
					val needed = maxOf(0L, summary.toMax - shop.balance)
					stat(
						g, font, "Still needed", if (needed == 0L) "None" else FormatUtil.commas(needed),
						if (needed == 0L) EssenceShopColumn.MAXED_COLOR else PvDraw.COLOR_TEXT, rightX, y2, colW
					)
				}
			}
		} else {
			stat(g, font, "Perks", "Loading...", PvDraw.COLOR_MUTED, cx, statY, colW)
		}
		zones.add(Zone(EssenceLayout.Rect(cx, statY, cw, EssenceLayout.STAT_ROW * 2), summaryTip(entry, summary)))

		val barY = statY + EssenceLayout.STAT_ROW * 2 + 4
		val progress = if (summary.maxLevels <= 0) 0F else summary.levels / summary.maxLevels.toFloat()
		PvDraw.progressBar(g, cx, barY, cw, EssenceLayout.BAR_H, progress, PvDraw.COLOR_BAR_FILL, summary.loaded && summary.complete())

		val sectionY = barY + EssenceLayout.BAR_H + 6
		PvDraw.sectionHeader(g, font, "Perks", cx, sectionY, cw)
		drawPerks(g, font, detail, shop, mouseX, mouseY)
	}

	private fun drawPerks(
		g: GuiGraphicsExtractor, font: Font, detail: EssenceLayout.Rect, shop: DungeonSnapshot.EssenceShop, mouseX: Int, mouseY: Int
	) {
		val perks = shop.perks
		val layout = EssenceLayout.perks(detail, font.lineHeight, perks.size)
		perkLayout = layout
		val list = layout.list
		if (perks.isEmpty()) {
			PvDraw.textCentered(g, font, "Loading...", list.x + list.w / 2, list.y + 4, PvDraw.COLOR_MUTED)
			return
		}
		perkScroll = EssenceLayout.clampScroll(perkScroll, layout.maxScroll())
		val scroll = perkScroll
		val rowH = layout.rowH
		val mx = mouseX.toDouble()
		val my = mouseY.toDouble()
		val mouseInList = list.contains(mx, my)

		g.enableScissor(list.x, list.y, list.right(), list.bottom())
		if (layout.cols > 1) {
			val lineX = list.x + layout.colW + EssenceLayout.PERK_COL_GAP / 2
			PvDraw.fill(g, lineX, list.y, 1, minOf(list.h, layout.contentH - scroll), PvDraw.COLOR_DIVIDER)
		}
		for ((i, perk) in perks.withIndex()) {
			val px = list.x + layout.col(i) * (layout.colW + EssenceLayout.PERK_COL_GAP)
			val py = list.y + layout.row(i) * rowH - scroll
			if (py + rowH <= list.y || py >= list.bottom()) {
				continue
			}
			val row = EssenceLayout.Rect(px, py, layout.colW, rowH)
			if (mouseInList && row.contains(mx, my)) {
				PvDraw.fill(g, row.x, row.y, row.w, row.h, ROW_HOVER)
			}
			val align = PvDraw.IconTextAlign.of(py, rowH, 16, font.lineHeight)
			g.item(EssenceIcons.perk(perk.id), px + 1, align.iconY)
			val levels = "${perk.level}/${perk.maxLevel}"
			val levelColor = when {
				perk.maxed() -> EssenceShopColumn.MAXED_COLOR
				perk.level <= 0 -> PvDraw.COLOR_MUTED
				else -> PvDraw.COLOR_TEXT
			}
			val right = px + layout.colW - 2
			val nameX = px + 1 + 16 + ICON_GAP
			val name = trim(font, perk.name, maxOf(4, right - font.width(levels) - NAME_BALANCE_GAP - nameX))
			PvDraw.text(g, font, name, nameX, align.textY, if (perk.level <= 0) PvDraw.COLOR_MUTED else PvDraw.COLOR_TEXT)
			PvDraw.textRight(g, font, levels, right, align.textY, levelColor)
			val hitTop = maxOf(py, list.y)
			val hitBottom = minOf(py + rowH, list.bottom())
			zones.add(Zone(EssenceLayout.Rect(px, hitTop, layout.colW, hitBottom - hitTop), EssencePerkTips.tip(perk)))
		}
		g.disableScissor()

		if (layout.maxScroll() > 0) {
			drawScrollCue(g, detail.right() - 3, list.y, list.h, scroll, layout.maxScroll())
		}
	}

	private class Zone(val rect: EssenceLayout.Rect, val lines: List<PvTooltip.Line>)

	private class RowHit(val index: Int, val rect: EssenceLayout.Rect)

	private companion object {
		const val ROW_SELECTED = 0x50386CC8
		const val ROW_HOVER = 0x24FFFFFF
		const val SCROLL_TRACK = 0x662A2A35
		const val ICON_GAP = 4
		const val NAME_BALANCE_GAP = 8
		val GROUP_SIZES: List<Int> = EssenceShops.Group.entries.map { group -> EssenceShops.ALL.count { it.group == group } }

		fun stat(g: GuiGraphicsExtractor, font: Font, label: String, value: String, valueColor: Int, x: Int, y: Int, w: Int) {
			val valueW = font.width(value)
			PvDraw.text(g, font, trim(font, label, maxOf(4, w - valueW - 6)), x, y, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, value, x + w, y, valueColor)
		}

		fun drawIcon(g: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int, size: Int) {
			if (size >= 16) {
				g.item(stack, x, y)
				return
			}
			g.pose().pushMatrix()
			g.pose().translate(x.toFloat(), y.toFloat())
			val s = size / 16F
			g.pose().scale(s, s)
			g.item(stack, 0, 0)
			g.pose().popMatrix()
		}

		fun drawScrollCue(g: GuiGraphicsExtractor, x: Int, y: Int, h: Int, scroll: Int, maxScroll: Int) {
			PvDraw.fill(g, x, y, 2, h, SCROLL_TRACK)
			val thumbH = maxOf(8, h * h / (h + maxScroll))
			val thumbY = y + Math.round((h - thumbH) * (scroll / maxScroll.toFloat()))
			PvDraw.fill(g, x, thumbY, 2, thumbH, PvDraw.COLOR_ACCENT)
		}

		fun summaryTip(entry: EssenceShops.Entry, summary: EssenceSummary): List<PvTooltip.Line> {
			val shop = entry.shop
			val lines = ArrayList<PvTooltip.Line>()
			lines.add(PvTooltip.Line.title(entry.def.name + " Essence", entry.def.color))
			lines.add(PvTooltip.Line.divider())
			lines.add(PvTooltip.Line.row("Balance", PvDraw.COLOR_MUTED, FormatUtil.commas(shop.balance), PvDraw.COLOR_TEXT))
			if (!summary.loaded) {
				lines.add(PvTooltip.Line.meta("Loading shop..."))
				return lines
			}
			lines.add(PvTooltip.Line.row("Perks maxed", PvDraw.COLOR_MUTED, "${summary.perksMaxed}/${summary.perkCount}", PvDraw.COLOR_TEXT))
			lines.add(PvTooltip.Line.row("Perk levels", PvDraw.COLOR_MUTED, "${summary.levels}/${summary.maxLevels}", PvDraw.COLOR_TEXT))
			if (summary.complete()) {
				lines.add(PvTooltip.Line.of("All perks maxed", EssenceShopColumn.MAXED_COLOR))
				return lines
			}
			var toMax = FormatUtil.commas(summary.toMax)
			if (summary.unknownCostPerks > 0) {
				toMax += "+"
			}
			lines.add(PvTooltip.Line.row("Essence to max", PvDraw.COLOR_MUTED, toMax, PvDraw.COLOR_GOLD))
			if (summary.unknownCostPerks == 0) {
				val needed = maxOf(0L, summary.toMax - shop.balance)
				lines.add(
					if (needed == 0L) PvTooltip.Line.of("Enough to max", EssenceShopColumn.MAXED_COLOR)
					else PvTooltip.Line.row("Still needed", PvDraw.COLOR_MUTED, FormatUtil.commas(needed), PvDraw.COLOR_TEXT)
				)
			} else {
				lines.add(PvTooltip.Line.meta(
					if (summary.unknownCostPerks == 1) "1 perk has an unknown cost, not counted"
					else summary.unknownCostPerks.toString() + " perks have unknown costs, not counted"
				))
			}
			return lines
		}

		fun loaded(shop: DungeonSnapshot.EssenceShop): Boolean = shop.perks.isNotEmpty()

		fun balanceShort(shop: DungeonSnapshot.EssenceShop): String =
			if (loaded(shop)) EssenceShopColumn.balance(shop.balance) else "..."

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
			return sb.toString() + ellipsis
		}
	}
}
