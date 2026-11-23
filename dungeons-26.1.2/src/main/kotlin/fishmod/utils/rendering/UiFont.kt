package fishmod.utils.rendering

import fishmod.utils.debug.FishDiag
import org.lwjgl.stb.STBTTFontinfo
import org.lwjgl.stb.STBTruetype
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

object UiFont {
    const val ATLAS = 1024
    private const val GAP = 2

    class Glyph(val u0: Float, val v0: Float, val u1: Float, val v1: Float, val xoff: Float, val yoff: Float, val w: Float, val h: Float)

    private var fontData: ByteBuffer? = null
    private val info: STBTTFontinfo = STBTTFontinfo.malloc()
    private var loaded = false
    private var failed = false
    private var ascent = 0

    // Main UI font: first installed system sans-serif (no bundled font)
    private val PRIMARY_PATHS = listOf(
        "C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/arial.ttf",
        "/System/Library/Fonts/Supplemental/Arial.ttf", "/System/Library/Fonts/Helvetica.ttc",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/TTF/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf", "/usr/share/fonts/liberation/LiberationSans-Regular.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
    )

    private class Fallback(val info: STBTTFontinfo, val data: ByteBuffer, val unitRatio: Float)
    private val FALLBACK_PATHS = listOf(
        "C:/Windows/Fonts/segoeui.ttf", "C:/Windows/Fonts/seguisym.ttf", "C:/Windows/Fonts/ebrima.ttf",
        "C:/Windows/Fonts/Nirmala.ttf", "C:/Windows/Fonts/arial.ttf",
        "/System/Library/Fonts/Supplemental/Arial Unicode.ttf", "/Library/Fonts/Arial Unicode.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/TTF/DejaVuSans.ttf",
    )
    private val fallbackSlots = arrayOfNulls<Fallback>(FALLBACK_PATHS.size)
    private val fallbackTried = BooleanArray(FALLBACK_PATHS.size)
    private val fontFor = it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap().apply { defaultReturnValue(-1) }

    private val glyphs = it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<Glyph>()
    private val atlas = ByteArray(ATLAS * ATLAS)
    private var penX = GAP
    private var penY = GAP
    private var rowH = 0
    private var dirty = true

    private fun ensureLoaded(): Boolean {
        if (loaded) return true
        if (failed) return false
        try {
            val file = PRIMARY_PATHS.map { java.io.File(it) }.firstOrNull { it.isFile } ?: throw IllegalStateException("no system sans-serif font found")
            val bytes = file.readBytes()
            val buf = MemoryUtil.memAlloc(bytes.size).put(bytes).flip()
            fontData = buf
            if (!STBTruetype.stbtt_InitFont(info, buf)) throw IllegalStateException("stbtt_InitFont failed")
            MemoryStack.stackPush().use { st ->
                val a = st.mallocInt(1); val d = st.mallocInt(1); val g = st.mallocInt(1)
                STBTruetype.stbtt_GetFontVMetrics(info, a, d, g)
                ascent = a[0]
                if (ascent <= 0) FishDiag.fail("UiFont.2", "UI font reports non-positive ascent ${a[0]}")
            }
            loaded = true
        } catch (t: Throwable) {
            failed = true
            fishmod.utils.debug.FishDebug.LOGGER.error("[UiFont] failed to load system UI font", t)
            FishDiag.fail("UiFont.1", "load system UI font", t)
        }
        return loaded
    }

    fun scaleFor(size: Float): Float = if (ensureLoaded()) STBTruetype.stbtt_ScaleForPixelHeight(info, size) else 0f

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

    private val kernCache = it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap().apply { defaultReturnValue(Int.MIN_VALUE) }
    fun kern(a: Int, b: Int): Int {
        val key = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)
        val cached = kernCache.get(key)
        if (cached != Int.MIN_VALUE) return cached
        val k = if (fontIndex(a) != 0 || fontIndex(b) != 0) 0 else STBTruetype.stbtt_GetCodepointKernAdvance(info, a, b)
        kernCache.put(key, k)
        return k
    }

    fun canDisplay(cp: Int): Boolean {
        if (!ensureLoaded()) return false
        if (STBTruetype.stbtt_FindGlyphIndex(info, cp) != 0) return true
        return FALLBACK_PATHS.indices.any { i -> fallback(i)?.let { STBTruetype.stbtt_FindGlyphIndex(it.info, cp) != 0 } == true }
    }

    private fun fontIndex(cp: Int): Int {
        val cached = fontFor.get(cp)
        if (cached >= 0) return cached
        var idx = 0
        if (STBTruetype.stbtt_FindGlyphIndex(info, cp) == 0) {
            for (i in FALLBACK_PATHS.indices) {
                val fb = fallback(i) ?: continue
                if (STBTruetype.stbtt_FindGlyphIndex(fb.info, cp) != 0) { idx = i + 1; break }
            }
        }
        fontFor.put(cp, idx)
        return idx
    }

    private fun fallback(i: Int): Fallback? {
        if (!fallbackTried[i]) {
            fallbackTried[i] = true
            fallbackSlots[i] = loadFallback(FALLBACK_PATHS[i])
        }
        return fallbackSlots[i]
    }

    private fun loadFallback(path: String): Fallback? {
        try {
            val f = java.io.File(path)
            if (!f.isFile) return null
            val bytes = f.readBytes()
            val buf = MemoryUtil.memAlloc(bytes.size).put(bytes).flip()
            val fi = STBTTFontinfo.malloc()
            if (!STBTruetype.stbtt_InitFont(fi, buf)) {
                FishDiag.fail("UiFont.3", "stbtt_InitFont rejected fallback font $path")
                fi.free(); MemoryUtil.memFree(buf); return null
            }
            val baseScale = STBTruetype.stbtt_ScaleForPixelHeight(info, 1f)
            if (!(baseScale > 0f)) FishDiag.fail("UiFont.4", "UI font scale invalid ($baseScale) while loading fallback $path")
            return Fallback(fi, buf, STBTruetype.stbtt_ScaleForPixelHeight(fi, 1f) / baseScale)
        } catch (t: Throwable) {
            fishmod.utils.debug.FishDebug.LOGGER.warn("[UiFont] fallback font $path failed: $t")
            FishDiag.fail("UiFont.5", "load fallback font $path", t)
            return null
        }
    }

    private val advCache = it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap().apply { defaultReturnValue(Int.MIN_VALUE) }
    fun advanceUnits(cp: Int): Int {
        val cached = advCache.get(cp)
        if (cached != Int.MIN_VALUE) return cached
        val fi = fontIndex(cp)
        val fb = if (fi == 0) null else fallback(fi - 1)
        val adv = MemoryStack.stackPush().use { st ->
            val adv = st.mallocInt(1); val lsb = st.mallocInt(1)
            STBTruetype.stbtt_GetCodepointHMetrics(fb?.info ?: info, cp, adv, lsb)
            if (fb == null) adv[0] else Math.round(adv[0] * fb.unitRatio)
        }
        advCache.put(cp, adv)
        return adv
    }

    fun glyph(cp: Int, devSize: Float): Glyph? {
        if (!ensureLoaded()) return null
        if (!(devSize > 0f) || devSize.isInfinite()) FishDiag.fail("UiFont.6", "glyph requested with invalid size $devSize cp=$cp")
        val q = Math.round(devSize * 4f)
        val key = (cp.toLong() shl 20) or q.toLong()
        glyphs.get(key)?.let { return it }
        val g = bake(cp, q / 4f)
        glyphs.put(key, g)
        return g
    }

    private fun bake(cp: Int, devSize: Float): Glyph {
        val fi = fontIndex(cp)
        val fb = if (fi == 0) null else fallback(fi - 1)
        if (fi != 0 && fb == null) FishDiag.fail("UiFont.7", "fallback font slot ${fi - 1} vanished for cp=$cp")
        val info = if (fi == 0) info else fb!!.info
        val sc = STBTruetype.stbtt_ScaleForPixelHeight(info, devSize)
        MemoryStack.stackPush().use { st ->
            val x0 = st.mallocInt(1); val y0 = st.mallocInt(1); val x1 = st.mallocInt(1); val y1 = st.mallocInt(1)
            STBTruetype.stbtt_GetCodepointBitmapBox(info, cp, sc, sc, x0, y0, x1, y1)
            val gw = x1[0] - x0[0]; val gh = y1[0] - y0[0]
            if (gw <= 0 || gh <= 0) return Glyph(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            if (gw + 2 * GAP >= ATLAS || gh + 2 * GAP >= ATLAS) {
                FishDiag.fail("UiFont.8", "glyph cp=$cp at size $devSize is ${gw}x$gh, bigger than the ${ATLAS}px atlas")
                return Glyph(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
            }
            if (penX + gw + GAP >= ATLAS) { penX = GAP; penY += rowH + GAP; rowH = 0 }
            if (penY + gh + GAP >= ATLAS) {
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

    private var upload: ByteBuffer? = null

    fun takeDirtyAtlas(): ByteBuffer? {
        if (!dirty) return null
        dirty = false
        val b = upload ?: MemoryUtil.memAlloc(atlas.size).also { upload = it }
        b.clear(); b.put(atlas).flip()
        return b
    }

    fun markAtlasLost() { dirty = true }
}
