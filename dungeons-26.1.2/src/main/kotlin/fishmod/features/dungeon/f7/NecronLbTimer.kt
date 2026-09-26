package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.Scheduler
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import kotlin.math.ceil
import kotlin.math.max

object NecronLbTimer {

    private const val NECRON_PHASE = 8
    private const val SHOOT_TICK = 8 * 20
    private val timer = TickTimer()

    @JvmStatic
    fun init() {
        timer.init(
            shouldCount = { Location.inDungeon() && Phase.isInFloor7() && Phase.getPhase() == NECRON_PHASE },
            resetOn = { Location.inDungeon() },
            onTick = { t ->
                if (Floor7.enableNecronLbTimer && t == endTick()) {
                    Misc.forceTitle(Component.literal("SHOOT LB!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.empty())
                    Scheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f)
                }
            }
        )
    }

    private fun endTick(): Int =
        max(1, SHOOT_TICK - ceil(max(0, Floor7.necronLbPingMs) / 50.0).toInt())

    @JvmStatic
    fun display(): Boolean = Floor7.enableTickTimers && Floor7.enableNecronLbTimer && Location.inDungeon() &&
        Phase.isInFloor7() && Phase.getPhase() == NECRON_PHASE && timer.tick in 0..endTick()

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        RenderUtils.drawTimer(component, context, (endTick() - timer.tick) * Constants.TICK_DURATION, Floor7.necronLbColor)
    }
}
