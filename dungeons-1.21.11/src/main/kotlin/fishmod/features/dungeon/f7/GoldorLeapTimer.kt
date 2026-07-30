package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.mixin.accessors.BossBarHudAccessor
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.Scheduler
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/** Counts down 3.45s from Goldor's health hitting 0, then tells you to leap. */
object GoldorLeapTimer {

    private const val TOTAL_TICKS = 75 // 3.75s / 0.05s per tick
    private var tick = 0
    private var wasAlive = false

    @JvmStatic
    fun init() {
        Events.ON_SERVER_TICK.register {
            if (!Location.inDungeon() || !Phase.inGoldorTunnel()) return@register false

            val progress = getGoldorProgress()
            if (progress > 0f) {
                wasAlive = true
            } else if (wasAlive) {
                wasAlive = false
                tick = TOTAL_TICKS
            }

            if (tick > 0) {
                tick--
                if (tick == 0 && Floor7.leapNotifications) {
                    Misc.forceTitle(Text.literal("LEAP!").formatted(Formatting.GREEN, Formatting.BOLD), Text.empty())
                    Scheduler.scheduleSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1f, 1f)
                }
            }
            false
        }

        Events.ON_LOCATION_CHANGE.register { newLocation ->
            tick = 0
            wasAlive = false
            false
        }
    }

    private fun getGoldorProgress(): Float {
        val mc = MinecraftClient.getInstance() ?: return -1f

        val accessor = mc.inGameHud.bossBarHud as BossBarHudAccessor
        val bossBars = accessor.bossBars
        if (bossBars == null || bossBars.isEmpty()) return -1f

        for (bar in bossBars.values) {
            val name = bar.name.string.replace(Regex("§."), "").trim()
            if (name.contains("Goldor")) return bar.percent
        }
        return -1f
    }

    @JvmStatic
    fun display(): Boolean {
        return Floor7.leapNotifications && Location.inDungeon() && Phase.inGoldorTunnel() && tick > 0
    }

    @JvmStatic
    fun render(component: HUDComponent, context: DrawContext) {
        RenderUtils.drawTimer(component, context, tick, if (tick < 20) Constants.GREEN else Constants.GOLD)
    }
}
