package fishmod.utils.rendering

import net.minecraft.client.Minecraft

object UiScale {
    private const val REFERENCE_GUI_SCALE = 2.0
    private const val FLAT_SHRINK = 0.77f

    private const val SIBLING_ENLARGE = 1.30f

    fun factor(): Float {
        val guiScale = Minecraft.getInstance().window.guiScale
        val compensation = (REFERENCE_GUI_SCALE / guiScale).coerceAtMost(1.0)
        val base = (compensation * FLAT_SHRINK).toFloat()
        return if (isSiblingScreen()) base * SIBLING_ENLARGE else base
    }

    private fun isSiblingScreen(): Boolean {
        val s = Minecraft.getInstance().screen ?: return false
        return s is fishmod.features.HasUiOverlay && s !is fishmod.features.FishModScreen
    }

    fun vx(real: Number): Int = (real.toDouble() / factor()).toInt()
}
