package fishmod.features
import fishmod.compat.showScreen

import fishmod.utils.debug.BoardReport
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
import kotlin.math.min

// Bug report / feature request form, sent to the test board inbox.
class BoardReportScreen(private val parent: Screen?) : Screen(Component.literal("Report")), HasUiOverlay {

    companion object {
        private const val BG_TOP = 0xFF11181F.toInt()
        private const val BG_BOT = 0xFF070B0F.toInt()
        private const val BORDER = 0xFF262F3A.toInt()
        private const val FIELD_BG = 0xFF14181D.toInt()
        private const val SCRIM = 0xB3000000.toInt()
        private const val ON_ACCENT = 0xFF052A29.toInt()
        private const val OK = 0xFF5FD18A.toInt()
        private const val PAD = 14
        private const val FIELD_H = 20
    }

    private var bug = true
    private var title: EditBox? = null
    private var details: EditBox? = null
    private var focused = 0
    @Volatile private var status = ""
    @Volatile private var statusOk = false
    @Volatile private var sending = false

    private val rects = HashMap<String, IntArray>()

    override fun isPauseScreen(): Boolean = false
    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    private fun vw(): Int = (this.width / UiScale.factor()).toInt()
    private fun vh(): Int = (this.height / UiScale.factor()).toInt()
    private fun pw(): Int = min(340, vw() - 20)
    private val ph = 206

    private fun field(old: EditBox?, max: Int, x: Int, y: Int, w: Int): EditBox {
        val f = old ?: EditBox(this.font, x, y, w, FIELD_H, Component.empty()).also { it.setMaxLength(max); it.setBordered(false) }
        f.setX(x); f.setY(y); f.setWidth(w)
        return f
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mx = UiScale.vx(mouseX)
        val my = UiScale.vx(mouseY)
        UiRecorder.clear()
        ScreenTheme.nRect(0, 0, vw(), vh(), SCRIM)
        rects.clear()

        val w = pw()
        val lx = (vw() - w) / 2
        val ty = (vh() - ph) / 2
        UiRecorder.dropShadow(lx.toFloat(), ty.toFloat(), w.toFloat(), ph.toFloat(), 10f, 16f, 0x70000000)
        UiRecorder.fillRectVGradient(lx.toFloat(), ty.toFloat(), w.toFloat(), ph.toFloat(), BG_TOP, BG_BOT, 10f)
        ScreenTheme.nRoundedRectRing(lx, ty, w, ph, 10, 1, 0, BORDER)
        UiRecorder.textBold("Report to RedFish", (lx + PAD).toFloat(), (ty + 14).toFloat(), 10f, ScreenTheme.TEXT_COLOR)
        pill("back", "Back", lx + w - PAD - 56, ty + 11, 56, 18, true, mx, my)
        ScreenTheme.nRect(lx + PAD, ty + 38, w - PAD * 2, 1, BORDER)

        val inner = w - PAD * 2
        var y = ty + 48
        val half = (inner - 6) / 2
        pill("bug", "Bug", lx + PAD, y, half, 18, bug, mx, my)
        pill("feat", "Feature Request", lx + PAD + half + 6, y, half, 18, !bug, mx, my)
        y += 28

        UiRecorder.text("Title", (lx + PAD).toFloat(), y.toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        y += 10
        title = field(title, 80, lx + PAD, y, inner).also { box("title", it, lx + PAD, y, inner, focused == 0, if (bug) "What broke?" else "What should it do?") }
        y += FIELD_H + 8
        UiRecorder.text("Details", (lx + PAD).toFloat(), y.toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        y += 10
        details = field(details, 500, lx + PAD, y, inner).also { box("details", it, lx + PAD, y, inner, focused == 1, if (bug) "Where, when, what you expected" else "Any details") }
        y += FIELD_H + 10

        UiRecorder.text(if (bug) "Sends mod/MC version, area and recent FishMod errors. No name or IP." else "Sends mod/MC version and area. No name or IP.", (lx + PAD).toFloat(), y.toFloat(), 5.8f, ScreenTheme.SUBTEXT_COLOR)
        y += 14
        if (status.isNotEmpty()) UiRecorder.text(status, (lx + PAD).toFloat(), (y + 5).toFloat(), 6.5f, if (statusOk) OK else ScreenTheme.DANGER)
        pill("send", if (sending) "Sending…" else "Send", lx + w - PAD - 70, y, 70, 18, true, mx, my)

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun box(id: String, f: EditBox, x: Int, y: Int, w: Int, active: Boolean, hint: String) {
        f.setFocused(active)
        ScreenTheme.nRoundedRectRing(x, y, w, FIELD_H, 6, 1, FIELD_BG, if (active) ScreenTheme.ACCENT else 0xFF3A3F48.toInt())
        if (f.value.isEmpty() && !active) UiRecorder.text(hint, (x + 3).toFloat(), y + (FIELD_H - 7f) / 2f, 7f, ScreenTheme.SUBTEXT_COLOR)
        else FishModScreen.nvgTextFieldContent(f, x, y, w, FIELD_H)
        rects[id] = intArrayOf(x, y, w, FIELD_H)
    }

    private fun pill(id: String, label: String, x: Int, y: Int, w: Int, h: Int, filled: Boolean, mx: Int, my: Int) {
        val hov = inside(mx, my, intArrayOf(x, y, w, h))
        if (filled) ScreenTheme.nRoundedRect(x, y, w, h, h / 2, if (hov) ScreenTheme.ACCENT_HOVER else ScreenTheme.ACCENT)
        else ScreenTheme.nRoundedRectRing(x, y, w, h, h / 2, 1, 0xFF171C21.toInt(), if (hov) ScreenTheme.ACCENT_HOVER else BORDER)
        val s = 7.5f
        UiRecorder.text(label, x + w / 2f - UiRecorder.textWidth(label, s) / 2f, y + (h - s) / 2f, s, if (filled) ON_ACCENT else ScreenTheme.TEXT_COLOR)
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

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        when {
            inside(mx, my, rects["back"]) -> onClose()
            inside(mx, my, rects["bug"]) -> bug = true
            inside(mx, my, rects["feat"]) -> bug = false
            inside(mx, my, rects["title"]) -> focused = 0
            inside(mx, my, rects["details"]) -> focused = 1
            inside(mx, my, rects["send"]) -> send()
            else -> return super.mouseClicked(click, bl)
        }
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(input)
        if (input.key() == GLFW.GLFW_KEY_TAB) { focused = 1 - focused; return true }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (focused == 0) focused = 1 else send()
            return true
        }
        (if (focused == 0) title else details)?.keyPressed(input)
        return true
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        (if (focused == 0) title else details)?.charTyped(input)
        return true
    }

    override fun onClose() {
        Minecraft.getInstance().showScreen(parent)
    }

    override fun paintUiOverlay() {
        fishmod.utils.rendering.UiRenderer.paint(this.width, this.height, fishmod.utils.rendering.UiScale.factor())
    }
}
