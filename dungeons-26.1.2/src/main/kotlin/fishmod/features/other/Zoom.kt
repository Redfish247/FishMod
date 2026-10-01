package fishmod.features.other

import fishmod.utils.Keybinds
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import kotlin.math.exp
import kotlin.math.pow

object Zoom {

    private const val MIN_FACTOR = 1.5
    private const val MAX_FACTOR = 50.0

    private var target = 1.0
    private var current = 1.0
    private var wasDown = false
    private var lastNanos = 0L

    @JvmStatic
    fun active(): Boolean {
        val key = Keybinds.zoom ?: return false
        return FishSettings.zoomEnabled && Minecraft.getInstance().screen == null && key.isDown
    }

    @JvmStatic
    fun smoothCamera(): Boolean = FishSettings.zoomSmoothCamera && active()

    // Returns true when the scroll was used for zoom.
    @JvmStatic
    fun onScroll(vertical: Double): Boolean {
        if (!FishSettings.zoomScroll || !active() || vertical == 0.0) return false
        target = (target * 1.25.pow(vertical)).coerceIn(MIN_FACTOR, MAX_FACTOR)
        return true
    }

    @JvmStatic
    fun modifyFov(fov: Float): Float {
        val down = active()
        if (down && !wasDown) target = FishSettings.zoomFactor.coerceIn(MIN_FACTOR, MAX_FACTOR)
        wasDown = down
        val goal = if (down) target else 1.0

        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.0 else ((now - lastNanos) / 1e9).coerceAtMost(0.1)
        lastNanos = now
        current = if (FishSettings.zoomSmoothAnim) {
            val next = current + (goal - current) * (1 - exp(-dt * 14))
            if (kotlin.math.abs(next - goal) < 0.001) goal else next
        } else goal

        return if (current == 1.0) fov else (fov / current).toFloat()
    }
}
