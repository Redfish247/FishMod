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
        private const val BG_TOP = 0xFF11181F.toInt()
        private const val BG_BOT = 0xFF070B0F.toInt()
        private const val BORDER = 0xFF262F3A.toInt()
        private const val CARD_BG = 0xFF141B22.toInt()
        private const val CARD_DONE = 0xFF15231F.toInt()
        private const val CARD_BORDER = 0xFF232D38.toInt()
        private const val ROW_HOVER = 0xFF1A222B.toInt()
        private const val BAR_BG = 0xFF0C1116.toInt()
        private const val GOLD = 0xFFE0B040.toInt()
        private const val DIM = 0xFF56616D.toInt()
        private const val SCRIM = 0xB3000000.toInt()
        private const val ON_ACCENT = 0xFF052A29.toInt()
        private const val ALL = "All"
        private const val RECENT = "Recent"
        private const val PAD = 12
        private const val HEADER_H = 52
        private const val SIDE_W = 118
        private const val GROUP_H = 16
        private const val CARD_H = 38
        private const val GAP = 4
        private val DATE = SimpleDateFormat("MMM d, yyyy")
    }

    private lateinit var search: EditBox
    private var group = ALL
    private var scroll = 0
    private var sideScroll = 0
    private var listY = 0
    private var listH = 0
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
        ScreenTheme.nRect(0, 0, vw(), vh(), SCRIM)

        val w = min(640, vw() - 20)
        val h = min(400, vh() - 20)
        val x = (vw() - w) / 2
        val y = (vh() - h) / 2
        UiRecorder.dropShadow(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 10f, 16f, 0x70000000)
        UiRecorder.fillRectVGradient(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), BG_TOP, BG_BOT, 10f)
        ScreenTheme.nRoundedRectRing(x, y, w, h, 10, 1, 0, BORDER)

        header(x, y, w, mx, my)
        ScreenTheme.nRect(x + PAD, y + HEADER_H, w - PAD * 2, 1, BORDER)

        val bodyY = y + HEADER_H + 8
        val bodyH = y + h - PAD - bodyY
        sidebar(x + PAD, bodyY, bodyH, mx, my)
        list(x + PAD + SIDE_W + 8, bodyY, w - PAD * 2 - SIDE_W - 8, bodyH, mx, my)

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun header(x: Int, y: Int, w: Int, mx: Int, my: Int) {
        val all = AchRegistry.ALL
        val pts = AchEngine.points()
        val maxPts = AchEngine.maxPoints()
        val done = all.count { AchEngine.unlocked(it) }
        UiRecorder.textBold("Achievements", (x + PAD).toFloat(), (y + 11).toFloat(), 10f, ScreenTheme.TEXT_COLOR)
        UiRecorder.textBold("$pts / $maxPts AP", (x + PAD).toFloat(), (y + 26).toFloat(), 7.5f, GOLD)
        val apW = UiRecorder.textWidth("$pts / $maxPts AP", 7.5f).toInt()
        UiRecorder.text("$done / ${all.size} unlocked", (x + PAD + apW + 10).toFloat(), (y + 26.5f), 7f, ScreenTheme.SUBTEXT_COLOR)
        bar(x + PAD, y + 38, 220, 5, if (maxPts > 0) pts.toFloat() / maxPts else 0f, GOLD)

        // Close button
        val bw = 50; val bh = 18
        val bx = x + w - PAD - bw; val by = y + 10
        val hov = inside(mx, my, bx, by, bw, bh)
        ScreenTheme.nRoundedRect(bx, by, bw, bh, bh / 2, if (hov) ScreenTheme.ACCENT_HOVER else ScreenTheme.ACCENT)
        centered("Close", bx + bw / 2, by, bh, ON_ACCENT, 7.5f)
        clicks.add(intArrayOf(bx, by, bw, bh) to { onClose() })

        // Search box
        val sw = 150; val sh = 16
        val sx = bx - 8 - sw; val sy = by + 1
        val focused = search.isFocused
        ScreenTheme.nRoundedRectRing(sx, sy, sw, sh, 4, 1, BAR_BG, if (focused) ScreenTheme.ACCENT else BORDER)
        ScreenTheme.nTextFieldContent(search, focused, sx + 4, sy, sw - 8, sh, 7f)
        if (search.value.isEmpty() && !focused) UiRecorder.text("Search...", (sx + 7).toFloat(), sy + 4.5f, 7f, DIM)
        clicks.add(intArrayOf(sx, sy, sw, sh) to { search.isFocused = true })
    }

    private fun sidebar(x: Int, y: Int, h: Int, mx: Int, my: Int) {
        ScreenTheme.nRoundedRectRing(x, y, SIDE_W, h, 8, 1, CARD_BG, CARD_BORDER)
        val gs = groups()
        val total = gs.size * GROUP_H + 8
        sideScroll = sideScroll.coerceIn(0, max(0, total - h))
        UiRecorder.pushScissor(x.toFloat(), (y + 1).toFloat(), SIDE_W.toFloat(), (h - 2).toFloat())
        var ry = y + 4 - sideScroll
        for (g in gs) {
            val sel = g == group
            val rx = x + 4; val rw = SIDE_W - 8
            val vis = ry + GROUP_H > y && ry < y + h
            if (vis) {
                val hov = inside(mx, my, rx, ry, rw, GROUP_H) && my in y until y + h
                if (sel) ScreenTheme.nRoundedRect(rx, ry, rw, GROUP_H, 4, 0x3324B6B0)
                else if (hov) ScreenTheme.nRoundedRect(rx, ry, rw, GROUP_H, 4, ROW_HOVER)
                if (sel) ScreenTheme.nRoundedRect(rx, ry + 3, 2, GROUP_H - 6, 1, ScreenTheme.ACCENT)
                UiRecorder.text(g, (rx + 7).toFloat(), ry + 4.5f, 7f, if (sel) ScreenTheme.TEXT_COLOR else ScreenTheme.SUBTEXT_COLOR)
                val cnt = when (g) {
                    RECENT -> "${AchStore.data.unlocked.size}"
                    else -> inGroup(g).let { l -> "${l.count { AchEngine.unlocked(it) }}/${l.size}" }
                }
                val cw = UiRecorder.textWidth(cnt, 6.2f)
                UiRecorder.text(cnt, rx + rw - 5 - cw, ry + 5f, 6.2f, DIM)
                if (my in y until y + h) {
                    val gg = g
                    clicks.add(intArrayOf(rx, ry, rw, GROUP_H) to { group = gg; scroll = 0 })
                }
            }
            ry += GROUP_H
        }
        UiRecorder.popScissor()
    }

    private fun list(x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
        val items = shown()
        listY = y; listH = h
        contentH = items.size * (CARD_H + GAP) - GAP
        scroll = scroll.coerceIn(0, max(0, contentH - h))
        if (items.isEmpty()) {
            centered(if (group == RECENT) "Nothing unlocked yet" else "No matches", x + w / 2, y + 20, 10, DIM, 7.5f)
            return
        }
        UiRecorder.pushScissor(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
        val cw = w - 8
        var cy = y - scroll
        for (a in items) {
            if (cy + CARD_H > y && cy < y + h) card(a, x, cy, cw)
            cy += CARD_H + GAP
        }
        UiRecorder.popScissor()
        if (contentH > h) {
            val trackH = h - 4
            val thumbH = max(16, trackH * h / contentH)
            val thumbY = y + 2 + (trackH - thumbH) * scroll / (contentH - h)
            ScreenTheme.nRoundedRect(x + w - 3, thumbY, 3, thumbH, 1, 0x60FFFFFF)
        }
    }

    private fun card(a: Ach, x: Int, y: Int, w: Int) {
        val done = AchEngine.unlocked(a)
        val secret = secretHidden(a)
        ScreenTheme.nRoundedRectRing(x, y, w, CARD_H, 6, 1, if (done) CARD_DONE else CARD_BG, if (done) 0x5924B6B0 else CARD_BORDER)
        if (done) ScreenTheme.nRoundedRect(x + 1, y + 6, 2, CARD_H - 12, 1, ScreenTheme.ACCENT)

        // AP badge
        val ap = "${a.points} AP"
        val apW = UiRecorder.textWidth(ap, 6.5f).toInt() + 10
        val apX = x + w - 8 - apW
        ScreenTheme.nRoundedRect(apX, y + 6, apW, 12, 6, if (done) 0x33E0B040 else 0x18FFFFFF)
        centered(ap, apX + apW / 2, y + 6, 12, if (done) GOLD else DIM, 6.5f)

        val nameColor = if (done) ScreenTheme.TEXT_COLOR else 0xFFB4BDC6.toInt()
        val name = (if (secret) "???" else a.name)
        UiRecorder.textBold(name, (x + 9).toFloat(), (y + 7).toFloat(), 7.5f, nameColor)
        val nw = UiRecorder.textWidth(name, 7.5f)
        val tag = when {
            a.secret -> "Secret"
            else -> a.section.takeIf { group == ALL || group == RECENT }
        }
        if (tag != null) UiRecorder.text(tag, x + 9 + nw + 6, y + 8f, 6f, DIM)

        val desc = if (secret) "Secret achievement" else a.desc
        UiRecorder.text(clip(desc, (apX - x - 16).toFloat(), 6.5f), (x + 9).toFloat(), (y + 18).toFloat(), 6.5f,
            if (done) ScreenTheme.SUBTEXT_COLOR else DIM)

        val ly = y + 28
        when {
            done -> {
                val t = AchStore.data.unlocked[a.id] ?: 0L
                UiRecorder.text("Unlocked " + DATE.format(Date(t)), (x + 9).toFloat(), ly.toFloat(), 6f, ScreenTheme.ACCENT)
            }
            secret -> {}
            a.serverSide -> UiRecorder.text("Needs FishMod server support", (x + 9).toFloat(), ly.toFloat(), 6f, DIM)
            !a.tracked -> UiRecorder.text("Not tracked yet", (x + 9).toFloat(), ly.toFloat(), 6f, 0xFFB07A3A.toInt())
            a.metric.startsWith("meta.") || a.metric.startsWith("secret.") || a.goal <= 1 -> {}
            else -> {
                val v = min(AchEngine.value(a), a.goal)
                val label = "${AchEngine.fmt(a, v)} / ${AchEngine.fmt(a, a.goal)}"
                val lw = UiRecorder.textWidth(label, 6f).toInt()
                val bw = min(160, w - 30 - lw)
                bar(x + 9, ly + 1, bw, 4, v.toFloat() / a.goal, ScreenTheme.ACCENT)
                UiRecorder.text(label, (x + 9 + bw + 6).toFloat(), (ly - 0.5f), 6f, ScreenTheme.SUBTEXT_COLOR)
            }
        }
    }

    private fun bar(x: Int, y: Int, w: Int, h: Int, frac: Float, color: Int) {
        if (w <= 0) return
        ScreenTheme.nRoundedRect(x, y, w, h, h / 2, BAR_BG)
        val fw = (w * frac.coerceIn(0f, 1f)).toInt()
        if (fw > 0) ScreenTheme.nRoundedRect(x, y, max(fw, h), h, h / 2, color)
    }

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
        val mx = UiScale.vx(mouseX)
        val step = (verticalAmount * 24).toInt()
        val sideRight = (vw() - min(640, vw() - 20)) / 2 + PAD + SIDE_W
        if (mx <= sideRight) sideScroll = max(0, sideScroll - step)
        else scroll = scroll - step
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
