package fishmod.features.dungeon.f7

import fishmod.features.HudPreview
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.events.FishEvents
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
        FishEvents.serverTick.register {
            try {
                if (shouldCount()) {
                    tick++
                    onTick(tick)
                }
            } catch (e: Exception) { FishDiag.fail("TickTimer.1", "tick timer count/onTick threw at tick $tick", e) }
            false
        }
        FishEvents.locationChange.register {
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
        return MaxorTicks.display() || StormTicks.display() || GoldorTicks.display()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        try {
            when {
                HudPreview.active -> MaxorTicks.render(component, context)
                MaxorTicks.display() -> MaxorTicks.render(component, context)
                StormTicks.display() -> StormTicks.render(component, context)
                GoldorTicks.display() -> GoldorTicks.render(component, context)
            }
        } catch (e: Exception) { FishDiag.fail("TickTimer.3", "boss tick timer render threw", e) }
    }
}
