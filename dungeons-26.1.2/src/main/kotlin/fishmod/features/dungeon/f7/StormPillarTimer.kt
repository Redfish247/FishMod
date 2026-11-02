package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.TickScheduler
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import fishmod.utils.debug.FishDiag

object StormPillarTimer {

    private const val TOTAL_TICKS = 20
    private var tick = 0

    @JvmStatic
    fun init() {
        FishEvents.ON_GAME_MESSAGE.register { text ->
            if (!Floor7Settings.stormCrushAlert && !Floor7Settings.pillarBlastTimer) return@register false
            if (!SkyblockLocation.inDungeon() || !BossPhase.inP2()) return@register false
            val string = text.string
            if (string == "[BOSS] Storm: Oof" || string == "[BOSS] Storm: Ouch, that hurt!") {
                tick = TOTAL_TICKS
                FishDiag.guard("PillarExplode.1", "storm crush sound failed") { TickScheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f) }
            }
            false
        }
        FishEvents.ON_SERVER_TICK.register {
            tick = maxOf(tick - 1, 0)
            false
        }
    }

    @JvmStatic
    fun displayTimer(): Boolean = Floor7Settings.pillarBlastTimer && tick > 0

    @JvmStatic
    fun renderTimer(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("PillarExplode.2", "pillar explosion timer render threw") { FishDraw.drawTimer(component, context, tick, if (tick < 6) FishConstants.GREEN else FishConstants.RED) }
    }

    @JvmStatic
    fun display(): Boolean = Floor7Settings.enableTickTimers && Floor7Settings.stormCrushAlert && tick > 0

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("PillarExplode.3", "storm crushed render threw") { FishDraw.drawCenteredText(context, component, Component.literal("§6||| §bStorm crushed! §6|||")) }
    }
}
