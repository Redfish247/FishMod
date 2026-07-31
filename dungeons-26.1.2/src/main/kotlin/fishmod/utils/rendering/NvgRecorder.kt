package fishmod.utils.rendering

import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG

/** Records draw commands from FishModScreen.render() and replays them against NanoVG later in the frame, since render() only builds a deferred descriptor, not immediate GL. */
object NvgRecorder {

    private val commands = ArrayList<Runnable>()
    private val colorA = NVGColor.create()
    private val colorB = NVGColor.create()

    @JvmStatic
    fun clear() {
        commands.clear()
    }

    @JvmStatic
    fun replay() {
        for (r in commands) r.run()
    }

    private fun record(r: Runnable) {
        commands.add(r)
    }

    private fun argb(argb: Int, out: NVGColor): NVGColor {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return out.r(r).g(g).b(b).a(a)
    }

    // ----- shapes -----

    @JvmStatic
    fun fillRoundedRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        record(Runnable {
            val ctx = NvgContext.get()
            NanoVG.nvgBeginPath(ctx)
            NanoVG.nvgRoundedRect(ctx, x, y, w, h, r)
            NanoVG.nvgFillColor(ctx, argb(color, colorA))
            NanoVG.nvgFill(ctx)
        })
    }

    @JvmStatic
    fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fillRoundedRect(x, y, w, h, 0f, color)
    }

    /** Hollow ring stroked in `ringColor` around a rect filled with `fillColor`. */
    @JvmStatic
    fun roundedRectRing(x: Float, y: Float, w: Float, h: Float, r: Float, strokeW: Float, fillColor: Int, ringColor: Int) {
        record(Runnable {
            val ctx = NvgContext.get()
            val half = strokeW / 2f
            NanoVG.nvgBeginPath(ctx)
            NanoVG.nvgRoundedRect(ctx, x + half, y + half, w - strokeW, h - strokeW, Math.max(0f, r - half))
            NanoVG.nvgFillColor(ctx, argb(fillColor, colorA))
            NanoVG.nvgFill(ctx)
            NanoVG.nvgStrokeWidth(ctx, strokeW)
            NanoVG.nvgStrokeColor(ctx, argb(ringColor, colorA))
            NanoVG.nvgStroke(ctx)
        })
    }

    @JvmStatic
    fun disc(cx: Float, cy: Float, r: Float, color: Int) {
        record(Runnable {
            val ctx = NvgContext.get()
            NanoVG.nvgBeginPath(ctx)
            NanoVG.nvgCircle(ctx, cx, cy, r)
            NanoVG.nvgFillColor(ctx, argb(color, colorA))
            NanoVG.nvgFill(ctx)
        })
    }

    /** Soft drop shadow behind a rounded rect — draw before the rect itself so the opaque
     *  rect covers the shadow's center, leaving only the soft edge visible around it. */
    @JvmStatic
    fun dropShadow(x: Float, y: Float, w: Float, h: Float, r: Float, spread: Float, shadowColor: Int) {
        record(Runnable {
            val ctx = NvgContext.get()
            val paint = NVGPaint.calloc()
            try {
                val from = argb(shadowColor, colorA)
                val to = argb(shadowColor and 0x00FFFFFF, colorB)
                NanoVG.nvgBoxGradient(ctx, x, y + spread * 0.5f, w, h, r + spread * 0.5f, spread, from, to, paint)
                NanoVG.nvgBeginPath(ctx)
                NanoVG.nvgRect(ctx, x - spread, y - spread, w + spread * 2, h + spread * 2)
                NanoVG.nvgFillPaint(ctx, paint)
                NanoVG.nvgFill(ctx)
            } finally {
                paint.free()
            }
        })
    }

    /** Vertical linear-gradient fill over a rect, top color to bottom color. */
    @JvmStatic
    fun fillRectVGradient(x: Float, y: Float, w: Float, h: Float, topColor: Int, botColor: Int) {
        record(Runnable {
            val ctx = NvgContext.get()
            val paint = NVGPaint.calloc()
            try {
                val from = argb(topColor, colorA)
                val to = argb(botColor, colorB)
                NanoVG.nvgLinearGradient(ctx, x, y, x, y + h, from, to, paint)
                NanoVG.nvgBeginPath(ctx)
                NanoVG.nvgRect(ctx, x, y, w, h)
                NanoVG.nvgFillPaint(ctx, paint)
                NanoVG.nvgFill(ctx)
            } finally {
                paint.free()
            }
        })
    }

    /** Small filled triangle: pointing down when `open`, right when closed. */
    @JvmStatic
    fun chevron(gx: Float, cy: Float, open: Boolean, color: Int) {
        record(Runnable {
            val ctx = NvgContext.get()
            NanoVG.nvgBeginPath(ctx)
            if (open) {
                NanoVG.nvgMoveTo(ctx, gx, cy - 2.5f)
                NanoVG.nvgLineTo(ctx, gx + 7, cy - 2.5f)
                NanoVG.nvgLineTo(ctx, gx + 3.5f, cy + 3)
            } else {
                NanoVG.nvgMoveTo(ctx, gx, cy - 3.5f)
                NanoVG.nvgLineTo(ctx, gx, cy + 3.5f)
                NanoVG.nvgLineTo(ctx, gx + 5, cy)
            }
            NanoVG.nvgClosePath(ctx)
            NanoVG.nvgFillColor(ctx, argb(color, colorA))
            NanoVG.nvgFill(ctx)
        })
    }

    // ----- text -----

    @JvmStatic
    fun text(s: String, x: Float, y: Float, size: Float, color: Int) {
        record(Runnable {
            val ctx = NvgContext.get()
            NanoVG.nvgFontFace(ctx, NvgContext.FONT_NAME)
            NanoVG.nvgFontSize(ctx, size)
            NanoVG.nvgTextAlign(ctx, NanoVG.NVG_ALIGN_LEFT or NanoVG.NVG_ALIGN_TOP)
            NanoVG.nvgFillColor(ctx, argb(color, colorA))
            NanoVG.nvgText(ctx, x, y, s)
        })
    }

    /** Text width at a given size, for layout/centering/truncation — must be measured with
     *  NanoVG's own font metrics since it draws with a different font than Minecraft's Font. */
    @JvmStatic
    fun textWidth(s: String, size: Float): Float {
        val ctx = NvgContext.get()
        NanoVG.nvgFontFace(ctx, NvgContext.FONT_NAME)
        NanoVG.nvgFontSize(ctx, size)
        val bounds = FloatArray(4)
        return NanoVG.nvgTextBounds(ctx, 0f, 0f, s, bounds)
    }

    // ----- scissor (nested via NanoVG's own save/restore state stack) -----

    @JvmStatic
    fun pushScissor(x: Float, y: Float, w: Float, h: Float) {
        record(Runnable {
            val ctx = NvgContext.get()
            NanoVG.nvgSave(ctx)
            NanoVG.nvgIntersectScissor(ctx, x, y, w, h)
        })
    }

    @JvmStatic
    fun popScissor() {
        record(Runnable { NanoVG.nvgRestore(NvgContext.get()) })
    }
}
