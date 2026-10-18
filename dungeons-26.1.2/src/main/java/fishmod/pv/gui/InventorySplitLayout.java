/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import fishmod.pv.gui.inventories.InventoryPage;
import fishmod.pv.gui.nav.IconButtonBar;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Inventory tab body split: left pane + right selector grid + search. */
final class InventorySplitLayout {
	static final int SEARCH_H = 16;
	static final int SEARCH_GAP = 4;


	void render(
		GuiGraphicsExtractor g,
		Font font,
		InventoryPage inventoryPage,
		IconButtonBar inventoryBar,
		EditBox inventorySearch,
		String inventorySearchQuery,
		int screenW,
		int screenH,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		float delta
	) {
		PvDraw.roundedFill(g, x, y, w, SEARCH_H, 4, PvDraw.CELL);
		PvDraw.roundedRing(g, x, y, w, SEARCH_H, 4, inventorySearch != null && inventorySearch.isFocused() ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER, PvDraw.C_ALL);
		layoutInventorySearch(font, inventorySearch, x, y, w);
		if (inventorySearch != null) {
			inventorySearch.extractWidgetRenderState(g, mouseX, mouseY, delta);
			// EditBox only draws its hint when unfocused; keep placeholder visible while empty.
			if (inventorySearch.getValue().isEmpty()) {
				String hint = Component.translatable("betterpv.inv.search_hint").getString();
				PvDraw.text(g, font, hint, inventorySearch.getX(), inventorySearch.getY(), PvDraw.COLOR_MUTED);
			}
		}

		int bodyY = y + SEARCH_H + SEARCH_GAP;
		int bodyH = h - SEARCH_H - SEARCH_GAP;
		PvDraw.innerPanel(g, x, bodyY, w, bodyH);
		inventoryPage.setSearchQuery(inventorySearchQuery);
		inventoryPage.render(g, font, x, bodyY, w, bodyH, mouseX, mouseY, screenW, screenH);
	}

	static void layoutInventorySearch(Font font, EditBox inventorySearch, int x, int y, int w) {
		if (inventorySearch == null) {
			return;
		}
		int inset = 4;
		// Unbordered EditBox draws text at getY() (not vertically centred) - match the box.
		int textH = Math.max(8, font.lineHeight);
		int textY = y + Math.max(0, (SEARCH_H - textH) / 2);
		inventorySearch.setWidth(Math.max(20, w - inset * 2));
		inventorySearch.setHeight(textH);
		inventorySearch.setX(x + inset);
		inventorySearch.setY(textY);
		inventorySearch.setVisible(true);
	}

	static void hideInventorySearch(EditBox inventorySearch) {
		if (inventorySearch == null) {
			return;
		}
		inventorySearch.setFocused(false);
		inventorySearch.setVisible(false);
	}

}
