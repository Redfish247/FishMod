package fishmod.features

import fishmod.utils.config.values.FishSettings
import java.time.LocalTime

/**
 * Client-side world-time override (NoammAddons' TimeChanger). The value is injected into the
 * level's clock getter every frame ([fishmod.mixin.LevelTimeMixin]) rather than pushed once per
 * tick — pushing per tick fought the server's own time packet and flickered.
 */
object TimeChanger {

    private val MODES = listOf("Day", "Noon", "Sunset", "Night", "Midnight", "Sunrise", "Real Time")
    private val VALUES = longArrayOf(1000L, 6000L, 12000L, 13000L, 18000L, 23000L)

    @JvmStatic
    fun modes(): Array<String> = MODES.toTypedArray()

    @JvmStatic
    fun init() { /* nothing — the mixin reads [overrideTicks] directly */ }

    @JvmStatic
    fun active(): Boolean = FishSettings.timeChangerEnabled

    @JvmStatic
    fun overrideTicks(): Long {
        val idx = MODES.indexOf(FishSettings.timeChangerMode)
        return VALUES.getOrElse(idx) { realTimeTicks() }
    }

    private fun realTimeTicks(): Long {
        val now = LocalTime.now()
        var ticks = now.hour * 1000L + (now.minute * 16.66).toLong() - 6000L
        if (ticks < 0) ticks += 24000L
        return ticks
    }
}
