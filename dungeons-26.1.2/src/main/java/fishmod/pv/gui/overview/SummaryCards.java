/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.overview;

import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Masonry grid of titled cards, used by the trial skill overviews. Scrolls when it doesn't fit. */
public final class SummaryCards {
	private static final int PAD = 6;
	private static final int GAP = 6;
	private static final int MIN_COL_W = 150;
	private static final int MAX_COLS = 3;
	private static final int BAR_LABEL_GAP = 2;
	private static final int SCROLL_STEP = 14;

	public sealed interface Item permits Stat, Bar, Note {
	}

	public record Stat(String label, String value, int labelColor, int valueColor, List<PvTooltip.Line> tip) implements Item {
		public static Stat of(String label, String value, int valueColor) {
			return new Stat(label, value, PvDraw.COLOR_MUTED, valueColor, null);
		}

		public static Stat of(String label, String value, int valueColor, List<PvTooltip.Line> tip) {
			return new Stat(label, value, PvDraw.COLOR_MUTED, valueColor, tip);
		}
	}

	public record Bar(String label, String value, float fill, boolean maxed, int color, List<PvTooltip.Line> tip) implements Item {
	}

	public record Note(String text) implements Item {
	}

	public record Card(String title, List<Item> items) {
	}

	private record Zone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}

	private final List<Zone> zones = new ArrayList<>();
	private int scroll;
	private int maxScroll;
	private int viewX;
	private int viewY;
	private int viewW;
	private int viewH;

	public void resetScroll() {
		this.scroll = 0;
	}

	public void render(GuiGraphicsExtractor g, Font font, List<Card> cards, int x, int y, int w, int h) {
		this.zones.clear();
		this.viewX = x;
		this.viewY = y;
		this.viewW = w;
		this.viewH = h;
		int cols = columnsFor(w);
		int colW = (w - GAP * (cols - 1)) / cols;
		int[] heights = new int[cards.size()];
		for (int i = 0; i < cards.size(); i++) {
			heights[i] = cardHeight(font, cards.get(i));
		}
		int[] columnOf = assignColumns(heights, cols);
		int[] colY = new int[cols];
		int contentH = 0;
		for (int i = 0; i < cards.size(); i++) {
			colY[columnOf[i]] += heights[i] + GAP;
			contentH = Math.max(contentH, colY[columnOf[i]] - GAP);
		}
		this.maxScroll = Math.max(0, contentH - h);
		this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll));

		g.enableScissor(x, y, x + w + 3, y + h);
		int[] drawY = new int[cols];
		for (int i = 0; i < cards.size(); i++) {
			int col = columnOf[i];
			int cx = x + col * (colW + GAP);
			int cy = y + drawY[col] - this.scroll;
			drawY[col] += heights[i] + GAP;
			if (cy + heights[i] < y || cy > y + h) {
				continue;
			}
			drawCard(g, font, cards.get(i), cx, cy, colW, heights[i]);
		}
		g.disableScissor();

		if (this.maxScroll > 0) {
			int trackX = x + w - 2;
			int thumbH = Math.max(12, h * h / (h + this.maxScroll));
			int thumbY = y + Math.round((h - thumbH) * (this.scroll / (float) this.maxScroll));
			PvDraw.fill(g, trackX, y, 2, h, PvDraw.COLOR_DIVIDER);
			PvDraw.fill(g, trackX, thumbY, 2, thumbH, PvDraw.COLOR_DIVIDER);
		}
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (mouseX < this.viewX || mouseX >= this.viewX + this.viewW || mouseY < this.viewY || mouseY >= this.viewY + this.viewH) {
			return;
		}
		for (Zone zone : this.zones) {
			if (mouseX >= zone.x() && mouseX < zone.x() + zone.w() && mouseY >= zone.y() && mouseY < zone.y() + zone.h()) {
				PvTooltip.drawStyled(g, font, zone.lines(), mouseX, mouseY, screenW, screenH);
				return;
			}
		}
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.maxScroll <= 0 || mouseX < this.viewX || mouseX >= this.viewX + this.viewW
			|| mouseY < this.viewY || mouseY >= this.viewY + this.viewH) {
			return false;
		}
		int next = Math.max(0, Math.min(this.maxScroll, this.scroll - (int) Math.round(scrollY * SCROLL_STEP)));
		if (next == this.scroll) {
			return false;
		}
		this.scroll = next;
		return true;
	}

	static int columnsFor(int w) {
		int cols = (w + GAP) / (MIN_COL_W + GAP);
		return Math.max(1, Math.min(MAX_COLS, cols));
	}

	/** Places each card, in order, into the currently shortest column. */
	static int[] assignColumns(int[] heights, int cols) {
		int n = Math.max(1, cols);
		int[] colH = new int[n];
		int[] out = new int[heights.length];
		for (int i = 0; i < heights.length; i++) {
			int best = 0;
			for (int c = 1; c < n; c++) {
				if (colH[c] < colH[best]) {
					best = c;
				}
			}
			out[i] = best;
			colH[best] += heights[i] + GAP;
		}
		return out;
	}

	private static int cardHeight(Font font, Card card) {
		int h = PAD * 2 + font.lineHeight + 3;
		for (Item item : card.items()) {
			h += itemHeight(font, item);
		}
		return h;
	}

	private static int itemHeight(Font font, Item item) {
		return switch (item) {
			case Stat stat -> font.lineHeight + 3;
			case Bar bar -> font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT + 4;
			case Note note -> font.lineHeight + 2;
		};
	}

	private void drawCard(GuiGraphicsExtractor g, Font font, Card card, int x, int y, int w, int h) {
		PvDraw.innerPanel(g, x, y, w, h);
		int ix = x + PAD;
		int iw = w - PAD * 2;
		int ly = y + PAD;
		ly += PvDraw.sectionHeader(g, font, card.title(), ix, ly, iw);
		for (Item item : card.items()) {
			int ih = itemHeight(font, item);
			switch (item) {
				case Stat stat -> {
					String value = stat.value() == null ? "" : stat.value();
					int labelMax = Math.max(8, iw - font.width(value) - 6);
					PvDraw.text(g, font, trim(font, stat.label(), labelMax), ix, ly, stat.labelColor());
					PvDraw.textRight(g, font, value, ix + iw, ly, stat.valueColor());
					addZone(ix, ly, iw, ih, stat.tip());
				}
				case Bar bar -> {
					String value = bar.value() == null ? "" : bar.value();
					String label = trim(font, bar.label(), Math.max(16, iw - font.width(value) - 8));
					PvDraw.labeledBar(g, font, label, value, bar.fill(), ix, ly, iw, bar.color(), bar.maxed());
					addZone(ix, ly, iw, ih, bar.tip());
				}
				case Note note -> PvDraw.text(g, font, trim(font, note.text(), iw), ix, ly, PvDraw.COLOR_MUTED);
			}
			ly += ih;
		}
	}

	private void addZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
		if (lines == null || lines.isEmpty()) {
			return;
		}
		int top = Math.max(y, this.viewY);
		int bottom = Math.min(y + h, this.viewY + this.viewH);
		if (bottom > top) {
			this.zones.add(new Zone(x, top, w, bottom - top, lines));
		}
	}

	private static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int budget = maxW - font.width(ellipsis);
		if (budget <= 0) {
			return ellipsis;
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(out.toString() + c) > budget) {
				break;
			}
			out.append(c);
		}
		return out + ellipsis;
	}
}
