package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import java.time.LocalTime

/**
 * Client-side world time override (ported from NoammAddons' TimeChanger). Re-applies the chosen
 * time each tick so the server's day/night cycle can't fight it.
 */
object TimeChanger {

    private val MODES = listOf("Day", "Noon", "Sunset", "Night", "Midnight", "Sunrise", "Real Time")
    private val VALUES = longArrayOf(1000L, 6000L, 12000L, 13000L, 18000L, 23000L)

    @JvmStatic
    fun modes(): Array<String> = MODES.toTypedArray()

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!FishSettings.timeChangerEnabled) return@register
            val level = mc.level ?: return@register
            val idx = MODES.indexOf(FishSettings.timeChangerMode)
            val time = VALUES.getOrElse(idx) { realTimeTicks() }
            level.setTimeFromServer(time)
        }
    }

    private fun realTimeTicks(): Long {
        val now = LocalTime.now()
        var ticks = now.hour * 1000L + (now.minute * 16.66).toLong() - 6000L
        if (ticks < 0) ticks += 24000L
        return ticks
    }
}
