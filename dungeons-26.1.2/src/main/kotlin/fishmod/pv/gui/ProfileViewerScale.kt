/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

object ProfileViewerScale {
	const val BASE_GUI_SCALE = 2

	@JvmStatic
	fun viewport(
		screenWidth: Int,
		screenHeight: Int,
		framebufferWidth: Int,
		framebufferHeight: Int,
		minecraftGuiScale: Int,
		selectedPercent: Int,
		bounds: Bounds?
	): Viewport {
		val guiScale = maxOf(1, minecraftGuiScale)
		val virtualWidth = ceilDiv(maxOf(1, framebufferWidth), BASE_GUI_SCALE)
		val virtualHeight = ceilDiv(maxOf(1, framebufferHeight), BASE_GUI_SCALE)
		val baseScale = BASE_GUI_SCALE / guiScale.toFloat()
		val selectedScale = maxOf(0.75f, minOf(1.5f, selectedPercent / 100.0f))
		val fitScale = if (bounds == null) selectedScale else fitScale(virtualWidth, virtualHeight, bounds)
		val effectiveScale = minOf(selectedScale, fitScale)
		val renderScale = baseScale * effectiveScale
		val offsetX = (screenWidth - virtualWidth * renderScale) / 2.0f
		val offsetY = (screenHeight - virtualHeight * renderScale) / 2.0f
		return Viewport(virtualWidth, virtualHeight, renderScale, offsetX, offsetY, selectedScale, effectiveScale)
	}

	private fun fitScale(width: Int, height: Int, bounds: Bounds): Float {
		val cx = width / 2.0f
		val cy = height / 2.0f
		val horizontalExtent = maxOf(cx - bounds.left, bounds.right - cx)
		val verticalExtent = maxOf(cy - bounds.top, bounds.bottom - cy)
		val fitX = if (horizontalExtent <= 0.0f) 1.5f else maxOf(1.0f, width / 2.0f - 2.0f) / horizontalExtent
		val fitY = if (verticalExtent <= 0.0f) 1.5f else maxOf(1.0f, height / 2.0f - 2.0f) / verticalExtent
		return maxOf(0.25f, minOf(1.5f, minOf(fitX, fitY)))
	}

	private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor

	@JvmStatic
	fun bottomLeftPosition(viewportWidth: Int, viewportHeight: Int, contentWidth: Int, contentHeight: Int, margin: Int): Point {
		val x = maxOf(0, minOf(margin, viewportWidth - contentWidth))
		val y = maxOf(0, viewportHeight - contentHeight - margin)
		return Point(x, y)
	}

	@JvmRecord
	data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

	@JvmRecord
	data class Point(val x: Int, val y: Int)

	@JvmRecord
	data class Viewport(
		val virtualWidth: Int,
		val virtualHeight: Int,
		val renderScale: Float,
		val offsetX: Float,
		val offsetY: Float,
		val selectedScale: Float,
		val effectiveScale: Float
	) {
		fun toVirtualX(screenX: Double): Double = (screenX - offsetX) / renderScale

		fun toVirtualY(screenY: Double): Double = (screenY - offsetY) / renderScale

		fun toScreenX(virtualX: Float): Float = offsetX + virtualX * renderScale

		fun toScreenY(virtualY: Float): Float = offsetY + virtualY * renderScale
	}
}
