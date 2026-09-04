package fishmod.features

import fishmod.utils.config.values.FishSettings

/**
 * Scrollable / scalable item tooltips:
 *  - scroll over a hovered item to move its tooltip up/down
 *  - shift + scroll to move it left/right
 *  - ctrl + scroll to scale it
 *
 * The offset/scale is applied to the pose in [fishmod.mixin.DrawContextMixin] around
 * `GuiGraphicsExtractor.tooltip`; scroll input is fed in from [fishmod.mixin.HandledScreenMixin].
 */
object ScrollableTooltip {

    @JvmField var offsetX = 0f
    @JvmField var offsetY = 0f
    @JvmField var scaleOverride = 0f          // tenths added onto the base scale

    private var lastSlot = Int.MIN_VALUE

    @JvmStatic
    fun isEnabled(): Boolean = FishSettings.tooltipScrollEnabled

    private fun baseScale(): Float = FishSettings.tooltipScrollScale.coerceIn(30, 150) / 100f

    @JvmStatic
    fun effectiveScale(): Float = (baseScale() + scaleOverride / 10f).coerceIn(0.3f, 2.0f)

    @JvmStatic
    fun resetScroll() {
        offsetX = 0f; offsetY = 0f; scaleOverride = 0f
    }

    /**
     * Drop the offset/scale as soon as the hovered slot changes (or you stop hovering), so a
     * tooltip nudged off one item doesn't render displaced over the next item / empty space.
     * Called every screen frame from [fishmod.mixin.HandledScreenMixin].
     */
    @JvmStatic
    fun trackHoveredSlot(slot: Int) {
        if (slot != lastSlot) { resetScroll(); lastSlot = slot }
    }

    /** @param slot hovered slot index (< 0 = none). @return true if the scroll was consumed. */
    @JvmStatic
    fun onScroll(vertical: Double, slot: Int, shift: Boolean, ctrl: Boolean): Boolean {
        if (!isEnabled() || slot < 0) return false
        if (slot != lastSlot) { resetScroll(); lastSlot = slot }

        val speed = FishSettings.tooltipScrollSpeed.coerceIn(1, 10)
        val amt = (vertical * speed).toFloat()
        when {
            shift && !ctrl -> offsetX -= amt
            ctrl && !shift -> {
                val next = (baseScale() + scaleOverride / 10f + (vertical / 100f).toFloat() * speed).coerceIn(0.3f, 2.0f)
                scaleOverride = (next - baseScale()) * 10f
            }
            else -> offsetY += amt
        }
        return true
    }
}
