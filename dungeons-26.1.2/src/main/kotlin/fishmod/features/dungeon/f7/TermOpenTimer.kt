package fishmod.features.dungeon.f7

import fishmod.features.HudPreview
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

// Countdown from Storm's death to terminals opening.
object TermOpenTimer {

    private const val COUNTDOWN_TICKS = 60
    private const val OVERDUE_TICKS = 260
    private var ticksSinceStorm = 0

    private fun waiting() = SkyblockLocation.inDungeon() && BossPhase.inP2() && BossPhase.stormDead()

    @JvmStatic
    fun init() {
        FishEvents.serverTick.register {
            // practice servers skip the P3 chat lines, so overdue is expected there
            if (!waiting()) ticksSinceStorm = 0 // restart per P2 so stale counts never mis-fire
            else if (++ticksSinceStorm == OVERDUE_TICKS && !fishmod.utils.dungeon.PracticeMode.active) {
                FishDiag.fail("TermOpenTimer.1", "terminals 10s overdue, P3 never detected (floor=${BossPhase.getFloor()} phase=${BossPhase.getPhase()})")
            }
            false
        }
        FishEvents.locationChange.register { ticksSinceStorm = 0; false }
    }

    @JvmStatic
    fun display(): Boolean = Floor7Settings.enableTickTimers && Floor7Settings.termOpenHud && waiting()

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val left = (if (HudPreview.active) 41 else COUNTDOWN_TICKS - ticksSinceStorm) * FishConstants.TICK_DURATION
        FishDiag.guard("TermOpenTimer.2", "terminal open hud threw") {
            FishDraw.drawCenteredText(context, component, Component.literal(FishConstants.DECIMAL_FORMAT.format(left)), FishConstants.YELLOW)
        }
    }
}
