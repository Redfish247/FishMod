/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import fishmod.pv.api.PvConfig
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class ProfileScaleSlider {
	private var x = 0
	private var y = 0
	private var hovered = false
	private var dragging = false
	private var opacity = IDLE_OPACITY
	private var lastFrameNanos = 0L

	fun layoutViewport(screenWidth: Int, screenHeight: Int) {
		val position = position(screenWidth, screenHeight)
		x = position.x
		y = position.y
	}

	fun render(graphics: GuiGraphicsExtractor, font: Font, mouseX: Double, mouseY: Double) {
		hovered = contains(mouseX, mouseY)
		val now = System.nanoTime()
		val elapsed = if (lastFrameNanos == 0L) 0L else maxOf(0L, now - lastFrameNanos)
		lastFrameNanos = now
		val target = if (hovered || dragging) ACTIVE_OPACITY else IDLE_OPACITY
		val blend = if (elapsed <= 0L) 1.0f else minOf(1.0f, elapsed / FADE_NANOS.toFloat())
		opacity += (target - opacity) * blend

		val track = withAlpha(0xFF363646.toInt(), opacity)
		val fill = withAlpha(PvDraw.COLOR_ACCENT, opacity)
		val knob = withAlpha(0xFFE8E8F0.toInt(), opacity)
		val centerY = y + HEIGHT / 2
		PvDraw.fill(graphics, x, centerY - 1, WIDTH, 3, track)
		val knobCenter = x + Math.round((WIDTH - 1) * progress())
		PvDraw.fill(graphics, x, centerY - 1, maxOf(1, knobCenter - x), 3, fill)
		PvDraw.fill(graphics, knobCenter - 2, y - 1, 5, HEIGHT + 2, knob)
		if (hovered || dragging) {
			val label = PvConfig.profileViewerScalePercent().toString() + "%  RMB reset"
			PvDraw.textRight(graphics, font, label, x + WIDTH, y - font.lineHeight - 3, withAlpha(PvDraw.COLOR_TEXT, opacity))
		}
	}

	fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
		if (!contains(mouseX, mouseY)) {
			return false
		}
		if (button == 1) {
			PvConfig.setProfileViewerScalePercent(100)
			dragging = false
			return true
		}
		if (button != 0) {
			return false
		}
		dragging = true
		setFromMouse(mouseX)
		return true
	}

	fun mouseDragged(mouseX: Double): Boolean {
		if (!dragging) {
			return false
		}
		setFromMouse(mouseX)
		return true
	}

	fun mouseReleased(button: Int): Boolean {
		if (button != 0 || !dragging) {
			return false
		}
		dragging = false
		return true
	}

	fun dragging(): Boolean = dragging

	private fun contains(mouseX: Double, mouseY: Double): Boolean =
		mouseX >= x - HIT_PAD && mouseX <= x + WIDTH + HIT_PAD &&
			mouseY >= y - HIT_PAD && mouseY <= y + HEIGHT + HIT_PAD

	private fun setFromMouse(mouseX: Double) {
		PvConfig.setProfileViewerScalePercent(percentAt(mouseX, x))
	}

	private fun progress(): Float = (PvConfig.profileViewerScalePercent() - 75) / 75.0f

	@JvmRecord
	data class Position(val x: Int, val y: Int)

	companion object {
		const val WIDTH = 76
		const val HEIGHT = 5
		const val HIT_PAD = 5
		const val SCREEN_MARGIN = 14
		private const val KNOB_OVERHANG = 2
		private const val IDLE_OPACITY = 0.25f
		private const val ACTIVE_OPACITY = 0.85f
		private const val FADE_NANOS = 140_000_000L

		@JvmStatic
		fun position(screenWidth: Int, screenHeight: Int): Position {
			var x = maxOf(KNOB_OVERHANG, screenWidth - WIDTH - SCREEN_MARGIN)
			var y = maxOf(1, screenHeight - HEIGHT - SCREEN_MARGIN)
			if (screenWidth >= WIDTH + KNOB_OVERHANG * 2) {
				x = minOf(x, screenWidth - WIDTH - KNOB_OVERHANG)
			}
			if (screenHeight >= HEIGHT + 2) {
				y = minOf(y, screenHeight - HEIGHT - 1)
			}
			return Position(x, y)
		}

		@JvmStatic
		fun percentAt(mouseX: Double, trackX: Int): Int {
			val raw = ((mouseX - trackX) / WIDTH).toFloat()
			return 75 + Math.round(maxOf(0.0f, minOf(1.0f, raw)) * 15.0f) * 5
		}

		private fun withAlpha(argb: Int, opacity: Float): Int {
			val sourceAlpha = (argb ushr 24) and 0xFF
			val alpha = maxOf(0, minOf(255, Math.round(sourceAlpha * opacity)))
			return (alpha shl 24) or (argb and 0x00FFFFFF)
		}
	}
}
