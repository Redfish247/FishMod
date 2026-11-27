package fishmod.features

import fishmod.utils.debug.BoardReport
import fishmod.utils.rendering.UiRecorder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import fishmod.compat.GLFW

class ReportPage(private val font: Font) {

    companion object {
        private const val OK = 0xFF5FD18A.toInt()
        private const val FIELD_H = 26
    }

    private var bug = true
    private var title: EditBox? = null
    private var details: EditBox? = null
    var focused = -1
    @Volatile private var status = ""
    @Volatile private var statusOk = false
    @Volatile private var sending = false

    private val rects = HashMap<String, IntArray>()

    private fun field(old: EditBox?, max: Int, x: Int, y: Int, w: Int): EditBox {
        val f = old ?: EditBox(font, x, y, w, FIELD_H, Component.empty()).also { it.setMaxLength(max); it.setBordered(false) }
        f.setX(x); f.setY(y); f.setWidth(w)
        return f
    }

    fun render(x: Int, y0: Int, w: Int, h: Int, mx: Int, my: Int) {
        rects.clear()
        val inner = w
        val bottom = y0 + h - 6
        var y = y0
        UiRecorder.text("Tell RedFish what broke or what you'd like added.", x.toFloat(), y.toFloat(), 10f, ScreenTheme.SUBTEXT_COLOR)
        y += 20
        val half = (inner - 8) / 2
        pill("bug", "Bug", x, y, half, 28, bug, mx, my)
        pill("feat", "Feature Request", x + half + 8, y, half, 28, !bug, mx, my)
        y += 42
        UiRecorder.text("Title", x.toFloat(), y.toFloat(), 9.6f, ScreenTheme.TEXT_COLOR)
        y += 15
        title = field(title, 80, x, y, inner).also { box("title", it, x, y, inner, FIELD_H + 6, focused == 0, if (bug) "What broke?" else "What should it do?") }
        y += FIELD_H + 6 + 16
        UiRecorder.text("Details", x.toFloat(), y.toFloat(), 9.6f, ScreenTheme.TEXT_COLOR)
        y += 15
        val dh = Math.max(FIELD_H + 6, bottom - 34 - y)
        details = field(details, 500, x, y, inner).also { box("details", it, x, y, inner, dh, focused == 1, if (bug) "Where, when, what you expected" else "Any details") }
        y += dh + 10
        UiRecorder.text(if (bug) "Sends mod/MC version, area and recent FishMod errors. No name or IP." else "Sends mod/MC version and area. No name or IP.", x.toFloat(), (y + 2).toFloat(), 8.8f, ScreenTheme.SUBTEXT_COLOR)
        if (status.isNotEmpty()) UiRecorder.text(status, x.toFloat(), (y + 15).toFloat(), 9f, if (statusOk) OK else ScreenTheme.DANGER)
        pill("send", if (sending) "Sending…" else "Send", x + inner - 110, y - 2, 110, 28, true, mx, my)
    }

    private fun box(id: String, f: EditBox, x: Int, y: Int, w: Int, bh: Int, active: Boolean, hint: String) {
        f.setFocused(active)
        ScreenTheme.nRoundedRectRing(x, y, w, bh, 7, 1, ScreenTheme.ink(0x0C), if (active) ScreenTheme.ACCENT else ScreenTheme.ink(0x33))
        val cy = if (bh > FIELD_H + 6) y + 3 else y + (bh - FIELD_H) / 2
        if (f.value.isEmpty() && !active) UiRecorder.text(hint, (x + 6).toFloat(), cy + (FIELD_H - 10f) / 2f, 10f, ScreenTheme.SUBTEXT_COLOR)
        else FishModScreen.nvgTextFieldContent(f, x + 2, cy, w - 2, FIELD_H, 10f)
        rects[id] = intArrayOf(x, y, w, bh)
    }

    private fun pill(id: String, label: String, x: Int, y: Int, w: Int, h: Int, filled: Boolean, mx: Int, my: Int) {
        val hov = inside(mx, my, intArrayOf(x, y, w, h))
        val bg = if (hov) ScreenTheme.ACCENT_HOVER else ScreenTheme.ACCENT
        if (filled) ScreenTheme.nRoundedRect(x, y, w, h, h / 2, bg)
        else ScreenTheme.nRoundedRectRing(x, y, w, h, h / 2, 1, ScreenTheme.ink(0x0C), if (hov) ScreenTheme.ACCENT_HOVER else ScreenTheme.ink(0x33))
        val s = 10f
        UiRecorder.text(label, x + w / 2f - UiRecorder.textWidth(label, s) / 2f, y + (h - s) / 2f, s, if (filled) ScreenTheme.textOn(bg) else ScreenTheme.TEXT_COLOR)
        rects[id] = intArrayOf(x, y, w, h)
    }

    private fun inside(mx: Int, my: Int, r: IntArray?): Boolean =
        r != null && mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]

    private fun send() {
        if (sending) return
        sending = true
        status = ""
        BoardReport.submit(if (bug) "bug" else "suggest", title?.value ?: "", details?.value ?: "") { ok, msg ->
            sending = false
            statusOk = ok
            status = if (ok) "Sent, thanks!" else "Not sent: $msg"
            if (ok) Minecraft.getInstance().execute { title?.value = ""; details?.value = "" }
        }
    }

    fun click(mx: Int, my: Int): Boolean {
        when {
            inside(mx, my, rects["bug"]) -> bug = true
            inside(mx, my, rects["feat"]) -> bug = false
            inside(mx, my, rects["title"]) -> focused = 0
            inside(mx, my, rects["details"]) -> focused = 1
            inside(mx, my, rects["send"]) -> send()
            else -> { focused = -1; return false }
        }
        return true
    }

    fun key(input: KeyEvent): Boolean {
        if (focused < 0 || input.key() == GLFW.GLFW_KEY_ESCAPE) return false
        if (input.key() == GLFW.GLFW_KEY_TAB) { focused = 1 - focused; return true }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (focused == 0) focused = 1 else send()
            return true
        }
        (if (focused == 0) title else details)?.keyPressed(input)
        return true
    }

    fun char(input: CharacterEvent): Boolean {
        if (focused < 0) return false
        (if (focused == 0) title else details)?.charTyped(input)
        return true
    }
}
