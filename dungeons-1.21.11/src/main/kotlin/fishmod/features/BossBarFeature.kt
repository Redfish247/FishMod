package fishmod.features

import fishmod.mixin.accessors.BossBarHudAccessor
import fishmod.utils.debug.Debug
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.hud.ClientBossBar
import net.minecraft.text.Text
import net.minecraft.util.Formatting

object BossBarFeature {

    /** Called from HudRenderCallback — fires BEFORE vanilla boss bar, so only used for non-boss-bar elements. */
    @JvmStatic
    fun renderHud(ctx: DrawContext) {
        // intentionally empty — boss HP drawn in renderAfterVanilla
    }

    /** Called from FishBossBarHudMixin @Inject(RETURN) — fires after vanilla draws its text. */
    @JvmStatic
    fun renderAfterVanilla(ctx: DrawContext) {
        val mc = MinecraftClient.getInstance()
        if (mc == null || mc.player == null) return

        val accessor = mc.inGameHud.bossBarHud as BossBarHudAccessor
        val bossBars = accessor.bossBars
        if (bossBars == null || bossBars.isEmpty()) return

        val screenWidth = ctx.scaledWindowWidth
        var y = 12

        for (bar in bossBars.values) {
            val customText = buildText(bar)
            if (customText != null) {
                val textWidth = mc.textRenderer.getWidth(customText)
                val textX = screenWidth / 2 - textWidth / 2
                ctx.drawText(mc.textRenderer, customText, textX, y - 9, 0xFFFFFF, true)
            }
            y += 19
        }
    }

    private fun buildText(bar: ClientBossBar): Text? {
        try {
            val name = bar.name.string.replace(Regex("§."), "").trim()
            val pct = bar.percent * 100f
            val pctStr = if (pct >= 10) String.format("%.1f%%", pct) else String.format("%.2f%%", pct)

            val maxHp: Float = when {
                name.contains("Maxor") -> 2.5e8f
                name.contains("Storm") -> 5e8f
                name.contains("Goldor") -> 7.5e8f
                name.contains("Necron") -> 1e9f
                else -> -1f
            }

            if (maxHp < 0) {
                return Text.literal("$name ").formatted(Formatting.RED)
                    .append(Text.literal(pctStr).formatted(Formatting.GREEN))
            }

            val currHp = maxHp * bar.percent
            return Text.literal("$name ").formatted(Formatting.RED)
                .append(Text.literal(RenderUtils.formatNumber(currHp)).formatted(Formatting.GREEN))
                .append(Text.literal("/").formatted(Formatting.GRAY))
                .append(Text.literal(RenderUtils.formatNumber(maxHp)).formatted(Formatting.GREEN))
        } catch (e: Exception) {
            Debug.LOGGER.error("BossBarFeature buildText error: {}", e.message)
            return null
        }
    }
}
