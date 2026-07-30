package fishmod.features.dungeon

import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.MinecraftClient
import java.util.regex.Pattern

/**
 * Self-contained lag tracker — measures seconds lost to server lag during a
 * dungeon run by comparing wall-clock time to server-tick count.
 *
 * This replaces reading Blade's "Xs lost to lag" chat message, so the feature
 * works regardless of whether Blade's lag message setting is on.
 */
object LagTracker {

    // Same start trigger the split timer uses (includes § color codes)
    private const val RUN_START_MSG =
        "§e[NPC] §bMort§f: Here, I found this map when I first entered the dungeon."

    private val RUN_END_PATTERN: Pattern =
        Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+)\\s*(\\(NEW RECORD!\\))?$")

    private var active = false
    private var startMs: Long = 0
    private var ticks: Long = 0

    /** Returns seconds of accumulated lag for the current run, or 0 if no run is active. */
    @JvmStatic
    fun getCurrentLag(): Double {
        if (!active || startMs == 0L) return 0.0
        val wallSec = (System.currentTimeMillis() - startMs) / 1000.0
        val tickSec = ticks * 0.05
        return maxOf(0.0, wallSec - tickSec)
    }

    @JvmStatic
    fun init() {

        // Detect run start / end from game messages
        Events.ON_GAME_MESSAGE.register { message ->
            val s = message.string

            if (!active && s == RUN_START_MSG) {
                startMs = System.currentTimeMillis()
                ticks = 0
                active = true

            } else if (active && RUN_END_PATTERN.matcher(s).find()) {
                val wallSec = (System.currentTimeMillis() - startMs) / 1000.0
                val tickSec = ticks * 0.05
                val lag = wallSec - tickSec
                active = false

                if (FishSettings.sendLagToParty && lag >= 0.1) {
                    val formatted = String.format("%.2f", lag)
                    val mc = MinecraftClient.getInstance()
                    if (mc.networkHandler != null) {
                        mc.send { mc.networkHandler!!.sendChatCommand("pc " + formatted + "s lost to lag.") }
                    }
                }
            }
            false
        }

        // Count server ticks while a run is active
        Events.ON_SERVER_TICK.register {
            if (active) ticks++
            false
        }

        // Reset on location change (left dungeon, lobby, etc.)
        Events.ON_LOCATION_CHANGE.register { _ ->
            active = false
            startMs = 0
            ticks = 0
            false
        }
    }
}
