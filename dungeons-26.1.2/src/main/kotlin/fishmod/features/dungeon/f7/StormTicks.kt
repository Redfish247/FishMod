package fishmod.features.dungeon.f7

import fishmod.utils.events.chatRaw
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.features.CritTracker
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.FishUtil
import fishmod.utils.TickScheduler
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.dungeon.DungeonRole
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import java.util.regex.Pattern
import kotlin.math.ceil
import kotlin.math.max
import fishmod.utils.debug.FishDiag

object StormTicks {

    private val PATTERN: Pattern = Pattern.compile("^⚠ Storm is enraged! ⚠$")
    private const val DEATH_DISPLAY_DURATION = 2000L
    private const val CRUSH_TICK = 31 * 20
    private const val COUNTDOWN_DURATION = 5 * 20

    private const val LB_START_TICK = 30 * 20
    private val LB_ARCHER_END_TICK: Int = Math.round(34.40 * 20).toInt()
    private val LB_HEALER_END_TICK: Int = Math.round(34.10 * 20).toInt()

    private val PY_TICK: Int = Math.round(29.5 * 20).toInt() // alpha: 2s earlier (was 31.5)

    private val STORM_OVER_TICK: Int = Math.round(26.75 * 20).toInt() // alpha: lightning 2s earlier (was 28.75)

    private val timer = TickTimer()
    private var deathTime = 0.0
    private var deathStartDisplayTime = 0L

    @JvmStatic
    fun init() {
        timer.init(
            shouldCount = { SkyblockLocation.inDungeon() && BossPhase.inP2() && !BossPhase.stormDead() },
            resetOn = { SkyblockLocation.inDungeon() },
            onTick = { t ->
                try {
                StormOverAlert.onTick(t, STORM_OVER_TICK)
                if (Floor7Settings.enablePyTimer && t == pyEndTick()) {
                    FishUtil.forceTitle(Component.literal("STAND ON CRUSHER!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), Component.empty())
                    TickScheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f)
                }
                val lbEnd = lbEndTick()
                if (Floor7Settings.enableLbReleaseTimer && lbEnd > 0 && t == lbEnd) {
                    FishUtil.forceTitle(Component.literal("RELEASE NOW!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), Component.empty())
                    TickScheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f)
                }
                } catch (e: Exception) { FishDiag.fail("StormTicks.1", "storm tick alerts threw at tick $t", e) }
            },
            onReset = { deathTime = 0.0; deathStartDisplayTime = 0 }
        )
        FishEvents.chat.register { text ->
            if (!SkyblockLocation.inDungeon() || !BossPhase.inP2()) return@register false
            if (deathTime == 0.0 && text.chatRaw.startsWith("⚠ Storm is enraged") && !PATTERN.matcher(text.chatRaw).find()) FishDiag.fail("StormTicks.2", "storm enraged line format changed: '${text.chatRaw}'")
            if (deathTime == 0.0 && PATTERN.matcher(text.chatRaw).find()) {
                FishDiag.check(timer.tick > 0, "StormTicks.3") { "storm died but P2 tick timer never counted" }
                deathTime = timer.tick * FishConstants.TICK_DURATION
                deathStartDisplayTime = System.currentTimeMillis()
                FishDiag.guard("StormTicks.4", "storm death listeners threw") {
                    CritTracker.onStormDeath(deathTime)
                    BossPhase.onStormKill(deathTime)
                }
                if (Floor7Settings.stormKillTimeHud && !fishmod.compat.BladeCompat.active) {
                    FishUtil.addChatMessage(Component.literal("§dStorm killed in §f${FishConstants.DECIMAL_FORMAT.format(deathTime)}s"))
                }
            }
            false
        }
    }

    @JvmStatic
    fun display(): Boolean {
        if (Floor7Settings.stormTicksCountDown) {
            val diff = CRUSH_TICK - timer.tick
            if (diff > COUNTDOWN_DURATION || diff < 0) return false
        }
        return Floor7Settings.enableTickTimers && Floor7Settings.stormTicksHud && SkyblockLocation.inDungeon() && BossPhase.inP2() && !BossPhase.stormDead()
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        var num = timer.tick * FishConstants.TICK_DURATION
        if (Floor7Settings.stormTicksCountDown) num = CRUSH_TICK * FishConstants.TICK_DURATION - num
        FishDiag.guard("StormTicks.5", "storm tick timer render threw") { FishDraw.drawTimer(component, context, num, Floor7Settings.stormTicksColor) }
    }

    @JvmStatic
    fun displayDeathTime(): Boolean {
        return Floor7Settings.enableTickTimers && Floor7Settings.stormKillTimeHud && SkyblockLocation.inDungeon() && BossPhase.inP2() && !BossPhase.stormDead()
                && deathTime > 0 && deathStartDisplayTime > System.currentTimeMillis() - DEATH_DISPLAY_DURATION
    }

    @JvmStatic
    fun renderDeathTime(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("StormTicks.6", "storm death time render threw") { FishDraw.drawTimer(component, context, deathTime, FishConstants.DARK_PURPLE) }
    }

    private fun pingTicks(): Int = ceil(max(0, Floor7Settings.lbReleaseTimerPingMs) / 50.0).toInt()

    private fun pyEndTick(): Int = max(COUNTDOWN_DURATION + 1, PY_TICK - ceil(max(0, Floor7Settings.pyTimerPingMs) / 50.0).toInt())

    @JvmStatic
    fun displayPyTimer(): Boolean {
        val end = pyEndTick()
        return Floor7Settings.enableTickTimers && Floor7Settings.enablePyTimer && SkyblockLocation.inDungeon() && BossPhase.inP2() && !BossPhase.stormDead()
                && timer.tick >= end - COUNTDOWN_DURATION && timer.tick <= end
    }

    @JvmStatic
    fun renderPyTimer(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("StormTicks.7", "py timer render threw") { FishDraw.drawTimer(component, context, (pyEndTick() - timer.tick) * FishConstants.TICK_DURATION, Floor7Settings.pyTimerColor) }
    }

    private fun lbEndTick(): Int {
        val base = when {
            DungeonRole.isClass(DungeonRole.ARCHER) -> LB_ARCHER_END_TICK
            DungeonRole.isClass(DungeonRole.HEALER) -> LB_HEALER_END_TICK
            else -> return -1
        }
        return max(LB_START_TICK + 1, base - pingTicks())
    }

    @JvmStatic
    fun displayLbReleaseTimer(): Boolean {
        val lbEnd = lbEndTick()
        return Floor7Settings.enableTickTimers && Floor7Settings.enableLbReleaseTimer && lbEnd > 0 && SkyblockLocation.inDungeon() && BossPhase.inP2() && !BossPhase.stormDead()
                && timer.tick >= LB_START_TICK && timer.tick <= lbEnd
    }

    @JvmStatic
    fun renderLbReleaseTimer(component: HUDComponent, context: GuiGraphicsExtractor) {
        val remaining = (lbEndTick() - timer.tick) * FishConstants.TICK_DURATION
        FishDiag.check(remaining >= 0, "StormTicks.8") { "LB release timer negative: $remaining" }
        FishDiag.guard("StormTicks.9", "LB release timer render threw") { FishDraw.drawTimer(component, context, remaining, Floor7Settings.lbReleaseTimerColor) }
    }
}
