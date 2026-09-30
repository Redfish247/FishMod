package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.events.Events
import net.minecraft.client.gui.GuiGraphicsExtractor
import fishmod.utils.debug.FishDiag

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
            try {
                if (shouldCount()) {
                    tick++
                    onTick(tick)
                }
            } catch (e: Exception) { FishDiag.fail("TickTimer.1", "tick timer count/onTick threw at tick $tick", e) }
            false
        }
        Events.ON_LOCATION_CHANGE.register {
            try {
                if (resetOn()) {
                    tick = 0
                    onReset()
                }
            } catch (e: Exception) { FishDiag.fail("TickTimer.2", "tick timer reset threw", e) }
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
        try {
            when {
                MaxorTickTimer.display() -> MaxorTickTimer.render(component, context)
                StormTickTimer.display() -> StormTickTimer.render(component, context)
                GoldorTickTimer.display() -> GoldorTickTimer.render(component, context)
            }
        } catch (e: Exception) { FishDiag.fail("TickTimer.3", "boss tick timer render threw", e) }
    }
}
