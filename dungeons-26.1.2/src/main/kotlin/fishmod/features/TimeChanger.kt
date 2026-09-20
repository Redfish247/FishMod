package fishmod.features

import fishmod.utils.config.values.FishSettings
import java.time.LocalTime

object TimeChanger {

    private const val REAL_TIME_MODE = "Real Time"
    private val MODES = listOf("Day", "Noon", "Sunset", "Night", "Midnight", "Sunrise", REAL_TIME_MODE)
    private val VALUES = longArrayOf(1000L, 6000L, 12000L, 13000L, 18000L, 23000L)

    @JvmStatic
    fun modes(): Array<String> = MODES.toTypedArray()

    @JvmStatic
    fun init() {  }

    @JvmStatic
    fun active(): Boolean = FishSettings.timeChangerEnabled

    @JvmStatic
    fun overrideTicks(): Long {
        val mode = FishSettings.timeChangerMode
        if (mode == REAL_TIME_MODE) return realTimeTicks()
        val idx = MODES.indexOf(mode)
        return VALUES.getOrElse(idx) { realTimeTicks() }
    }

    private fun realTimeTicks(): Long {
        val now = LocalTime.now()
        var ticks = now.hour * 1000L + (now.minute * 16.66).toLong() - 6000L
        if (ticks < 0) ticks += 24000L
        return ticks
    }
}
