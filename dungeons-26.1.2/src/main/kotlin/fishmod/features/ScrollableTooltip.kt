package fishmod.features

import fishmod.utils.config.values.FishSettings

object ScrollableTooltip {

    @JvmField var offsetX = 0f
    @JvmField var offsetY = 0f
    @JvmField var scaleOverride = 0f

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

    @JvmStatic
    fun trackHoveredSlot(slot: Int) {
        if (slot != lastSlot) { resetScroll(); lastSlot = slot }
    }

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
