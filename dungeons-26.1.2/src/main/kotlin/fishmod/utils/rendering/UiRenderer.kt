package fishmod.utils.rendering

import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.nio.FloatBuffer

// FishMod's own GPU UI renderer (replaces NanoVG). Every shape is a quad; the fragment shader
// evaluates a rounded-box SDF for anti-aliased fills, rings, gradients and soft shadows, and
// samples a glyph coverage atlas for text. Coordinates are GUI-scaled pixels, like NanoVG's frame.
object UiRenderer {
    private const val MODE_SHAPE = 0f
    private const val MODE_TEXT = 1f
    private const val MODE_SOLID = 2f

    // pos2 local2 half2 rad4 colA4 colB4 params4(mode, stroke, feather, grad) uv2
    private const val FLOATS = 24
    private const val STRIDE = FLOATS * 4

    private var program = 0
    private var vao = 0
    private var vbo = 0
    private var uView = -1
    private var uAtlas = -1
    private var failed = false
    private var failLogged = false

    private var buf: FloatBuffer = MemoryUtil.memAllocFloat(FLOATS * 6 * 512)
    private var verts = 0

    private var viewW = 1f
    private var viewH = 1f
    private var pixelRatio = 1f
    private var fbH = 1
    private val scissors = ArrayDeque<FloatArray>()
    private val guard = GlStateGuard()

    private const val VSH = """
#version 330 core
layout(location=0) in vec2 aPos;
layout(location=1) in vec2 aLocal;
layout(location=2) in vec2 aHalf;
layout(location=3) in vec4 aRad;
layout(location=4) in vec4 aColA;
layout(location=5) in vec4 aColB;
layout(location=6) in vec4 aParams;
layout(location=7) in vec2 aUv;
uniform vec2 uView;
out vec2 vLocal; flat out vec2 vHalf; flat out vec4 vRad; flat out vec4 vColA; flat out vec4 vColB; flat out vec4 vParams; out vec2 vUv;
void main(){
    gl_Position = vec4(aPos.x / uView.x * 2.0 - 1.0, 1.0 - aPos.y / uView.y * 2.0, 0.0, 1.0);
    vLocal = aLocal; vHalf = aHalf; vRad = aRad; vColA = aColA; vColB = aColB; vParams = aParams; vUv = aUv;
}
"""

    private const val FSH = """
#version 330 core
in vec2 vLocal; flat in vec2 vHalf; flat in vec4 vRad; flat in vec4 vColA; flat in vec4 vColB; flat in vec4 vParams; in vec2 vUv;
uniform sampler2D uAtlas;
out vec4 fragColor;
float sdBox(vec2 p, vec2 b, vec4 r){
    // r = (tl, tr, br, bl), y grows downward
    float rr = p.x > 0.0 ? (p.y > 0.0 ? r.z : r.y) : (p.y > 0.0 ? r.w : r.x);
    rr = min(rr, min(b.x, b.y));
    vec2 q = abs(p) - b + rr;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - rr;
}
vec4 pm(vec4 c){ return vec4(c.rgb * c.a, c.a); }
void main(){
    float mode = vParams.x;
    if (mode > 1.5) { fragColor = pm(vColA); return; }
    if (mode > 0.5) {
        fragColor = pm(vColA) * texture(uAtlas, vUv).r;
        return;
    }
    float d = sdBox(vLocal, vHalf, vRad);
    float feather = vParams.z;
    if (feather > 0.0) {
        float t = clamp((d + feather * 0.5) / feather, 0.0, 1.0);
        fragColor = pm(vColA) * (1.0 - t);
        return;
    }
    float aa = max(fwidth(d), 1e-4);
    float cov = clamp(0.5 - d / aa, 0.0, 1.0);
    vec4 fill = vColA;
    float g = vParams.w;
    if (g > 0.5) {
        float t = g > 1.5 ? (vLocal.x / (2.0 * vHalf.x) + 0.5) : (vLocal.y / (2.0 * vHalf.y) + 0.5);
        fill = mix(vColA, vColB, clamp(t, 0.0, 1.0));
    }
    float stroke = vParams.y;
    if (stroke > 0.0) {
        float inner = clamp(0.5 - (d + stroke) / aa, 0.0, 1.0);
        fragColor = pm(fill) * inner + pm(vColB) * max(cov - inner, 0.0);
        return;
    }
    fragColor = pm(fill) * cov;
}
"""

    private fun compile(type: Int, src: String): Int {
        val s = GL20.glCreateShader(type)
        GL20.glShaderSource(s, src)
        GL20.glCompileShader(s)
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            val log = GL20.glGetShaderInfoLog(s)
            GL20.glDeleteShader(s)
            throw IllegalStateException("UI shader compile failed: $log")
        }
        return s
    }

    private fun init() {
        if (program != 0) return
        val vs = compile(GL20.GL_VERTEX_SHADER, VSH)
        val fs = compile(GL20.GL_FRAGMENT_SHADER, FSH)
        val p = GL20.glCreateProgram()
        GL20.glAttachShader(p, vs); GL20.glAttachShader(p, fs)
        GL20.glLinkProgram(p)
        GL20.glDeleteShader(vs); GL20.glDeleteShader(fs)
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) throw IllegalStateException("UI shader link failed: " + GL20.glGetProgramInfoLog(p))
        program = p
        uView = GL20.glGetUniformLocation(p, "uView")
        uAtlas = GL20.glGetUniformLocation(p, "uAtlas")
        vao = GL30.glGenVertexArrays()
        vbo = GL15.glGenBuffers()
        GL30.glBindVertexArray(vao)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
        val sizes = intArrayOf(2, 2, 2, 4, 4, 4, 4, 2)
        var off = 0L
        for ((i, n) in sizes.withIndex()) {
            GL20.glEnableVertexAttribArray(i)
            GL20.glVertexAttribPointer(i, n, GL11.GL_FLOAT, false, STRIDE, off)
            off += n * 4L
        }
    }

    // Replays the recorded UI for the current screen. Called from the GameRenderer hook.
    @JvmStatic
    fun paint(screenW: Int, screenH: Int, scale: Float) {
        if (failed) return
        guard.capture()
        try {
            init()
            val win = Minecraft.getInstance().window
            viewW = screenW.toFloat(); viewH = screenH.toFloat()
            pixelRatio = win.guiScale.toFloat()
            fbH = win.height
            GL11.glViewport(0, 0, win.width, win.height)
            GL20.glUseProgram(program)
            GL20.glUniform2f(uView, viewW, viewH)
            GL20.glUniform1i(uAtlas, 0)
            GL30.glBindVertexArray(vao)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            GL11.glEnable(GL11.GL_BLEND)
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL14.glBlendEquation(GL14.GL_FUNC_ADD)
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glDisable(GL11.GL_CULL_FACE)
            GL11.glDisable(GL11.GL_STENCIL_TEST)
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            GL11.glDepthMask(false)
            scissors.clear()
            verts = 0
            UiRecorder.replay(scale)
            flush()
        } catch (t: Throwable) {
            failed = true
            if (!failLogged) {
                failLogged = true
                fishmod.utils.debug.Debug.LOGGER.error("[UiRenderer] paint failed - FishMod screens will render without their UI layer", t)
            }
        } finally {
            guard.restore()
        }
    }

    private fun flush() {
        if (verts == 0) return
        UiFont.texture()
        buf.flip()
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STREAM_DRAW)
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, verts)
        buf.clear()
        verts = 0
    }

    private fun ensure(n: Int) {
        if (buf.remaining() >= n * FLOATS) return
        val nb = MemoryUtil.memAllocFloat(Math.max(buf.capacity() * 2, buf.position() + n * FLOATS))
        buf.flip(); nb.put(buf); MemoryUtil.memFree(buf); buf = nb
    }

    private fun vert(x: Float, y: Float, lx: Float, ly: Float, hx: Float, hy: Float, r: FloatArray, a: Int, b: Int, mode: Float, stroke: Float, feather: Float, grad: Float, u: Float, v: Float) {
        buf.put(x).put(y).put(lx).put(ly).put(hx).put(hy).put(r[0]).put(r[1]).put(r[2]).put(r[3])
        color(a); color(b)
        buf.put(mode).put(stroke).put(feather).put(grad).put(u).put(v)
        verts++
    }

    private fun color(c: Int) {
        buf.put(((c ushr 16) and 0xFF) / 255f).put(((c ushr 8) and 0xFF) / 255f).put((c and 0xFF) / 255f).put(((c ushr 24) and 0xFF) / 255f)
    }

    private val ZERO = FloatArray(4)
    private val radTmp = FloatArray(4)

    // A quad over [qx0,qy0]-[qx1,qy1]; local coords are relative to (cx, cy).
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

    // ---- primitives used by UiRecorder.replay (already in scaled GUI coordinates) ----

    fun shape(x: Float, y: Float, w: Float, h: Float, tl: Float, tr: Float, br: Float, bl: Float, colA: Int, colB: Int = colA, stroke: Float = 0f, grad: Float = 0f) {
        if (w <= 0f || h <= 0f) return
        radTmp[0] = tl; radTmp[1] = tr; radTmp[2] = br; radTmp[3] = bl
        val pad = 1.5f / pixelRatio + 0.5f
        quad(x - pad, y - pad, x + w + pad, y + h + pad, x + w / 2, y + h / 2, w / 2, h / 2, radTmp, colA, colB, MODE_SHAPE, stroke, 0f, grad)
    }

    fun shadow(x: Float, y: Float, w: Float, h: Float, r: Float, feather: Float, color: Int) {
        // Matches nvgBoxGradient(x, y + f/2, w, h, r + f/2, f) filled over the rect grown by f.
        val rr = r + feather * 0.5f
        radTmp[0] = rr; radTmp[1] = rr; radTmp[2] = rr; radTmp[3] = rr
        quad(x - feather, y - feather, x + w + feather, y + h + feather,
            x + w / 2, y + feather * 0.5f + h / 2, w / 2, h / 2, radTmp, color, color, MODE_SHAPE, 0f, feather, 0f)
    }

    fun triangle(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        ensure(3)
        vert(x0, y0, 0f, 0f, 0f, 0f, ZERO, color, color, MODE_SOLID, 0f, 0f, 0f, 0f, 0f)
        vert(x1, y1, 0f, 0f, 0f, 0f, ZERO, color, color, MODE_SOLID, 0f, 0f, 0f, 0f, 0f)
        vert(x2, y2, 0f, 0f, 0f, 0f, ZERO, color, color, MODE_SOLID, 0f, 0f, 0f, 0f, 0f)
    }

    // Line segment as a rounded capsule, so it stays anti-aliased at any angle.
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int) {
        val dx = x1 - x0; val dy = y1 - y0
        val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        if (len <= 0f) return
        val ux = dx / len; val uy = dy / len
        val hw = width / 2f
        val pad = hw + 1f
        // corners of an oriented quad; local coords are in the segment's own frame
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

    // Glyphs are baked at device-pixel size and placed on whole device pixels, as fontstash did.
    fun text(s: String, x: Float, y: Float, size: Float, color: Int) {
        if (s.isEmpty() || size <= 0f) return
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
            }
            pen += UiFont.advanceUnits(cp) * sc
        }
    }

    fun devicePixel(): Float = 1f / pixelRatio

    fun pushScissor(x: Float, y: Float, w: Float, h: Float) {
        var x0 = x; var y0 = y; var x1 = x + w; var y1 = y + h
        scissors.lastOrNull()?.let { p ->
            x0 = Math.max(x0, p[0]); y0 = Math.max(y0, p[1]); x1 = Math.min(x1, p[2]); y1 = Math.min(y1, p[3])
        }
        flush()
        scissors.addLast(floatArrayOf(x0, y0, Math.max(x0, x1), Math.max(y0, y1)))
        applyScissor()
    }

    fun popScissor() {
        if (scissors.isEmpty()) return
        flush()
        scissors.removeLast()
        applyScissor()
    }

    private fun applyScissor() {
        val s = scissors.lastOrNull()
        if (s == null) { GL11.glDisable(GL11.GL_SCISSOR_TEST); return }
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        val x0 = Math.floor((s[0] * pixelRatio).toDouble()).toInt()
        val x1 = Math.ceil((s[2] * pixelRatio).toDouble()).toInt()
        val y0 = Math.floor((s[1] * pixelRatio).toDouble()).toInt()
        val y1 = Math.ceil((s[3] * pixelRatio).toDouble()).toInt()
        GL11.glScissor(x0, fbH - y1, Math.max(0, x1 - x0), Math.max(0, y1 - y0))
    }
}
