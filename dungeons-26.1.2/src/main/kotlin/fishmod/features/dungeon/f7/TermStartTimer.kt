package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import fishmod.utils.debug.FishDiag

object TermStartTimer {

    private const val TOTAL_TICKS = 60 // alpha: 2s earlier (was 100)
    private var tick = TOTAL_TICKS

    @JvmStatic
    fun init() {
        Events.ON_SERVER_TICK.register {
            if (Location.inDungeon() && Phase.inP2() && Phase.stormDead()) {
                tick--
                if (tick == -200) FishDiag.fail("TermStartTimer.1", "term start timer 10s overdue, P3 never detected")
            }
            false
        }
        Events.ON_LOCATION_CHANGE.register { _ ->
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
        FishDiag.guard("TermStartTimer.2", "term start timer render threw") { RenderUtils.drawCenteredText(context, component, Component.literal(Constants.DECIMAL_FORMAT.format(num)), Constants.YELLOW) }
    }
}
