/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections.page;

import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.collections.CollectionsUi;

/**
 * Collection popup geometry. Fitted inside the board it covers with a margin, and sized from the
 * collection's widest content and busiest tier so switching tiers never moves anything.
 */
record CollectionPopupLayout(
	int x, int y, int w, int h,
	int innerX, int innerW,
	int headerY, int statsY, int barY, int nextY,
	int tiersY, int trackY,
	int selectedY, int selectedBarY,
	int columnsY, int bottom,
	int rewardsW, int recipeX, int recipeW
) {
	static final int MARGIN = 10;
	static final int PAD = 8;
	static final int MAX_W = 460;
	static final int MIN_INNER_W = 240;
	static final int HEADER_H = 16;
	static final int CELL = 18;
	static final int ROW_H = 18;
	static final int COL_GAP = 12;
	static final int RECIPE_GRID_W = CELL * 3 + 16 + CELL;
	static final int RECIPE_MAX_W = 180;
	static final int MIN_TRACK_GAP = 12;
	private static final int GAP = 8;
	private static final int TIGHT_GAP = 5;

	/**
	 * Measured with the real font. {@code statsW} is the collected text plus the next-tier text on one line,
	 * {@code rewardW} the widest reward row, {@code recipeTitleW} the widest name that has a recipe.
	 */
	record Content(int tierCount, int maxRewards, boolean recipes, int headerW, int statsW, int rewardW, int recipeTitleW) {
	}

	boolean hasTiersHeader() {
		return this.tiersY >= 0;
	}

	boolean nextInline() {
		return this.nextY < 0;
	}

	int columnsBodyY(int lineH) {
		return this.columnsY + lineH + 3;
	}

	static int recipeBodyH(int lineH, boolean recipes) {
		return recipes ? lineH + 4 + CELL * 3 : lineH + 2;
	}

	static CollectionPopupLayout compute(int px, int py, int pw, int ph, int lineH, Content c) {
		int maxW = Math.max(120, pw - MARGIN * 2);
		int maxH = Math.max(100, ph - MARGIN * 2);
		int recipeW = c.recipes() ? Math.max(RECIPE_GRID_W, Math.min(RECIPE_MAX_W, c.recipeTitleW())) : RECIPE_GRID_W;
		int trackW = c.tierCount() * CollectionsUi.SLOT + Math.max(0, c.tierCount() - 1) * MIN_TRACK_GAP;
		int wantInner = Math.max(Math.max(MIN_INNER_W, trackW), Math.max(Math.max(c.headerW(), c.statsW()),
			c.rewardW() + COL_GAP + recipeW));
		int w = Math.min(Math.min(MAX_W, maxW), wantInner + PAD * 2);
		int innerW = w - PAD * 2;
		recipeW = Math.min(recipeW, Math.max(RECIPE_GRID_W, innerW - COL_GAP - 80));
		int rewardsW = Math.max(40, innerW - COL_GAP - recipeW);
		boolean nextInline = c.statsW() <= innerW;

		int body = Math.max(ROW_H, Math.max(c.maxRewards() * ROW_H, recipeBodyH(lineH, c.recipes())));
		boolean tight = height(lineH, nextInline, false, body) > maxH;
		int h = Math.min(maxH, height(lineH, nextInline, tight, body));
		int x = px + (pw - w) / 2;
		int y = py + (ph - h) / 2;
		int gap = tight ? TIGHT_GAP : GAP;

		int cy = y + PAD;
		int headerY = cy;
		cy += HEADER_H + 4;
		int statsY = cy;
		cy += lineH + 3;
		int barY = cy;
		cy += PvDraw.BAR_HEIGHT;
		int nextY = -1;
		if (!nextInline) {
			nextY = cy + 3;
			cy = nextY + lineH;
		}
		cy += gap;
		int tiersY = -1;
		if (!tight) {
			tiersY = cy;
			cy += lineH + 3;
		}
		int trackY = cy;
		cy += CollectionsUi.SLOT + 2 + lineH + gap;
		int selectedY = cy;
		cy += lineH + 3;
		int selectedBarY = cy;
		cy += lineH + 6;
		int innerX = x + PAD;
		return new CollectionPopupLayout(
			x, y, w, h, innerX, innerW, headerY, statsY, barY, nextY, tiersY, trackY, selectedY, selectedBarY,
			cy, y + h - PAD, rewardsW, innerX + innerW - recipeW, recipeW
		);
	}

	private static int height(int lineH, boolean nextInline, boolean tight, int body) {
		int gap = tight ? TIGHT_GAP : GAP;
		return PAD + HEADER_H + 4
			+ lineH + 3 + PvDraw.BAR_HEIGHT + (nextInline ? 0 : 3 + lineH) + gap
			+ (tight ? 0 : lineH + 3) + CollectionsUi.SLOT + 2 + lineH + gap
			+ lineH + 3 + lineH + 6
			+ lineH + 3 + body + PAD;
	}
}
