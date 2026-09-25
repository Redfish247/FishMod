package fishmod.utils.rendering


// Records FishMod UI draw commands during extractRenderState; UiRenderer replays them on the GPU.
object UiRecorder {

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
    private const val OP_FILL_ROUNDED_CORNERS = 13

    private const val FLOATS_PER_CMD = 8
    private const val INTS_PER_CMD = 2

    private var ops = ByteArray(256)
    private var floats = FloatArray(256 * FLOATS_PER_CMD)
    private var ints = IntArray(256 * INTS_PER_CMD)
    private var strings = arrayOfNulls<String>(256)
    private var count = 0

    @JvmStatic
    fun clear() {
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
        f0: Float = 0f, f1: Float = 0f, f2: Float = 0f, f3: Float = 0f, f4: Float = 0f, f5: Float = 0f, f6: Float = 0f, f7: Float = 0f,
        i0: Int = 0, i1: Int = 0,
        s: String? = null,
    ) {
        ensureCapacity()
        val idx = count
        ops[idx] = op.toByte()
        val fb = idx * FLOATS_PER_CMD
        floats[fb] = f0; floats[fb + 1] = f1; floats[fb + 2] = f2; floats[fb + 3] = f3; floats[fb + 4] = f4; floats[fb + 5] = f5; floats[fb + 6] = f6; floats[fb + 7] = f7
        val ib = idx * INTS_PER_CMD
        ints[ib] = i0; ints[ib + 1] = i1
        strings[idx] = s
        count++
    }

    // Called by UiRenderer.paint with GL state prepared; scale shrinks the whole layout like nvgScale did.
    @JvmStatic
    fun replay(scale: Float) {
        for (idx in 0 until count) exec(idx, scale)
    }

    private fun exec(idx: Int, k: Float) {
        val op = ops[idx].toInt()
        val fb = idx * FLOATS_PER_CMD
        val ib = idx * INTS_PER_CMD
        val x = floats[fb] * k; val y = floats[fb + 1] * k; val w = floats[fb + 2] * k; val h = floats[fb + 3] * k
        val f4 = floats[fb + 4] * k; val f5 = floats[fb + 5] * k
        when (op) {
            OP_FILL_ROUNDED_RECT -> UiRenderer.shape(x, y, w, h, f4, f4, f4, f4, ints[ib])
            OP_FILL_RECT_TOP_ROUNDED -> UiRenderer.shape(x, y, w, h, f4, f4, 0f, 0f, ints[ib])
            OP_ROUNDED_RECT_RING -> UiRenderer.shape(x, y, w, h, f4, f4, f4, f4, ints[ib], ints[ib + 1], stroke = f5)
            // disc: (cx, cy, r) stored in the x/y/w slots
            OP_DISC -> UiRenderer.shape(x - w, y - w, w * 2, w * 2, w, w, w, w, ints[ib])
            OP_DROP_SHADOW -> UiRenderer.shadow(x, y, w, h, f4, f5, ints[ib])
            OP_FILL_RECT_VGRADIENT -> UiRenderer.shape(x, y, w, h, f4, f4, f4, f4, ints[ib], ints[ib + 1], grad = 1f)
            OP_FILL_RECT_HGRADIENT -> UiRenderer.shape(x, y, w, h, 0f, 0f, 0f, 0f, ints[ib], ints[ib + 1], grad = 2f)
            OP_CHEVRON -> {
                val c = ints[ib + 1]
                if (ints[ib] != 0) UiRenderer.triangle(x, y - 2.5f * k, x + 7 * k, y - 2.5f * k, x + 3.5f * k, y + 3 * k, c)
                else UiRenderer.triangle(x, y - 3.5f * k, x, y + 3.5f * k, x + 5 * k, y, c)
            }
            OP_POP_OUT_ICON -> {
                val c = ints[ib]; val sz = w; val lw = 1.4f * k
                UiRenderer.line(x, y + sz, x + sz, y, lw, c)
                UiRenderer.line(x + sz * 0.5f, y, x + sz, y, lw, c)
                UiRenderer.line(x + sz, y, x + sz, y + sz * 0.5f, lw, c)
            }
            OP_TEXT -> UiRenderer.text(strings[idx] ?: "", x, y, w, ints[ib])
            OP_TEXT_BOLD -> {
                val s = strings[idx] ?: ""
                UiRenderer.text(s, x, y, w, ints[ib])
                // Exactly one device pixel; a fractional offset snaps unevenly per glyph and ghosts.
                UiRenderer.text(s, x + UiRenderer.devicePixel(), y, w, ints[ib])
            }
            OP_PUSH_SCISSOR -> UiRenderer.pushScissor(x, y, w, h)
            OP_POP_SCISSOR -> UiRenderer.popScissor()
            OP_FILL_ROUNDED_CORNERS -> UiRenderer.shape(x, y, w, h, f4, f5, floats[fb + 6] * k, floats[fb + 7] * k, ints[ib])
        }
    }

    @JvmStatic
    fun fillRoundedRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        push(OP_FILL_ROUNDED_RECT, x, y, w, h, r, i0 = color)
    }

    // per-corner radii: top-left, top-right, bottom-right, bottom-left
    @JvmStatic
    fun fillRoundedRectCorners(x: Float, y: Float, w: Float, h: Float, tl: Float, tr: Float, br: Float, bl: Float, color: Int) {
        push(OP_FILL_ROUNDED_CORNERS, x, y, w, h, tl, tr, br, bl, i0 = color)
    }

    @JvmStatic
    fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fillRoundedRect(x, y, w, h, 0f, color)
    }

    @JvmStatic
    fun fillRectTopRounded(x: Float, y: Float, w: Float, h: Float, rTop: Float, color: Int) {
        push(OP_FILL_RECT_TOP_ROUNDED, x, y, w, h, rTop, i0 = color)
    }

    @JvmStatic
    fun fillPillBar(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fillRoundedRect(x, y, w, h, Math.min(w, h) / 2f, color)
    }

    @JvmStatic
    fun roundedRectRing(x: Float, y: Float, w: Float, h: Float, r: Float, strokeW: Float, fillColor: Int, ringColor: Int) {
        push(OP_ROUNDED_RECT_RING, x, y, w, h, r, strokeW, i0 = fillColor, i1 = ringColor)
    }

    @JvmStatic
    fun disc(cx: Float, cy: Float, r: Float, color: Int) {
        push(OP_DISC, cx, cy, r, i0 = color)
    }

    @JvmStatic
    fun dropShadow(x: Float, y: Float, w: Float, h: Float, r: Float, spread: Float, shadowColor: Int) {
        push(OP_DROP_SHADOW, x, y, w, h, r, spread, i0 = shadowColor)
    }

    @JvmStatic
    @JvmOverloads
    fun fillRectVGradient(x: Float, y: Float, w: Float, h: Float, topColor: Int, botColor: Int, r: Float = 0f) {
        push(OP_FILL_RECT_VGRADIENT, x, y, w, h, r, i0 = topColor, i1 = botColor)
    }

    @JvmStatic
    fun fillRectHGradient(x: Float, y: Float, w: Float, h: Float, leftColor: Int, rightColor: Int) {
        push(OP_FILL_RECT_HGRADIENT, x, y, w, h, i0 = leftColor, i1 = rightColor)
    }

    @JvmStatic
    fun chevron(gx: Float, cy: Float, open: Boolean, color: Int) {
        push(OP_CHEVRON, gx, cy, i0 = if (open) 1 else 0, i1 = color)
    }

    @JvmStatic
    fun popOutIcon(x: Float, y: Float, size: Float, color: Int) {
        push(OP_POP_OUT_ICON, x, y, size, i0 = color)
    }

    @JvmStatic
    fun text(s: String, x: Float, y: Float, size: Float, color: Int) {
        push(OP_TEXT, x, y, size, i0 = color, s = s)
    }

    @JvmStatic
    fun textBold(s: String, x: Float, y: Float, size: Float, color: Int) {
        push(OP_TEXT_BOLD, x, y, size, i0 = color, s = s)
    }

    @JvmStatic
    fun textWidth(s: String, size: Float): Float = UiFont.width(s, size)

    @JvmStatic
    fun pushScissor(x: Float, y: Float, w: Float, h: Float) {
        push(OP_PUSH_SCISSOR, x, y, w, h)
    }

    @JvmStatic
    fun popScissor() {
        push(OP_POP_SCISSOR)
    }
}
