package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import fishmod.utils.debug.FishDiag

object TermOpenTimer {

    private const val TOTAL_TICKS = 60 // alpha: 2s earlier (was 100)
    private var tick = TOTAL_TICKS

    @JvmStatic
    fun init() {
        FishEvents.ON_SERVER_TICK.register {
            if (SkyblockLocation.inDungeon() && BossPhase.inP2() && BossPhase.stormDead()) {
                tick--
                if (tick == -200) FishDiag.fail("TermStartTimer.1", "term start timer 10s overdue, P3 never detected (floor=${BossPhase.getFloor()} phase=${BossPhase.getPhase()}, no Goldor line or terminal progress seen)")
            }
            false
        }
        FishEvents.ON_LOCATION_CHANGE.register { _ ->
            tick = TOTAL_TICKS
            false
        }
    }

    @JvmStatic
    fun display(): Boolean {
        return Floor7Settings.enableTickTimers && Floor7Settings.termOpenHud && SkyblockLocation.inDungeon() && BossPhase.inP2() && BossPhase.stormDead()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val num = tick * FishConstants.TICK_DURATION
        FishDiag.guard("TermStartTimer.2", "term start timer render threw") { FishDraw.drawCenteredText(context, component, Component.literal(FishConstants.DECIMAL_FORMAT.format(num)), FishConstants.YELLOW) }
    }
}
