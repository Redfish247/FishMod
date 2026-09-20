package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.events.Events
import net.minecraft.client.gui.GuiGraphicsExtractor

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
