package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

/** Draws either a built-in preset shape or a user-supplied crosshair image (config/FishMod/crosshairs/)
 *  centered on screen, in place of the vanilla crosshair. */
object CustomCrosshair {

    val PRESETS = arrayOf("Dot", "Cross", "Plus", "Square")

    @JvmStatic
    fun register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "custom_crosshair")) { g, _ ->
            val mc = Minecraft.getInstance()
            if (active(mc)) {
                // matches vanilla Gui.extractCrosshair's own centering: (guiWidth/Height - size) / 2
                // off g.guiWidth()/guiHeight(), not the window's guiScaledWidth/Height
                val cx = g.guiWidth() / 2
                val cy = g.guiHeight() / 2
                if (FishSettings.crosshairMode == "Preset") {
                    val (texId, halfExtent) = CrosshairPresetTextures.getTexture(FishSettings.crosshairPreset, FishSettings.crosshairScale)
                    val size = halfExtent * 2 + 1
                    g.blit(RenderPipelines.GUI_TEXTURED, texId, cx - halfExtent, cy - halfExtent, 0.0f, 0.0f, size, size, size, size, FishSettings.crosshairColor)
                } else {
                    val imgId = CrosshairImageLoader.getImageId(FishSettings.crosshairImageSelection)
                    val size = CrosshairImageLoader.getImageSize(FishSettings.crosshairImageSelection)
                    if (imgId != null && size != null) {
                        val scale = FishSettings.crosshairScale.coerceIn(0.1, 8.0)
                        val w = (size[0] * scale).toInt().coerceAtLeast(1)
                        val h = (size[1] * scale).toInt().coerceAtLeast(1)
                        g.blit(RenderPipelines.GUI_TEXTURED, imgId, cx - w / 2, cy - h / 2, 0.0f, 0.0f, w, h, w, h, FishSettings.crosshairColor)
                    }
                }
            }
        }
    }

    /** Whether the vanilla crosshair should be replaced (a preset, or an actual image, is selected). */
    @JvmStatic
    fun active(mc: Minecraft): Boolean {
        if (!FishSettings.crosshairEnabled || mc.options.hideGui) return false
        if (FishSettings.crosshairMode == "Preset") return true
        val sel = FishSettings.crosshairImageSelection
        return sel.isNotEmpty() && sel != CrosshairImageLoader.NO_IMAGE && CrosshairImageLoader.getImageId(sel) != null
    }
}
