package fishmod.features.dungeon.f7.s4

import config.practical.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.Scheduler
import fishmod.utils.config.values.Floor7
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

enum class S4AlertType(val label: String, val color: Int) {
    EARLY_LEAP("LEAPED EARLY", Constants.RED),
    LATE_LEAP("LEAPED LATE", Constants.GOLD),
    MISSED_TERM("MAY HAVE MISSED TERM", Constants.RED),
    DEATH("DIED", Constants.DARK_RED),
}

/**
 * Big, hard-to-miss on-screen alert + sound for S4 failures. One global cooldown (not per-player) so
 * a cluster of near-simultaneous events (e.g. two people leaping late) can't spam the sound.
 */
object S4Alerts {

    private const val TICK_MS = 50L

    private var cooldownUntil = 0L
    private var activeType: S4AlertType? = null
    private var activePlayer: String? = null
    private var activeUntil = 0L

    @JvmStatic
    fun trigger(type: S4AlertType, playerName: String) {
        if (!Floor7.s4AlertsEnabled || !alertTypeEnabled(type)) return
        val now = System.currentTimeMillis()
        if (now < cooldownUntil) return

        activeType = type
        activePlayer = playerName
        activeUntil = now + Floor7.s4AlertDurationTicks * TICK_MS
        cooldownUntil = now + Floor7.s4AlertCooldownTicks * TICK_MS

        if (Floor7.s4AlertSoundEnabled) {
            Scheduler.scheduleSound(Floor7.s4AlertSound)
        }
    }

    private fun alertTypeEnabled(type: S4AlertType): Boolean = when (type) {
        S4AlertType.EARLY_LEAP -> Floor7.s4EarlyLeapAlert
        S4AlertType.LATE_LEAP -> Floor7.s4LateLeapAlert
        S4AlertType.MISSED_TERM -> Floor7.s4MissedTermAlert
        S4AlertType.DEATH -> Floor7.s4DeathAlert
    }

    @JvmStatic
    fun reset() {
        activeType = null
        activePlayer = null
    }

    @JvmStatic
    fun display(): Boolean = Floor7.s4AlertsEnabled && activeType != null && System.currentTimeMillis() < activeUntil

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val type = activeType ?: return
        val text = Component.literal("⚠ ${activePlayer ?: "Someone"} ${type.label}").withColor(type.color)
        RenderUtils.drawCenteredText(context, component, text)
    }
}
