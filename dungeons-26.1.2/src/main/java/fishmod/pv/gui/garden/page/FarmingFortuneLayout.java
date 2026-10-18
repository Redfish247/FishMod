/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page;

import java.util.ArrayList;
import java.util.List;

/** Positions for the Farming Fortune page, kept free of rendering so the layout can be tested. */
final class FarmingFortuneLayout {
	static final int CROP_SLOT = 18;
	static final int CROP_GAP = 2;
	static final int HERO_H = 22;
	static final int HERO_CROPS_GAP = 12;
	static final int HEADER_H = 12;
	static final int TILE_H = 20;
	static final int TILE_GAP = 3;
	static final int SECTION_GAP = 6;
	static final int COL_GAP = 10;
	static final int COND_H = 18;
	static final int COND_GAP = 4;
	// Narrowest column that still fits an icon, a short label and a value like "+1,026.5".
	static final int MIN_COL_W = 130;

	enum Section { CROP, GEAR, PET, PERMANENT }

	record Rect(int x, int y, int w, int h) {
		int right() {
			return x + w;
		}

		int bottom() {
			return y + h;
		}

		boolean overlaps(Rect o) {
			return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom();
		}

		boolean contains(int px, int py) {
			return px >= x && px < right() && py >= y && py < bottom();
		}
	}

	private FarmingFortuneLayout() {
	}

	static int cropRowW(int count) {
		return count * CROP_SLOT + Math.max(0, count - 1) * CROP_GAP;
	}

	/** Crop selector beside the hero when the row fits, otherwise wrapped below it. */
	static boolean cropsBesideHero(int w, int heroW, int count) {
		return heroW + HERO_CROPS_GAP + cropRowW(count) <= w;
	}

	static List<Rect> cropSlots(int x, int y, int w, int heroW, int count) {
		List<Rect> out = new ArrayList<>(count);
		if (cropsBesideHero(w, heroW, count)) {
			int sx = x + w - cropRowW(count);
			int sy = y + (HERO_H - CROP_SLOT) / 2;
			for (int i = 0; i < count; i++) {
				out.add(new Rect(sx + i * (CROP_SLOT + CROP_GAP), sy, CROP_SLOT, CROP_SLOT));
			}
			return out;
		}
		int perRow = Math.max(1, (w + CROP_GAP) / (CROP_SLOT + CROP_GAP));
		int top = y + HERO_H + SECTION_GAP;
		for (int i = 0; i < count; i++) {
			out.add(new Rect(
				x + (i % perRow) * (CROP_SLOT + CROP_GAP),
				top + (i / perRow) * (CROP_SLOT + CROP_GAP),
				CROP_SLOT, CROP_SLOT
			));
		}
		return out;
	}

	/** Bottom of the hero row plus any wrapped crop rows. */
	static int heroBottom(int y, List<Rect> cropSlots) {
		int bottom = y + HERO_H;
		for (Rect r : cropSlots) {
			bottom = Math.max(bottom, r.bottom());
		}
		return bottom;
	}

	static int columnCount(int w) {
		for (int n = 3; n > 1; n--) {
			if ((w - (n - 1) * COL_GAP) / n >= MIN_COL_W) {
				return n;
			}
		}
		return 1;
	}

	/** Column x and width; the last column takes the rounding remainder so the grid ends flush. */
	static List<Rect> columns(int x, int w, int count) {
		List<Rect> out = new ArrayList<>(count);
		int colW = (w - (count - 1) * COL_GAP) / count;
		for (int i = 0; i < count; i++) {
			int cx = x + i * (colW + COL_GAP);
			int cw = i == count - 1 ? x + w - cx : colW;
			out.add(new Rect(cx, 0, cw, 0));
		}
		return out;
	}

	static List<List<Section>> sections(int columns) {
		return switch (columns) {
			case 3 -> List.of(List.of(Section.CROP), List.of(Section.GEAR, Section.PET), List.of(Section.PERMANENT));
			case 2 -> List.of(List.of(Section.CROP, Section.PET), List.of(Section.PERMANENT, Section.GEAR));
			default -> List.of(List.of(Section.CROP, Section.GEAR, Section.PET, Section.PERMANENT));
		};
	}

	/** Chips flow left to right and wrap; a chip wider than the row is narrowed to fit. */
	static List<Rect> chips(int x, int y, int w, int[] widths) {
		List<Rect> out = new ArrayList<>(widths.length);
		int cx = x;
		int cy = y;
		for (int width : widths) {
			int cw = Math.min(width, w);
			if (cx > x && cx + cw > x + w) {
				cx = x;
				cy += COND_H + COND_GAP;
			}
			out.add(new Rect(cx, cy, cw, COND_H));
			cx += cw + COND_GAP;
		}
		return out;
	}
}
