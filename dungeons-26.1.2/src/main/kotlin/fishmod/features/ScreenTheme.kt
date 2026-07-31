package fishmod.features

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Shared smooth/anti-aliased drawing primitives + color palette for FishMod's custom GUI screens
 * (rounded rects, pills, ring outlines, scaled text). Originally lived only in [FishModScreen]'s
 * companion object; extracted here so [CommandAliasesScreen], [CommandKeysScreen], and any future
 * screen can match its "Odin-style" smooth look without duplicating the drawing code.
 */
object ScreenTheme {
    val ACCENT = 0xFF24B6B0.toInt()
    val ACCENT_HOVER = 0xFF3AD8D1.toInt()
    val CARD_BG = 0xFF14181D.toInt()
    val TEXT_COLOR = 0xFFEDF1F5.toInt()
    val SUBTEXT_COLOR = 0xFF8A96A3.toInt()
    val DANGER = 0xFFE05A5A.toInt()
    val DANGER_HOVER = 0xFFEE7A7A.toInt()

    const val TEXT_SCALE = 0.75f

    /** Blends `color`'s alpha channel by `coverage` (0..1), keeping RGB unchanged. */
    private fun withCoverage(color: Int, coverage: Double): Int {
        val a = ((color ushr 24) and 0xFF)
        val newA = Math.round(a * coverage.coerceIn(0.0, 1.0)).toInt().coerceIn(0, 255)
        return (newA shl 24) or (color and 0x00FFFFFF)
    }

    /**
     * Rounded rect with a single-pixel anti-aliased fringe on each corner (fractional circle
     * coverage blended into the boundary pixel) instead of a hard-edged pixel-stairstep corner.
     */
    fun roundedRect(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, color: Int) {
        if (w <= 0 || h <= 0) return
        val rr = Math.max(0, Math.min(r, Math.min(w, h) / 2))
        if (rr == 0) { ctx.fill(x, y, x + w, y + h, color); return }
        ctx.fill(x + rr, y, x + w - rr, y + h, color)
        ctx.fill(x, y + rr, x + rr, y + h - rr, color)
        ctx.fill(x + w - rr, y + rr, x + w, y + h - rr, color)
        for (i in 0 until rr) {
            val dy = (rr - i).toDouble() - 0.5
            val dxExact = Math.sqrt(Math.max(0.0, rr.toDouble() * rr - dy * dy))
            val dxFloor = Math.floor(dxExact).toInt()
            val coverage = dxExact - dxFloor
            val inset = rr - dxFloor
            val topY = y + i
            val botY = y + h - 1 - i
            // solid interior of the corner
            ctx.fill(x + inset, topY, x + rr, topY + 1, color)
            ctx.fill(x + w - rr, topY, x + w - inset, topY + 1, color)
            ctx.fill(x + inset, botY, x + rr, botY + 1, color)
            ctx.fill(x + w - rr, botY, x + w - inset, botY + 1, color)
            // one partially-covered fringe pixel, softening the stairstep edge
            if (inset > 0) {
                val aa = withCoverage(color, coverage)
                ctx.fill(x + inset - 1, topY, x + inset, topY + 1, aa)
                ctx.fill(x + w - inset, topY, x + w - inset + 1, topY + 1, aa)
                ctx.fill(x + inset - 1, botY, x + inset, botY + 1, aa)
                ctx.fill(x + w - inset, botY, x + w - inset + 1, botY + 1, aa)
            }
        }
    }

    fun roundRect(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, r: Int, color: Int) {
        roundedRect(ctx, x1, y1, x2 - x1, y2 - y1, r, color)
    }

    fun roundedRectRing(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, strokeW: Int, fillColor: Int, ringColor: Int) {
        roundedRect(ctx, x - strokeW, y - strokeW, w + strokeW * 2, h + strokeW * 2, r + strokeW, ringColor)
        roundedRect(ctx, x, y, w, h, r, fillColor)
    }

    fun pill(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        val h = y2 - y1
        roundedRect(ctx, x1, y1, x2 - x1, h, h / 2, color)
    }

    fun panel(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int, r: Int, fill: Int, border: Int) {
        roundedRect(ctx, x1, y1, x2 - x1, y2 - y1, r, border)
        roundedRect(ctx, x1 + 1, y1 + 1, x2 - x1 - 2, y2 - y1 - 2, Math.max(0, r - 1), fill)
    }

    fun disc(ctx: GuiGraphicsExtractor, cx: Int, cy: Int, r: Int, color: Int) {
        for (dy in -r..r) {
            val dyF = dy.toDouble()
            val dxExact = Math.sqrt(Math.max(0.0, r.toDouble() * r - dyF * dyF))
            val dxFloor = Math.floor(dxExact).toInt()
            val coverage = dxExact - dxFloor
            ctx.fill(cx - dxFloor, cy + dy, cx + dxFloor + 1, cy + dy + 1, color)
            if (dxFloor >= 0) {
                val aa = withCoverage(color, coverage)
                ctx.fill(cx - dxFloor - 1, cy + dy, cx - dxFloor, cy + dy + 1, aa)
                ctx.fill(cx + dxFloor + 1, cy + dy, cx + dxFloor + 2, cy + dy + 1, aa)
            }
        }
    }

    fun st(ctx: GuiGraphicsExtractor, tr: Font, s: String, x: Int, y: Int, color: Int) {
        ctx.pose().pushMatrix()
        ctx.pose().translate(x.toFloat(), y + 1f)
        ctx.pose().scale(TEXT_SCALE, TEXT_SCALE)
        ctx.text(tr, s, 0, 0, color, false)
        ctx.pose().popMatrix()
    }
    fun stw(tr: Font, s: String): Int = Math.ceil((tr.width(s) * TEXT_SCALE).toDouble()).toInt()

    fun sst(ctx: GuiGraphicsExtractor, tr: Font, s: String, x: Int, y: Int, color: Int, scale: Float) {
        ctx.pose().pushMatrix()
        ctx.pose().translate(x.toFloat(), y.toFloat())
        ctx.pose().scale(scale, scale)
        ctx.text(tr, s, 0, 0, color, false)
        ctx.pose().popMatrix()
    }
    fun sw(tr: Font, s: String, scale: Float): Int = Math.ceil((tr.width(s) * scale).toDouble()).toInt()

    fun drawChevron(ctx: GuiGraphicsExtractor, gx: Int, cy: Int, open: Boolean, color: Int) {
        if (open) {
            ctx.fill(gx, cy - 2, gx + 7, cy - 1, color)
            ctx.fill(gx + 1, cy - 1, gx + 6, cy, color)
            ctx.fill(gx + 2, cy, gx + 5, cy + 1, color)
            ctx.fill(gx + 3, cy + 1, gx + 4, cy + 2, color)
        } else {
            ctx.fill(gx, cy - 3, gx + 1, cy + 4, color)
            ctx.fill(gx + 1, cy - 2, gx + 2, cy + 3, color)
            ctx.fill(gx + 2, cy - 1, gx + 3, cy + 2, color)
            ctx.fill(gx + 3, cy, gx + 4, cy + 1, color)
        }
    }
}
