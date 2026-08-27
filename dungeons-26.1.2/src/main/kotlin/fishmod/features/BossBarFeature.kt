package fishmod.features

import fishmod.mixin.accessors.BossBarHudAccessor
import fishmod.utils.debug.Debug
import fishmod.utils.rendering.RenderUtils
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.LerpingBossEvent
import net.minecraft.network.chat.Component

object BossBarFeature {

    /** Called from HudRenderCallback — fires BEFORE vanilla boss bar, so only used for non-boss-bar elements. */
    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor) {
        // intentionally empty — boss HP drawn in renderAfterVanilla
    }

    /** Called from FishBossBarHudMixin @Inject(RETURN) — fires after vanilla draws its text. */
    @JvmStatic
    fun renderAfterVanilla(ctx: GuiGraphicsExtractor) {
        if (!fishmod.utils.config.values.Dungeons.bossHealthNumbers) return
        val mc = Minecraft.getInstance()
        if (mc == null || mc.player == null) return

        val accessor = mc.gui.bossOverlay as BossBarHudAccessor
        val bossBars = accessor.bossBars
        if (bossBars == null || bossBars.isEmpty()) return

        val screenWidth = ctx.guiWidth()
        var y = 12

        for (bar in bossBars.values) {
            val customText = buildText(bar)
            if (customText != null) {
                val textWidth = mc.font.width(customText)
                val textX = screenWidth / 2 - textWidth / 2
                ctx.text(mc.font, customText, textX, y - 9, 0xFFFFFF, true)
            }
            y += 19
        }
    }

    private fun buildText(bar: LerpingBossEvent): Component? {
        try {
            val name = bar.name.string.replace(Regex("§."), "").trim()
            val pct = bar.progress * 100f
            val pctStr = if (pct >= 10) String.format("%.1f%%", pct) else String.format("%.2f%%", pct)

            val maxHp: Float = when {
                name.contains("Maxor") -> 2.5e8f
                name.contains("Storm") -> 5e8f
                name.contains("Goldor") -> 7.5e8f
                name.contains("Necron") -> 1e9f
                else -> -1f
            }

            if (maxHp < 0) {
                return Component.literal("$name ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(pctStr).withStyle(ChatFormatting.GREEN))
            }

            val currHp = maxHp * bar.progress
            return Component.literal("$name ").withStyle(ChatFormatting.RED)
                .append(Component.literal(RenderUtils.formatNumber(currHp)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("/").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(RenderUtils.formatNumber(maxHp)).withStyle(ChatFormatting.GREEN))
        } catch (e: Exception) {
            Debug.LOGGER.error("BossBarFeature buildText error: {}", e.message)
            return null
        }
    }
}
