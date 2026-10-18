/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import fishmod.pv.data.BestiaryData;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.bestiary.BestiaryPage;
import fishmod.pv.gui.nav.IconButtonBar;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Bestiary tab body split: left list + right category grid + search. */
final class BestiarySplitLayout {
	private Component bestiaryCategoryTip;
	private int bestiaryCategoryTipX;
	private int bestiaryCategoryTipY;

	Component categoryTip() {
		return this.bestiaryCategoryTip;
	}

	int categoryTipX() {
		return this.bestiaryCategoryTipX;
	}

	int categoryTipY() {
		return this.bestiaryCategoryTipY;
	}

	void render(
		GuiGraphicsExtractor g,
		Font font,
		BestiaryPage bestiaryPage,
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
		int searchH = InventorySplitLayout.SEARCH_H;
		int searchGap = InventorySplitLayout.SEARCH_GAP;

		// Header bar: tier totals on the left, family search on the right.
		PvDraw.roundedFill(g, x, y, w, searchH, 3, PvDraw.CELL);
		PvDraw.roundedRing(g, x, y, w, searchH, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL);
		String header = "Tiers "
			+ FormatUtil.commas(bestiaryPage.totalUnlockedTiers())
			+ "/"
			+ FormatUtil.commas(bestiaryPage.totalMaxTiers())
			+ "  ·  Milestone "
			+ FormatUtil.commas(bestiaryPage.milestone());
		PvDraw.text(g, font, header, x + 6, y + (searchH - font.lineHeight) / 2, PvDraw.COLOR_MUTED);
		int searchBoxW = Math.max(80, w * 36 / 100);
		int searchBoxX = x + w - searchBoxW;
		g.outline(
			searchBoxX, y, searchBoxW, searchH,
			inventorySearch != null && inventorySearch.isFocused() ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER
		);
		InventorySplitLayout.layoutInventorySearch(font, inventorySearch, searchBoxX, y, searchBoxW);
		if (inventorySearch != null) {
			inventorySearch.extractWidgetRenderState(g, mouseX, mouseY, delta);
			if (inventorySearch.getValue().isEmpty()) {
				String hint = Component.translatable("betterpv.bestiary.search_hint").getString();
				PvDraw.text(g, font, hint, inventorySearch.getX(), inventorySearch.getY(), PvDraw.COLOR_MUTED);
			}
		}

		// Family tabs across the top, wrapping to as many rows as the width needs.
		BestiaryData.ensureLoaded();
		List<BestiaryData.Category> cats = BestiaryData.categories();
		int btn = 18;
		int btnGap = 2;
		int perRow = Math.max(1, (w + btnGap) / (btn + btnGap));
		int rows = cats.isEmpty() ? 0 : (cats.size() + perRow - 1) / perRow;
		int stripY = y + searchH + searchGap;
		int stripH = rows == 0 ? 0 : rows * btn + (rows - 1) * btnGap;
		this.bestiaryCategoryTip = null;
		for (int i = 0; i < cats.size(); i++) {
			BestiaryData.Category cat = cats.get(i);
			int bx = x + (i % perRow) * (btn + btnGap);
			int by = stripY + (i / perRow) * (btn + btnGap);
			boolean selected = cat.id().equals(bestiaryPage.categoryId());
			boolean hovered = mouseX >= bx && mouseX < bx + btn && mouseY >= by && mouseY < by + btn;
			boolean searchHit = bestiaryPage.searchHighlightsCategory(cat.id());
			int bg = selected ? 0x50386CC8 : hovered ? 0x24FFFFFF : PvDraw.CELL;
			int border = searchHit ? 0xFF55FF55 : selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
			PvDraw.roundedFill(g, bx, by, btn, btn, 3, bg);
			PvDraw.roundedRing(g, bx, by, btn, btn, 3, border, PvDraw.C_ALL);
			ItemStack icon = bestiaryPage.categoryIcon(cat.id());
			if (!icon.isEmpty()) {
				g.item(icon, bx + (btn - 16) / 2, by + (btn - 16) / 2);
			}
			String catId = cat.id();
			inventoryBar.addHit(bx, by, btn, btn, () -> bestiaryPage.setCategory(catId));
			if (hovered) {
				this.bestiaryCategoryTip = Component.literal(cat.name());
				this.bestiaryCategoryTipX = bx + btn / 2;
				this.bestiaryCategoryTipY = by + btn + 14;
			}
		}

		int bodyY = stripY + stripH + searchGap;
		int bodyH = h - (bodyY - y);
		PvDraw.innerPanel(g, x, bodyY, w, bodyH);
		bestiaryPage.setSearchQuery(inventorySearchQuery);
		bestiaryPage.render(g, font, x, bodyY, w, bodyH, mouseX, mouseY, screenW, screenH);
	}

}
