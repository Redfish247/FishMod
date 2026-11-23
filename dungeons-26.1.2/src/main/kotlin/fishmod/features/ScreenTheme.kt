package fishmod.features
import fishmod.compat.Fmt

import net.minecraft.client.gui.GuiGraphicsExtractor
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.debug.FishDiag

object ScreenTheme {
    val SOLID_PANEL = 0xFF11121F.toInt()
    var ACCENT = 0xFFD6D6D6.toInt()
    var ACCENT_HOVER = 0xFFFFFFFF.toInt()
    val CARD_BG = 0xFF171717.toInt()
    private var lbC = 0; private var lbV = false
    fun lightBg(): Boolean { val c = windowBg(); if (c != lbC) { lbC = c; lbV = isLight(c) }; return lbV }
    fun luma(c: Int): Float {
        fun ch(v: Int): Float { val s = v / 255f; return if (s <= 0.03928f) s / 12.92f else Math.pow(((s + 0.055f) / 1.055f).toDouble(), 2.4).toFloat() }
        val a = (c ushr 24) / 255f
        val l = 0.2126f * ch((c shr 16) and 0xFF) + 0.7152f * ch((c shr 8) and 0xFF) + 0.0722f * ch(c and 0xFF)
        return l * a + 0.02f * (1f - a)
    }
    fun isLight(c: Int): Boolean = luma(c) > 0.18f
    fun onAccent(): Int = if (isLight(over(ACCENT, windowBg() or 0xFF000000.toInt()))) 0xFF141414.toInt() else 0xFFEDEDED.toInt()
    fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean = mx >= x && mx <= x + w && my >= y && my <= y + h
    fun textOn(bg: Int): Int = if (isLight(bg)) 0xFF141414.toInt() else 0xFFF2F2F2.toInt()
    fun subtextOn(bg: Int): Int = if (isLight(bg)) 0xFF4A4A4A.toInt() else 0xFFA8A8A8.toInt()
    fun over(fg: Int, bg: Int): Int {
        val a = (fg ushr 24) / 255f
        fun m(s: Int) = Math.round(((fg shr s) and 0xFF) * a + ((bg shr s) and 0xFF) * (1f - a))
        return (Math.max(fg ushr 24, bg ushr 24) shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }
    val TEXT_COLOR: Int get() = if (lightBg()) 0xFF141414.toInt() else 0xFFEDEDED.toInt()
    val SUBTEXT_COLOR: Int get() = if (lightBg()) 0xFF5A5A5A.toInt() else 0xFF949494.toInt()
    fun ink(alpha: Int): Int = (alpha shl 24) or (if (lightBg()) 0x000000 else 0xFFFFFF)
    fun accentFg(): Int {
        val a = ACCENT or 0xFF000000.toInt()
        val l1 = luma(a); val l2 = luma(windowBg() or 0xFF000000.toInt())
        val ratio = (Math.max(l1, l2) + 0.05f) / (Math.min(l1, l2) + 0.05f)
        return if (ratio >= 3f) a else TEXT_COLOR
    }
    fun surface(level: Int): Int = over(ink(level), windowBg() or 0xFF000000.toInt())
    val DANGER = 0xFFE05A5A.toInt()
    val DANGER_HOVER = 0xFFEE7A7A.toInt()

    const val TEXT_SCALE = 0.75f

    private fun withCoverage(color: Int, coverage: Double): Int {
        val a = ((color ushr 24) and 0xFF)
        val newA = Math.round(a * coverage.coerceIn(0.0, 1.0)).toInt().coerceIn(0, 255)
        return (newA shl 24) or (color and 0x00FFFFFF)
    }

    private class Corner(val inset: IntArray, val coverage: DoubleArray)
    private val cornerTables = arrayOfNulls<Corner>(65)

    private fun cornerTable(rr: Int): Corner {
        if (rr >= cornerTables.size) return buildCorner(rr)
        return cornerTables[rr] ?: buildCorner(rr).also { cornerTables[rr] = it }
    }

    private fun buildCorner(rr: Int): Corner {
        val inset = IntArray(rr)
        val coverage = DoubleArray(rr)
        for (i in 0 until rr) {
            val dy = (rr - i).toDouble() - 0.5
            val dxExact = Math.sqrt(Math.max(0.0, rr.toDouble() * rr - dy * dy))
            val dxFloor = Math.floor(dxExact).toInt()
            coverage[i] = dxExact - dxFloor
            inset[i] = rr - dxFloor
        }
        return Corner(inset, coverage)
    }

    fun roundedRect(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, color: Int) {
        if (w <= 0 || h <= 0) return
        val rr = Math.max(0, Math.min(r, Math.min(w, h) / 2))
        if (rr == 0) { ctx.fill(x, y, x + w, y + h, color); return }
        ctx.fill(x + rr, y, x + w - rr, y + h, color)
        ctx.fill(x, y + rr, x + rr, y + h - rr, color)
        ctx.fill(x + w - rr, y + rr, x + w, y + h - rr, color)
        val corner = cornerTable(rr)
        for (i in 0 until rr) {
            val coverage = corner.coverage[i]
            val inset = corner.inset[i]
            val topY = y + i
            val botY = y + h - 1 - i
            ctx.fill(x + inset, topY, x + rr, topY + 1, color)
            ctx.fill(x + w - rr, topY, x + w - inset, topY + 1, color)
            ctx.fill(x + inset, botY, x + rr, botY + 1, color)
            ctx.fill(x + w - rr, botY, x + w - inset, botY + 1, color)
            if (inset > 0) {
                val aa = withCoverage(color, coverage)
                ctx.fill(x + inset - 1, topY, x + inset, topY + 1, aa)
                ctx.fill(x + w - inset, topY, x + w - inset + 1, topY + 1, aa)
                ctx.fill(x + inset - 1, botY, x + inset, botY + 1, aa)
                ctx.fill(x + w - inset, botY, x + w - inset + 1, botY + 1, aa)
            }
        }
    }

    fun roundedRectRing(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, strokeW: Int, fillColor: Int, ringColor: Int) {
        roundedRect(ctx, x - strokeW, y - strokeW, w + strokeW * 2, h + strokeW * 2, r + strokeW, ringColor)
        roundedRect(ctx, x, y, w, h, r, fillColor)
    }

    fun windowBg(): Int {
        val s = fishmod.utils.config.values.FishSettings
        val rgb = when (s.fmBgPreset) {
            "Charcoal" -> 0x2B2B2B
            "Obsidian" -> 0x0B0B0D
            "Moss" -> 0x24311F
            "Wine" -> 0x3A1C24
            "Deep Sea" -> 0x13262E
            "Custom" -> s.fmBgCustomColor and 0xFFFFFF
            else -> 0x1E1E1E
        }
        return (Math.round(s.fmBgAlpha.coerceIn(20, 100) * 2.55f) shl 24) or rgb
    }
    private fun themed(fill: Int): Int {
        val a = fill ushr 24; val r = (fill shr 16) and 0xFF; val g = (fill shr 8) and 0xFF; val b = fill and 0xFF
        return if (a >= 0xC0 && r < 0x30 && g < 0x30 && b < 0x30 && Math.abs(r - g) < 8 && Math.abs(g - b) < 8) windowBg() else fill
    }

    fun panel(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, r: Int, fill: Int, border: Int) {
        roundedRect(ctx, x1, y1, x2 - x1, y2 - y1, r, border)
        roundedRect(ctx, x1 + 1, y1 + 1, x2 - x1 - 2, y2 - y1 - 2, Math.max(0, r - 1), themed(fill))
    }

    private const val NVG_BASE_TEXT_SIZE = 9.5f

    fun nst(s: String, x: Int, y: Int, color: Int, scale: Float = TEXT_SCALE) {
        UiRecorder.text(s, x.toFloat(), y.toFloat(), NVG_BASE_TEXT_SIZE * scale, color)
    }
    fun nstw(s: String, scale: Float = TEXT_SCALE): Int =
        Math.ceil(UiRecorder.textWidth(s, NVG_BASE_TEXT_SIZE * scale).toDouble()).toInt()

    fun nRoundedRect(x: Int, y: Int, w: Int, h: Int, r: Int, color: Int) {
        UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r.toFloat(), color)
    }

    fun nRect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        UiRecorder.fillRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), color)
    }

    fun nRoundedRectRing(x: Int, y: Int, w: Int, h: Int, r: Int, strokeW: Int, fillColor: Int, ringColor: Int) {
        UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), r.toFloat(), strokeW.toFloat(), fillColor, ringColor)
    }

    fun nPanel(x1: Int, y1: Int, x2: Int, y2: Int, r: Int, fill: Int, border: Int) {
        nRoundedRect(x1, y1, x2 - x1, y2 - y1, r, border)
        nRoundedRect(x1 + 1, y1 + 1, x2 - x1 - 2, y2 - y1 - 2, Math.max(0, r - 1), themed(fill))
    }

    fun nTextField(field: net.minecraft.client.gui.components.EditBox, focused: Boolean, x: Int, y: Int, w: Int, h: Int, textSize: Float = 7f) {
        nRoundedRectRing(x, y, w, h, 3, 1, FIELD_BG_DEFAULT, if (focused) ACCENT else FIELD_BORDER_DEFAULT)
        nTextFieldContent(field, focused, x, y, w, h, textSize)
    }

    fun nTextFieldContent(field: net.minecraft.client.gui.components.EditBox, focused: Boolean, x: Int, y: Int, w: Int, h: Int, textSize: Float = 7f) {
        val text = field.value
        val cursor = field.cursorPosition.coerceIn(0, text.length)
        val cursorX = UiRecorder.textWidth(text.substring(0, cursor), textSize)
        val pad = 3f
        val visibleW = w - pad * 2f
        val scroll = if (focused) Math.max(0f, cursorX - visibleW) else 0f
        UiRecorder.pushScissor((x + 1).toFloat(), (y + 1).toFloat(), (w - 2).toFloat(), (h - 2).toFloat())
        try {
            UiRecorder.text(text, x + pad - scroll, y + (h - textSize) / 2f, textSize, TEXT_COLOR)
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0L) {
                UiRecorder.fillRect(x + pad + cursorX - scroll, (y + 2).toFloat(), 1f, (h - 4).toFloat(), TEXT_COLOR)
            }
        } catch (e: Exception) {
            FishDiag.fail("ScreenTheme.1", "text field render failed (len=${text.length}, cursor=$cursor)", e)
        } finally {
            UiRecorder.popScissor()
        }
    }

    private class Run(val text: String, val color: Int, val bold: Boolean)

    private fun legacyRuns(s: String, baseColor: Int, baseBold: Boolean, out: MutableList<Run>) {
        var color = baseColor
        var bold = baseBold
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '§' && i + 1 < s.length) {
                val fmt = net.minecraft.ChatFormatting.getByCode(s[i + 1].lowercaseChar())
                if (fmt != null) {
                    if (sb.isNotEmpty()) { out.add(Run(sb.toString(), color, bold)); sb.setLength(0) }
                    val fc = Fmt.colorOf(s[i + 1])
                    when {
                        fmt == net.minecraft.ChatFormatting.RESET -> { color = baseColor; bold = false }
                        fmt == net.minecraft.ChatFormatting.BOLD -> bold = true
                        fc != null -> { color = (0xFF shl 24) or fc; bold = false }
                    }
                    i += 2
                    continue
                }
            }
            sb.append(c)
            i++
        }
        if (sb.isNotEmpty()) out.add(Run(sb.toString(), color, bold))
    }

    private fun componentRuns(c: net.minecraft.network.chat.Component, defaultColor: Int): List<Run> {
        val out = ArrayList<Run>()
        c.visit(net.minecraft.network.chat.FormattedText.StyledContentConsumer<Unit> { style, text ->
            if (text.isNotEmpty()) {
                val col = style.color?.let { (0xFF shl 24) or it.value } ?: defaultColor
                legacyRuns(text, col, style.isBold, out)
            }
            java.util.Optional.empty()
        }, net.minecraft.network.chat.Style.EMPTY)
        return out
    }

    fun nLegacyText(s: String, x: Int, y: Int, baseColor: Int, size: Float = 7.5f) {
        val out = ArrayList<Run>()
        if (FishDiag.guard("ScreenTheme.2", "legacy text parse failed") { legacyRuns(s, baseColor, false, out) } == null) return
        var px = x.toFloat()
        for (r in out) {
            if (r.bold) UiRecorder.textBold(r.text, px, y.toFloat(), size, r.color) else UiRecorder.text(r.text, px, y.toFloat(), size, r.color)
            px += UiRecorder.textWidth(r.text, size)
        }
    }

    private var tooltipLines: List<net.minecraft.network.chat.Component>? = null
    private var tooltipRuns: List<List<Run>> = emptyList()
    private var tooltipWidthSize = -1f
    private var tooltipTextW = 0f

    private fun runWidth(r: Run, size: Float, paintScale: Float): Float {
        val w = UiRecorder.textWidth(r.text, size)
        if (!r.bold) return w
        val devPerVirtual = net.minecraft.client.Minecraft.getInstance().window.guiScale * paintScale
        return w + r.text.codePointCount(0, r.text.length) / devPerVirtual.toFloat()
    }

    fun nItemTooltip(lines: List<net.minecraft.network.chat.Component>, mx: Int, my: Int, screenW: Int, screenH: Int, k: Float = 1f, paintScale: Float = 1f) {
        if (lines.isEmpty()) return
        if (lines != tooltipLines) {
            tooltipLines = lines
            tooltipRuns = FishDiag.guard("ScreenTheme.3", "tooltip component parse failed (${lines.size} lines)") {
                lines.mapIndexed { i, c -> componentRuns(c, if (i == 0) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt()) }
            } ?: emptyList()
            tooltipWidthSize = -1f
        }
        if (!FishDiag.check(tooltipRuns.isNotEmpty(), "ScreenTheme.4") { "tooltip runs empty for ${lines.size} lines" }) return
        val runs = tooltipRuns
        val rawH = runs.size * 10f + (if (runs.size > 1) 2f else 0f) + 12f - 2f
        val u = k * Math.min(1f, (screenH - 8f) / (rawH * k)).coerceAtLeast(0.3f)
        if (!FishDiag.check(u.isFinite() && u > 0f, "ScreenTheme.5") { "tooltip scale invalid u=$u k=$k rawH=$rawH screenH=$screenH" }) return
        val size = 7.5f * u
        val lineH = 10f * u
        val pad = 6f * u
        if (size != tooltipWidthSize) {
            tooltipWidthSize = size
            tooltipTextW = runs.maxOf { rs -> rs.fold(0f) { a, r -> a + runWidth(r, size, paintScale) } }
        }
        val textW = tooltipTextW
        val w = textW + pad * 2
        val h = rawH * u
        var tx = mx + 4f * u
        if (tx + w > screenW - 4) tx = mx - 4f * u - w
        tx = tx.coerceAtLeast(4f)
        val ty = (my - 12f * u).coerceAtMost(screenH - h - 4f).coerceAtLeast(4f)
        UiRecorder.dropShadow(tx, ty, w, h, 5f * u, 6f * u, 0x66000000)
        UiRecorder.roundedRectRing(tx, ty, w, h, 5f * u, 1f * k, SOLID_PANEL, FIELD_BORDER_DEFAULT)
        var ly = ty + pad
        for ((i, rs) in runs.withIndex()) {
            var px = tx + pad
            for (r in rs) {
                if (r.bold) UiRecorder.textBold(r.text, px, ly, size, r.color) else UiRecorder.text(r.text, px, ly, size, r.color)
                px += runWidth(r, size, paintScale)
            }
            ly += lineH + if (i == 0) 2f * u else 0f
        }
    }

    private val FIELD_BG_DEFAULT = 0xFF1E1E1E.toInt()
    private val FIELD_BORDER_DEFAULT = 0xFF333333.toInt()
}
