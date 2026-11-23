package fishmod.features.cosmetics
import fishmod.compat.curScreen
import fishmod.compat.hudHidden

import fishmod.utils.debug.FishDiag
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

object CustomCrosshair {

    val PRESETS = arrayOf("Dot", "Cross", "Plus", "Square", "Circle Dot", "Target", "Brackets", "Corners", "X")

    @JvmStatic
    fun register() {
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "custom_crosshair")) { g, _ ->
            val mc = Minecraft.getInstance()
            if (mc.curScreen == null && active(mc)) try {
                val cx = g.guiWidth() / 2
                val cy = g.guiHeight() / 2
                if (FishSettings.crosshairMode == "Preset") {
                    val (texId, halfExtent) = CrosshairPresetTextures.getTexture(FishSettings.crosshairPreset, FishSettings.crosshairScale)
                    val size = halfExtent * 2 + 1
                    g.blit(RenderPipelines.GUI_TEXTURED, texId, cx - halfExtent, cy - halfExtent, 0.0f, 0.0f, size, size, size, size, FishSettings.crosshairColor)
                } else if (FishSettings.crosshairMode == "Custom") {
                    drawPixels(g, cx, cy)
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
            } catch (t: Throwable) {
                FishDiag.fail("CustomCrosshair.1", "crosshair render failed (mode ${FishSettings.crosshairMode}, preset ${FishSettings.crosshairPreset})", t)
            }
        }
    }

    // draws the 16x16 hand-drawn grid centred on (cx, cy), each cell scaled by crosshairScale
    private fun drawPixels(g: net.minecraft.client.gui.GuiGraphicsExtractor, cx: Int, cy: Int) {
        val px = CrosshairPixels.get()
        val n = CrosshairPixels.SIZE
        val cell = Math.max(1, Math.round(FishSettings.crosshairScale).toInt())
        val x0 = cx - n * cell / 2
        val y0 = cy - n * cell / 2
        for (i in 0 until n * n) {
            val c = px[i]
            if ((c ushr 24) == 0) continue
            val x = x0 + (i % n) * cell
            val y = y0 + (i / n) * cell
            g.fill(x, y, x + cell, y + cell, c)
        }
    }

    @JvmStatic
    fun active(mc: Minecraft): Boolean {
        if (!FishSettings.crosshairEnabled || mc.hudHidden) return false
        if (!mc.options.cameraType.isFirstPerson) return false
        if (FishSettings.crosshairHideInF3 && mc.debugOverlay != null && mc.debugOverlay.showDebugScreen()) return false
        if (FishSettings.crosshairMode == "Preset") return true
        if (FishSettings.crosshairMode == "Custom") return !CrosshairPixels.isEmpty()
        val sel = FishSettings.crosshairImageSelection
        return sel.isNotEmpty() && sel != CrosshairImageLoader.NO_IMAGE && CrosshairImageLoader.getImageId(sel) != null
    }
}
