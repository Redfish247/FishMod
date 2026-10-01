package fishmod.features.diana

import fishmod.features.FishHudEditor
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier

// Diana's own movable titles, so the warp title and rare mob alerts never replace each other (or vanilla titles)
object DianaTitles {

    private class Slot(
        val name: String, val id: String, val w: Int, val h: Int, val mainScale: Float, val centerOffsetY: Int,
        val gx: () -> Int, val sx: (Int) -> Unit, val gy: () -> Int, val sy: (Int) -> Unit,
        val gs: () -> Double, val ss: (Double) -> Unit,
    ) {
        var main = ""; var sub = ""
        var start = 0L; var fadeIn = 0; var stay = 0; var fadeOut = 0

        fun screenW() = Minecraft.getInstance().window.guiScaledWidth
        fun screenH() = Minecraft.getInstance().window.guiScaledHeight
        // -1 means centred on screen until the player moves it
        fun x(): Int = gx().takeIf { it >= 0 } ?: ((screenW() - w * gs()) / 2).toInt()
        fun y(): Int = gy().takeIf { it >= 0 } ?: (screenH() / 2 + centerOffsetY)

        fun alpha(now: Long): Float {
            val t = (now - start) / 50f
            return when {
                start == 0L -> 0f
                t < fadeIn -> t / fadeIn
                t < fadeIn + stay -> 1f
                t < fadeIn + stay + fadeOut -> 1f - (t - fadeIn - stay) / fadeOut
                else -> 0f
            }
        }
    }

    private val rare = Slot(
        "Diana Rare Mob Alert", "diana_rare_title", 240, 34, 3f, -60,
        { DianaSettings.dianaRareTitleX }, { DianaSettings.dianaRareTitleX = it },
        { DianaSettings.dianaRareTitleY }, { DianaSettings.dianaRareTitleY = it },
        { DianaSettings.dianaRareTitleScale }, { DianaSettings.dianaRareTitleScale = it },
    )

    private val warp = Slot(
        "Diana Warp Title", "diana_warp_title", 160, 18, 2f, 20,
        { DianaSettings.dianaWarpTitleX }, { DianaSettings.dianaWarpTitleX = it },
        { DianaSettings.dianaWarpTitleY }, { DianaSettings.dianaWarpTitleY = it },
        { DianaSettings.dianaWarpTitleScale }, { DianaSettings.dianaWarpTitleScale = it },
    )

    fun init() {
        for (s in listOf(rare, warp)) {
            FishHudEditor.register(s.name, { s.x() }, { v -> s.sx(v) }, { s.y() }, { v -> s.sy(v) }, s.w, s.h,
                { s.gs() }, { v -> s.ss(v) }, { true })
            HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", s.id)) { ctx, _ ->
                if (!FishHudEditor.isOpen()) {
                    val a = s.alpha(System.currentTimeMillis())
                    if (a > 0.02f) draw(ctx, s, s.main, s.sub, a)
                }
            }
        }
    }

    fun rareAlert(main: String, sub: String) = show(rare, main, sub,
        DianaSettings.dianaTitleFadeIn.coerceAtLeast(0), DianaSettings.dianaTitleStay.coerceAtLeast(1), DianaSettings.dianaTitleFadeOut.coerceAtLeast(0))

    fun warpTitle(text: String) = show(warp, text, "", 2, 30, 6)

    private fun show(s: Slot, main: String, sub: String, fadeIn: Int, stay: Int, fadeOut: Int) {
        s.main = main; s.sub = sub
        s.fadeIn = fadeIn; s.stay = stay; s.fadeOut = fadeOut
        s.start = System.currentTimeMillis()
    }

    private fun draw(ctx: GuiGraphicsExtractor, s: Slot, main: String, sub: String, alpha: Float) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui || main.isEmpty()) return
        val font = mc.font
        val scale = s.gs().toFloat()
        val small = s === warp && DianaSettings.dianaWarpTitleSubtitle
        val mainScale = s.mainScale * (if (small) 0.6f else 1f)
        val color = ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(s.x().toFloat(), s.y().toFloat())
        pose.scale(scale, scale)
        val cx = s.w / 2f
        pose.pushMatrix()
        pose.translate(cx, 0f)
        pose.scale(mainScale, mainScale)
        ctx.text(font, main, -font.width(main) / 2, 0, color, true)
        pose.popMatrix()
        if (sub.isNotEmpty()) {
            pose.pushMatrix()
            pose.translate(cx, font.lineHeight * mainScale + 2f)
            pose.scale(1.5f, 1.5f)
            ctx.text(font, sub, -font.width(sub) / 2, 0, color, true)
            pose.popMatrix()
        }
        pose.popMatrix()
    }
}
