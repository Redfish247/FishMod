package fishmod.features.storage

import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.config.values.FishSettings
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
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

private const val SLOT = 18
private const val GAP = 10
private const val MARGIN = 14
private const val TOP_BAR = 30
private const val CARD_HEAD = 16
private const val PANEL_R = 8
private const val CARD_R = 4

private const val BASE_TINT = 0x22_0A0A12
private val PANEL_BG = 0xFF0E1016.toInt()
private val PANEL_BORDER = 0xFF2A2D38.toInt()
private val CARD_BG = ScreenTheme.CARD_BG
private val CARD_BORDER = 0xFF252932.toInt()
private val CELL_BG = 0x14_FFFFFF
private val FIELD_BG = 0xFF1A1E26.toInt()
private val FIELD_BORDER = 0xFF2E333D.toInt()
private val GOLD = 0xFFF2C14E.toInt()
private val GOLD_SOFT = 0x40_F2C14E
private val DIM = 0xC00E1016.toInt()
private val KNOB = 0x55_C8D2E6

class StorageViewerScreen : Screen(Component.literal("Storage Viewer")), HasUiOverlay {

    private var scroll = 0
    private var contentHeight = 0
    private val titleRects = ArrayList<IntArray>()
    private var loadAllRect = IntArray(4)
    private lateinit var search: EditBox
    private var lastQuery = ""
    private var k = 1f
    private var vw = 0
    private var vh = 0

    private fun updateScale() {
        k = UiScale.userScale(this)
        vw = (width / k).toInt()
        vh = (height / k).toInt()
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}
    override fun isPauseScreen() = false

    override fun init() {
        val prev = if (::search.isInitialized) search.value else ""
        search = EditBox(font, 0, 0, 180, 18, Component.literal("Search"))
        search.setMaxLength(64)
        search.setBordered(false)
        search.setValue(prev)
        search.isFocused = true
    }

    private fun query(): String = search.value.trim().lowercase()

    private fun matches(stack: ItemStack, q: String): Boolean =
        !stack.isEmpty && stack.hoverName.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").lowercase().contains(q)

    override fun paintUiOverlay() {
        UiRenderer.paint(width, height, k)
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, rawMouseX: Int, rawMouseY: Int, delta: Float) {
        titleRects.clear()
        UiRecorder.clear()
        updateScale()
        val mouseX = (rawMouseX / k).toInt()
        val mouseY = (rawMouseY / k).toInt()
        runCatching { ctx.blurBeforeThisStratum() }
        runCatching { ctx.nextStratum() }
        ctx.pose().pushMatrix()
        ctx.pose().scale(k, k)
        ctx.fill(0, 0, vw + 1, vh + 1, BASE_TINT)

        val q = query()
        if (q != lastQuery) { lastQuery = q; scroll = 0 }

        val cached = StorageCache.view()
        val data = java.util.TreeMap<Int, NBTInventory>(cached)
        for (idx in StorageCache.knownPages()) {
            if (idx !in data) data[idx] = NBTInventory(List((StorageCache.expectedRows(idx) ?: 1) * 9) { ItemStack.EMPTY })
        }
        val cellW = 9 * SLOT + 8

        val panelX = MARGIN
        val panelY = MARGIN
        val panelX2 = vw - MARGIN
        val panelY2 = vh - MARGIN
        drawPanel(ctx, panelX, panelY, panelX2, panelY2)

        drawSearch(panelX + 10, panelY + 7)
        drawLoadButton(panelX2 - 10, panelY + 7, mouseX, mouseY)
        ScreenTheme.nRect(panelX + 1, panelY + TOP_BAR, panelX2 - panelX - 2, 1, PANEL_BORDER)

        val viewTop = panelY + TOP_BAR + 1
        val viewBot = panelY2 - 6
        val innerW = panelX2 - panelX - 24
        val cols = min(FishSettings.storageViewerColumns.coerceIn(1, 6), max(1, (innerW + GAP) / (cellW + GAP)))
        val gridW = cellW * cols + GAP * (cols - 1)
        val gridLeft = max(panelX + 12, (panelX + panelX2) / 2 - gridW / 2)
        val topBase = viewTop + 8 - scroll

        runCatching { ctx.enableScissor(panelX + 2, viewTop, panelX2 - 2, viewBot) }
        UiRecorder.pushScissor((panelX + 2).toFloat(), viewTop.toFloat(), (panelX2 - panelX - 4).toFloat(), (viewBot - viewTop).toFloat())
        val dims = ArrayList<IntArray>()
        var hovered: ItemStack? = null
        var x = gridLeft
        var y = topBase
        var rowMaxH = 0
        var col = 0
        for ((idx, inv) in data) {
            val rows = inv.rows.coerceAtLeast(1)
            val cardH = CARD_HEAD + rows * SLOT + 4
            if (y + cardH >= viewTop && y <= viewBot) {
                val h = drawPage(ctx, x, y, idx, inv, rows, q, mouseX, mouseY, mouseY in viewTop..viewBot, dims)
                if (h != null) hovered = h
            }
            val titleTop = max(y, viewTop)
            val titleBot = min(y + CARD_HEAD, viewBot)
            if (titleTop < titleBot) titleRects.add(intArrayOf(x, titleTop, cellW, titleBot - titleTop, idx))
            rowMaxH = max(rowMaxH, cardH)
            col++
            if (col >= cols) { col = 0; x = gridLeft; y += rowMaxH + GAP; rowMaxH = 0 } else x += cellW + GAP
        }
        contentHeight = (y + rowMaxH) - topBase + 16
        UiRecorder.popScissor()
        runCatching { ctx.disableScissor() }

        if (dims.isNotEmpty()) {
            runCatching { ctx.nextStratum() }
            runCatching { ctx.enableScissor(panelX + 2, viewTop, panelX2 - 2, viewBot) }
            for (d in dims) ctx.fill(d[0], d[1], d[0] + SLOT - 1, d[1] + SLOT - 1, DIM)
            runCatching { ctx.disableScissor() }
        }

        val maxScroll = maxScroll()
        if (maxScroll > 0) {
            val trackH = viewBot - viewTop
            val knobH = max(20, trackH * trackH / (trackH + maxScroll))
            val knobY = viewTop + (trackH - knobH) * scroll / maxScroll
            UiRecorder.fillRoundedRect((panelX2 - 6).toFloat(), knobY.toFloat(), 3f, knobH.toFloat(), 1.5f, KNOB)
        }

        if (data.isEmpty()) {
            ScreenTheme.nLegacyText("§7Open §f/storage§7 and page through your ender chests / backpacks — they'll show up here.",
                panelX + 16, viewTop + 16, ScreenTheme.SUBTEXT_COLOR, 7.5f)
        }

        ctx.pose().popMatrix()

        val mc = minecraft
        val hs = hovered
        if (hs != null && mc != null) {
            val lines = runCatching { Screen.getTooltipFromItem(mc, hs) }.getOrNull()
            if (lines != null) ScreenTheme.nItemTooltip(lines, mouseX, mouseY, vw, vh)
        }
    }

    private fun drawPanel(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int) {
        val r = PANEL_R
        ctx.fill(x1 + r, y1, x2 - r, y2, PANEL_BG)
        ctx.fill(x1, y1 + r, x1 + r, y2 - r, PANEL_BG)
        ctx.fill(x2 - r, y1 + r, x2, y2 - r, PANEL_BG)
        val s = (r * 2).toFloat()
        val rf = r.toFloat()
        cornerPiece(x1, y1, r + 1, r + 1, x1.toFloat(), y1.toFloat(), s, s, rf, 0f, 0f, 0f, PANEL_BG)
        cornerPiece(x2 - r - 1, y1, r + 1, r + 1, x2 - s, y1.toFloat(), s, s, 0f, rf, 0f, 0f, PANEL_BG)
        cornerPiece(x2 - r - 1, y2 - r - 1, r + 1, r + 1, x2 - s, y2 - s, s, s, 0f, 0f, rf, 0f, PANEL_BG)
        cornerPiece(x1, y2 - r - 1, r + 1, r + 1, x1.toFloat(), y2 - s, s, s, 0f, 0f, 0f, rf, PANEL_BG)
        ScreenTheme.nRoundedRectRing(x1, y1, x2 - x1, y2 - y1, r, 1, 0, PANEL_BORDER)
    }

    private fun cornerPiece(cx: Int, cy: Int, cw: Int, ch: Int, sx: Float, sy: Float, sw: Float, sh: Float,
                            tl: Float, tr: Float, br: Float, bl: Float, color: Int) {
        UiRecorder.pushScissor(cx.toFloat(), cy.toFloat(), cw.toFloat(), ch.toFloat())
        UiRecorder.fillRoundedRectCorners(sx, sy, sw, sh, tl, tr, br, bl, color)
        UiRecorder.popScissor()
    }

    private fun drawSearch(x: Int, y: Int) {
        val w = 180; val h = 16
        ScreenTheme.nRoundedRectRing(x, y, w, h, 4, 1, FIELD_BG, if (search.value.isEmpty()) FIELD_BORDER else ScreenTheme.ACCENT)
        ScreenTheme.nTextFieldContent(search, true, x + 2, y, w - 4, h, 7.5f)
        if (search.value.isEmpty()) UiRecorder.text("Search items…", (x + 7).toFloat(), y + (h - 7.5f) / 2f, 7.5f, ScreenTheme.SUBTEXT_COLOR)
    }

    private fun drawLoadButton(right: Int, y: Int, mouseX: Int, mouseY: Int) {
        val running = StorageAutoLoader.running()
        val label = if (running) "● Fetching… click to stop" else "Fetch page list"
        val w = ScreenTheme.nstw(label, 0.75f) + 16
        val h = 16
        val bx = right - w
        val hover = mouseX in bx..(bx + w) && mouseY in y..(y + h)
        val ring = if (running) (if (hover) 0xFFFFD98A.toInt() else GOLD) else if (hover) ScreenTheme.ACCENT_HOVER else ScreenTheme.ACCENT
        val ink = if (running) GOLD else if (hover) ScreenTheme.ACCENT_HOVER else ScreenTheme.TEXT_COLOR
        ScreenTheme.nRoundedRectRing(bx, y, w, h, h / 2, 1, FIELD_BG, ring)
        ScreenTheme.nst(label, bx + 8, y + 4, ink, 0.75f)
        loadAllRect = intArrayOf(bx, y, w, h)
    }

    private fun drawPage(
        ctx: GuiGraphicsExtractor, x: Int, y: Int, idx: Int, inv: NBTInventory, rows: Int, q: String,
        mouseX: Int, mouseY: Int, mouseInView: Boolean, dims: MutableList<IntArray>,
    ): ItemStack? {
        val page = StoragePage(idx)
        val cardW = 9 * SLOT + 8
        val cardH = CARD_HEAD + rows * SLOT + 4
        val filled = inv.stacks.count { !it.isEmpty }
        val hits = if (q.isEmpty()) 0 else inv.stacks.count { matches(it, q) }
        val lit = q.isNotEmpty() && hits > 0

        val cx1 = x - 2; val cy1 = y - 2; val cx2 = cx1 + cardW; val cy2 = y + cardH
        val sx1 = x + 2; val sy1 = y + CARD_HEAD; val sx2 = sx1 + 9 * SLOT; val sy2 = sy1 + rows * SLOT

        ctx.fill(sx1 - 1, sy1 - 1, sx2 + 1, sy2 + 1, CARD_BG)

        val r = CARD_R.toFloat()
        UiRecorder.fillRoundedRectCorners(cx1.toFloat(), cy1.toFloat(), cardW.toFloat(), (sy1 - cy1).toFloat(), r, r, 0f, 0f, CARD_BG)
        ScreenTheme.nRect(cx1, sy1 - 1, sx1 - cx1, sy2 - sy1 + 2, CARD_BG)
        ScreenTheme.nRect(sx2, sy1 - 1, cx2 - sx2, sy2 - sy1 + 2, CARD_BG)
        cornerPiece(cx1, sy2, cardW, cy2 - sy2, cx1.toFloat(), cy2 - 3 * r, cardW.toFloat(), 3 * r, 0f, 0f, r, r, CARD_BG)
        ScreenTheme.nRoundedRectRing(cx1, cy1, cardW, cy2 - cy1, CARD_R, 1, 0, if (lit) GOLD else CARD_BORDER)

        val titleHover = mouseInView && mouseX in x..(x + cardW) && mouseY in y..(y + CARD_HEAD)
        ScreenTheme.nst(page.name, x + 3, y + 3, if (titleHover) ScreenTheme.ACCENT_HOVER else ScreenTheme.TEXT_COLOR, 0.8f)
        val info = if (q.isNotEmpty()) "$hits match" + (if (hits == 1) "" else "es") else "$filled items"
        ScreenTheme.nst(info, x + cardW - 7 - ScreenTheme.nstw(info, 0.7f), y + 4, if (lit) GOLD else ScreenTheme.SUBTEXT_COLOR, 0.7f)

        var hovered: ItemStack? = null
        for (i in 0 until rows * 9) {
            val cx = sx1 + (i % 9) * SLOT
            val cy = sy1 + (i / 9) * SLOT
            val stack = inv.stacks.getOrNull(i) ?: ItemStack.EMPTY
            val hit = q.isNotEmpty() && matches(stack, q)
            if (hit) {
                ctx.fill(cx, cy, cx + SLOT - 1, cy + SLOT - 1, GOLD_SOFT)
                ctx.fill(cx, cy, cx + SLOT - 1, cy + 1, GOLD)
                ctx.fill(cx, cy + SLOT - 2, cx + SLOT - 1, cy + SLOT - 1, GOLD)
                ctx.fill(cx, cy, cx + 1, cy + SLOT - 1, GOLD)
                ctx.fill(cx + SLOT - 2, cy, cx + SLOT - 1, cy + SLOT - 1, GOLD)
            } else {
                ctx.fill(cx, cy, cx + SLOT - 1, cy + SLOT - 1, CELL_BG)
                if (q.isNotEmpty()) dims.add(intArrayOf(cx, cy))
            }
            if (!stack.isEmpty) {
                ctx.item(stack, cx, cy)
                ctx.itemDecorations(font, stack, cx, cy)
                if (mouseInView && mouseX in cx..(cx + 16) && mouseY in cy..(cy + 16)) hovered = stack
            }
        }
        return hovered
    }

    private fun maxScroll() = (contentHeight - (vh - 2 * MARGIN - TOP_BAR - 7)).coerceAtLeast(0)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        updateScale()
        scroll = (scroll - (verticalAmount * 30 / k).toInt()).coerceIn(0, maxScroll())
        return true
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        updateScale()
        val mx = (click.x() / k).toInt(); val my = (click.y() / k).toInt()
        loadAllRect.let { r ->
            if (mx in r[0]..(r[0] + r[2]) && my in r[1]..(r[1] + r[3])) {
                if (StorageAutoLoader.running()) StorageAutoLoader.stop()
                else StorageAutoLoader.start()
                return true
            }
        }
        if (my > MARGIN + TOP_BAR) {
            for (r in titleRects) {
                if (mx in r[0]..(r[0] + r[2]) && my in r[1]..(r[1] + r[3])) {
                    StoragePage(r[4]).open(); onClose(); return true
                }
            }
        }
        return super.mouseClicked(click, doubled)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        if (search.keyPressed(input)) return true
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        if (search.charTyped(input)) return true
        return super.charTyped(input)
    }

    companion object {
        @JvmStatic
        fun open() {
            if (!FishSettings.storageOverlayEnabled) return
            Minecraft.getInstance().setScreen(StorageViewerScreen())
        }
    }
}
