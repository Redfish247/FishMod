package fishmod.features

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object WorldPreview {
    private val scenes = HashMap<String, (GuiGraphicsExtractor, Int, Int) -> Unit>()

    @JvmStatic
    fun register(feature: String, draw: (GuiGraphicsExtractor, Int, Int) -> Unit) { scenes[feature] = draw }

    @JvmStatic
    fun has(feature: String) = scenes.containsKey(feature)

    @JvmStatic
    fun draw(ctx: GuiGraphicsExtractor, feature: String, w: Int, h: Int): Boolean {
        val s = scenes[feature] ?: return false
        s(ctx, w / 2, h / 2)
        return true
    }

    fun box(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, argb: Int) {
        val c = argb or 0xFF000000.toInt()
        ctx.fill(x, y, x + w, y + h, (argb and 0xFFFFFF) or 0x44000000)
        ctx.fill(x, y, x + w, y + 1, c); ctx.fill(x, y + h - 1, x + w, y + h, c)
        ctx.fill(x, y, x + 1, y + h, c); ctx.fill(x + w - 1, y, x + w, y + h, c)
    }

    fun line(ctx: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, argb: Int) {
        val c = argb or 0xFF000000.toInt()
        val n = maxOf(Math.abs(x1 - x0), Math.abs(y1 - y0), 1)
        for (i in 0..n) {
            val x = x0 + (x1 - x0) * i / n; val y = y0 + (y1 - y0) * i / n
            ctx.fill(x, y, x + 2, y + 2, c)
        }
    }

    fun text(ctx: GuiGraphicsExtractor, s: String, cx: Int, y: Int, scale: Float = 1f) {
        val f = Minecraft.getInstance().font
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(cx.toFloat(), y.toFloat())
        pose.scale(scale, scale)
        ctx.text(f, s, -f.width(s) / 2, 0, -1, true)
        pose.popMatrix()
    }

    fun title(ctx: GuiGraphicsExtractor, cx: Int, cy: Int, main: String, sub: String? = null) {
        text(ctx, main, cx, cy - 60, 4f)
        if (sub != null) text(ctx, sub, cx, cy - 24, 2f)
    }

    fun caption(ctx: GuiGraphicsExtractor, cx: Int, cy: Int, s: String) = text(ctx, "§7$s", cx, cy + 70)
}
