package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** Counts down ~5s from Storm's death to terminal start. */
object TermStartTimer {

    private const val TOTAL_TICKS = 100
    private var tick = TOTAL_TICKS

    @JvmStatic
    fun init() {
        Events.ON_SERVER_TICK.register {
            if (Location.inDungeon() && Phase.inP2() && Phase.stormDead()) tick--
            false
        }
        Events.ON_LOCATION_CHANGE.register { newLocation ->
            tick = TOTAL_TICKS
            false
        }
    }

    @JvmStatic
    fun display(): Boolean {
        return Floor7.enableTickTimers && Floor7.enableTermStartTimer && Location.inDungeon() && Phase.inP2() && Phase.stormDead()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val num = tick * Constants.TICK_DURATION
        RenderUtils.drawCenteredText(context, component, Component.literal(Constants.DECIMAL_FORMAT.format(num)), Constants.YELLOW)
    }
}
