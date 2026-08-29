package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Goldor / terminals tick timer (3-tick cycle, optional tick-up). Ported from blade-addons. */
object GoldorTickTimer {

    private val timer = TickTimer()

    @JvmStatic
    fun init() {
        timer.init(
            shouldCount = { Location.inDungeon() && Phase.inTerminals() },
            resetOn = { Location.inDungeon() }
        )
    }

    @JvmStatic
    fun display(): Boolean {
        return Floor7.enableTickTimers && Floor7.enableGoldorTickTimer && Location.inDungeon() && Phase.inTerminals()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        var num = timer.tick * Constants.TICK_DURATION
        var mod = num % 3
        if (Floor7.inDeathTicks && !Floor7.makeGoldorTickUp) mod = 3.0 - mod
        if (Floor7.inDeathTicks) num = mod
        val color = if (mod < 1) Constants.GREEN else if (mod < 2) Constants.GOLD else Constants.RED
        RenderUtils.drawTimer(component, context, num, color)
    }
}
