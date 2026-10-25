/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.essence;

import java.util.List;

/** Geometry for Home → Essence. Kept free of rendering so bounds can be unit tested. */
public final class EssenceLayout {
	public static final int PAD = 6;
	public static final int GAP = 6;
	public static final int SELECTOR_MIN_W = 104;
	public static final int SELECTOR_ROW_MAX = 18;
	public static final int SELECTOR_ROW = 16;
	public static final int SELECTOR_ROW_MIN = 14;
	public static final int GROUP_GAP = 2;
	public static final int DIVIDER_H = 5;
	public static final int DETAIL_HEADER_H = 18;
	public static final int STAT_ROW = 12;
	public static final int BAR_H = 6;
	public static final int PERK_ROW = 20;
	public static final int PERK_ROW_MIN = 18;
	public static final int PERK_COL_MIN = 140;
	public static final int PERK_COL_GAP = 10;

	public record Rect(int x, int y, int w, int h) {
		public boolean contains(double mx, double my) {
			return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
		}

		public int right() {
			return this.x + this.w;
		}

		public int bottom() {
			return this.y + this.h;
		}
	}

	/**
	 * Selector rows. {@code rowY} and {@code headerY} are offsets from {@code list.y()} before
	 * scrolling; {@code headerY} is empty when there is only room for dividers between groups.
	 */
	public record Selector(Rect panel, Rect list, boolean headers, int rowH, int[] headerY, int[] dividerY, int[] rowY, int contentH) {
		public int maxScroll() {
			return Math.max(0, this.contentH - this.list.h());
		}
	}

	/** Column-major perk grid inside the detail list area. */
	public record Perks(Rect list, int cols, int rows, int rowH, int colW, int contentH) {
		public int maxScroll() {
			return Math.max(0, this.contentH - this.list.h());
		}

		public int col(int index) {
			return this.rows <= 0 ? 0 : index / this.rows;
		}

		public int row(int index) {
			return this.rows <= 0 ? 0 : index % this.rows;
		}
	}

	private EssenceLayout() {
	}

	public static int selectorWidth(int bodyW, int widestRow) {
		int wanted = widestRow + PAD * 2;
		int cap = Math.max(SELECTOR_MIN_W, bodyW * 34 / 100);
		return Math.max(SELECTOR_MIN_W, Math.min(cap, wanted));
	}

	public static Rect detail(Rect body, int selectorW) {
		int x = body.x() + selectorW + GAP;
		return new Rect(x, body.y(), Math.max(1, body.right() - x), body.h());
	}

	public static Selector selector(Rect panel, int lineHeight, List<Integer> groupSizes) {
		Rect list = new Rect(panel.x() + PAD, panel.y() + PAD, Math.max(1, panel.w() - PAD * 2), Math.max(1, panel.h() - PAD * 2));
		int groups = groupSizes.size();
		int rows = 0;
		for (int size : groupSizes) {
			rows += size;
		}
		rows = Math.max(1, rows);
		int headerH = lineHeight + 3;
		int headerSpace = groups * headerH + Math.max(0, groups - 1) * GROUP_GAP;
		int withHeaders = (list.h() - headerSpace) / rows;
		boolean headers = withHeaders >= SELECTOR_ROW;
		int rowH = headers
			? Math.min(SELECTOR_ROW_MAX, withHeaders)
			: Math.max(SELECTOR_ROW_MIN, Math.min(SELECTOR_ROW, (list.h() - Math.max(0, groups - 1) * DIVIDER_H) / rows));

		int[] headerY = new int[headers ? groups : 0];
		int[] dividerY = new int[headers ? 0 : Math.max(0, groups - 1)];
		int[] rowY = new int[rows];
		int y = 0;
		int r = 0;
		for (int gi = 0; gi < groups; gi++) {
			if (headers) {
				if (gi > 0) {
					y += GROUP_GAP;
				}
				headerY[gi] = y;
				y += headerH;
			} else if (gi > 0) {
				dividerY[gi - 1] = y + DIVIDER_H / 2;
				y += DIVIDER_H;
			}
			for (int i = 0; i < groupSizes.get(gi); i++) {
				rowY[r++] = y;
				y += rowH;
			}
		}
		return new Selector(panel, list, headers, rowH, headerY, dividerY, rowY, y);
	}

	/** Top of the perk list: header row, two stat rows, the level bar and the "Perks" section header. */
	public static int perkListTop(Rect detail, int lineHeight) {
		return detail.y() + PAD + DETAIL_HEADER_H + 4 + STAT_ROW * 2 + 4 + BAR_H + 6 + lineHeight + 3;
	}

	public static Perks perks(Rect detail, int lineHeight, int perkCount) {
		int top = perkListTop(detail, lineHeight);
		Rect list = new Rect(
			detail.x() + PAD, top,
			Math.max(1, detail.w() - PAD * 2), Math.max(1, detail.bottom() - PAD - top)
		);
		int cols = list.w() >= PERK_COL_MIN * 2 + PERK_COL_GAP ? 2 : 1;
		int rows = perkCount <= 0 ? 0 : (perkCount + cols - 1) / cols;
		int rowH = rows == 0 ? PERK_ROW : Math.max(PERK_ROW_MIN, Math.min(PERK_ROW, list.h() / rows));
		int colW = (list.w() - PERK_COL_GAP * (cols - 1)) / cols;
		return new Perks(list, cols, rows, rowH, colW, rows * rowH);
	}

	public static int clampScroll(int value, int max) {
		return Math.max(0, Math.min(Math.max(0, max), value));
	}
}
