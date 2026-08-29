package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Maxor (P1) tick timer — counts server ticks while in P1. Ported from blade-addons. */
object MaxorTickTimer {

    private val timer = TickTimer()

    @JvmStatic
    fun init() {
        timer.init(shouldCount = { Location.inDungeon() && Phase.inP1() })
    }

    @JvmStatic
    fun display(): Boolean {
        return Floor7.enableTickTimers && Floor7.enableMaxorTickTimer && Location.inDungeon() && Phase.inP1()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        RenderUtils.drawTimer(component, context, timer.tick, 0xffffffff.toInt())
    }
}
