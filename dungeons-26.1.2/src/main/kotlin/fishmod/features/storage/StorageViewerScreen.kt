package fishmod.features.storage

import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.math.max

private const val SLOT = 18
private const val GAP = 12
private const val MARGIN = 18
private const val HEADER = 26

// translucent palette so the blurred game shows through
private const val BASE_TINT = 0x22_0A0A12
private const val PANEL_BG = 0x40_1B2130
private const val GLASS_TOP = 0x26_FFFFFF
private const val GLASS_BOT = 0x06_FFFFFF
private const val EDGE_LIGHT = 0x55_FFFFFF
private const val EDGE_DARK = 0x33_000000
private const val CARD_BG = 0x33_2A3242
private const val CARD_TOP = 0x1E_FFFFFF
private const val CELL_BG = 0x26_10121C
private const val GRID_LINE = 0x22_8FA0C0
private const val HOVER = 0x33_FFFFFF
private const val KNOB = 0x55_C8D2E6

/** Read-only viewer for everything [StorageCache] has captured, in a frosted-glass panel. */
class StorageViewerScreen : Screen(Component.literal("Storage Viewer")) {

    private var scroll = 0
    private var contentHeight = 0
    private val titleRects = ArrayList<IntArray>()
    private var loadAllRect = IntArray(4)

    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}
    override fun isPauseScreen() = false

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        titleRects.clear()
        runCatching { ctx.blurBeforeThisStratum() }
        runCatching { ctx.nextStratum() }
        ctx.fill(0, 0, width, height, BASE_TINT)

        val data = StorageCache.view()
        val cols = FishSettings.storageViewerColumns.coerceIn(1, 6)
        val cellW = 9 * SLOT + 8

        val panelX = MARGIN
        val panelY = MARGIN
        val panelX2 = width - MARGIN
        val panelY2 = height - MARGIN
        glassPanel(ctx, panelX, panelY, panelX2, panelY2)

        ctx.text(font, "§fStorage Viewer  §7${data.size} pages", panelX + 12, panelY + 9, -1)

        val btn = if (StorageAutoLoader.running()) "§e● loading… (click to stop)" else "§b[ Load all pages ]"
        val btnW = font.width(btn.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, ""))
        val bx = panelX2 - btnW - 14
        val by = panelY + 5
        val bHover = mouseX in bx - 4..bx + btnW + 4 && mouseY in by - 2..by + 12
        if (bHover) ctx.fill(bx - 4, by - 2, bx + btnW + 4, by + 11, HOVER)
        ctx.text(font, btn, bx, by + 2, -1)
        loadAllRect = intArrayOf(bx - 4, by - 2, btnW + 8, 13)

        val viewTop = panelY + HEADER
        val viewBot = panelY2 - 8
        runCatching { ctx.enableScissor(panelX + 8, viewTop, panelX2 - 8, viewBot) }

        val gridW = cellW * cols + GAP * (cols - 1)
        val gridLeft = max(panelX + 12, (panelX + panelX2) / 2 - gridW / 2)
        val topBase = viewTop + 6 - scroll
        var x = gridLeft
        var y = topBase
        var rowMaxH = 0
        var col = 0

        for ((idx, inv) in data) {
            val rows = inv.rows.coerceAtLeast(1)
            val cellH = 14 + rows * SLOT + 6
            if (y + cellH >= viewTop && y <= viewBot) drawPage(ctx, x, y, idx, inv, rows, mouseX, mouseY)
            titleRects.add(intArrayOf(x, y, cellW, 14, idx))
            rowMaxH = max(rowMaxH, cellH)
            col++
            if (col >= cols) { col = 0; x = gridLeft; y += rowMaxH + GAP; rowMaxH = 0 } else x += cellW + GAP
        }
        contentHeight = (y + rowMaxH) - topBase + 24
        runCatching { ctx.disableScissor() }

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
    }

    private fun glassPanel(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int) {
        ctx.fill(x1, y1, x2, y2, PANEL_BG)
        runCatching { ctx.fillGradient(x1, y1, x2, y1 + (y2 - y1) / 2, GLASS_TOP, GLASS_BOT) }
        ctx.fill(x1, y1, x2, y1 + 1, EDGE_LIGHT)
        ctx.fill(x1, y1, x1 + 1, y2, EDGE_LIGHT)
        ctx.fill(x1, y2 - 1, x2, y2, EDGE_DARK)
        ctx.fill(x2 - 1, y1, x2, y2, EDGE_DARK)
        ctx.fill(x1 + 8, y1 + HEADER - 2, x2 - 8, y1 + HEADER - 1, 0x22_FFFFFF)
    }

    private fun drawPage(ctx: GuiGraphicsExtractor, x: Int, y: Int, idx: Int, inv: NBTInventory, rows: Int, mouseX: Int, mouseY: Int) {
        val page = StoragePage(idx)
        val gridH = rows * SLOT
        val cardX2 = x + 9 * SLOT + 8
        val cardY2 = y + 14 + gridH + 4
        ctx.fill(x - 2, y - 2, cardX2, cardY2, CARD_BG)
        ctx.fill(x - 2, y - 2, cardX2, y - 1, CARD_TOP)

        val titleHover = mouseX in x..cardX2 && mouseY in y..(y + 14)
        ctx.text(font, (if (titleHover) "§e" else "§b") + page.name, x + 2, y + 2, -1)

        val gx = x + 3
        val gy = y + 14
        ctx.fill(gx, gy, gx + 9 * SLOT, gy + gridH, CELL_BG)
        var hovered: ItemStack? = null
        for (i in inv.stacks.indices) {
            val sx = gx + (i % 9) * SLOT + 1
            val sy = gy + (i / 9) * SLOT + 1
            val stack = inv.stacks[i]
            if (!stack.isEmpty) {
                ctx.item(stack, sx, sy)
                ctx.itemDecorations(font, stack, sx, sy)
            }
            if (mouseX in sx..(sx + 16) && mouseY in sy..(sy + 16) && !stack.isEmpty) hovered = stack
        }
        for (c in 0..9) ctx.fill(gx + c * SLOT, gy, gx + c * SLOT + 1, gy + gridH, GRID_LINE)
        for (r in 0..rows) ctx.fill(gx, gy + r * SLOT, gx + 9 * SLOT, gy + r * SLOT + 1, GRID_LINE)
        if (hovered != null) ctx.setTooltipForNextFrame(font, hovered, mouseX, mouseY)
    }

    private fun maxScroll() = (contentHeight - (height - 2 * MARGIN - HEADER - 8)).coerceAtLeast(0)

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
        for (r in titleRects) {
            if (mx in r[0]..(r[0] + r[2]) && my in r[1]..(r[1] + r[3])) {
                StoragePage(r[4]).open(); onClose(); return true
            }
        }
        return super.mouseClicked(click, doubled)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(input)
    }

    companion object {
        @JvmStatic
        fun open() {
            if (!FishSettings.storageOverlayEnabled) return
            Minecraft.getInstance().setScreen(StorageViewerScreen())
        }
    }
}
