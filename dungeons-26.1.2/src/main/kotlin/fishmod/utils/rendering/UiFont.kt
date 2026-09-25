package fishmod.utils.rendering

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL21
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

// Inter via stb_truetype (bundled with Minecraft). Like fontstash, glyphs are rasterised at the exact
// device-pixel size they are drawn at, so small UI text stays sharp. Size = ascent - descent in px.
object UiFont {
    const val ATLAS = 1024
    private const val GAP = 2

    class Glyph(val u0: Float, val v0: Float, val u1: Float, val v1: Float, val xoff: Float, val yoff: Float, val w: Float, val h: Float)

    private var fontData: ByteBuffer? = null
    private val info: STBTTFontinfo = STBTTFontinfo.malloc()
    private var loaded = false
    private var failed = false
    private var ascent = 0

    // System fonts for codepoints Inter lacks (e.g. Hypixel's admin prefix glyph U+12DE)
    private class Fallback(val info: STBTTFontinfo, val data: ByteBuffer, val unitRatio: Float)
    private val FALLBACK_PATHS = listOf(
        "C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/seguisym.ttf", "C:/Windows/Fonts/ebrima.ttf",
        "C:/Windows/Fonts/Nirmala.ttf", "C:/Windows/Fonts/arial.ttf",
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf", "/Library/Fonts/Arial Unicode.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/TTF/DejaVuSans.ttf",
    )
    private val fallbacks: List<Fallback> by lazy { loadFallbacks() }
    private val fontFor = HashMap<Int, Int>()

    private val glyphs = HashMap<Long, Glyph>()
    private val atlas = ByteArray(ATLAS * ATLAS)
    private var penX = GAP
    private var penY = GAP
    private var rowH = 0
    private var dirty = false
    private var texture = 0

    private fun ensureLoaded(): Boolean {
        if (loaded) return true
        if (failed) return false
        try {
            val bytes = UiFont::class.java.getResourceAsStream("/assets/fishmod/fonts/Inter-Regular.ttf")!!.use { it.readAllBytes() }
            val buf = MemoryUtil.memAlloc(bytes.size).put(bytes).flip()
            fontData = buf
            if (!STBTruetype.stbtt_InitFont(info, buf)) throw IllegalStateException("stbtt_InitFont failed")
            MemoryStack.stackPush().use { st ->
                val a = st.mallocInt(1); val d = st.mallocInt(1); val g = st.mallocInt(1)
                STBTruetype.stbtt_GetFontVMetrics(info, a, d, g)
                ascent = a[0]
            }
            loaded = true
        } catch (t: Throwable) {
            failed = true
            fishmod.utils.debug.Debug.LOGGER.error("[UiFont] failed to load Inter", t)
        }
        return loaded
    }

    fun scaleFor(size: Float): Float = if (ensureLoaded()) STBTruetype.stbtt_ScaleForPixelHeight(info, size) else 0f

    // Top-aligned text: baseline sits one ascender below y.
    fun ascender(size: Float): Float = ascent * scaleFor(size)

    fun width(s: String, size: Float): Float {
        if (!ensureLoaded() || s.isEmpty()) return 0f
        val sc = scaleFor(size)
        var w = 0f
        var prev = -1
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (prev >= 0) w += kern(prev, cp) * sc
            w += advanceUnits(cp) * sc
            prev = cp
            i += Character.charCount(cp)
        }
        return w
    }

    private val kernCache = HashMap<Long, Int>()
    fun kern(a: Int, b: Int): Int = kernCache.getOrPut((a.toLong() shl 32) or b.toLong()) {
        if (fontIndex(a) != 0 || fontIndex(b) != 0) 0 else STBTruetype.stbtt_GetCodepointKernAdvance(info, a, b)
    }

    // 0 = Inter, n = fallbacks[n - 1]; Inter's .notdef box if nothing has it
    private fun fontIndex(cp: Int): Int = fontFor.getOrPut(cp) {
        if (STBTruetype.stbtt_FindGlyphIndex(info, cp) != 0) return@getOrPut 0
        val i = fallbacks.indexOfFirst { STBTruetype.stbtt_FindGlyphIndex(it.info, cp) != 0 }
        if (i < 0) 0 else i + 1
    }

    private fun loadFallbacks(): List<Fallback> {
        val out = ArrayList<Fallback>()
        val interScale = STBTruetype.stbtt_ScaleForPixelHeight(info, 1f)
        for (path in FALLBACK_PATHS) {
            try {
                val f = java.io.File(path)
                if (!f.isFile) continue
                val bytes = f.readBytes()
                val buf = MemoryUtil.memAlloc(bytes.size).put(bytes).flip()
                val fi = STBTTFontinfo.malloc()
                if (!STBTruetype.stbtt_InitFont(fi, buf)) { fi.free(); MemoryUtil.memFree(buf); continue }
                out.add(Fallback(fi, buf, STBTruetype.stbtt_ScaleForPixelHeight(fi, 1f) / interScale))
            } catch (t: Throwable) {
                fishmod.utils.debug.Debug.LOGGER.warn("[UiFont] fallback font $path failed: $t")
            }
        }
        return out
    }

    private val advCache = HashMap<Int, Int>()
    fun advanceUnits(cp: Int): Int = advCache.getOrPut(cp) {
        val fi = fontIndex(cp)
        val fb = if (fi == 0) null else fallbacks[fi - 1]
        MemoryStack.stackPush().use { st ->
            val adv = st.mallocInt(1); val lsb = st.mallocInt(1)
            STBTruetype.stbtt_GetCodepointHMetrics(fb?.info ?: info, cp, adv, lsb)
            if (fb == null) adv[0] else Math.round(adv[0] * fb.unitRatio)
        }
    }

    // Glyph bitmap for a device-pixel font size (quantised to 1/4 px). Metrics are in device pixels.
    fun glyph(cp: Int, devSize: Float): Glyph? {
        if (!ensureLoaded()) return null
        val q = Math.round(devSize * 4f)
        val key = (cp.toLong() shl 20) or q.toLong()
        glyphs[key]?.let { return it }
        val g = bake(cp, q / 4f)
        glyphs[key] = g
        return g
    }

    private fun bake(cp: Int, devSize: Float): Glyph {
        val fi = fontIndex(cp)
        val info = if (fi == 0) info else fallbacks[fi - 1].info
        val sc = STBTruetype.stbtt_ScaleForPixelHeight(info, devSize)
        MemoryStack.stackPush().use { st ->
            val x0 = st.mallocInt(1); val y0 = st.mallocInt(1); val x1 = st.mallocInt(1); val y1 = st.mallocInt(1)
            STBTruetype.stbtt_GetCodepointBitmapBox(info, cp, sc, sc, x0, y0, x1, y1)
            val gw = x1[0] - x0[0]; val gh = y1[0] - y0[0]
            if (gw <= 0 || gh <= 0) return Glyph(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            if (penX + gw + GAP >= ATLAS) { penX = GAP; penY += rowH + GAP; rowH = 0 }
            if (penY + gh + GAP >= ATLAS) {
                // Atlas full: start over (only after thousands of glyph/size combos).
                glyphs.clear(); java.util.Arrays.fill(atlas, 0); penX = GAP; penY = GAP; rowH = 0
            }
            val tmp = MemoryUtil.memAlloc(gw * gh)
            STBTruetype.stbtt_MakeCodepointBitmap(info, tmp, gw, gh, gw, sc, sc, cp)
            for (row in 0 until gh) for (col in 0 until gw) atlas[(penY + row) * ATLAS + penX + col] = tmp.get(row * gw + col)
            MemoryUtil.memFree(tmp)
            val g = Glyph(
                penX / ATLAS.toFloat(), penY / ATLAS.toFloat(), (penX + gw) / ATLAS.toFloat(), (penY + gh) / ATLAS.toFloat(),
                x0[0].toFloat(), y0[0].toFloat(), gw.toFloat(), gh.toFloat(),
            )
            penX += gw + GAP
            rowH = Math.max(rowH, gh)
            dirty = true
            return g
        }
    }

    // Render thread only. Returns the GL texture id with any newly baked glyphs uploaded.
    fun texture(): Int {
        if (texture == 0) {
            texture = GL11.glGenTextures()
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
            dirty = true
        } else GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
        if (dirty) {
            // Minecraft leaves unpack state (PBO, row length, skips) set from its own uploads; clear it for ours.
            val pbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING)
            val rowLen = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH)
            val skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS)
            val skipPx = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS)
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0)
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0)
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0)
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1)
            val buf = MemoryUtil.memAlloc(atlas.size).put(atlas).flip()
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL30.GL_R8, ATLAS, ATLAS, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, buf)
            MemoryUtil.memFree(buf)
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLen)
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows)
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPx)
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo)
            dirty = false
        }
        return texture
    }
}
