package fishmod.features.dungeon

import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiRenderer
import fishmod.utils.rendering.UiScale
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.function.Consumer

class DungeonWaypointTitleScreen(private val onSubmit: Consumer<String?>?) : Screen(Component.literal("Dungeon Waypoint Title")), HasUiOverlay {

    private companion object {
        private const val SCRIM = 0x99000000.toInt()
        private const val BG = 0xFF12151B.toInt()
        private const val BORDER = 0xFF2A2D38.toInt()
        private const val CARD = 0xFF181C23.toInt()
        private const val CARD_HOVER = 0xFF1E232C.toInt()
        private const val PREVIEW_BG = 0xFF0B0E12.toInt()
        private const val ON_ACCENT = 0xFF06302F.toInt()
    }

    private lateinit var field: EditBox
    private var panelX = 0
    private var panelY = 0
    private val panelW = 220
    private val panelH = 178
    private val pad = 12
    private val fieldH = 18
    private val btnH = 20
    private val cancelW = 60
    private val addW = 84
    private var btnY = 0
    private var cancelX = 0
    private var addX = 0

    override fun init() {
        val vw = (this.width / UiScale.factor()).toInt()
        val vh = (this.height / UiScale.factor()).toInt()
        panelX = (vw - panelW) / 2
        panelY = Math.max(8, (vh - panelH) / 2)

        val old = if (::field.isInitialized) field.value else ""
        field = EditBox(this.font, panelX + pad, panelY + 60, panelW - pad * 2, fieldH, Component.literal("Title"))
        field.setMaxLength(64)
        field.value = old
        field.isFocused = true

        btnY = panelY + panelH - pad - btnH
        addX = panelX + panelW - pad - addW
        cancelX = addX - 6 - cancelW
    }

    private fun submit() {
        val text = field.value
        onClose()
        onSubmit?.accept(if (text == null || text.isBlank()) null else text)
    }

    private fun inside(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx <= x + w && my >= y && my <= y + h

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        if (inside(mx, my, addX, btnY, addW, btnH)) { submit(); return true }
        if (inside(mx, my, cancelX, btnY, cancelW, btnH)) { onClose(); return true }
        return super.mouseClicked(click, doubled)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        when (input.key()) {
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { submit(); return true }
            GLFW.GLFW_KEY_ESCAPE -> return super.keyPressed(input)
        }
        if (field.keyPressed(input)) return true
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        field.charTyped(input)
        return true
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mx = UiScale.vx(mouseX)
        val my = UiScale.vx(mouseY)
        UiRecorder.clear()
        val x = panelX
        val y = panelY
        val innerW = panelW - pad * 2
        val vw = this.width / UiScale.factor()
        val vh = this.height / UiScale.factor()

        UiRecorder.fillRect(0f, 0f, vw, vh, SCRIM)
        UiRecorder.dropShadow(x.toFloat(), y.toFloat(), panelW.toFloat(), panelH.toFloat(), 10f, 16f, 0x70000000)
        ScreenTheme.nPanel(x, y, x + panelW, y + panelH, 10, BG, BORDER)
        UiRecorder.fillRect((x + 10).toFloat(), y.toFloat(), (panelW - 20).toFloat(), 2f, ScreenTheme.ACCENT)

        ScreenTheme.nRoundedRect(x + pad, y + 12, 24, 24, 6, 0x2424B6B0)
        UiRecorder.disc((x + pad + 12).toFloat(), (y + 22).toFloat(), 4.5f, ScreenTheme.ACCENT)
        UiRecorder.fillRect((x + pad + 11).toFloat(), (y + 25).toFloat(), 2f, 6f, ScreenTheme.ACCENT)
        val tx = (x + pad + 32).toFloat()
        UiRecorder.textBold("Add Waypoint", tx, (y + 13).toFloat(), 9f, ScreenTheme.TEXT_COLOR)
        UiRecorder.text("Enter to confirm · Esc to cancel", tx, (y + 26).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        ScreenTheme.nRect(x + pad, y + 44, innerW, 1, BORDER)

        UiRecorder.text("Title", (x + pad).toFloat(), (y + 50).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        ScreenTheme.nTextField(field, true, field.x, field.y, field.width, field.height)
        if (field.value.isEmpty()) {
            UiRecorder.text("Waypoint title (optional)", (field.x + 3).toFloat(), field.y + 5.5f, 7f, 0xFF5A6470.toInt())
        }

        val py = y + 86
        val ph = 50
        ScreenTheme.nRoundedRect(x + pad, py, innerW, ph, 6, PREVIEW_BG)
        val col = DungeonWaypoints.getColorArgb() or 0xFF000000.toInt()
        val label = fit(field.value.ifBlank { "Waypoint" }, innerW - 24, 8f)
        val lw = UiRecorder.textWidth(label, 8f)
        val cx = x + panelW / 2f
        val ly = py + 10f
        UiRecorder.fillRoundedRect(cx - lw / 2f - 6f, ly, lw + 12f, 13f, 4f, 0x88000000.toInt())
        UiRecorder.textBold(label, cx - lw / 2f, ly + 2.5f, 8f, col)
        UiRecorder.fillRect(cx - 1f, ly + 16f, 2f, 16f, (col and 0x00FFFFFF) or 0x99000000.toInt())

        val cHov = inside(mx, my, cancelX, btnY, cancelW, btnH)
        ScreenTheme.nRoundedRectRing(cancelX, btnY, cancelW, btnH, btnH / 2, 1, if (cHov) CARD_HOVER else CARD, BORDER)
        centered("Cancel", cancelX + cancelW / 2, btnY, if (cHov) ScreenTheme.TEXT_COLOR else ScreenTheme.SUBTEXT_COLOR)

        val aHov = inside(mx, my, addX, btnY, addW, btnH)
        ScreenTheme.nRoundedRect(addX, btnY, addW, btnH, btnH / 2, if (aHov) ScreenTheme.ACCENT_HOVER else ScreenTheme.ACCENT)
        centered("Add waypoint", addX + addW / 2, btnY, ON_ACCENT)

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun centered(s: String, cx: Int, y: Int, color: Int) {
        val size = 7.5f
        UiRecorder.textBold(s, cx - UiRecorder.textWidth(s, size) / 2f, y + (btnH - size) / 2f, size, color)
    }

    private fun fit(s: String, maxW: Int, size: Float): String {
        if (UiRecorder.textWidth(s, size) <= maxW) return s
        val n = fishmod.utils.rendering.TextFit.prefixLength(s, "...", maxW.toFloat(), 0) { UiRecorder.textWidth(it, size) }
        return s.substring(0, n) + "..."
    }

    override fun paintUiOverlay() {
        UiRenderer.paint(this.width, this.height, UiScale.factor())
    }

    override fun isPauseScreen(): Boolean {
        return false
    }
}
