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
private const val PAD = 10
private const val GAP = 12

/** Read-only viewer for everything [StorageCache] has captured. */
class StorageViewerScreen : Screen(Component.literal("Storage Viewer")) {

    private var scroll = 0
    private var contentHeight = 0
    // page-title hit rects captured each frame: (x, y, w, h, pageIndex)
    private val titleRects = ArrayList<IntArray>()

    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}
    override fun isPauseScreen() = false

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        ctx.fill(0, 0, width, height, 0xE6101012.toInt())
        titleRects.clear()

        val data = StorageCache.view()
        val cols = FishSettings.storageViewerColumns.coerceIn(1, 6)
        val cellW = 9 * SLOT + 8

        ctx.text(font, "§fStorage Viewer §7— §b${data.size}§7 pages  §8(scroll · click a title to open that page)", PAD, 6, -1)

        val gridLeft = max(PAD, (width - (cellW * cols + GAP * (cols - 1))) / 2)
        val top = 24 - scroll
        var x = gridLeft
        var y = top
        var rowMaxH = 0
        var col = 0

        for ((idx, inv) in data) {
            val rows = inv.rows.coerceAtLeast(1)
            val cellH = 12 + rows * SLOT + 6

            if (y + cellH >= 0 && y <= height) drawPage(ctx, x, y, idx, inv, rows, mouseX, mouseY)
            titleRects.add(intArrayOf(x, y, cellW, 12, idx))

            rowMaxH = max(rowMaxH, cellH)
            col++
            if (col >= cols) {
                col = 0; x = gridLeft; y += rowMaxH + GAP; rowMaxH = 0
            } else {
                x += cellW + GAP
            }
        }
        contentHeight = (y + rowMaxH) - top + 20
    }

    private fun drawPage(ctx: GuiGraphicsExtractor, x: Int, y: Int, idx: Int, inv: NBTInventory, rows: Int, mouseX: Int, mouseY: Int) {
        val page = StoragePage(idx)
        val titleHover = mouseX in x..(x + 9 * SLOT + 8) && mouseY in y..(y + 12)
        ctx.text(font, (if (titleHover) "§e" else "§7") + page.name, x, y + 2, -1)

        val gx = x
        val gy = y + 12
        ctx.fill(gx, gy, gx + 9 * SLOT, gy + rows * SLOT, 0xFF1E1E22.toInt())
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
        // grid lines
        for (c in 0..9) ctx.fill(gx + c * SLOT, gy, gx + c * SLOT + 1, gy + rows * SLOT, 0xFF2A2A30.toInt())
        for (r in 0..rows) ctx.fill(gx, gy + r * SLOT, gx + 9 * SLOT, gy + r * SLOT + 1, 0xFF2A2A30.toInt())

        if (hovered != null) ctx.setTooltipForNextFrame(font, hovered, mouseX, mouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val maxScroll = (contentHeight - height + 40).coerceAtLeast(0)
        scroll = (scroll - (verticalAmount * 30).toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = click.x().toInt(); val my = click.y().toInt()
        for (r in titleRects) {
            if (mx in r[0]..(r[0] + r[2]) && my in r[1]..(r[1] + r[3])) {
                StoragePage(r[4]).open()
                onClose()
                return true
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
