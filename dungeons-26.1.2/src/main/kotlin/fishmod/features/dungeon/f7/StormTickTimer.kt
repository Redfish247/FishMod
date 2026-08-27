package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
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

/** Storm (P2) tick timer + first-death time. Ported from blade-addons (spirit-mask warning omitted). */
object StormTickTimer {

    private val PATTERN: Pattern = Pattern.compile("^⚠ Storm is enraged! ⚠$")
    private const val DEATH_DISPLAY_DURATION = 2000L
    private const val CRUSH_TICK = 31 * 20
    private const val COUNTDOWN_DURATION = 5 * 20

    // LB (Last Breath) release window: visible once the Storm clock hits 30s.
    // Archer releases at 34.35s, Healer at 34.05s; hidden on other classes.
    private const val LB_START_TICK = 30 * 20
    private val LB_ARCHER_END_TICK: Int = Math.round(34.35 * 20).toInt()
    private val LB_HEALER_END_TICK: Int = Math.round(34.05 * 20).toInt()

    private val timer = TickTimer()
    private var deathTime = 0.0
    private var deathStartDisplayTime = 0L

    @JvmStatic
    fun init() {
        timer.init(
            shouldCount = { Location.inDungeon() && Phase.inP2() && !Phase.stormDead() },
            resetOn = { Location.inDungeon() },
            onTick = { t ->
                val lbEnd = lbEndTick()
                if (Floor7.enableLbReleaseTimer && lbEnd > 0 && t == lbEnd) {
                    Misc.forceTitle(Component.literal("RELEASE NOW!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.empty())
                    Scheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f)
                }
            },
            onReset = { deathTime = 0.0; deathStartDisplayTime = 0 }
        )
        Events.ON_GAME_MESSAGE.register { text ->
            if (!Location.inDungeon() || !Phase.inP2() || !Floor7.enableStormDeathTime) return@register false
            if (PATTERN.matcher(text.string).find()) {
                deathTime = timer.tick * Constants.TICK_DURATION
                deathStartDisplayTime = System.currentTimeMillis()
                Misc.addChatMessage(
                    Component.literal(
                        "§aStorm died at: §e"
                                + Constants.DECIMAL_FORMAT.format(deathTime) + "s§a."
                    )
                )
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

    /** Ping compensation in ticks (20 tps): shifts the release cue earlier so the arrow leaves on time. */
    private fun pingTicks(): Int = ceil(max(0, Floor7.lbReleaseTimerPingMs) / 50.0).toInt()

    /** Class-specific LB release tick (ping-compensated), or -1 when the timer shouldn't show for this class. */
    private fun lbEndTick(): Int {
        val base = when {
            DungeonClass.isClass(DungeonClass.ARCHER) -> LB_ARCHER_END_TICK
            DungeonClass.isClass(DungeonClass.HEALER) -> LB_HEALER_END_TICK
            else -> return -1
        }
        // Never pull the cue before the window even opens.
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
