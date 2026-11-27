package fishmod.features.cosmetics

import fishmod.compat.showScreen
import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiRenderer
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

// 16x16 pixel editor for the Custom crosshair: left paint, right erase
class CrosshairEditorScreen(private val parent: Screen?) : Screen(Component.literal("Crosshair Editor")), HasUiOverlay {

    private companion object {
        const val N = CrosshairPixels.SIZE
        const val CELL = 10
        const val SCRIM = 0x99000000.toInt()
        const val BG = 0xFF151515.toInt()
        const val BORDER = 0xFF2D2D2D.toInt()
        const val CHECK_A = 0xFF262626.toInt()
        const val CHECK_B = 0xFF1E1E1E.toInt()
        const val SW = 12
        val PALETTE = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFAAAAAA.toInt(), 0xFF555555.toInt(), 0xFF000000.toInt(),
            0xFFFF5555.toInt(), 0xFFAA0000.toInt(), 0xFFFFAA00.toInt(), 0xFFFFFF55.toInt(),
            0xFF55FF55.toInt(), 0xFF00AA00.toInt(), 0xFF55FFFF.toInt(), 0xFF00AAAA.toInt(),
            0xFF5555FF.toInt(), 0xFF0000AA.toInt(), 0xFFFF55FF.toInt(), 0xFFAA00AA.toInt()
        )
    }

    private val px = CrosshairPixels.get().copyOf()
    private var color = PALETTE[0]
    private var paintMode = 0
    private val pad = 12
    private val panelW = N * CELL + pad * 2
    private val panelH = N * CELL + pad * 2 + 30 + SW * 2 + 6 + 28
    private var panelX = 0
    private var panelY = 0
    private val gridX get() = panelX + pad
    private val gridY get() = panelY + pad + 30
    private val palY get() = gridY + N * CELL + 8
    private val btnY get() = palY + SW * 2 + 10
    private val btnW = 60
    private val btnH = 18

    override fun init() {
        val vw = (this.width / UiScale.factor()).toInt()
        val vh = (this.height / UiScale.factor()).toInt()
        panelX = (vw - panelW) / 2
        panelY = Math.max(8, (vh - panelH) / 2)
    }

    // paints/erases the cell under the virtual mouse; true if inside the grid
    private fun paintAt(mx: Int, my: Int): Boolean {
        if (paintMode == 0 || !ScreenTheme.inside(mx, my, gridX, gridY, N * CELL, N * CELL)) return false
        val i = ((my - gridY) / CELL).coerceAtMost(N - 1) * N + ((mx - gridX) / CELL).coerceAtMost(N - 1)
        px[i] = if (paintMode == 1) color else 0
        return true
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        if (ScreenTheme.inside(mx, my, gridX, gridY, N * CELL, N * CELL)) {
            paintMode = if (click.button() == 1) 2 else 1
            paintAt(mx, my)
            return true
        }
        for (k in PALETTE.indices) {
            if (ScreenTheme.inside(mx, my, gridX + (k % 8) * (SW + 4), palY + (k / 8) * (SW + 2), SW, SW)) { color = PALETTE[k]; return true }
        }
        if (ScreenTheme.inside(mx, my, gridX, btnY, btnW, btnH)) { px.fill(0); return true }
        if (ScreenTheme.inside(mx, my, panelX + panelW - pad - btnW, btnY, btnW, btnH)) { onClose(); return true }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (paintAt(UiScale.vx(click.x()), UiScale.vx(click.y()))) return true
        return super.mouseDragged(click, dragX, dragY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        paintMode = 0
        return super.mouseReleased(click)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        try {
            renderPanel(UiScale.vx(mouseX), UiScale.vx(mouseY))
        } catch (e: Exception) {
            FishDiag.fail("CrosshairEditorScreen.1", "crosshair editor render failed", e)
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun renderPanel(mx: Int, my: Int) {
        UiRecorder.clear()
        UiRecorder.fillRect(0f, 0f, this.width / UiScale.factor(), this.height / UiScale.factor(), SCRIM)
        ScreenTheme.nPanel(panelX, panelY, panelX + panelW, panelY + panelH, 10, BG, BORDER)
        UiRecorder.fillRect((panelX + 10).toFloat(), panelY.toFloat(), (panelW - 20).toFloat(), 2f, ScreenTheme.ACCENT)
        UiRecorder.textBold("Draw Crosshair", (panelX + pad).toFloat(), (panelY + pad).toFloat(), 9f, ScreenTheme.TEXT_COLOR)
        UiRecorder.text("Left click paint · Right click erase", (panelX + pad).toFloat(), (panelY + pad + 12).toFloat(), 6.5f, ScreenTheme.SUBTEXT_COLOR)
        for (r in 0 until N) for (c in 0 until N) {
            val x = (gridX + c * CELL).toFloat()
            val y = (gridY + r * CELL).toFloat()
            UiRecorder.fillRect(x, y, CELL.toFloat(), CELL.toFloat(), if ((r + c) % 2 == 0) CHECK_A else CHECK_B)
            val p = px[r * N + c]
            if ((p ushr 24) != 0) UiRecorder.fillRect(x, y, CELL.toFloat(), CELL.toFloat(), p)
        }
        // centre guides so the crosshair can be lined up
        UiRecorder.fillRect((gridX + N * CELL / 2).toFloat() - 0.5f, gridY.toFloat(), 1f, (N * CELL).toFloat(), 0x40FFFFFF)
        UiRecorder.fillRect(gridX.toFloat(), (gridY + N * CELL / 2).toFloat() - 0.5f, (N * CELL).toFloat(), 1f, 0x40FFFFFF)
        if (ScreenTheme.inside(mx, my, gridX, gridY, N * CELL, N * CELL)) {
            val hx = gridX + ((mx - gridX) / CELL).coerceAtMost(N - 1) * CELL
            val hy = gridY + ((my - gridY) / CELL).coerceAtMost(N - 1) * CELL
            UiRecorder.fillRect(hx.toFloat(), hy.toFloat(), CELL.toFloat(), CELL.toFloat(), 0x30FFFFFF)
        }
        for (k in PALETTE.indices) {
            val sx = gridX + (k % 8) * (SW + 4)
            val sy = palY + (k / 8) * (SW + 2)
            if (PALETTE[k] == color) UiRecorder.fillRect((sx - 1).toFloat(), (sy - 1).toFloat(), (SW + 2).toFloat(), (SW + 2).toFloat(), ScreenTheme.ACCENT)
            UiRecorder.fillRect(sx.toFloat(), sy.toFloat(), SW.toFloat(), SW.toFloat(), PALETTE[k])
        }
        button("Clear", gridX, mx, my)
        button("Done", panelX + panelW - pad - btnW, mx, my)
    }

    private fun button(label: String, x: Int, mx: Int, my: Int) {
        val hov = ScreenTheme.inside(mx, my, x, btnY, btnW, btnH)
        ScreenTheme.nRoundedRect(x, btnY, btnW, btnH, btnH / 2, if (hov) ScreenTheme.ACCENT_HOVER else ScreenTheme.ACCENT)
        val size = 7.5f
        UiRecorder.textBold(label, x + btnW / 2f - UiRecorder.textWidth(label, size) / 2f, btnY + (btnH - size) / 2f, size, ScreenTheme.onAccent())
    }

    override fun paintUiOverlay() {
        UiRenderer.paint(this.width, this.height, UiScale.factor())
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        CrosshairPixels.save(px)
        FishDiag.guard("CrosshairEditorScreen.2", "saving custom crosshair failed") { fishmod.utils.config.FishConfig.manager.save() }
        Minecraft.getInstance().showScreen(parent)
    }
}
