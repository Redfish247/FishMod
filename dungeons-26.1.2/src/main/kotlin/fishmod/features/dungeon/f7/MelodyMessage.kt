package fishmod.features.dungeon.f7

import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.features.dungeon.f7.terminal.MelodyHandler
import fishmod.features.dungeon.f7.terminal.TerminalSolver
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/**
 * Melody Message. Announces the melody terminal to party chat when it opens and, optionally,
 * calls out 25 / 50 / 75 % as the green-clay marker moves down — the same lines
 * [fishmod.features.dungeon.f7.MelodyWarning] already listens for.
 */
object MelodyMessage {

    private var wasOpen = false
    private var lastPctSent = -1

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> tick() }
        Events.ON_WORLD_CHANGE.register { reset(); false }
    }

    private fun reset() { wasOpen = false; lastPctSent = -1 }

    private fun tick() {
        if (!FishSettings.melodyMessageEnabled) { reset(); return }
        val melody = TerminalSolver.current as? MelodyHandler
        val open = melody != null && Phase.inP3()

        if (open && !wasOpen) {
            wasOpen = true
            lastPctSent = -1
            if (FishSettings.melodyMessageOnOpen && FishSettings.melodyMessageText.isNotBlank()) {
                fishmod.utils.ChatQueue.enqueue("pc ${FishSettings.melodyMessageText}")
            }
        } else if (!open && wasOpen) {
            reset()
        }

        if (open && FishSettings.melodyMessageProgress && melody != null) {
            val pct = when (melody.greenClayRow) {
                2 -> 25
                3 -> 50
                4 -> 75
                else -> -1
            }
            if (pct > 0 && pct != lastPctSent) {
                lastPctSent = pct
                fishmod.utils.ChatQueue.enqueue("pc Melody $pct%")
            }
        }
    }
}
