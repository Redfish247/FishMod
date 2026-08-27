package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.utils.Location
import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Invincibility Timer (ported from Odin's InvincibilityTimer). Each save-your-life item has a fixed
 * proc: chat message -> [maxActive] ticks of invulnerability, then [maxCooldown] ticks before it can
 * proc again. Counters tick down on the server tick (20/s). Revives blade's
 * [Dungeons.displayInvincibilityTimer].
 */
object InvincibilityTracker {

    enum class Type(val regex: Regex, val maxActive: Int, val maxCooldown: Int, val label: String) {
        SPIRIT(Regex("^Second Wind Activated! Your Spirit Mask saved your life!$"), 30, 600, "Spirit"),
        BONZO(Regex("^Your (?:. )?Bonzo's Mask saved your life!$"), 60, 3600, "Bonzo"),
        PHOENIX(Regex("^Your Phoenix Pet saved you from certain death!$"), 80, 1200, "Phoenix");

        @JvmField var active = 0
        @JvmField var cooldown = 0

        fun proc() { active = maxActive; cooldown = maxCooldown }
        fun tick() { if (cooldown > 0) cooldown--; if (active > 0) active-- }
        fun reset() { active = 0; cooldown = 0 }
    }

    private const val NAME = "Invincibility Timer"
    private const val LINE_H = 10
    private val COLOR = Regex("§.")

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.invincHudX }, { v -> FishSettings.invincHudX = v },
            { FishSettings.invincHudY }, { v -> FishSettings.invincHudY = v },
            80, 40,
            { FishSettings.invincScale }, { v -> FishSettings.invincScale = v }
        )

        Events.ON_GAME_MESSAGE.register { text ->
            if (Dungeons.displayInvincibilityTimer) {
                val s = COLOR.replace(text.string, "").trim()
                Type.entries.firstOrNull { it.regex.matches(s) }?.proc()
            }
            false
        }
        Events.ON_SERVER_TICK.register { Type.entries.forEach { it.tick() }; false }
        Events.ON_WORLD_CHANGE.register { Type.entries.forEach { it.reset() }; false }
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!Dungeons.displayInvincibilityTimer || !Location.inDungeon()) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return

        val shown = Type.entries.filter { it.active > 0 || it.cooldown > 0 }
        if (shown.isEmpty()) return

        val sc = FishSettings.invincScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.invincHudX.toFloat(), FishSettings.invincHudY.toFloat())
        ctx.pose().scale(sc, sc)
        shown.forEachIndexed { i, t ->
            val line = when {
                t.active > 0 -> "§7${t.label} §6" + String.format("%.1fs", t.active / 20f)
                else -> "§7${t.label} §c" + String.format("%.1fs", t.cooldown / 20f)
            }
            ctx.text(mc.font, line, 0, i * LINE_H, -1, true)
        }
        ctx.pose().popMatrix()
    }
}
