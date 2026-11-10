package fishmod.features

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/** One static HUD example: drawn at (0,0) at scale 1 by the HUD's own render code; w/h is its real in-game size. */
class HudExample(val w: Int, val h: Int, val draw: (GuiGraphicsExtractor) -> Unit)

/** HUD editor previews: each HUD registers an example built from its real render path fed with example data. */
object HudPreview {
    const val LINE_H = 10
    const val TEXT_H = 9

    /** True only while the editor draws an example, so a render that reads live state can use example data instead. */
    @JvmField
    var active = false

    private val makers = HashMap<String, () -> HudExample?>()

    @JvmStatic
    fun register(name: String, make: () -> HudExample?) {
        makers[name] = make
    }

    @JvmStatic
    fun has(name: String) = makers.containsKey(name)

    @JvmStatic
    fun example(name: String): HudExample? {
        val m = makers[name] ?: return null
        return try { m() } catch (t: Throwable) { FishDiag.fail("HudPreview.1", "HUD example '$name' build failed", t); null }
    }

    @JvmStatic
    fun draw(ex: HudExample, ctx: GuiGraphicsExtractor, name: String) {
        active = true
        try { ex.draw(ctx) } catch (t: Throwable) { FishDiag.fail("HudPreview.2", "HUD example '$name' draw failed", t) } finally { active = false }
    }

    private fun font() = Minecraft.getInstance().font

    @JvmStatic
    fun textWidth(lines: List<String>, right: List<String>? = null, gap: Int = 6): Int {
        val f = font()
        if (right != null) return lines.indices.maxOfOrNull { f.width(lines[it]) + gap + f.width(right.getOrElse(it) { "" }) } ?: 0
        return lines.maxOfOrNull { f.width(it) } ?: 0
    }

    @JvmStatic
    fun textHeight(lines: Int, lineH: Int = LINE_H) = if (lines <= 0) 0 else (lines - 1) * lineH + TEXT_H

    /** The shared text-block draw (white shadowed text at origin) used by the in-game HUDs and their examples. */
    @JvmStatic
    @JvmOverloads
    fun drawText(ctx: GuiGraphicsExtractor, lines: List<String>, lineH: Int = LINE_H, w: Int = 0, center: Boolean = false, color: Int = -1) {
        val f = font()
        lines.forEachIndexed { i, s -> ctx.text(f, s, if (center) (w - f.width(s)) / 2 else 0, i * lineH, color, true) }
    }

    /** A plain text example: size from the text itself (minW applies when centered, like the component box). */
    @JvmStatic
    @JvmOverloads
    fun text(lines: List<String>, lineH: Int = LINE_H, center: Boolean = false, minW: Int = 0, color: Int = -1): HudExample {
        val w = Math.max(minW, textWidth(lines))
        return HudExample(w, textHeight(lines.size, lineH)) { ctx -> drawText(ctx, lines, lineH, w, center, color) }
    }

    /** Example of a HUDComponent HUD: its real RenderSupplier, drawn where the component sits so scaledX/scaledY land on the origin. */
    @JvmStatic
    @JvmOverloads
    fun component(c: HUDComponent, render: HUDComponent.RenderSupplier, w: Int = c.width, h: Int = c.height): HudExample =
        HudExample(w, h) { ctx ->
            val pose = ctx.pose()
            pose.pushMatrix()
            try {
                pose.translate(-c.scaledX.toFloat(), -c.scaledY.toFloat())
                render.render(c, ctx)
            } finally {
                pose.popMatrix()
            }
        }
}
