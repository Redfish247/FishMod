package fishmod.features.dungeon.f7

import fishmod.features.HudPreview
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.FishUtil
import fishmod.utils.TickScheduler
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.rendering.FishDraw
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import kotlin.math.ceil
import kotlin.math.max
import fishmod.utils.debug.FishDiag

object NecronLbTimer {

    private const val NECRON_PHASE = 8
    private const val SHOOT_TICK = 4 * 20
    private val timer = TickTimer()

    @JvmStatic
    fun init() {
        timer.init(
            shouldCount = { SkyblockLocation.inDungeon() && BossPhase.isInFloor7() && BossPhase.getPhase() == NECRON_PHASE },
            resetOn = { SkyblockLocation.inDungeon() },
            onTick = { t ->
                if (Floor7Settings.enableNecronLbTimer && t == endTick()) {
                    try {
                        FishUtil.forceTitle(Component.literal("SHOOT LB!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.empty())
                        TickScheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f)
                    } catch (e: Exception) { FishDiag.fail("NecronLbTimer.1", "necron LB alert threw", e) }
                }
            }
        )
    }

    private fun endTick(): Int =
        max(1, SHOOT_TICK - ceil(max(0, Floor7Settings.necronLbPingMs) / 50.0).toInt())

    @JvmStatic
    fun display(): Boolean = Floor7Settings.enableTickTimers && Floor7Settings.enableNecronLbTimer && SkyblockLocation.inDungeon() &&
        BossPhase.isInFloor7() && BossPhase.getPhase() == NECRON_PHASE && timer.tick in 0..endTick()

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("NecronLbTimer.2", "necron LB timer render threw") { FishDraw.drawTimer(component, context, (if (HudPreview.active) 64 else endTick() - timer.tick) * FishConstants.TICK_DURATION, Floor7Settings.necronLbColor) }
    }
}
