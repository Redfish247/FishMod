package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.features.CritTracker
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.Scheduler
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import java.util.regex.Pattern
import kotlin.math.ceil
import kotlin.math.max

object StormTickTimer {

    private val PATTERN: Pattern = Pattern.compile("^⚠ Storm is enraged! ⚠$")
    private const val DEATH_DISPLAY_DURATION = 2000L
    private const val CRUSH_TICK = 31 * 20
    private const val COUNTDOWN_DURATION = 5 * 20

    private const val LB_START_TICK = 30 * 20
    private val LB_ARCHER_END_TICK: Int = Math.round(34.35 * 20).toInt()
    private val LB_HEALER_END_TICK: Int = Math.round(34.05 * 20).toInt()

    // Py: count down 5s to the crusher window (31.5s into P2), pulled earlier by ping.
    private val PY_TICK: Int = Math.round(31.5 * 20).toInt()

    private val timer = TickTimer()
    private var deathTime = 0.0
    private var deathStartDisplayTime = 0L

    @JvmStatic
    fun init() {
        timer.init(
            shouldCount = { Location.inDungeon() && Phase.inP2() && !Phase.stormDead() },
            resetOn = { Location.inDungeon() },
            onTick = { t ->
                if (Floor7.enablePyTimer && t == pyEndTick()) {
                    Misc.forceTitle(Component.literal("STAND ON CRUSHER!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), Component.empty())
                    Scheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f)
                }
                val lbEnd = lbEndTick()
                if (Floor7.enableLbReleaseTimer && lbEnd > 0 && t == lbEnd) {
                    Misc.forceTitle(Component.literal("RELEASE NOW!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.empty())
                    Scheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f)
                }
            },
            onReset = { deathTime = 0.0; deathStartDisplayTime = 0 }
        )
        Events.ON_GAME_MESSAGE.register { text ->
            if (!Location.inDungeon() || !Phase.inP2()) return@register false
            if (PATTERN.matcher(text.string).find()) {
                deathTime = timer.tick * Constants.TICK_DURATION
                deathStartDisplayTime = System.currentTimeMillis()
                CritTracker.onStormDeath(deathTime)
                if (Floor7.enableStormDeathTime) {
                    Misc.addChatMessage(
                        Component.literal(
                            "§aStorm died at: §e"
                                    + Constants.DECIMAL_FORMAT.format(deathTime) + "s§a."
                        )
                    )
                }
            }
            false
        }
    }

    @JvmStatic
    fun display(): Boolean {
        if (Floor7.tickDownStormTickTimer) {
            val diff = CRUSH_TICK - timer.tick
            if (diff > COUNTDOWN_DURATION || diff < 0) return false
        }
        return Floor7.enableTickTimers && Floor7.enableStormTickTimer && Location.inDungeon() && Phase.inP2() && !Phase.stormDead()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        var num = timer.tick * Constants.TICK_DURATION
        if (Floor7.tickDownStormTickTimer) num = CRUSH_TICK * Constants.TICK_DURATION - num
        RenderUtils.drawTimer(component, context, num, Floor7.stormTickTimerColor)
    }

    @JvmStatic
    fun displayDeathTime(): Boolean {
        return Floor7.enableTickTimers && Floor7.enableStormDeathTime && Location.inDungeon() && Phase.inP2() && !Phase.stormDead()
                && deathTime > 0 && deathStartDisplayTime > System.currentTimeMillis() - DEATH_DISPLAY_DURATION
    }

    @JvmStatic
    fun renderDeathTime(component: HUDComponent, context: GuiGraphicsExtractor) {
        RenderUtils.drawTimer(component, context, deathTime, Constants.DARK_PURPLE)
    }

    private fun pingTicks(): Int = ceil(max(0, Floor7.lbReleaseTimerPingMs) / 50.0).toInt()

    private fun pyEndTick(): Int = max(COUNTDOWN_DURATION + 1, PY_TICK - ceil(max(0, Floor7.pyTimerPingMs) / 50.0).toInt())

    @JvmStatic
    fun displayPyTimer(): Boolean {
        val end = pyEndTick()
        return Floor7.enableTickTimers && Floor7.enablePyTimer && Location.inDungeon() && Phase.inP2() && !Phase.stormDead()
                && timer.tick >= end - COUNTDOWN_DURATION && timer.tick <= end
    }

    @JvmStatic
    fun renderPyTimer(component: HUDComponent, context: GuiGraphicsExtractor) {
        RenderUtils.drawTimer(component, context, (pyEndTick() - timer.tick) * Constants.TICK_DURATION, Floor7.pyTimerColor)
    }

    private fun lbEndTick(): Int {
        val base = when {
            DungeonClass.isClass(DungeonClass.ARCHER) -> LB_ARCHER_END_TICK
            DungeonClass.isClass(DungeonClass.HEALER) -> LB_HEALER_END_TICK
            else -> return -1
        }
        return max(LB_START_TICK + 1, base - pingTicks())
    }

    @JvmStatic
    fun displayLbReleaseTimer(): Boolean {
        val lbEnd = lbEndTick()
        return Floor7.enableTickTimers && Floor7.enableLbReleaseTimer && lbEnd > 0 && Location.inDungeon() && Phase.inP2() && !Phase.stormDead()
                && timer.tick >= LB_START_TICK && timer.tick <= lbEnd
    }

    @JvmStatic
    fun renderLbReleaseTimer(component: HUDComponent, context: GuiGraphicsExtractor) {
        val remaining = (lbEndTick() - timer.tick) * Constants.TICK_DURATION
        RenderUtils.drawTimer(component, context, remaining, Floor7.lbReleaseTimerColor)
    }
}
