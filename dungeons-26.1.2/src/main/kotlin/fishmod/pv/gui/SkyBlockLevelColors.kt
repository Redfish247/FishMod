/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

object SkyBlockLevelColors {
	@JvmStatic
	fun colorFor(level: Int): Int = when {
		level >= 480 -> 0xFFAA0000.toInt()
		level >= 440 -> 0xFFFF5555.toInt()
		level >= 400 -> 0xFFFFAA00.toInt()
		level >= 360 -> 0xFFAA00AA.toInt()
		level >= 320 -> 0xFFFF55FF.toInt()
		level >= 280 -> 0xFF5555FF.toInt()
		level >= 240 -> 0xFF00AAAA.toInt()
		level >= 200 -> 0xFF55FFFF.toInt()
		level >= 160 -> 0xFF00AA00.toInt()
		level >= 120 -> 0xFF55FF55.toInt()
		level >= 80 -> 0xFFFFFF55.toInt()
		level >= 40 -> 0xFFFFFFFF.toInt()
		else -> 0xFFAAAAAA.toInt()
	}
}
