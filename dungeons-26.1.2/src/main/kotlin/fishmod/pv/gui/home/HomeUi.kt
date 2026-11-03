/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home

import kotlin.math.pow

/** Shared layout constants for Home subpages. */
object HomeUi {
	const val PAD = 6
	const val GAP = 6
	const val STAT_ROW = 12
	const val SEP_GAP = 10
	const val PANEL_HOVER = 0x0AFFFFFF
	const val FLIP_MS = 480
	const val ITEM_SLOT_BG = 0x1CFFFFFF
	const val ITEM_SLOT_BORDER = 0x00FFFFFF
	const val ENABLED = 0xFF55FF55.toInt()
	const val DISABLED = 0xFFFF5555.toInt()
	/** Profile / misc section headers. */
	const val HEADER_PROFILE = 0xFF5B8CFF.toInt()
	const val HEADER_GUILD = 0xFF55FF55.toInt()
	const val HEADER_HIGHLIGHTS = 0xFFFFAA00.toInt()
	const val HEADER_COMMUNITY = 0xFFD97FFF.toInt()
	const val HEADER_PETS = 0xFF55FFFF.toInt()
	const val HEADER_KILLS = 0xFF55FF55.toInt()
	const val HEADER_DEATHS = 0xFFFF5555.toInt()
	const val HEADER_SECTION = 0xFFFFAA55.toInt()

	@JvmStatic
	fun easeInOutCubic(t: Float): Float =
		if (t < 0.5F) 4F * t * t * t else 1F - (-2F * t + 2F).toDouble().pow(3).toFloat() / 2F
}
