package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.TickScheduler
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

// Storm's hit lines mean a pillar just crushed him: 1s pillar blast countdown plus a banner.
object StormPillarTimer {

    private val HIT_LINES = setOf("[BOSS] Storm: Oof", "[BOSS] Storm: Ouch, that hurt!")
    private const val BLAST_TICKS = 20
    private var ticksLeft = 0

    @JvmStatic
    fun init() {
        FishEvents.chat.register { text ->
            val wanted = Floor7Settings.stormCrushAlert || Floor7Settings.pillarBlastTimer
            if (wanted && SkyblockLocation.inDungeon() && BossPhase.inP2() && text.string in HIT_LINES) {
                ticksLeft = BLAST_TICKS
                FishDiag.guard("StormPillarTimer.1", "storm crush sound failed") { TickScheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f) }
            }
            false
        }
        FishEvents.serverTick.register { if (ticksLeft > 0) ticksLeft--; false }
    }

    @JvmStatic
    fun displayTimer(): Boolean = Floor7Settings.pillarBlastTimer && ticksLeft > 0

    @JvmStatic
    fun renderTimer(component: HUDComponent, context: GuiGraphicsExtractor) {
        val color = if (ticksLeft >= 6) FishConstants.RED else FishConstants.GREEN
        FishDiag.guard("StormPillarTimer.2", "pillar blast hud threw") { FishDraw.drawTimer(component, context, ticksLeft, color) }
    }

    @JvmStatic
    fun display(): Boolean = Floor7Settings.enableTickTimers && Floor7Settings.stormCrushAlert && ticksLeft > 0

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("StormPillarTimer.3", "storm crush banner threw") { FishDraw.drawCenteredText(context, component, Component.literal("§d§lSTORM CRUSHED")) }
    }
}
