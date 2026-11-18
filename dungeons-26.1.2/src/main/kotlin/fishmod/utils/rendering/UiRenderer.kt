package fishmod.utils.rendering

import fishmod.compat.UiGpu
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer

object UiRenderer {
    private const val MODE_SHAPE = 0f
    private const val MODE_TEXT = 1f
    private const val MODE_SOLID = 2f

    const val FLOATS = 24
    const val STRIDE = FLOATS * 4

    private val noUi = System.getProperty("fishmod.noui") != null
    private var failed = false
    private var failedAt = 0L
    private var failLogged = false
    private var consecutiveFailures = 0

    private var buf: FloatBuffer = MemoryUtil.memAllocFloat(FLOATS * 6 * 512)
    private var verts = 0

    private var viewW = 1f
    private var viewH = 1f
    private var pixelRatio = 1f
    private var fbH = 1
    private var fbW = 1
    private val scissors = ArrayDeque<FloatArray>()

    // Draw segments split at scissor changes: first vertex, count, scissor x/y/w/h (w < 0 = none)
    private var segs = IntArray(6 * 64)
    private var nSeg = 0
    private var segStart = 0
    private val curScissor = intArrayOf(0, 0, -1, 0)

    // Colour view of the last full-size pass (the GUI's own target); set by the render pass mixin
    @JvmField @Volatile var lastPassView: Any? = null

    @JvmStatic
    fun paint(screenW: Int, screenH: Int, scale: Float) {
        if (noUi) return
        // After repeated failures, back off for 5s then retry instead of killing every menu for the session
        if (failed) {
            if (System.currentTimeMillis() - failedAt < 5000L) return
            failed = false; consecutiveFailures = 0
        }
        try {
            val win = Minecraft.getInstance().window
            pixelRatio = win.guiScale.toFloat()
            if (!(pixelRatio > 0f)) FishDiag.fail("UiRenderer.5", "window guiScale invalid: ${win.guiScale}")
            if (win.width <= 0 || win.height <= 0) FishDiag.fail("UiRenderer.6", "painting UI into empty window ${win.width}x${win.height}")
            viewW = win.width / pixelRatio; viewH = win.height / pixelRatio
            fbH = win.height; fbW = win.width
            scissors.clear()
            // A paint that threw mid-replay leaves stale floats behind; drop them or they draw in place of this frame's.
            buf.clear()
            verts = 0; nSeg = 0; segStart = 0; curScissor[2] = -1
            if (!(scale > 0f)) FishDiag.fail("UiRenderer.7", "UI paint with invalid scale $scale")
            UiRecorder.replay(scale)
            cut()
            if (scissors.isNotEmpty()) FishDiag.fail("UiRenderer.8", "unbalanced pushScissor: ${scissors.size} left open after paint")
            if (verts > 0) {
                buf.flip()
                UiGpu.paint(lastPassView, MemoryUtil.memByteBuffer(buf), verts, viewW, viewH, UiFont.takeDirtyAtlas(), segs, nSeg)
            }
            consecutiveFailures = 0
        } catch (t: Throwable) {
            UiFont.markAtlasLost()
            FishDiag.fail("UiRenderer.9", "UI paint failed (attempt ${consecutiveFailures + 1})", t)
            if (++consecutiveFailures >= 3) {
                failed = true; failedAt = System.currentTimeMillis()
                FishDiag.fail("UiRenderer.10", "UI renderer paused 5s after 3 consecutive paint failures")
            }
            if (!failLogged) {
                failLogged = true
                fishmod.utils.debug.FishDebug.LOGGER.error("[UiRenderer] paint failed - FishMod screens will render without their UI layer", t)
            }
        } finally {
            buf.clear()
            verts = 0
        }
    }

    // Close the current segment so later vertices can use a different scissor
    private fun cut() {
        if (verts == segStart) return
        if (segs.size < (nSeg + 1) * 6) segs = segs.copyOf(segs.size * 2)
        val o = nSeg * 6
        segs[o] = segStart; segs[o + 1] = verts - segStart
        segs[o + 2] = curScissor[0]; segs[o + 3] = curScissor[1]; segs[o + 4] = curScissor[2]; segs[o + 5] = curScissor[3]
        nSeg++
        segStart = verts
    }

    private fun ensure(n: Int) {
        if (buf.remaining() >= n * FLOATS) return
        if (buf.capacity() > FLOATS * 6 * 262144) FishDiag.fail("UiRenderer.14", "UI vertex buffer growing past ${buf.capacity()} floats, runaway draw?")
        val nb = MemoryUtil.memAllocFloat(Math.max(buf.capacity() * 2, buf.position() + n * FLOATS))
        buf.flip(); nb.put(buf); MemoryUtil.memFree(buf); buf = nb
    }

    private fun vert(x: Float, y: Float, lx: Float, ly: Float, hx: Float, hy: Float, r: FloatArray, a: Int, b: Int, mode: Float, stroke: Float, feather: Float, grad: Float, u: Float, v: Float) {
        buf.put(x).put(y).put(lx).put(ly).put(hx).put(hy).put(u).put(v).put(r[0]).put(r[1]).put(r[2]).put(r[3])
        color(a); color(b)
        buf.put(mode).put(stroke).put(feather).put(grad)
        verts++
    }

    private fun color(c: Int) {
        buf.put(((c ushr 16) and 0xFF) / 255f).put(((c ushr 8) and 0xFF) / 255f).put((c and 0xFF) / 255f).put(((c ushr 24) and 0xFF) / 255f)
    }

    private val ZERO = FloatArray(4)
    private val radTmp = FloatArray(4)

    private fun quad(qx0: Float, qy0: Float, qx1: Float, qy1: Float, cx: Float, cy: Float, hx: Float, hy: Float, r: FloatArray,
                     a: Int, b: Int, mode: Float, stroke: Float, feather: Float, grad: Float,
                     u0: Float = 0f, v0: Float = 0f, u1: Float = 0f, v1: Float = 0f) {
        ensure(6)
        vert(qx0, qy0, qx0 - cx, qy0 - cy, hx, hy, r, a, b, mode, stroke, feather, grad, u0, v0)
        vert(qx1, qy0, qx1 - cx, qy0 - cy, hx, hy, r, a, b, mode, stroke, feather, grad, u1, v0)
        vert(qx1, qy1, qx1 - cx, qy1 - cy, hx, hy, r, a, b, mode, stroke, feather, grad, u1, v1)
        vert(qx0, qy0, qx0 - cx, qy0 - cy, hx, hy, r, a, b, mode, stroke, feather, grad, u0, v0)
        vert(qx1, qy1, qx1 - cx, qy1 - cy, hx, hy, r, a, b, mode, stroke, feather, grad, u1, v1)
        vert(qx0, qy1, qx0 - cx, qy1 - cy, hx, hy, r, a, b, mode, stroke, feather, grad, u0, v1)
    }

    fun shape(x: Float, y: Float, w: Float, h: Float, tl: Float, tr: Float, br: Float, bl: Float, colA: Int, colB: Int = colA, stroke: Float = 0f, grad: Float = 0f) {
        if (w <= 0f || h <= 0f) return
        if (x.isNaN() || y.isNaN() || w.isNaN() || h.isNaN()) FishDiag.fail("UiRenderer.15", "shape() given NaN x=$x y=$y w=$w h=$h")
        radTmp[0] = tl; radTmp[1] = tr; radTmp[2] = br; radTmp[3] = bl
        val pad = 1.5f / pixelRatio + 0.5f
        quad(x - pad, y - pad, x + w + pad, y + h + pad, x + w / 2, y + h / 2, w / 2, h / 2, radTmp, colA, colB, MODE_SHAPE, stroke, 0f, grad)
    }

    fun shadow(x: Float, y: Float, w: Float, h: Float, r: Float, feather: Float, color: Int) {
        if (feather < 0f || x.isNaN() || y.isNaN() || w.isNaN() || h.isNaN()) FishDiag.fail("UiRenderer.16", "shadow() bad input x=$x y=$y w=$w h=$h feather=$feather")
        val rr = r + feather * 0.5f
        radTmp[0] = rr; radTmp[1] = rr; radTmp[2] = rr; radTmp[3] = rr
        quad(x - feather, y - feather, x + w + feather, y + h + feather,
            x + w / 2, y + feather * 0.5f + h / 2, w / 2, h / 2, radTmp, color, color, MODE_SHAPE, 0f, feather, 0f)
    }

    fun triangle(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        if (x0.isNaN() || y0.isNaN() || x1.isNaN() || y1.isNaN() || x2.isNaN() || y2.isNaN()) FishDiag.fail("UiRenderer.17", "triangle() given NaN vertex")
        ensure(3)
        vert(x0, y0, 0f, 0f, 0f, 0f, ZERO, color, color, MODE_SOLID, 0f, 0f, 0f, 0f, 0f)
        vert(x1, y1, 0f, 0f, 0f, 0f, ZERO, color, color, MODE_SOLID, 0f, 0f, 0f, 0f, 0f)
        vert(x2, y2, 0f, 0f, 0f, 0f, ZERO, color, color, MODE_SOLID, 0f, 0f, 0f, 0f, 0f)
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        val dx = x1 - x0; val dy = y1 - y0
        val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        if (len.isNaN() || width.isNaN()) FishDiag.fail("UiRenderer.18", "line() given NaN ($x0,$y0)->($x1,$y1) w=$width")
        if (len <= 0f) return
        val ux = dx / len; val uy = dy / len
        val hw = width / 2f
        val pad = hw + 1f
        val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2
        val hl = len / 2 + hw
        radTmp[0] = hw; radTmp[1] = hw; radTmp[2] = hw; radTmp[3] = hw
        ensure(6)
        fun v(s: Float, t: Float) {
            val px = cx + ux * s - uy * t
            val py = cy + uy * s + ux * t
            vert(px, py, s, t, hl, hw, radTmp, color, color, MODE_SHAPE, 0f, 0f, 0f, 0f, 0f)
        }
        val se = hl + 1f
        v(-se, -pad); v(se, -pad); v(se, pad); v(-se, -pad); v(se, pad); v(-se, pad)
    }

    fun text(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
        if (s.isEmpty() || size <= 0f) return
        if (size.isNaN() || x.isNaN() || y.isNaN()) FishDiag.fail("UiRenderer.19", "text() given NaN x=$x y=$y size=$size for '${s.take(24)}'")
        val pr = pixelRatio
        val dev = size * pr
        val sc = UiFont.scaleFor(dev)
        val baseline = Math.round((y + UiFont.ascender(size)) * pr).toFloat()
        var pen = x * pr
        var prev = -1
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            if (prev >= 0) pen += UiFont.kern(prev, cp) * sc
            prev = cp
            val g = UiFont.glyph(cp, dev) ?: continue
            if (g.w > 0f) {
                val gx = (Math.round(pen) + g.xoff) / pr
                val gy = (baseline + g.yoff) / pr
                quad(gx, gy, gx + g.w / pr, gy + g.h / pr, 0f, 0f, 0f, 0f, ZERO, color, color, MODE_TEXT, 0f, 0f, 0f, g.u0, g.v0, g.u1, g.v1)
                if (bold) {
                    val bx = gx + 1f / pr
                    quad(bx, gy, bx + g.w / pr, gy + g.h / pr, 0f, 0f, 0f, 0f, ZERO, color, color, MODE_TEXT, 0f, 0f, 0f, g.u0, g.v0, g.u1, g.v1)
                }
            }
            pen += UiFont.advanceUnits(cp) * sc
            if (bold) pen += 1f
        }
    }

    fun pushScissor(x: Float, y: Float, w: Float, h: Float) {
        if (x.isNaN() || y.isNaN() || w.isNaN() || h.isNaN()) FishDiag.fail("UiRenderer.21", "pushScissor given NaN x=$x y=$y w=$w h=$h")
        if (scissors.size > 64) FishDiag.fail("UiRenderer.22", "scissor stack depth ${scissors.size}, missing popScissor?")
        var x0 = x; var y0 = y; var x1 = x + w; var y1 = y + h
        scissors.lastOrNull()?.let { p ->
            x0 = Math.max(x0, p[0]); y0 = Math.max(y0, p[1]); x1 = Math.min(x1, p[2]); y1 = Math.min(y1, p[3])
        }
        cut()
        scissors.addLast(floatArrayOf(x0, y0, Math.max(x0, x1), Math.max(y0, y1)))
        applyScissor()
    }

    fun popScissor() {
        if (scissors.isEmpty()) {
            FishDiag.fail("UiRenderer.20", "popScissor with empty scissor stack")
            return
        }
        cut()
        scissors.removeLast()
        applyScissor()
    }

    private fun applyScissor() {
        val s = scissors.lastOrNull()
        if (s == null) { curScissor[2] = -1; return }
        // GPU API rejects scissors outside the target (GL clipped silently), so clamp to the framebuffer
        val x0 = Math.floor((s[0] * pixelRatio).toDouble()).toInt().coerceIn(0, fbW)
        val x1 = Math.ceil((s[2] * pixelRatio).toDouble()).toInt().coerceIn(0, fbW)
        val y0 = Math.floor((s[1] * pixelRatio).toDouble()).toInt().coerceIn(0, fbH)
        val y1 = Math.ceil((s[3] * pixelRatio).toDouble()).toInt().coerceIn(0, fbH)
        curScissor[0] = x0; curScissor[1] = fbH - y1
        curScissor[2] = Math.max(0, x1 - x0); curScissor[3] = Math.max(0, y1 - y0)
    }
}
