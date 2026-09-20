package fishmod.features

import fishmod.utils.config.values.Visual
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.ARGB

object DarkMode {

    @JvmStatic
    fun drawOverlay(ctx: GuiGraphicsExtractor) {
        if (!Visual.darkModeEnabled) return
        val window = Minecraft.getInstance().window
        ctx.fill(0, 0, window.guiScaledWidth, window.guiScaledHeight,
            ARGB.multiplyAlpha(0xFF000000.toInt(), Visual.darkModeOpacity.coerceIn(1, 80) / 100f))
    }
}
