/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import fishmod.pv.gui.inventories.InventoryPage
import fishmod.pv.gui.nav.IconButtonBar
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

class InventorySplitLayout {
	@Suppress("UNUSED_PARAMETER")
	fun render(
		g: GuiGraphicsExtractor,
		font: Font,
		inventoryPage: InventoryPage,
		inventoryBar: IconButtonBar?,
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
		PvDraw.roundedFill(g, x, y, w, SEARCH_H, 4, PvDraw.CELL)
		PvDraw.roundedRing(g, x, y, w, SEARCH_H, 4, if (inventorySearch != null && inventorySearch.isFocused) PvDraw.COLOR_ACCENT else PvDraw.COLOR_BORDER, PvDraw.C_ALL)
		layoutInventorySearch(font, inventorySearch, x, y, w)
		if (inventorySearch != null) {
			inventorySearch.extractWidgetRenderState(g, mouseX, mouseY, delta)
			if (inventorySearch.value.isEmpty()) {
				val hint = Component.translatable("fishmod.pv.inv.search_hint").string
				PvDraw.text(g, font, hint, inventorySearch.x, inventorySearch.y, PvDraw.COLOR_MUTED)
			}
		}

		val bodyY = y + SEARCH_H + SEARCH_GAP
		val bodyH = h - SEARCH_H - SEARCH_GAP
		PvDraw.innerPanel(g, x, bodyY, w, bodyH)
		inventoryPage.setSearchQuery(inventorySearchQuery)
		inventoryPage.render(g, font, x, bodyY, w, bodyH, mouseX, mouseY, screenW, screenH)
	}

	companion object {
		const val SEARCH_H = 16
		const val SEARCH_GAP = 4

		@JvmStatic
		fun layoutInventorySearch(font: Font, inventorySearch: EditBox?, x: Int, y: Int, w: Int) {
			if (inventorySearch == null) {
				return
			}
			val inset = 4
			val textH = maxOf(8, font.lineHeight)
			val textY = y + maxOf(0, (SEARCH_H - textH) / 2)
			inventorySearch.width = maxOf(20, w - inset * 2)
			inventorySearch.setHeight(textH)
			inventorySearch.x = x + inset
			inventorySearch.y = textY
			inventorySearch.setVisible(true)
		}

		@JvmStatic
		fun hideInventorySearch(inventorySearch: EditBox?) {
			if (inventorySearch == null) {
				return
			}
			inventorySearch.setFocused(false)
			inventorySearch.setVisible(false)
		}
	}
}
