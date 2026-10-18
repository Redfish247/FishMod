/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import fishmod.pv.api.SearchHistory;
import fishmod.pv.gui.ItemIcons;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Recent searches dropdown under the player search box. */
final class SearchHistoryMenu {
	static final int PAD = 4;
	static final int ICON = 10;
	static final int ICON_GAP = 4;
	static final int ROW_PAD = 4;
	static final int EDGE = 2;

	record Layout(int x, int y, int w, int h, int rowH, int rows) {
		boolean contains(double mx, double my) {
			return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
		}

		int rowY(int index) {
			return this.y + 2 + index * this.rowH;
		}
	}

	private final Supplier<SearchHistory> history;
	private final Consumer<String> opener;
	private ItemStack icon;
	private boolean open;
	private boolean lastFocused;
	private String query = "";
	private int anchorX;
	private int anchorY;
	private int anchorW;
	private int anchorH;
	private int viewMinX;
	private int viewMinY;
	private int viewMaxX = Integer.MAX_VALUE / 2;
	private int viewMaxY = Integer.MAX_VALUE / 2;
	private Layout layout;

	SearchHistoryMenu(Supplier<SearchHistory> history, Consumer<String> opener) {
		this.history = history;
		this.opener = opener;
	}

	boolean isOpen() {
		return this.open;
	}

	/** Open and actually showing something; an empty or fully filtered history never draws. */
	boolean isVisible() {
		return this.open && !entries().isEmpty();
	}

	void open() {
		this.open = true;
	}

	void close() {
		this.open = false;
	}

	List<SearchHistory.Entry> entries() {
		List<SearchHistory.Entry> matches = this.history.get().filter(this.query);
		return matches.size() > SearchHistory.MAX ? matches.subList(0, SearchHistory.MAX) : matches;
	}

	/**
	 * Follows the search box each frame: gaining focus or typing opens the dropdown, losing focus closes it.
	 * Returns true when this call made it appear, so the caller can close other menus.
	 */
	boolean syncSearch(boolean focused, String value) {
		String next = value == null ? "" : value;
		boolean wasVisible = isVisible();
		if (focused && (!this.lastFocused || !next.equals(this.query))) {
			this.open = true;
		} else if (!focused && this.lastFocused) {
			this.open = false;
		}
		this.lastFocused = focused;
		this.query = next;
		return !wasVisible && isVisible();
	}

	void setAnchor(int x, int y, int w, int h) {
		this.anchorX = x;
		this.anchorY = y;
		this.anchorW = w;
		this.anchorH = h;
	}

	void setViewport(int minX, int minY, int maxX, int maxY) {
		this.viewMinX = minX;
		this.viewMinY = minY;
		this.viewMaxX = maxX;
		this.viewMaxY = maxY;
	}

	Layout layout() {
		return this.layout;
	}

	void layoutDropdown(int lineHeight) {
		int rows = entries().size();
		this.layout = rows == 0 ? null : dropdownLayout(
			this.anchorX, this.anchorY, this.anchorW, this.anchorH, rows, lineHeight,
			this.viewMinX, this.viewMinY, this.viewMaxX, this.viewMaxY
		);
	}

	/** Matches the search box width, opens under it, flips above when there is no room, and stays on screen. */
	static Layout dropdownLayout(
		int anchorX, int anchorY, int anchorW, int anchorH, int rows, int lineHeight,
		int minX, int minY, int maxX, int maxY
	) {
		int shown = Math.max(1, Math.min(SearchHistory.MAX, rows));
		int rowH = lineHeight + ROW_PAD;
		int w = anchorW;
		int h = shown * rowH + 4;
		int x = Math.max(minX + EDGE, Math.min(anchorX, maxX - EDGE - w));
		int y = anchorY + anchorH + 1;
		if (y + h > maxY - EDGE) {
			y = anchorY - 1 - h;
		}
		y = Math.max(minY + EDGE, y);
		return new Layout(x, y, w, h, rowH, shown);
	}

	boolean anchorContains(double mx, double my) {
		return this.anchorW > 0
			&& mx >= this.anchorX && mx < this.anchorX + this.anchorW
			&& my >= this.anchorY && my < this.anchorY + this.anchorH;
	}

	boolean blocksMouse(double mx, double my) {
		return isVisible() && this.layout != null && this.layout.contains(mx, my);
	}

	int rowAt(double mx, double my) {
		if (!blocksMouse(mx, my)) {
			return -1;
		}
		int count = Math.min(this.layout.rows(), entries().size());
		for (int i = 0; i < count; i++) {
			int rowY = this.layout.rowY(i);
			if (my >= rowY && my < rowY + this.layout.rowH()) {
				return i;
			}
		}
		return -1;
	}

	/** Consumes clicks on the dropdown; outside clicks close it and carry on to whatever is underneath. */
	boolean mouseClicked(double mx, double my, int button) {
		if (!isVisible()) {
			return false;
		}
		if (blocksMouse(mx, my)) {
			int row = rowAt(mx, my);
			if (row >= 0 && button == 0) {
				String name = entries().get(row).name();
				this.open = false;
				this.opener.accept(name);
			}
			return true;
		}
		if (!anchorContains(mx, my)) {
			this.open = false;
		}
		return false;
	}

	boolean mouseScrolled(double mx, double my) {
		return blocksMouse(mx, my);
	}

	boolean keyPressed(int key) {
		if (key == 256 && isVisible()) {
			this.open = false;
			return true;
		}
		return false;
	}

	void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		if (!isVisible()) {
			return;
		}
		layoutDropdown(font.lineHeight);
		Layout l = this.layout;
		List<SearchHistory.Entry> shown = entries();
		PvDraw.popupSurface(g, l.x(), l.y(), l.w(), l.h());
		if (this.icon == null) {
			this.icon = new ItemStack(Items.CLOCK);
		}
		int hoverRow = rowAt(mouseX, mouseY);
		int nameX = l.x() + PAD + ICON + ICON_GAP;
		int nameW = Math.max(0, l.x() + l.w() - PAD - nameX);
		for (int i = 0; i < Math.min(l.rows(), shown.size()); i++) {
			int rowY = l.rowY(i);
			if (i == hoverRow) {
				PvDraw.fill(g, l.x() + 1, rowY, l.w() - 2, l.rowH(), 0x33FFFFFF);
			}
			ItemIcons.drawItemIcon(g, this.icon, l.x() + PAD, rowY + (l.rowH() - ICON) / 2, ICON);
			String name = font.plainSubstrByWidth(shown.get(i).name(), nameW);
			PvDraw.text(g, font, name, nameX, rowY + ROW_PAD / 2, i == hoverRow ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_TEXT);
		}
	}
}
