package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Maxor (P1) tick timer — counts server ticks while in P1. Ported from blade-addons. */
object MaxorTickTimer {

    private var tick = 0

    @JvmStatic
    fun init() {
        Events.ON_SERVER_TICK.register {
            if (Location.inDungeon() && Phase.inP1()) tick++
            false
        }
        Events.ON_LOCATION_CHANGE.register { newLocation ->
            tick = 0
            false
        }
    }

    @JvmStatic
    fun display(): Boolean {
        return Floor7.enableMaxorTickTimer && Location.inDungeon() && Phase.inP1()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        RenderUtils.drawTimer(component, context, tick, 0xffffffff.toInt())
    }
}
