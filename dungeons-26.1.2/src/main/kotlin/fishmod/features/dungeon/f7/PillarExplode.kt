package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Scheduler
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents

/** "Storm crushed!" notification + optional pillar-explode timer. Ported from blade-addons. */
object PillarExplode {

    private const val TOTAL_TICKS = 20
    private var tick = 0

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (!Floor7.notifyStormCrush && !Floor7.timePillarExplosion) return@register false
            if (!Location.inDungeon() || !Phase.inP2()) return@register false
            val string = text.string
            if (string == null) return@register false
            if (string == "[BOSS] Storm: Oof" || string == "[BOSS] Storm: Ouch, that hurt!") {
                tick = TOTAL_TICKS
                Scheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f)
            }
            false
        }
        Events.ON_SERVER_TICK.register {
            tick = maxOf(tick - 1, 0)
            false
        }
    }

    @JvmStatic
    fun displayTimer(): Boolean = Floor7.timePillarExplosion && tick > 0

    @JvmStatic
    fun renderTimer(component: HUDComponent, context: GuiGraphicsExtractor) {
        RenderUtils.drawTimer(component, context, tick, if (tick < 6) Constants.GREEN else Constants.RED)
    }

    @JvmStatic
    fun display(): Boolean = Floor7.notifyStormCrush && tick > 0

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        RenderUtils.drawCenteredText(context, component, Component.literal("§6||| §bStorm crushed! §6|||"))
    }
}
