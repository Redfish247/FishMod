/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import fishmod.pv.data.BestiaryData
import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.bestiary.BestiaryPage
import fishmod.pv.gui.nav.IconButtonBar
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

class BestiarySplitLayout {
	private var bestiaryCategoryTip: Component? = null
	private var bestiaryCategoryTipX = 0
	private var bestiaryCategoryTipY = 0

	fun categoryTip(): Component? = bestiaryCategoryTip

	fun categoryTipX(): Int = bestiaryCategoryTipX

	fun categoryTipY(): Int = bestiaryCategoryTipY

	fun render(
		g: GuiGraphicsExtractor,
		font: Font,
		bestiaryPage: BestiaryPage,
		inventoryBar: IconButtonBar,
		inventorySearch: EditBox?,
		inventorySearchQuery: String?,
		screenW: Int,
		screenH: Int,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
		delta: Float
	) {
		val searchH = InventorySplitLayout.SEARCH_H
		val searchGap = InventorySplitLayout.SEARCH_GAP

		PvDraw.roundedFill(g, x, y, w, searchH, 3, PvDraw.CELL)
		PvDraw.roundedRing(g, x, y, w, searchH, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL)
		val header = "Tiers " +
			FormatUtil.commas(bestiaryPage.totalUnlockedTiers().toLong()) +
			"/" +
			FormatUtil.commas(bestiaryPage.totalMaxTiers().toLong()) +
			"  ·  Milestone " +
			FormatUtil.commas(bestiaryPage.milestone().toLong())
		PvDraw.text(g, font, header, x + 6, y + (searchH - font.lineHeight) / 2, PvDraw.COLOR_MUTED)
		val searchBoxW = maxOf(80, w * 36 / 100)
		val searchBoxX = x + w - searchBoxW
		g.outline(
			searchBoxX, y, searchBoxW, searchH,
			if (inventorySearch != null && inventorySearch.isFocused) PvDraw.COLOR_ACCENT else PvDraw.COLOR_BORDER
		)
		InventorySplitLayout.layoutInventorySearch(font, inventorySearch, searchBoxX, y, searchBoxW)
		if (inventorySearch != null) {
			inventorySearch.extractWidgetRenderState(g, mouseX, mouseY, delta)
			if (inventorySearch.value.isEmpty()) {
				val hint = Component.translatable("fishmod.pv.bestiary.search_hint").string
				PvDraw.text(g, font, hint, inventorySearch.x, inventorySearch.y, PvDraw.COLOR_MUTED)
			}
		}

		BestiaryData.ensureLoaded()
		val cats = BestiaryData.categories()
		val btn = 18
		val btnGap = 2
		val perRow = maxOf(1, (w + btnGap) / (btn + btnGap))
		val rows = if (cats.isEmpty()) 0 else (cats.size + perRow - 1) / perRow
		val stripY = y + searchH + searchGap
		val stripH = if (rows == 0) 0 else rows * btn + (rows - 1) * btnGap
		bestiaryCategoryTip = null
		cats.forEachIndexed { i, cat ->
			val bx = x + (i % perRow) * (btn + btnGap)
			val by = stripY + (i / perRow) * (btn + btnGap)
			val catId = cat.id
			val selected = catId == bestiaryPage.categoryId()
			val hovered = mouseX >= bx && mouseX < bx + btn && mouseY >= by && mouseY < by + btn
			val searchHit = bestiaryPage.searchHighlightsCategory(catId)
			val bg = if (selected) 0x50386CC8 else if (hovered) 0x24FFFFFF else PvDraw.CELL
			val border = if (searchHit) 0xFF55FF55.toInt() else if (selected) PvDraw.COLOR_ACCENT else PvDraw.COLOR_BORDER
			PvDraw.roundedFill(g, bx, by, btn, btn, 3, bg)
			PvDraw.roundedRing(g, bx, by, btn, btn, 3, border, PvDraw.C_ALL)
			val icon = bestiaryPage.categoryIcon(catId)
			if (!icon.isEmpty) {
				g.item(icon, bx + (btn - 16) / 2, by + (btn - 16) / 2)
			}
			inventoryBar.addHit(bx, by, btn, btn) { bestiaryPage.setCategory(catId) }
			if (hovered) {
				bestiaryCategoryTip = Component.literal(cat.name)
				bestiaryCategoryTipX = bx + btn / 2
				bestiaryCategoryTipY = by + btn + 14
			}
		}

		val bodyY = stripY + stripH + searchGap
		val bodyH = h - (bodyY - y)
		PvDraw.innerPanel(g, x, bodyY, w, bodyH)
		bestiaryPage.setSearchQuery(inventorySearchQuery)
		bestiaryPage.render(g, font, x, bodyY, w, bodyH, mouseX, mouseY, screenW, screenH)
	}
}
