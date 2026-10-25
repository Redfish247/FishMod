/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import java.util.Locale;

/** Colour sets for the viewer's glass surfaces; picked in the /fm screen. */
public final class PvTheme {
	public static final String[] NAMES = {"Dark Glass", "Midnight", "Clear Glass", "Frost", "Ember", "Obsidian"};

	private PvTheme() {}

	public static void apply(String name) {
		String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
		switch (key) {
			case "midnight" -> set(0x88241A40, 0x940E0A22, 0x2CB48CFF, 0x16B48CFF, 0x22B48CFF, 0xFFB68CFF);
			case "clear glass" -> set(0x48123C46, 0x5206171E, 0x2058E6E6, 0x1058E6E6, 0x1858E6E6, 0xFF58E6E6);
			case "frost" -> set(0x92345070, 0x9C1E3556, 0x30FFFFFF, 0x1CFFFFFF, 0x26FFFFFF, 0xFF8CC8FF);
			case "ember" -> set(0x88381810, 0x940E0704, 0x28FFA064, 0x14FFA064, 0x1EFFA064, 0xFFFFA05A);
			case "obsidian" -> set(0xF0070910, 0xF0030408, 0x30000000, 0x40000000, 0x40000000, 0xFF8A8AA0);
			default -> set(0x78070910, 0x8A030408, 0x34000000, 0x48000000, 0x38000000, 0xFF5B8CFF);
		}
	}

	private static void set(int top, int bottom, int innerTop, int innerBottom, int cell, int accent) {
		PvDraw.COLOR_ACCENT = accent;
		PvDraw.GLASS_TOP = top;
		PvDraw.GLASS_BOTTOM = bottom;
		PvDraw.GLASS_INNER_TOP = innerTop;
		PvDraw.GLASS_INNER_BOTTOM = innerBottom;
		PvDraw.CELL = cell;
	}
}
