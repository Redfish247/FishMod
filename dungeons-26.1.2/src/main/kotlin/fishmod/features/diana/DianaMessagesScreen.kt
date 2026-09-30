package fishmod.features.diana

import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.config.FishConfig
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiRenderer
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
import kotlin.reflect.KMutableProperty0
import kotlin.math.max
import kotlin.math.min

// Popup editor for Diana drop announcements and rare-mob spawn party text, with live coloured previews
class DianaMessagesScreen(private val parent: Screen?) : Screen(Component.literal("Diana Messages")), HasUiOverlay {

    companion object {
        fun open() {
            val mc = Minecraft.getInstance()
            val parent = mc.screen
            mc.schedule { mc.setScreen(DianaMessagesScreen(parent)) }
        }

        private val ACCENT = ScreenTheme.ACCENT
        private val TEXT = ScreenTheme.TEXT_COLOR
        private val SUB = ScreenTheme.SUBTEXT_COLOR
        private val CARD_BG = ScreenTheme.CARD_BG
        private val BG_PANEL = 0xF20E1016.toInt()
        private val BG_SECTION = 0xFF171A22.toInt()
        private val BORDER = 0xFF2A2D38.toInt()
        private val FIELD_BG = 0xFF1A1E26.toInt()
        private val FIELD_BORDER = 0xFF2E333D.toInt()
        private val ROW_HOVER = 0xFF1F232D.toInt()
        private val DIM = 0xFF5C6673.toInt()
        private val DARK_TEXT = 0xFF06110F.toInt()
        private val PREVIEW_BG = 0xCC000000.toInt()

        private const val HEADER_H = 44
        private const val FOOTER_H = 32
        private const val ROW_H = 50

        // Minecraft's 16 chat colours, index = hex digit
        private val MC_COLORS = intArrayOf(
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
        )
        private const val HEX = "0123456789abcdef"

        private val DROP_TAGS = listOf("{mf}", "{amount}", "{percentage}", "{price}", "{since}", "{lstext}")
        private val SPAWN_TAGS = listOf("{since}", "{chance}")
    }

    private class Entry(val name: String, val prop: KMutableProperty0<String>, val default: String, val drop: Boolean, val field: EditBox)
    private class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val action: () -> Unit) {
        fun contains(mx: Int, my: Int) = mx >= x && mx < x + w && my >= y && my < y + h
    }

    private val drops = ArrayList<Entry>()
    private val spawns = ArrayList<Entry>()
    private var loaded = false
    private var saved = false

    private var px = 0; private var py = 0; private var pw = 0; private var ph = 0
    private var mx = 0; private var my = 0
    private var scroll = 0; private var maxScroll = 0
    private var body = IntArray(4)
    private val hits = ArrayList<Hit>()
    private var clip: IntArray? = null
    private var focused: EditBox? = null
    private var lastField: EditBox? = null

    override fun init() {
        if (!loaded) {
            val props = mapOf(
                "CHIMERA" to DianaSettings::dianaMsgChimera, "MANTI_CORE" to DianaSettings::dianaMsgCore,
                "FATEFUL_STINGER" to DianaSettings::dianaMsgStinger, "BRAIN_FOOD" to DianaSettings::dianaMsgFood,
                "SHIMMERING_WOOL" to DianaSettings::dianaMsgWool,
            )
            for (k in DianaTracker.EDITABLE) {
                val d = DianaTracker.drop(k)
                val p = props.getValue(k)
                val def = DianaTracker.defaultMsg(d)
                drops += Entry(d.name, p, def, true, mkField(p.get().ifBlank { def }))
            }
            for ((n, p) in listOf(
                "Inquisitor" to DianaSettings::dianaInqSpawnText, "King Minos" to DianaSettings::dianaKingSpawnText,
                "Manticore" to DianaSettings::dianaMantiSpawnText, "Sphinx" to DianaSettings::dianaSphinxSpawnText,
            )) spawns += Entry(n, p, "", false, mkField(p.get()))
            loaded = true
        }
        val f = UiScale.factor()
        val vw = (width / f).toInt(); val vh = (height / f).toInt()
        pw = min(600, vw - 16); ph = min(460, vh - 16)
        px = (vw - pw) / 2; py = max(8, (vh - ph) / 2)
    }

    private fun mkField(v: String) = EditBox(font, 0, 0, 100, 18, Component.literal("")).also {
        it.setMaxLength(256); it.setBordered(false); it.setValue(v)
    }

    private fun save() {
        if (saved) return
        saved = true
        // Drops: storing blank keeps following the built-in default
        for (e in drops) e.prop.set(if (e.field.value == e.default) "" else e.field.value)
        for (e in spawns) e.prop.set(e.field.value.trim())
        FishConfig.manager.save()
    }

    override fun onClose() {
        save()
        minecraft.setScreen(parent)
    }

    override fun removed() {
        save()
        super.removed()
    }

    override fun isPauseScreen(): Boolean = false

    override fun paintUiOverlay() {
        UiRenderer.paint(width, height, UiScale.factor())
    }

    // ---- drawing ----

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        mx = UiScale.vx(mouseX); my = UiScale.vx(mouseY)
        UiRecorder.clear()
        hits.clear()
        drawFrame()
        drawBody()
        super.extractRenderState(ctx, mx, my, delta)
    }

    private fun drawFrame() {
        UiRecorder.dropShadow(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), 10f, 16f, 0x70000000)
        ScreenTheme.nPanel(px, py, px + pw, py + ph, 10, BG_PANEL, BORDER)
        UiRecorder.fillRectTopRounded(px + 1f, py + 1f, pw - 2f, HEADER_H - 1f, 9f, BG_SECTION)
        ScreenTheme.nRect(px, py + HEADER_H, pw, 1, BORDER)
        UiRecorder.textBold("Diana Messages", px + 14f, py + 11f, 10f, TEXT)
        text("Click a box and type. Click a colour or tag to insert it where your cursor is.", px + 14f, py + 26f, 7f, SUB)

        val fy = py + ph - FOOTER_H
        ScreenTheme.nRect(px, fy, pw, 1, BORDER)
        text("Saved when you press Done", px + 14f, fy + 13f, 7f, DIM)
        button("Done", px + pw - 14 - 72, fy + 6, 72, 20, primary = true) { onClose() }
    }

    private fun drawBody() {
        val bx = px + 14; val bw = pw - 28
        val top = py + HEADER_H + 8
        val bh = py + ph - FOOTER_H - 6 - top

        // Colour palette stays pinned above the scrolling list
        text("COLOURS", bx.toFloat(), top + 4f, 7f, ACCENT)
        var sx = bx + 50
        for (i in 0 until 16) {
            val c = 0xFF000000.toInt() or MC_COLORS[i]
            val hov = inside(sx, top, 14, 14)
            ScreenTheme.nRoundedRectRing(sx, top, 14, 14, 3, 1, c, if (hov) ACCENT else FIELD_BORDER)
            val code = "&" + HEX[i]
            hit(sx, top, 14, 14) { insert(code) }
            sx += 17
        }
        sx += 6
        sx += chipButton("Bold", sx, top + 1) { insert("&l") } + 4
        chipButton("Reset style", sx, top + 1) { insert("&r") }

        val ly = top + 22
        val lh = bh - 22
        body = intArrayOf(bx, ly, bw, lh)
        scroll = scroll.coerceIn(0, maxScroll)
        pushClip(bx, ly, bw, lh)
        var y = ly - scroll

        y = section("RARE DROP MESSAGES", "Empty = default. Sent to your chat and, with Loot Party Message on, to party chat.", DROP_TAGS, bx, y, bw)
        for (e in drops) { drawEntry(e, bx, y, bw); y += ROW_H }
        y += 8
        y = section("PARTY TEXT WHEN YOU DIG A RARE MOB", "Empty = off. Sent to party chat 5 seconds after the mob spawns.", SPAWN_TAGS, bx, y, bw)
        for (e in spawns) { drawEntry(e, bx, y, bw); y += ROW_H }
        popClip()
        maxScroll = max(0, (y + scroll) - (ly + lh))
    }

    private fun section(title: String, note: String, tags: List<String>, x: Int, y0: Int, w: Int): Int {
        var y = y0
        text(title, x.toFloat(), y.toFloat(), 7f, ACCENT)
        text(note, x.toFloat(), y + 11f, 7f, SUB)
        y += 24
        var cx = x
        text("Tags:", cx.toFloat(), y + 3f, 7f, DIM)
        cx += 28
        for (t in tags) cx += chipButton(t, cx, y) { insert(t) } + 4
        return y + 20
    }

    private fun drawEntry(e: Entry, x: Int, y: Int, w: Int) {
        text(e.name, x.toFloat(), y.toFloat(), 7.5f, TEXT)
        val resetW = 44
        field(e.field, x, y + 10, w - resetW - 6, 18, if (e.drop) "Default message" else "Off")
        button(if (e.drop) "Default" else "Clear", x + w - resetW, y + 10, resetW, 18) {
            e.field.setValue(e.default); setFocus(e.field)
        }
        val raw = e.field.value
        val preview = when {
            e.drop -> DianaTracker.fillTemplate(raw.ifBlank { e.default }, 250, false, 3, 1.23, 12_500_000.0, 87)
            raw.isBlank() -> ""
            else -> raw.replace("{since}", "120").replace("{chance}", "0.85").replace('&', '§')
        }
        ScreenTheme.nRoundedRect(x, y + 31, w, 13, 3, PREVIEW_BG)
        if (preview.isEmpty()) text("(off)", x + 5f, y + 34f, 7f, DIM)
        else colored(preview, x + 5f, y + 34f, 7f, w - 10)
    }

    // Draws §-formatted text; colours and bold only
    private fun colored(s: String, x0: Float, y: Float, size: Float, maxW: Int) {
        var x = x0
        var color = 0xFFFFFFFF.toInt()
        var bold = false
        val seg = StringBuilder()
        fun flush() {
            if (seg.isEmpty()) return
            val t = seg.toString()
            val w = if (bold) UiRecorder.textWidthBold(t, size) else tw(t, size)
            if (x - x0 + w > maxW) { seg.clear(); return }
            if (bold) UiRecorder.textBold(t, x, y, size, color) else text(t, x, y, size, color)
            x += w
            seg.clear()
        }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '§' && i + 1 < s.length) {
                flush()
                val k = s[i + 1].lowercaseChar()
                val idx = HEX.indexOf(k)
                when {
                    idx >= 0 -> { color = 0xFF000000.toInt() or MC_COLORS[idx]; bold = false }
                    k == 'l' -> bold = true
                    k == 'r' -> { color = 0xFFFFFFFF.toInt(); bold = false }
                }
                i += 2
                continue
            }
            seg.append(c); i++
        }
        flush()
    }

    private fun insert(s: String) {
        val f = lastField ?: return
        setFocus(f)
        f.insertText(s)
    }

    // ---- widgets ----

    private fun text(s: String, x: Float, y: Float, size: Float, color: Int) = UiRecorder.text(s, x, y, size, color)
    private fun tw(s: String, size: Float): Float = UiRecorder.textWidth(s, size)

    private fun inside(x: Int, y: Int, w: Int, h: Int): Boolean {
        val c = clip
        if (c != null && (mx < c[0] || my < c[1] || mx >= c[0] + c[2] || my >= c[1] + c[3])) return false
        return mx >= x && mx < x + w && my >= y && my < y + h
    }

    private fun hit(x: Int, y: Int, w: Int, h: Int, action: () -> Unit) {
        var x0 = x; var y0 = y; var x1 = x + w; var y1 = y + h
        clip?.let { c -> x0 = max(x0, c[0]); y0 = max(y0, c[1]); x1 = min(x1, c[0] + c[2]); y1 = min(y1, c[1] + c[3]) }
        if (x1 <= x0 || y1 <= y0) return
        hits.add(Hit(x0, y0, x1 - x0, y1 - y0, action))
    }

    private fun pushClip(x: Int, y: Int, w: Int, h: Int) {
        clip = intArrayOf(x, y, w, h)
        UiRecorder.pushScissor(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
    }

    private fun popClip() {
        clip = null
        UiRecorder.popScissor()
    }

    private fun setFocus(f: EditBox?) {
        focused?.isFocused = false
        focused = f
        f?.isFocused = true
        if (f != null) lastField = f
    }

    private fun field(f: EditBox, x: Int, y: Int, w: Int, h: Int, placeholder: String) {
        val foc = focused === f
        ScreenTheme.nRoundedRectRing(x, y, w, h, 4, 1, FIELD_BG, if (foc) ACCENT else FIELD_BORDER)
        if (f.value.isEmpty() && !foc) text(placeholder, x + 4f, y + (h - 7.5f) / 2f, 7.5f, DIM)
        else ScreenTheme.nTextFieldContent(f, foc, x, y, w, h, 8f)
        hit(x, y, w, h) { setFocus(f) }
    }

    private fun chipButton(label: String, x: Int, y: Int, action: () -> Unit): Int {
        val w = tw(label, 7f).toInt() + 10
        val hov = inside(x, y, w, 13)
        ScreenTheme.nRoundedRectRing(x, y, w, 13, 4, 1, if (hov) ROW_HOVER else CARD_BG, if (hov) ACCENT else FIELD_BORDER)
        text(label, x + 5f, y + 3f, 7f, if (hov) TEXT else SUB)
        hit(x, y, w, 13, action)
        return w
    }

    private fun button(label: String, x: Int, y: Int, w: Int, h: Int, primary: Boolean = false, action: () -> Unit) {
        val hov = inside(x, y, w, h)
        if (primary) ScreenTheme.nRoundedRect(x, y, w, h, h / 2, if (hov) ScreenTheme.ACCENT_HOVER else ACCENT)
        else ScreenTheme.nRoundedRectRing(x, y, w, h, 5, 1, FIELD_BG, if (hov) ACCENT else FIELD_BORDER)
        text(label, x + (w - tw(label, 7.5f)) / 2f, y + (h - 7.5f) / 2f, 7.5f, if (primary) DARK_TEXT else TEXT)
        hit(x, y, w, h, action)
    }

    // ---- input ----

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val x = UiScale.vx(click.x()); val y = UiScale.vx(click.y())
        val h = hits.asReversed().firstOrNull { it.contains(x, y) }
        if (h == null) { setFocus(null); return super.mouseClicked(click, doubled) }
        focused?.isFocused = false
        focused = null
        h.action()
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scroll = (scroll - (verticalAmount * 18).toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val f = focused
        if (f != null) {
            val k = input.key()
            if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { setFocus(null); return true }
            f.keyPressed(input)
            return true
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        val f = focused ?: return super.charTyped(input)
        f.charTyped(input)
        return true
    }
}
