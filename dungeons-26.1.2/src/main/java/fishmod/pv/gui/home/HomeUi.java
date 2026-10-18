/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home;

/** Shared layout constants for Home subpages. */
public final class HomeUi {
	public static final int PAD = 6;
	public static final int GAP = 6;
	public static final int STAT_ROW = 12;
	public static final int SEP_GAP = 10;
	public static final int PANEL_HOVER = 0x0AFFFFFF;
	public static final int FLIP_MS = 480;
	public static final int ITEM_SLOT_BG = 0x1CFFFFFF;
	public static final int ITEM_SLOT_BORDER = 0x00FFFFFF;
	public static final int ENABLED = 0xFF55FF55;
	public static final int DISABLED = 0xFFFF5555;
	/** Profile / misc section headers. */
	public static final int HEADER_PROFILE = 0xFF5B8CFF;
	public static final int HEADER_GUILD = 0xFF55FF55;
	public static final int HEADER_HIGHLIGHTS = 0xFFFFAA00;
	public static final int HEADER_COMMUNITY = 0xFFD97FFF;
	public static final int HEADER_PETS = 0xFF55FFFF;
	public static final int HEADER_KILLS = 0xFF55FF55;
	public static final int HEADER_DEATHS = 0xFFFF5555;
	public static final int HEADER_SECTION = 0xFFFFAA55;

	private HomeUi() {
	}

	public static float easeInOutCubic(float t) {
		return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}
}
