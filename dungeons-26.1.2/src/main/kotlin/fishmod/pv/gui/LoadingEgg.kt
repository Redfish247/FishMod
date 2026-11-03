/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import java.util.Locale
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

/**
 * NEU-style “stuck loading forever” easter egg for the profile viewer.
 * Escalating copy while the PV never becomes ready (missing player / failed fetch / hung load).
 *
 * At finale: close PV → /limbo → fake Hypixel ban. Unlocked lines stack.
 */
object LoadingEgg {
	private const val SECOND_MS = 1000L
	private const val MINUTE_MS = 60L * SECOND_MS
	private const val TEST_BREAK_SECONDS = 30L

	/** Trigger the limbo → fake-ban finale after this long on a stuck load. */
	const val BREAK_AT_MS = 60L * MINUTE_MS

	/** Old test threshold seconds, proportionally scaled across the one-hour finale. */
	private val BEATS = arrayOf(
		Beat(1, "This is taking awhile, try again?", 0xFF9A9AAC, false, false),
		Beat(3, "Okay, really.. try again", 0xFFE8E8F0, false, false),
		Beat(5, "You're still here..?", 0xFFFFCC66, false, false),
		Beat(8, "Im starting to get concerned", 0xFFFFAA55, false, false),
		Beat(10, "Okay, whats wrong with you", 0xFFFFAA00, true, false),
		Beat(13, "Seriously, what is wrong with you", 0xFFFF8844, true, false),
		Beat(15, "If you dont close it somethings gonna happen..", 0xFFFF6666, true, false),
		Beat(18, "Im warning you..", 0xFFFF5555, true, false),
		Beat(20, "Dont make me tell you again..", 0xFFFF4444, true, false),
		Beat(23, "Seriously.", 0xFFFF3333, true, false),
		Beat(25, "FINAL WARNING.", 0xFFFF0000, true, true),
		Beat(30, "TIME TO TAKE A BREAK", 0xFFFF5555, true, true)
	)

	@JvmRecord
	data class Stage(val line: Component, val color: Int, val bold: Boolean, val shout: Boolean)

	private class Beat(val testAtSeconds: Long, val text: String, color: Long, val bold: Boolean, val shout: Boolean) {
		val color: Int = color.toInt()

		fun atMs(): Long = (testAtSeconds * BREAK_AT_MS) / TEST_BREAK_SECONDS
	}

	/** All messages unlocked so far, oldest → newest (stack top to bottom). */
	@JvmStatic
	fun stagesUnlocked(elapsedMs: Long): List<Stage> {
		val elapsed = maxOf(0L, elapsedMs)
		return BEATS.filter { elapsed >= it.atMs() }.map { stage(it.text, it.color, it.bold, it.shout) }
	}

	private fun stage(text: String, color: Int, bold: Boolean, shout: Boolean): Stage {
		val shown = if (shout) text.uppercase(Locale.ROOT) else text
		val line = Component.literal(shown).setStyle(
			Style.EMPTY
				.withColor(TextColor.fromRgb(color and 0xFFFFFF))
				.withBold(bold || shout)
		)
		return Stage(line, color, bold || shout, shout)
	}
}
