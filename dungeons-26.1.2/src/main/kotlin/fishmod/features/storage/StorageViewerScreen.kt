package fishmod.features.storage

import fishmod.features.ScreenTheme
import fishmod.utils.config.values.FishSettings
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

private const val BASE_TINT = 0x22_0A0A12
private val PANEL_BG = 0xE00E1016.toInt()
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

class StorageViewerScreen : Screen(Component.literal("Storage Viewer")) {

    private var scroll = 0
    private var contentHeight = 0
    private val titleRects = ArrayList<IntArray>()
    private var loadAllRect = IntArray(4)
    private lateinit var search: EditBox
    private var lastQuery = ""

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

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        titleRects.clear()
        runCatching { ctx.blurBeforeThisStratum() }
        runCatching { ctx.nextStratum() }
        ctx.fill(0, 0, width, height, BASE_TINT)

        val q = query()
        if (q != lastQuery) { lastQuery = q; scroll = 0 }

        val data = StorageCache.view()
        val cellW = 9 * SLOT + 8

        val panelX = MARGIN
        val panelY = MARGIN
        val panelX2 = width - MARGIN
        val panelY2 = height - MARGIN
        ScreenTheme.panel(ctx, panelX, panelY, panelX2, panelY2, 8, PANEL_BG, PANEL_BORDER)

        drawSearch(ctx, panelX + 10, panelY + 7)
        drawLoadButton(ctx, panelX2 - 10, panelY + 7, mouseX, mouseY)
        ctx.fill(panelX + 1, panelY + TOP_BAR, panelX2 - 1, panelY + TOP_BAR + 1, PANEL_BORDER)

        val viewTop = panelY + TOP_BAR + 1
        val viewBot = panelY2 - 6
        val innerW = panelX2 - panelX - 24
        val cols = min(FishSettings.storageViewerColumns.coerceIn(1, 6), max(1, (innerW + GAP) / (cellW + GAP)))
        val gridW = cellW * cols + GAP * (cols - 1)
        val gridLeft = max(panelX + 12, (panelX + panelX2) / 2 - gridW / 2)
        val topBase = viewTop + 8 - scroll

        runCatching { ctx.enableScissor(panelX + 2, viewTop, panelX2 - 2, viewBot) }
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
            titleRects.add(intArrayOf(x, y, cellW, CARD_HEAD, idx))
            rowMaxH = max(rowMaxH, cardH)
            col++
            if (col >= cols) { col = 0; x = gridLeft; y += rowMaxH + GAP; rowMaxH = 0 } else x += cellW + GAP
        }
        contentHeight = (y + rowMaxH) - topBase + 16
        runCatching { ctx.disableScissor() }

        // dims go in a later stratum so they sit over the item icons
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
            ctx.fill(panelX2 - 6, knobY, panelX2 - 3, knobY + knobH, KNOB)
        }

        if (data.isEmpty()) {
            ctx.text(font, "§7Open §f/storage§7 and page through your ender chests / backpacks — they'll show up here.",
                panelX + 16, viewTop + 16, -1)
        }

        if (hovered != null) ctx.setTooltipForNextFrame(font, hovered, mouseX, mouseY)
    }

    private fun drawSearch(ctx: GuiGraphicsExtractor, x: Int, y: Int) {
        val w = 180; val h = 16
        ScreenTheme.roundedRectRing(ctx, x, y, w, h, 4, 1, FIELD_BG, if (search.value.isEmpty()) FIELD_BORDER else ScreenTheme.ACCENT)
        val text = search.value
        val tx = x + 5; val ty = y + 4
        ctx.enableScissor(x + 2, y, x + w - 2, y + h)
        if (text.isEmpty()) {
            ctx.text(font, "Search items…", tx, ty, ScreenTheme.SUBTEXT_COLOR, false)
        } else {
            val cur = search.cursorPosition.coerceIn(0, text.length)
            val curX = font.width(text.substring(0, cur))
            val off = max(0, curX - (w - 12))
            ctx.text(font, text, tx - off, ty, ScreenTheme.TEXT_COLOR, false)
            if ((System.currentTimeMillis() / 500) % 2 == 0L) ctx.fill(tx - off + curX, ty - 1, tx - off + curX + 1, ty + 9, ScreenTheme.TEXT_COLOR)
        }
        ctx.disableScissor()
    }

    private fun drawLoadButton(ctx: GuiGraphicsExtractor, right: Int, y: Int, mouseX: Int, mouseY: Int) {
        val running = StorageAutoLoader.running()
        val label = if (running) "● Loading… click to stop" else "Load all pages"
        val w = font.width(label) + 16
        val h = 16
        val bx = right - w
        val hover = mouseX in bx..(bx + w) && mouseY in y..(y + h)
        if (running) {
            ScreenTheme.roundedRectRing(ctx, bx, y, w, h, h / 2, 1, FIELD_BG, if (hover) 0xFFFFD98A.toInt() else GOLD)
            ctx.text(font, label, bx + 8, y + 4, GOLD, false)
        } else {
            ScreenTheme.roundedRectRing(ctx, bx, y, w, h, h / 2, 1, FIELD_BG, if (hover) ScreenTheme.ACCENT_HOVER else ScreenTheme.ACCENT)
            ctx.text(font, label, bx + 8, y + 4, if (hover) ScreenTheme.ACCENT_HOVER else ScreenTheme.TEXT_COLOR, false)
        }
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
        ScreenTheme.panel(ctx, x - 2, y - 2, x + cardW - 2, y + cardH, 5, CARD_BG, if (lit) GOLD else CARD_BORDER)

        val titleHover = mouseInView && mouseX in x..(x + cardW) && mouseY in y..(y + CARD_HEAD)
        ctx.text(font, page.name, x + 2, y + 3, if (titleHover) ScreenTheme.ACCENT_HOVER else ScreenTheme.TEXT_COLOR, false)
        val info = if (q.isNotEmpty()) "$hits match" + (if (hits == 1) "" else "es") else "$filled items"
        ctx.text(font, info, x + cardW - 6 - font.width(info), y + 3, if (lit) GOLD else ScreenTheme.SUBTEXT_COLOR, false)

        val gx = x + 2
        val gy = y + CARD_HEAD
        var hovered: ItemStack? = null
        for (i in 0 until rows * 9) {
            val cx = gx + (i % 9) * SLOT
            val cy = gy + (i / 9) * SLOT
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

    private fun maxScroll() = (contentHeight - (height - 2 * MARGIN - TOP_BAR - 7)).coerceAtLeast(0)

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scroll = (scroll - (verticalAmount * 30).toInt()).coerceIn(0, maxScroll())
        return true
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = click.x().toInt(); val my = click.y().toInt()
        loadAllRect.let { r ->
            if (mx in r[0]..(r[0] + r[2]) && my in r[1]..(r[1] + r[3])) {
                if (StorageAutoLoader.running()) StorageAutoLoader.stop()
                else { StorageAutoLoader.start(); onClose() }
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
