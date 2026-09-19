package fishmod.utils.rendering

import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG

/** Records draw commands from FishModScreen.render() and replays them against NanoVG later in the frame, since render() only builds a deferred descriptor, not immediate GL.
 *  Commands are packed into flat primitive buffers (op code + floats + ints [+ string for text ops]) instead of allocating a Runnable closure per draw call, since a busy panel can issue 50-200+ draw calls/frame. */
object NvgRecorder {

    private const val OP_FILL_ROUNDED_RECT = 0
    private const val OP_FILL_RECT_TOP_ROUNDED = 1
    private const val OP_ROUNDED_RECT_RING = 2
    private const val OP_DISC = 3
    private const val OP_DROP_SHADOW = 4
    private const val OP_FILL_RECT_VGRADIENT = 5
    private const val OP_FILL_RECT_HGRADIENT = 6
    private const val OP_CHEVRON = 7
    private const val OP_POP_OUT_ICON = 8
    private const val OP_TEXT = 9
    private const val OP_TEXT_BOLD = 10
    private const val OP_PUSH_SCISSOR = 11
    private const val OP_POP_SCISSOR = 12

    private const val FLOATS_PER_CMD = 6
    private const val INTS_PER_CMD = 2

    private var ops = ByteArray(256)
    private var floats = FloatArray(256 * FLOATS_PER_CMD)
    private var ints = IntArray(256 * INTS_PER_CMD)
    private var strings = arrayOfNulls<String>(256)
    private var count = 0

    private val colorA = NVGColor.create()
    private val colorB = NVGColor.create()
    // Shared native paint struct, reused across dropShadow/gradient replays instead of calloc/free per frame.
    private val paintBuf = NVGPaint.calloc()

    @JvmStatic
    fun clear() {
        // drop string refs so cleared screens don't pin old text in memory between frames
        if (count > 0) java.util.Arrays.fill(strings, 0, count, null)
        count = 0
    }

    @JvmStatic
    fun size(): Int = count

    private fun ensureCapacity() {
        if (count < ops.size) return
        val newCap = ops.size * 2
        ops = ops.copyOf(newCap)
        floats = floats.copyOf(newCap * FLOATS_PER_CMD)
        ints = ints.copyOf(newCap * INTS_PER_CMD)
        strings = strings.copyOf(newCap)
    }

    private fun push(
        op: Int,
        f0: Float = 0f, f1: Float = 0f, f2: Float = 0f, f3: Float = 0f, f4: Float = 0f, f5: Float = 0f,
        i0: Int = 0, i1: Int = 0,
        s: String? = null,
    ) {
        ensureCapacity()
        val idx = count
        ops[idx] = op.toByte()
        val fb = idx * FLOATS_PER_CMD
        floats[fb] = f0; floats[fb + 1] = f1; floats[fb + 2] = f2; floats[fb + 3] = f3; floats[fb + 4] = f4; floats[fb + 5] = f5
        val ib = idx * INTS_PER_CMD
        ints[ib] = i0; ints[ib + 1] = i1
        strings[idx] = s
        count++
    }

    @JvmStatic
    @JvmOverloads
    fun replay(scale: Float = 1f) {
        val ctx = NvgContext.get()
        val scaled = scale != 1f
        if (scaled) {
            NanoVG.nvgSave(ctx)
            NanoVG.nvgScale(ctx, scale, scale)
        }
        for (idx in 0 until count) exec(ctx, idx)
        if (scaled) NanoVG.nvgRestore(ctx)
    }

    private fun argb(argb: Int, out: NVGColor): NVGColor {
        val a = ((argb ushr 24) and 0xFF) / 255f
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return out.r(r).g(g).b(b).a(a)
    }

    private fun exec(ctx: Long, idx: Int) {
        val op = ops[idx].toInt()
        val fb = idx * FLOATS_PER_CMD
        val ib = idx * INTS_PER_CMD
        when (op) {
            OP_FILL_ROUNDED_RECT -> {
                val x = floats[fb]; val y = floats[fb + 1]; val w = floats[fb + 2]; val h = floats[fb + 3]; val r = floats[fb + 4]
                NanoVG.nvgBeginPath(ctx)
                NanoVG.nvgRoundedRect(ctx, x, y, w, h, r)
                NanoVG.nvgFillColor(ctx, argb(ints[ib], colorA))
                NanoVG.nvgFill(ctx)
            }
            OP_FILL_RECT_TOP_ROUNDED -> {
                val x = floats[fb]; val y = floats[fb + 1]; val w = floats[fb + 2]; val h = floats[fb + 3]; val rTop = floats[fb + 4]
                NanoVG.nvgBeginPath(ctx)
                NanoVG.nvgRoundedRectVarying(ctx, x, y, w, h, rTop, rTop, 0f, 0f)
                NanoVG.nvgFillColor(ctx, argb(ints[ib], colorA))
                NanoVG.nvgFill(ctx)
            }
            OP_ROUNDED_RECT_RING -> {
                val x = floats[fb]; val y = floats[fb + 1]; val w = floats[fb + 2]; val h = floats[fb + 3]; val r = floats[fb + 4]; val strokeW = floats[fb + 5]
                val half = strokeW / 2f
                NanoVG.nvgBeginPath(ctx)
                NanoVG.nvgRoundedRect(ctx, x + half, y + half, w - strokeW, h - strokeW, Math.max(0f, r - half))
                NanoVG.nvgFillColor(ctx, argb(ints[ib], colorA))
                NanoVG.nvgFill(ctx)
                NanoVG.nvgStrokeWidth(ctx, strokeW)
                NanoVG.nvgStrokeColor(ctx, argb(ints[ib + 1], colorA))
                NanoVG.nvgStroke(ctx)
            }
            OP_DISC -> {
                val cx = floats[fb]; val cy = floats[fb + 1]; val r = floats[fb + 2]
                NanoVG.nvgBeginPath(ctx)
                NanoVG.nvgCircle(ctx, cx, cy, r)
                NanoVG.nvgFillColor(ctx, argb(ints[ib], colorA))
                NanoVG.nvgFill(ctx)
            }
            OP_DROP_SHADOW -> {
                val x = floats[fb]; val y = floats[fb + 1]; val w = floats[fb + 2]; val h = floats[fb + 3]; val r = floats[fb + 4]; val spread = floats[fb + 5]
                val shadowColor = ints[ib]
                val from = argb(shadowColor, colorA)
                val to = argb(shadowColor and 0x00FFFFFF, colorB)
                NanoVG.nvgBoxGradient(ctx, x, y + spread * 0.5f, w, h, r + spread * 0.5f, spread, from, to, paintBuf)
                NanoVG.nvgBeginPath(ctx)
                NanoVG.nvgRect(ctx, x - spread, y - spread, w + spread * 2, h + spread * 2)
                NanoVG.nvgFillPaint(ctx, paintBuf)
                NanoVG.nvgFill(ctx)
            }
            OP_FILL_RECT_VGRADIENT -> {
                val x = floats[fb]; val y = floats[fb + 1]; val w = floats[fb + 2]; val h = floats[fb + 3]; val r = floats[fb + 4]
                val from = argb(ints[ib], colorA)
                val to = argb(ints[ib + 1], colorB)
                NanoVG.nvgLinearGradient(ctx, x, y, x, y + h, from, to, paintBuf)
                NanoVG.nvgBeginPath(ctx)
                if (r > 0f) NanoVG.nvgRoundedRect(ctx, x, y, w, h, r) else NanoVG.nvgRect(ctx, x, y, w, h)
                NanoVG.nvgFillPaint(ctx, paintBuf)
                NanoVG.nvgFill(ctx)
            }
            OP_FILL_RECT_HGRADIENT -> {
                val x = floats[fb]; val y = floats[fb + 1]; val w = floats[fb + 2]; val h = floats[fb + 3]
                val from = argb(ints[ib], colorA)
                val to = argb(ints[ib + 1], colorB)
                NanoVG.nvgLinearGradient(ctx, x, y, x + w, y, from, to, paintBuf)
                NanoVG.nvgBeginPath(ctx)
                NanoVG.nvgRect(ctx, x, y, w, h)
                NanoVG.nvgFillPaint(ctx, paintBuf)
                NanoVG.nvgFill(ctx)
            }
            OP_CHEVRON -> {
                val gx = floats[fb]; val cy = floats[fb + 1]
                val open = ints[ib] != 0
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
                NanoVG.nvgFillColor(ctx, argb(ints[ib + 1], colorA))
                NanoVG.nvgFill(ctx)
            }
            OP_POP_OUT_ICON -> {
                val x = floats[fb]; val y = floats[fb + 1]; val size = floats[fb + 2]
                NanoVG.nvgStrokeWidth(ctx, 1.4f)
                NanoVG.nvgStrokeColor(ctx, argb(ints[ib], colorA))
                NanoVG.nvgBeginPath(ctx)
                NanoVG.nvgMoveTo(ctx, x, y + size)
                NanoVG.nvgLineTo(ctx, x + size, y)
                NanoVG.nvgStroke(ctx)
                NanoVG.nvgBeginPath(ctx)
                NanoVG.nvgMoveTo(ctx, x + size * 0.5f, y)
                NanoVG.nvgLineTo(ctx, x + size, y)
                NanoVG.nvgLineTo(ctx, x + size, y + size * 0.5f)
                NanoVG.nvgStroke(ctx)
            }
            OP_TEXT -> {
                val x = floats[fb]; val y = floats[fb + 1]; val size = floats[fb + 2]
                NanoVG.nvgFontFace(ctx, NvgContext.FONT_NAME)
                NanoVG.nvgFontSize(ctx, size)
                NanoVG.nvgTextAlign(ctx, NanoVG.NVG_ALIGN_LEFT or NanoVG.NVG_ALIGN_TOP)
                NanoVG.nvgFillColor(ctx, argb(ints[ib], colorA))
                NanoVG.nvgText(ctx, x, y, strings[idx] ?: "")
            }
            OP_TEXT_BOLD -> {
                val x = floats[fb]; val y = floats[fb + 1]; val size = floats[fb + 2]
                NanoVG.nvgFontFace(ctx, NvgContext.FONT_NAME)
                NanoVG.nvgFontSize(ctx, size)
                NanoVG.nvgTextAlign(ctx, NanoVG.NVG_ALIGN_LEFT or NanoVG.NVG_ALIGN_TOP)
                NanoVG.nvgFillColor(ctx, argb(ints[ib], colorA))
                val s = strings[idx] ?: ""
                NanoVG.nvgText(ctx, x, y, s)
                NanoVG.nvgText(ctx, x + 0.4f, y, s)
            }
            OP_PUSH_SCISSOR -> {
                val x = floats[fb]; val y = floats[fb + 1]; val w = floats[fb + 2]; val h = floats[fb + 3]
                NanoVG.nvgSave(ctx)
                NanoVG.nvgIntersectScissor(ctx, x, y, w, h)
            }
            OP_POP_SCISSOR -> {
                NanoVG.nvgRestore(ctx)
            }
        }
    }

    @JvmStatic
    fun fillRoundedRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        push(OP_FILL_ROUNDED_RECT, x, y, w, h, r, i0 = color)
    }

    @JvmStatic
    fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fillRoundedRect(x, y, w, h, 0f, color)
    }

    /** Rect rounded only on its top two corners (radius [rTop]), square on the bottom — for a strip
     *  meant to sit flush against a card's own rounded top without poking past its corners. */
    @JvmStatic
    fun fillRectTopRounded(x: Float, y: Float, w: Float, h: Float, rTop: Float, color: Int) {
        push(OP_FILL_RECT_TOP_ROUNDED, x, y, w, h, rTop, i0 = color)
    }

    /** Small pill (fully rounded ends) — softer than [fillRect] for thin accent ticks/bars. */
    @JvmStatic
    fun fillPillBar(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fillRoundedRect(x, y, w, h, Math.min(w, h) / 2f, color)
    }

    /** Hollow ring stroked in `ringColor` around a rect filled with `fillColor`. */
    @JvmStatic
    fun roundedRectRing(x: Float, y: Float, w: Float, h: Float, r: Float, strokeW: Float, fillColor: Int, ringColor: Int) {
        push(OP_ROUNDED_RECT_RING, x, y, w, h, r, strokeW, i0 = fillColor, i1 = ringColor)
    }

    @JvmStatic
    fun disc(cx: Float, cy: Float, r: Float, color: Int) {
        push(OP_DISC, cx, cy, r, i0 = color)
    }

    /** Soft drop shadow behind a rounded rect — draw before the rect itself so the opaque
     *  rect covers the shadow's center, leaving only the soft edge visible around it. */
    @JvmStatic
    fun dropShadow(x: Float, y: Float, w: Float, h: Float, r: Float, spread: Float, shadowColor: Int) {
        push(OP_DROP_SHADOW, x, y, w, h, r, spread, i0 = shadowColor)
    }

    /** Vertical linear-gradient fill over a rect (optionally rounded), top color to bottom color. */
    @JvmStatic
    @JvmOverloads
    fun fillRectVGradient(x: Float, y: Float, w: Float, h: Float, topColor: Int, botColor: Int, r: Float = 0f) {
        push(OP_FILL_RECT_VGRADIENT, x, y, w, h, r, i0 = topColor, i1 = botColor)
    }

    /** Horizontal linear-gradient fill over a rect, left color to right color. */
    @JvmStatic
    fun fillRectHGradient(x: Float, y: Float, w: Float, h: Float, leftColor: Int, rightColor: Int) {
        push(OP_FILL_RECT_HGRADIENT, x, y, w, h, i0 = leftColor, i1 = rightColor)
    }

    /** Small filled triangle: pointing down when `open`, right when closed. */
    @JvmStatic
    fun chevron(gx: Float, cy: Float, open: Boolean, color: Int) {
        push(OP_CHEVRON, gx, cy, i0 = if (open) 1 else 0, i1 = color)
    }

    /** Small "detach" glyph (↗ with a short shaft) used to pop a stacked column back out to its
     *  own top-level slot; drawn in a [size]x[size] box anchored at (x, y). */
    @JvmStatic
    fun popOutIcon(x: Float, y: Float, size: Float, color: Int) {
        push(OP_POP_OUT_ICON, x, y, size, i0 = color)
    }

    @JvmStatic
    fun text(s: String, x: Float, y: Float, size: Float, color: Int) {
        push(OP_TEXT, x, y, size, i0 = color, s = s)
    }

    /** Faux-bold: only "Inter-Regular" is bundled (no bold weight), so this fakes the heavier
     *  stroke by drawing the glyphs twice with a sub-pixel horizontal offset. */
    @JvmStatic
    fun textBold(s: String, x: Float, y: Float, size: Float, color: Int) {
        push(OP_TEXT_BOLD, x, y, size, i0 = color, s = s)
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

    @JvmStatic
    fun pushScissor(x: Float, y: Float, w: Float, h: Float) {
        push(OP_PUSH_SCISSOR, x, y, w, h)
    }

    @JvmStatic
    fun popScissor() {
        push(OP_POP_SCISSOR)
    }
}
