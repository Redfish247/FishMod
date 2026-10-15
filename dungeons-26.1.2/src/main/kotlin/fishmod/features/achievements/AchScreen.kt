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
        private const val FOOT_H = 32
        private const val ROW_H = 32
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

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mx = UiScale.vx(mouseX)
        val my = UiScale.vx(mouseY)
        clicks.clear()
        UiRecorder.clear()
        ScreenTheme.nRect(0, 0, vw(), vh(), 0x99000000.toInt())

        val w = min(640, vw() - 32)
        val h = min(400, vh() - 32)
        val x = (vw() - w) / 2
        val y = (vh() - h) / 2
        UiRecorder.dropShadow(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 12f, 16f, 0x50000000)
        ScreenTheme.nRoundedRect(x, y, w, h, 12, PANEL)
        UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 12f, 1f, 0, LINE)

        header(x, y)
        ScreenTheme.nRect(x, y + 44, w, 1, LINE)

        val footY = y + h - FOOT_H
        footer(x, footY, w, mx, my)

        val bx = x + 14
        var by = y + 44 + 10
        val bw = w - 28
        by = chips(bx, by, bw, mx, my) + 10
        val tileH = 40
        tiles(bx, by, bw, tileH)
        by += tileH + 10
        by = searchRow(bx, by, bw) + 8
        list(bx, by, bw, footY - 10 - by, mx, my)

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun header(x: Int, y: Int) {
        val gx = x + 14
        val gy = y + 11
        ScreenTheme.nRoundedRect(gx, gy, 22, 22, 6, ACC_SOFT)
        UiRecorder.textBold("★", gx + 11 - tw("★", S_LG) / 2f, gy + (22 - S_LG) / 2f, S_LG, ACCENT)
        UiRecorder.textBold("Achievements", (gx + 30).toFloat(), (gy + 1).toFloat(), S_LG, ScreenTheme.TEXT_COLOR)
        UiRecorder.text("Browse your progress and unlocks", (gx + 30).toFloat(), (gy + 13).toFloat(), S_XS, ScreenTheme.SUBTEXT_COLOR)
    }

    private fun footer(x: Int, y: Int, w: Int, mx: Int, my: Int) {
        UiRecorder.fillRoundedRect((x + 1).toFloat(), y.toFloat(), (w - 2).toFloat(), (FOOT_H - 1).toFloat(), 11f, PANEL2)
        UiRecorder.fillRect((x + 1).toFloat(), y.toFloat(), (w - 2).toFloat(), 12f, PANEL2)
        UiRecorder.fillRect(x.toFloat(), y.toFloat(), w.toFloat(), 1f, LINE)
        UiRecorder.text("/fm ach", (x + 14).toFloat(), y + (FOOT_H - S_XS) / 2f, S_XS, ScreenTheme.SUBTEXT_COLOR)
        val bw = 60
        val bh = 18
        val bx = x + w - 14 - bw
        val by = y + (FOOT_H - bh) / 2
        val hov = inside(mx, my, bx, by, bw, bh)
        UiRecorder.fillPillBar(bx.toFloat(), by.toFloat(), bw.toFloat(), bh.toFloat(), if (hov) ScreenTheme.ACCENT_HOVER else ACCENT)
        UiRecorder.textBold("Done", bx + (bw - tw("Done", S_SM)) / 2f, by + (bh - S_SM) / 2f, S_SM, ACC_INK)
        clicks.add(intArrayOf(bx, by, bw, bh) to { onClose() })
    }

    // group pills, wrapping; returns bottom y
    private fun chips(x: Int, y: Int, w: Int, mx: Int, my: Int): Int {
        val chipH = 14
        var cx = x
        var cy = y
        for (g in groups()) {
            val on = g == group
            val cnt = when (g) {
                RECENT -> "${AchStore.data.unlocked.size}"
                else -> inGroup(g).let { l -> "${l.count { AchEngine.unlocked(it) }}/${l.size}" }
            }
            val label = "$g  $cnt"
            val cw = (if (on) Math.ceil(UiRecorder.textWidthBold(label, S_XS).toDouble()).toInt() else tw(label, S_XS)) + 12
            if (cx + cw > x + w) { cx = x; cy += chipH + 3 }
            val hov = !on && inside(mx, my, cx, cy, cw, chipH)
            if (on) UiRecorder.fillPillBar(cx.toFloat(), cy.toFloat(), cw.toFloat(), chipH.toFloat(), ACCENT)
            else UiRecorder.roundedRectRing(cx.toFloat(), cy.toFloat(), cw.toFloat(), chipH.toFloat(), chipH / 2f, 1f, if (hov) RAISE else PANEL2, if (hov) ACCENT else LINE2)
            val fg = if (on) ACC_INK else if (hov) ScreenTheme.TEXT_COLOR else ScreenTheme.SUBTEXT_COLOR
            val ty = cy + (chipH - S_XS) / 2f
            if (on) UiRecorder.textBold(label, (cx + 6).toFloat(), ty, S_XS, fg) else UiRecorder.text(label, (cx + 6).toFloat(), ty, S_XS, fg)
            val gg = g
            clicks.add(intArrayOf(cx, cy, cw, chipH) to { group = gg; scroll = 0 })
            cx += cw + 3
        }
        return cy + chipH
    }

    private fun tiles(x: Int, y: Int, w: Int, h: Int) {
        val all = AchRegistry.ALL
        val pts = AchEngine.points()
        val maxPts = AchEngine.maxPoints()
        val done = all.count { AchEngine.unlocked(it) }
        val t4 = (w - 3 * 8) / 4
        tile(x, y, t4, h, "POINTS", "%,d".format(pts), GOLD, "of %,d".format(maxPts))
        tile(x + (t4 + 8), y, t4, h, "UNLOCKED", "$done", ScreenTheme.TEXT_COLOR, "of ${all.size}")
        val pct = if (all.isNotEmpty()) done * 100 / all.size else 0
        tile(x + (t4 + 8) * 2, y, t4, h, "COMPLETION", "$pct%", ScreenTheme.TEXT_COLOR, null)
        bar(x + (t4 + 8) * 2 + 10, y + h - 9, t4 - 20, 3, if (all.isNotEmpty()) done.toFloat() / all.size else 0f, ACCENT)
        tile(x + (t4 + 8) * 3, y, t4, h, "RECENT", "${AchStore.data.unlocked.size}", ScreenTheme.TEXT_COLOR, null)
    }

    private fun tile(x: Int, y: Int, w: Int, h: Int, label: String, value: String, color: Int, side: String?) {
        UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 8f, 1f, PANEL2, LINE)
        UiRecorder.textBold(label, (x + 10).toFloat(), (y + 7).toFloat(), S_XS, ScreenTheme.SUBTEXT_COLOR)
        if (side != null) UiRecorder.text(side, (x + w - 10 - tw(side, S_XS)).toFloat(), (y + 7).toFloat(), S_XS, DIM)
        UiRecorder.textBold(clip(value, (w - 20).toFloat(), S_VAL), (x + 10).toFloat(), y + 18f, S_VAL, color)
    }

    private fun searchRow(x: Int, y: Int, w: Int): Int {
        val sh = 20
        val sw = min(220, w / 2)
        val focused = search.isFocused
        UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), sw.toFloat(), sh.toFloat(), 6f, 1f, PANEL2, if (focused) ACCENT else LINE2)
        ScreenTheme.nTextFieldContent(search, focused, x + 5, y, sw - 10, sh, S_SM)
        if (search.value.isEmpty() && !focused) UiRecorder.text("Search achievements...", (x + 8).toFloat(), y + (sh - S_SM) / 2f, S_SM, DIM)
        clicks.add(intArrayOf(x, y, sw, sh) to { search.isFocused = true })
        UiRecorder.text("${shown().size} shown", (x + sw + 10).toFloat(), y + (sh - S_XS) / 2f, S_XS, ScreenTheme.SUBTEXT_COLOR)
        return y + sh
    }

    private fun list(x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
        val items = shown()
        contentH = items.size * ROW_H
        scroll = scroll.coerceIn(0, max(0, contentH - h))
        if (items.isEmpty()) {
            val msg = if (group == RECENT) "Nothing unlocked yet." else "No achievements match."
            UiRecorder.text(msg, x + (w - tw(msg, S_SM)) / 2f, (y + 24).toFloat(), S_SM, DIM)
            return
        }
        UiRecorder.pushScissor(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
        val rowW = if (contentH > h) w - 6 else w
        for (i in items.indices) {
            val ry = y + i * ROW_H - scroll
            if (ry + ROW_H < y || ry > y + h) continue
            row(items[i], x, ry, rowW, inside(mx, my, x, ry, rowW, ROW_H - 2) && my in y..(y + h))
        }
        UiRecorder.popScissor()
        if (contentH > h) {
            val barH = max(12, h * h / contentH)
            val barY = y + (h - barH) * scroll / max(1, contentH - h)
            UiRecorder.fillPillBar((x + w - 3).toFloat(), y.toFloat(), 3f, h.toFloat(), 0x22FFFFFF)
            UiRecorder.fillPillBar((x + w - 3).toFloat(), barY.toFloat(), 3f, barH.toFloat(), ACCENT)
        }
    }

    private fun row(a: Ach, x: Int, y: Int, w: Int, hov: Boolean) {
        val done = AchEngine.unlocked(a)
        val secret = secretHidden(a)
        if (hov) ScreenTheme.nRoundedRect(x, y, w, ROW_H - 2, 6, RAISE)
        val iy = y + (ROW_H - 2 - 20) / 2
        if (done) {
            ScreenTheme.nRoundedRect(x + 6, iy, 20, 20, 5, ACCENT)
            UiRecorder.textBold("✓", x + 16 - tw("✓", S_MD) / 2f, iy + (20 - S_MD) / 2f, S_MD, ACC_INK)
        } else {
            UiRecorder.roundedRectRing((x + 6).toFloat(), iy.toFloat(), 20f, 20f, 5f, 1f, LINE, 0x1FFFFFFF)
            val g = if (secret) "?" else (a.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?")
            UiRecorder.textBold(g, x + 16 - tw(g, S_XS) / 2f, iy + (20 - S_XS) / 2f, S_XS, DIM)
        }

        val ap = "${a.points} AP"
        val rightW = 90
        val rightX = x + w - 8
        UiRecorder.text(ap, (rightX - tw(ap, S_MD)).toFloat(), (y + 4).toFloat(), S_MD, if (done) GOLD else DIM)

        val nameX = x + 34
        val nameW = rightX - rightW - nameX
        val name = if (secret) "???" else a.name
        UiRecorder.text(clip(name, nameW.toFloat(), S_MD), nameX.toFloat(), (y + 3).toFloat(), S_MD, if (done) ScreenTheme.TEXT_COLOR else 0xFFB4BDC6.toInt())
        val nw = tw(name, S_MD)
        val tag = if (a.secret) "Secret" else a.section.takeIf { group == ALL || group == RECENT }
        if (tag != null && nw + 6 + tw(tag, S_XS) <= nameW) UiRecorder.text(tag, (nameX + nw + 6).toFloat(), y + 4.5f, S_XS, DIM)

        var sub = a.desc
        var subColor = ScreenTheme.SUBTEXT_COLOR
        var right: String? = null
        var rightColor = ScreenTheme.SUBTEXT_COLOR
        var frac = -1f
        when {
            done -> { right = "Unlocked " + DATE.format(Date(AchStore.data.unlocked[a.id] ?: 0L)); rightColor = ACCENT }
            secret -> { sub = "Secret achievement"; subColor = DIM }
            a.serverSide -> { sub = "Needs FishMod server support"; subColor = DIM }
            !a.tracked -> { sub = "Not tracked yet"; subColor = 0xFFB07A3A.toInt() }
            else -> if (!(a.metric.startsWith("meta.") || a.metric.startsWith("secret.") || a.goal <= 1)) {
                val v = min(AchEngine.value(a), a.goal)
                frac = v.toFloat() / a.goal
                right = "${AchEngine.fmt(a, v)} / ${AchEngine.fmt(a, a.goal)}"
            }
        }
        if (!done && !secret && frac < 0f && subColor == ScreenTheme.SUBTEXT_COLOR) subColor = DIM
        val textW = if (frac >= 0f) nameW else nameW + rightW - 8
        UiRecorder.text(clip(sub, textW.toFloat(), S_XS), nameX.toFloat(), (y + 15).toFloat(), S_XS, subColor)
        if (right != null) UiRecorder.text(right, (rightX - tw(right, S_XS)).toFloat(), (y + 17).toFloat(), S_XS, rightColor)
        if (frac >= 0f) {
            UiRecorder.fillRoundedRect(nameX.toFloat(), (y + 25).toFloat(), nameW.toFloat(), 3f, 1.5f, LINE)
            if (frac > 0f) UiRecorder.fillRoundedRect(nameX.toFloat(), (y + 25).toFloat(), max(2f, nameW * frac), 3f, 1.5f, ACCENT)
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
        scroll = max(0, scroll - (verticalAmount * ROW_H).toInt())
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
