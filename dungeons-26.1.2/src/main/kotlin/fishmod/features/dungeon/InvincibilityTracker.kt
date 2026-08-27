package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.utils.Location
import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.DrawEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

/**
 * Invincibility Timer (ported from Odin's InvincibilityTimer). Each "saved your life" item has a
 * fixed proc: chat line -> [maxActive] ticks of invulnerability, then [maxCooldown] ticks before it
 * can proc again. Counters tick down on the server tick (20/s).
 *
 * Enable: [Dungeons.displayInvincibilityTimer]. [Dungeons.InvincibilityDuration] shows the numeric
 * "X.Xs" vs a plain dot; [Dungeons.useStatusColorForInvincibility] colours by state (gold active /
 * red cooldown / green ready).
 */
object InvincibilityTracker {

    enum class Type(
        val regex: Regex,
        val maxActive: Int,
        val maxCooldown: Int,
        val label: String,
        val ids: Set<String>,
        val show: () -> Boolean,
    ) {
        SPIRIT(Regex("^Second Wind Activated! Your Spirit Mask saved your life!$"), 30, 600, "Spirit",
            setOf("SPIRIT_MASK", "STARRED_SPIRIT_MASK"), { FishSettings.invincShowSpirit }),
        BONZO(Regex("^Your (?:. )?Bonzo's Mask saved your life!$"), 60, 3600, "Bonzo",
            setOf("BONZO_MASK", "STARRED_BONZO_MASK"), { FishSettings.invincShowBonzo }),
        PHOENIX(Regex("^Your Phoenix Pet saved you from certain death!$"), 80, 1200, "Phoenix",
            emptySet(), { FishSettings.invincShowPhoenix });

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
                Type.entries.firstOrNull { it.regex.matches(s) }?.let { t ->
                    t.proc()
                    if (FishSettings.invincAnnounce) {
                        val mc = Minecraft.getInstance()
                        mc.execute { mc.connection?.sendCommand("pc ${t.label} Procced!") }
                    }
                }
            }
            false
        }
        Events.ON_SERVER_TICK.register { Type.entries.forEach { it.tick() }; false }
        Events.ON_WORLD_CHANGE.register { Type.entries.forEach { it.reset() }; false }

        // Durability-style cooldown bar on the mask item in any slot GUI.
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!Dungeons.displayInvincibilityTimer || !FishSettings.invincShowCooldown) return@register
            drawSlotBar(ctx, stack, x, y)
        }
    }

    /** Odin's "Show" selector: which entries appear given their active/cooldown state. */
    private fun visible(t: Type): Boolean {
        if (!t.show()) return false
        return when (FishSettings.invincShowWhen) {
            "Always" -> true
            "Active" -> t.active > 0
            "Cooldown" -> t.cooldown > 0
            else -> t.active > 0 || t.cooldown > 0 // "Any"
        }
    }

    private fun stateColor(t: Type): String {
        if (!Dungeons.useStatusColorForInvincibility) return "§7"
        return when {
            t.active > 0 -> "§6"
            t.cooldown > 0 -> "§c"
            else -> "§a"
        }
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!Dungeons.displayInvincibilityTimer || !Location.inDungeon()) return
        if (FishSettings.invincShowInBoss && !Phase.inBoss()) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return

        val shown = Type.entries.filter { visible(it) }
        if (shown.isEmpty()) return

        val sc = FishSettings.invincScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.invincHudX.toFloat(), FishSettings.invincHudY.toFloat())
        ctx.pose().scale(sc, sc)
        shown.forEachIndexed { i, t ->
            val c = stateColor(t)
            val value = when {
                t.active > 0 || t.cooldown > 0 ->
                    if (Dungeons.InvincibilityDuration)
                        String.format("%.1fs", (if (t.active > 0) t.active else t.cooldown) / 20f)
                    else "●"
                else -> "✔"
            }
            ctx.text(mc.font, "§7${t.label} $c$value", 0, i * LINE_H, -1, true)
        }
        ctx.pose().popMatrix()
    }

    private fun drawSlotBar(ctx: GuiGraphicsExtractor, stack: ItemStack?, x: Int, y: Int) {
        if (stack == null || stack.isEmpty) return
        val id = ItemUtil.getId(stack) ?: return
        val t = Type.entries.firstOrNull { id in it.ids } ?: return
        if (t.cooldown <= 0) return
        val frac = t.cooldown.toFloat() / t.maxCooldown
        val w = (13 * (1f - frac)).toInt().coerceIn(0, 13)
        // background then remaining (green->red by fraction), 1px above the slot's bottom edge.
        ctx.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000.toInt())
        val col = if (frac > 0.5f) 0xFFFF5555.toInt() else 0xFF55FF55.toInt()
        ctx.fill(x + 2, y + 13, x + 2 + w, y + 14, col)
    }
}
