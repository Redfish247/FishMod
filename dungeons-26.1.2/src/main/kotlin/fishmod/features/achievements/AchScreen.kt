package fishmod.features.achievements

import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.features.achievements.AchRegistry.Ach
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.min

// Achievements browser opened by /fm ach
class AchScreen(private val parent: Screen? = null) : Screen(Component.literal("Achievements")), HasUiOverlay {

    companion object {
        private val ACCENT = ScreenTheme.ACCENT
        private val PANEL = 0xF014181D.toInt()
        private val PANEL2 = 0xFF0F1317.toInt()
        private val RAISE = 0xFF1B2027.toInt()
        private val LINE = 0xFF262D36.toInt()
        private val LINE2 = 0xFF3A3F48.toInt()
        private val DIM = 0xFF5D6873.toInt()
        private val GOLD = 0xFFF2C14E.toInt()
        private const val ACC_SOFT = 0x2424B6B0
        private val ACC_INK = 0xFF06302F.toInt()
        private const val S_XS = 6.5f
        private const val S_SM = 7.5f
        private const val S_MD = 8f
        private const val S_LG = 9.5f
        private const val S_VAL = 12f
        private const val RAIL_W = 148
        private const val HERO_H = 76
        private const val TILE_W = 112
        private const val TILE_H = 82
        private const val GAP = 10
        private const val ALL = "All"
        private const val RECENT = "Recent"
        private val DATE = SimpleDateFormat("MMM d, yyyy")
    }

    private lateinit var search: EditBox
    private var group = ALL
    private var scroll = 0
    private var contentH = 0
    private val clicks = ArrayList<Pair<IntArray, () -> Unit>>()

    override fun init() {
        search = EditBox(this.font, 0, 0, 100, 16, Component.literal(""))
        search.setMaxLength(48)
        search.setBordered(false)
        search.setResponder { scroll = 0 }
        AchEngine.evaluate()
    }

    override fun isPauseScreen(): Boolean = false
    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    private fun vw(): Int = (this.width / UiScale.factor()).toInt()
    private fun vh(): Int = (this.height / UiScale.factor()).toInt()

    private fun hidden(a: Ach): Boolean = AchSettings.achHideUntracked && !a.tracked && !AchEngine.unlocked(a)
    private fun secretHidden(a: Ach): Boolean = a.secret && !AchEngine.unlocked(a) && !AchSettings.achShowSecrets

    private fun groups(): List<String> = listOf(ALL, RECENT) + AchRegistry.SECTIONS

    private fun inGroup(g: String): List<Ach> = when (g) {
        ALL -> AchRegistry.ALL.filter { !hidden(it) }
        RECENT -> AchStore.data.unlocked.entries.sortedByDescending { it.value }
            .mapNotNull { AchRegistry.BY_ID[it.key] }.take(50)
        else -> AchRegistry.ALL.filter { it.section == g && !hidden(it) }
    }

    private fun shown(): List<Ach> {
        val q = search.value.trim().lowercase()
        val list = inGroup(group)
        if (q.isEmpty()) return list
        return list.filter { !secretHidden(it) && (it.name.lowercase().contains(q) || it.desc.lowercase().contains(q)) }
    }

    private var hoverAch: Ach? = null
    private var hoverX = 0
    private var hoverY = 0
    private val rowStride = TILE_H + GAP

    private fun gridCols(w: Int): Int = max(1, (w + GAP) / (TILE_W + GAP))

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mx = UiScale.vx(mouseX)
        val my = UiScale.vx(mouseY)
        clicks.clear()
        UiRecorder.clear()
        hoverAch = null
        ScreenTheme.nRect(0, 0, vw(), vh(), 0xAA000000.toInt())

        val w = min(780, vw() - 32)
        val h = min(450, vh() - 32)
        val x = (vw() - w) / 2
        val y = (vh() - h) / 2
        UiRecorder.dropShadow(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 14f, 20f, 0x60000000)
        ScreenTheme.nRoundedRect(x, y, w, h, 14, PANEL)
        UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 14f, 1f, 0, LINE)

        rail(x, y, RAIL_W, h, mx, my)
        val cx = x + RAIL_W + 16
        val cw = w - RAIL_W - 32
        hero(cx, y + 14, cw)
        val top = y + 14 + HERO_H + 12
        grid(cx, top, cw, y + h - 14 - top, mx, my)
        hoverAch?.let { detail(it, mx, my) }

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    // left category rail with count rings
    private fun rail(x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
        UiRecorder.fillRoundedRect((x + 1).toFloat(), (y + 1).toFloat(), w.toFloat(), (h - 2).toFloat(), 13f, PANEL2)
        UiRecorder.fillRect((x + w - 10).toFloat(), (y + 1).toFloat(), 11f, (h - 2).toFloat(), PANEL2)
        UiRecorder.fillRect((x + w).toFloat(), (y + 1).toFloat(), 1f, (h - 2).toFloat(), LINE)
        UiRecorder.textBold("TROPHIES", (x + 16).toFloat(), (y + 18).toFloat(), S_XS, DIM)
        var ry = y + 36
        val rh = 28
        val maxRows = (h - 36 - 50) / (rh + 2)
        for ((i, g) in groups().withIndex()) {
            if (i >= maxRows) break
            val on = g == group
            val l = inGroup(g)
            val done = l.count { AchEngine.unlocked(it) }
            val frac = if (g == RECENT || l.isEmpty()) 0f else done.toFloat() / l.size
            val bx = x + 8
            val bw = w - 16
            val hov = !on && inside(mx, my, bx, ry, bw, rh)
            if (on) {
                UiRecorder.fillRoundedRect(bx.toFloat(), ry.toFloat(), bw.toFloat(), rh.toFloat(), 8f, ACC_SOFT)
                UiRecorder.fillRoundedRect(bx.toFloat(), (ry + 7).toFloat(), 3f, (rh - 14).toFloat(), 1.5f, ACCENT)
            } else if (hov) UiRecorder.fillRoundedRect(bx.toFloat(), ry.toFloat(), bw.toFloat(), rh.toFloat(), 8f, RAISE)
            val rcx = bx + 20
            val rcy = ry + rh / 2
            ring(rcx.toFloat(), rcy.toFloat(), 9f, frac, if (on) ACCENT else 0xFF2F8F8B.toInt(), 2f)
            val glyph = when (g) { ALL -> "*"; RECENT -> "R"; else -> g.first().uppercase() }
            UiRecorder.textBold(glyph, rcx - UiRecorder.textWidthBold(glyph, S_XS) / 2f, rcy - S_XS / 2f, S_XS, if (on) ACCENT else ScreenTheme.SUBTEXT_COLOR)
            val cnt = if (g == RECENT) "${AchStore.data.unlocked.size}" else "$done"
            val nameW = bw - 38 - tw(cnt, S_XS) - 12
            UiRecorder.text(clip(g, nameW.toFloat(), S_MD), (bx + 36).toFloat(), ry + (rh - S_MD) / 2f, S_MD, if (on || hov) ScreenTheme.TEXT_COLOR else ScreenTheme.SUBTEXT_COLOR)
            UiRecorder.text(cnt, (bx + bw - 8 - tw(cnt, S_XS)).toFloat(), ry + (rh - S_XS) / 2f, S_XS, DIM)
            val gg = g
            clicks.add(intArrayOf(bx, ry, bw, rh) to { group = gg; scroll = 0 })
            ry += rh + 2
        }
        val bw = w - 32
        val bh = 22
        val bx = x + 16
        val by = y + h - 16 - bh
        val hov = inside(mx, my, bx, by, bw, bh)
        UiRecorder.fillPillBar(bx.toFloat(), by.toFloat(), bw.toFloat(), bh.toFloat(), if (hov) ScreenTheme.ACCENT_HOVER else ACCENT)
        UiRecorder.textBold("Done", bx + (bw - tw("Done", S_SM)) / 2f, by + (bh - S_SM) / 2f, S_SM, ACC_INK)
        clicks.add(intArrayOf(bx, by, bw, bh) to { onClose() })
    }

    // hero strip: AP ring, rank title, overall bar, search
    private fun hero(x: Int, y: Int, w: Int) {
        val all = AchRegistry.ALL
        val done = all.count { AchEngine.unlocked(it) }
        val frac = if (all.isNotEmpty()) done.toFloat() / all.size else 0f
        val pct = (frac * 100).toInt()
        UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), HERO_H.toFloat(), 12f, 1f, PANEL2, LINE)
        val rcx = x + 14 + 28
        val rcy = y + HERO_H / 2
        ring(rcx.toFloat(), rcy.toFloat(), 26f, frac, ACCENT, 5f)
        val p = "$pct%"
        UiRecorder.textBold(p, rcx - UiRecorder.textWidthBold(p, S_LG) / 2f, rcy - S_LG / 2f, S_LG, ScreenTheme.TEXT_COLOR)

        val tx = x + 14 + 56 + 16
        val rank = when {
            pct >= 100 -> "Legend"; pct >= 75 -> "Master"; pct >= 50 -> "Expert"
            pct >= 25 -> "Adept"; pct >= 10 -> "Apprentice"; else -> "Novice"
        }
        UiRecorder.textBold(rank, tx.toFloat(), (y + 14).toFloat(), S_VAL, GOLD)
        val sub = "%,d / %,d AP   -   $done / ${all.size} unlocked".format(AchEngine.points(), AchEngine.maxPoints())
        val sw = min(190, w / 3)
        val sx = x + w - 14 - sw
        UiRecorder.text(clip(sub, (sx - tx - 16).toFloat(), S_SM), tx.toFloat(), (y + 33).toFloat(), S_SM, ScreenTheme.SUBTEXT_COLOR)
        bar(tx, y + 52, sx - tx - 16, 4, frac, ACCENT)

        val sh = 22
        val sy = y + (HERO_H - sh) / 2
        val focused = search.isFocused
        UiRecorder.roundedRectRing(sx.toFloat(), sy.toFloat(), sw.toFloat(), sh.toFloat(), 8f, 1f, PANEL, if (focused) ACCENT else LINE2)
        ScreenTheme.nTextFieldContent(search, focused, sx + 6, sy, sw - 12, sh, S_SM)
        if (search.value.isEmpty() && !focused) UiRecorder.text("Search...", (sx + 9).toFloat(), sy + (sh - S_SM) / 2f, S_SM, DIM)
        clicks.add(intArrayOf(sx, sy, sw, sh) to { search.isFocused = true })
    }

    private fun grid(x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
        val items = shown()
        if (items.isEmpty()) {
            val msg = if (group == RECENT) "Nothing unlocked yet." else "No achievements match."
            UiRecorder.text(msg, x + (w - tw(msg, S_SM)) / 2f, (y + 40).toFloat(), S_SM, DIM)
            return
        }
        val scrolls = ((items.size + gridCols(w) - 1) / gridCols(w)) * rowStride > h
        val usable = if (scrolls) w - 10 else w
        val cols = gridCols(usable)
        val rows = (items.size + cols - 1) / cols
        contentH = rows * rowStride
        val tileW = (usable - (cols - 1) * GAP) / cols
        scroll = scroll.coerceIn(0, max(0, contentH - GAP - h))
        UiRecorder.pushScissor(x.toFloat() - 8, y.toFloat(), w.toFloat() + 8, h.toFloat())
        for ((i, a) in items.withIndex()) {
            val tx = x + (i % cols) * (tileW + GAP)
            val ty = y + (i / cols) * rowStride - scroll
            if (ty + TILE_H < y || ty > y + h) continue
            val hov = inside(mx, my, tx, ty, tileW, TILE_H) && my in y..(y + h)
            tile(a, tx, ty, tileW, hov)
            if (hov) { hoverAch = a; hoverX = mx; hoverY = my }
        }
        UiRecorder.popScissor()
        if (scrolls) {
            val barH = max(16, h * h / contentH)
            val barY = y + (h - barH) * scroll / max(1, contentH - GAP - h)
            UiRecorder.fillPillBar((x + w - 3).toFloat(), y.toFloat(), 3f, h.toFloat(), 0x22FFFFFF)
            UiRecorder.fillPillBar((x + w - 3).toFloat(), barY.toFloat(), 3f, barH.toFloat(), ACCENT)
        }
    }

    private fun tile(a: Ach, x: Int, y: Int, w: Int, hov: Boolean) {
        val done = AchEngine.unlocked(a)
        val secret = secretHidden(a)
        val xf = x.toFloat(); val yf = y.toFloat(); val wf = w.toFloat(); val hf = TILE_H.toFloat()
        val top = if (done) 0xFF1B2A2D.toInt() else if (hov) 0xFF1E242B.toInt() else 0xFF181D23.toInt()
        val bot = if (done) 0xFF13191D.toInt() else 0xFF12161B.toInt()
        UiRecorder.fillRectVGradient(xf, yf, wf, hf, top, bot, 10f)
        UiRecorder.roundedRectRing(xf, yf, wf, hf, 10f, 1f, 0, if (done) ACCENT else if (hov) LINE2 else LINE)

        val bcx = x + w / 2f
        val bcy = y + 24f
        val glyph = if (secret) "?" else (a.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?")
        val gw = UiRecorder.textWidthBold(glyph, S_LG)
        if (done) {
            UiRecorder.disc(bcx, bcy, 15f, 0x3324B6B0)
            UiRecorder.disc(bcx, bcy, 12f, ACCENT)
            UiRecorder.textBold(glyph, bcx - gw / 2f, bcy - S_LG / 2f, S_LG, ACC_INK)
        } else {
            UiRecorder.disc(bcx, bcy, 12f, if (secret) 0xFF0D1014.toInt() else LINE)
            UiRecorder.textBold(glyph, bcx - gw / 2f, bcy - S_LG / 2f, S_LG, if (secret) 0xFF3A424B.toInt() else DIM)
        }

        val name = if (secret) "???" else a.name
        val nc = if (done) ScreenTheme.TEXT_COLOR else if (secret) DIM else 0xFF9AA4AE.toInt()
        val shownName = clip(name, (w - 14).toFloat(), S_SM)
        UiRecorder.text(shownName, bcx - tw(shownName, S_SM) / 2f, (y + 46).toFloat(), S_SM, nc)
        val ap = "${a.points} AP"
        UiRecorder.text(ap, bcx - tw(ap, S_XS) / 2f, (y + 59).toFloat(), S_XS, if (done) GOLD else DIM)

        val frac = progress(a)
        val by = y + TILE_H - 11
        if (done) bar(x + 12, by, w - 24, 3, 1f, ACCENT)
        else bar(x + 12, by, w - 24, 3, max(frac, 0f), 0xFF2F8F8B.toInt())
    }

    // -1 when there is no numeric progress to show
    private fun progress(a: Ach): Float {
        if (secretHidden(a) || a.serverSide || !a.tracked) return -1f
        if (a.metric.startsWith("meta.") || a.metric.startsWith("secret.") || a.goal <= 1) return -1f
        return min(AchEngine.value(a), a.goal).toFloat() / a.goal
    }

    private fun wrap(s: String, maxW: Float, size: Float): List<String> {
        val out = ArrayList<String>()
        var line = ""
        for (word in s.split(" ")) {
            val t = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && UiRecorder.textWidth(t, size) > maxW) { out.add(line); line = word } else line = t
        }
        if (line.isNotEmpty()) out.add(line)
        return out
    }

    // floating detail card for the hovered tile
    private fun detail(a: Ach, mx: Int, my: Int) {
        val done = AchEngine.unlocked(a)
        val secret = secretHidden(a)
        val cw = 210
        val inner = (cw - 24).toFloat()
        val title = if (secret) "???" else a.name
        var desc = a.desc
        var descColor = ScreenTheme.SUBTEXT_COLOR
        when {
            secret -> { desc = "Secret achievement"; descColor = DIM }
            a.serverSide && !done -> { desc = a.desc + " (needs FishMod server support)"; descColor = DIM }
            !a.tracked && !done -> { desc = a.desc + " (not tracked yet)"; descColor = 0xFFB07A3A.toInt() }
        }
        val lines = wrap(desc, inner, S_SM)
        val frac = progress(a)
        val showProg = frac >= 0f && !done
        val ch = 10 + 18 + lines.size * 11 + 6 + (if (showProg) 20 else 0) + 12 + 10
        var cx = mx + 14
        var cy = my + 14
        if (cx + cw > vw() - 8) cx = mx - 14 - cw
        if (cy + ch > vh() - 8) cy = vh() - 8 - ch
        cx = max(8, cx); cy = max(8, cy)
        UiRecorder.dropShadow(cx.toFloat(), cy.toFloat(), cw.toFloat(), ch.toFloat(), 10f, 10f, 0x80000000.toInt())
        UiRecorder.roundedRectRing(cx.toFloat(), cy.toFloat(), cw.toFloat(), ch.toFloat(), 10f, 1f, 0xFF10141A.toInt(), if (done) ACCENT else LINE2)
        var ty = cy + 10
        val ap = "${a.points} AP"
        UiRecorder.textBold(clip(title, inner - tw(ap, S_SM) - 8, S_MD), (cx + 12).toFloat(), ty.toFloat(), S_MD, ScreenTheme.TEXT_COLOR)
        UiRecorder.text(ap, (cx + cw - 12 - tw(ap, S_SM)).toFloat(), ty + 0.5f, S_SM, GOLD)
        ty += 18
        for (l in lines) { UiRecorder.text(l, (cx + 12).toFloat(), ty.toFloat(), S_SM, descColor); ty += 11 }
        ty += 6
        if (showProg) {
            val v = min(AchEngine.value(a), a.goal)
            bar(cx + 12, ty, cw - 24, 4, frac, ACCENT)
            ty += 8
            UiRecorder.text("${AchEngine.fmt(a, v)} / ${AchEngine.fmt(a, a.goal)}", (cx + 12).toFloat(), ty.toFloat(), S_XS, ScreenTheme.SUBTEXT_COLOR)
            ty += 12
        }
        val foot = if (done) "Unlocked " + DATE.format(Date(AchStore.data.unlocked[a.id] ?: 0L)) else "Locked"
        UiRecorder.text(foot, (cx + 12).toFloat(), ty.toFloat(), S_XS, if (done) ACCENT else DIM)
    }

    // arc drawn as small discs, clockwise from top
    private fun ring(cx: Float, cy: Float, r: Float, frac: Float, color: Int, thick: Float) {
        val n = max(24, (r * 4).toInt())
        for (i in 0 until n) {
            val ang = -Math.PI / 2 + 2 * Math.PI * i / n
            UiRecorder.disc(cx + (Math.cos(ang) * r).toFloat(), cy + (Math.sin(ang) * r).toFloat(), thick / 2f, LINE)
        }
        if (frac <= 0f) return
        val f = frac.coerceIn(0f, 1f)
        val m = max(1, (n * f).toInt())
        for (i in 0..m) {
            val ang = -Math.PI / 2 + 2 * Math.PI * min(i.toFloat(), n * f) / n
            UiRecorder.disc(cx + (Math.cos(ang) * r).toFloat(), cy + (Math.sin(ang) * r).toFloat(), thick / 2f, color)
        }
    }

    private fun bar(x: Int, y: Int, w: Int, h: Int, frac: Float, color: Int) {
        if (w <= 0) return
        UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), h / 2f, LINE)
        val fw = (w * frac.coerceIn(0f, 1f)).toInt()
        if (fw > 0) UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), max(fw, h).toFloat(), h.toFloat(), h / 2f, color)
    }

    private fun tw(s: String, size: Float): Int = Math.ceil(UiRecorder.textWidth(s, size).toDouble()).toInt()

    private fun clip(s: String, maxW: Float, size: Float): String {
        if (UiRecorder.textWidth(s, size) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && UiRecorder.textWidth("$t...", size) > maxW) t = t.dropLast(1)
        return "$t..."
    }

    private fun centered(s: String, cx: Int, y: Int, h: Int, color: Int, size: Float) {
        UiRecorder.text(s, cx - UiRecorder.textWidth(s, size) / 2f, y + (h - size) / 2f, size, color)
    }

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean =
        mx >= x && mx <= x + w && my >= y && my <= y + h

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scroll = max(0, scroll - (verticalAmount * 40).toInt())
        return true
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        search.isFocused = false
        for ((r, fn) in clicks.asReversed()) {
            if (inside(mx, my, r[0], r[1], r[2], r[3])) { fn(); return true }
        }
        return super.mouseClicked(click, bl)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val key = input.key()
        if (search.isFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (search.value.isNotEmpty()) search.setValue("") else search.isFocused = false
                return true
            }
            search.keyPressed(input)
            return true
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        if (search.isFocused) { search.charTyped(input); return true }
        // Typing anywhere starts a search
        search.isFocused = true
        search.charTyped(input)
        return true
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }

    override fun paintUiOverlay() {
        fishmod.utils.rendering.UiRenderer.paint(this.width, this.height, UiScale.factor())
    }
}
