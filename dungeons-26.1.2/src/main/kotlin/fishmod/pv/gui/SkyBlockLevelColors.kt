/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

/**
 * Hypixel SkyBlock level number colors (wiki Century Party / level brackets).
 */
object SkyBlockLevelColors {
	/** ARGB color for the given SkyBlock level number. */
	@JvmStatic
	fun colorFor(level: Int): Int = when {
		level >= 480 -> 0xFFAA0000.toInt() // dark red
		level >= 440 -> 0xFFFF5555.toInt() // red
		level >= 400 -> 0xFFFFAA00.toInt() // gold
		level >= 360 -> 0xFFAA00AA.toInt() // purple / dark purple
		level >= 320 -> 0xFFFF55FF.toInt() // light purple / pink
		level >= 280 -> 0xFF5555FF.toInt() // blue
		level >= 240 -> 0xFF00AAAA.toInt() // dark aqua / cyan
		level >= 200 -> 0xFF55FFFF.toInt() // aqua
		level >= 160 -> 0xFF00AA00.toInt() // dark green
		level >= 120 -> 0xFF55FF55.toInt() // green
		level >= 80 -> 0xFFFFFF55.toInt() // yellow
		level >= 40 -> 0xFFFFFFFF.toInt() // white
		else -> 0xFFAAAAAA.toInt() // gray
	}
}
