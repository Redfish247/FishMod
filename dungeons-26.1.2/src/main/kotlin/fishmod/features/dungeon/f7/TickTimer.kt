package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.utils.events.Events
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Shared server-tick counter backing the F7 boss tick timers (Maxor/Storm/Goldor):
 * increments on [Events.ON_SERVER_TICK] while [shouldCount] holds, and resets on
 * [Events.ON_LOCATION_CHANGE] while [resetOn] holds.
 */
class TickTimer {

    var tick: Int = 0
        private set

    fun init(
        shouldCount: () -> Boolean,
        resetOn: () -> Boolean = { true },
        onTick: (Int) -> Unit = {},
        onReset: () -> Unit = {}
    ) {
        Events.ON_SERVER_TICK.register {
            if (shouldCount()) {
                tick++
                onTick(tick)
            }
            false
        }
        Events.ON_LOCATION_CHANGE.register {
            if (resetOn()) {
                tick = 0
                onReset()
            }
            false
        }
    }
}

/**
 * Merges the Maxor/Storm/Goldor tick timers into a single HUD element: they're mutually
 * exclusive by dungeon phase (P1/P2/terminals), so at most one is ever active at once, and
 * showing them as one draggable box avoids three overlapping boxes at the same default spot.
 */
object BossTickTimer {

    @JvmStatic
    fun display(): Boolean {
        return MaxorTickTimer.display() || StormTickTimer.display() || GoldorTickTimer.display()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        when {
            MaxorTickTimer.display() -> MaxorTickTimer.render(component, context)
            StormTickTimer.display() -> StormTickTimer.render(component, context)
            GoldorTickTimer.display() -> GoldorTickTimer.render(component, context)
        }
    }
}
