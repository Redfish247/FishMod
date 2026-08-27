package fishmod.features.storage

import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import kotlin.math.max

private const val SLOT = 18

// liquid-glass palette (ARGB)
private val BASE = 0xDD0B0C12.toInt()   // near-opaque so the vanilla GUI underneath is hidden
private const val PANEL = 0x551B2130
private const val GLASS_TOP = 0x26FFFFFF
private const val GLASS_BOT = 0x06FFFFFF
private const val EDGE_L = 0x55FFFFFF
private const val EDGE_D = 0x33000000
private const val CARD = 0x332A3242
private const val CELL = 0x2610121C
private const val GRID = 0x228FA0C0
private val DIM = 0xB0000000.toInt()
private const val KNOB = 0x55C8D2E6
private const val FIELD_BG = 0x500A0C14

/**
 * Interactive Storage Overlay. While a `/storage`, ender-chest or backpack GUI is open it paints a
 * frosted panel over the vanilla screen showing every cached page at once with a live search;
 * clicking an item on the page you actually have open performs the real click, clicking anything on
 * another page opens that page.
 */
object StorageOverlay {

    @Volatile var search = ""
    private var searchFocused = false
    private var scroll = 0
    private var contentH = 0

    private val slotHits = ArrayList<IntArray>() // x, y, pageIdx, cacheIdx
    private val titleHits = ArrayList<IntArray>() // x, y, w, h, pageIdx
    private var closeHit = IntArray(4)
    private var fieldHit = IntArray(4)

    private fun on(screen: AbstractContainerScreen<*>): Boolean {
        if (!FishSettings.storageOverlayEnabled) return false
        val t = screen.title.string.replace(Regex("§."), "")
        return t == "Storage" || StoragePage.fromTitle(t) != null
    }

    private fun activeIdx(screen: AbstractContainerScreen<*>): Int =
        StoragePage.fromTitle(screen.title.string.replace(Regex("§."), ""))?.index ?: -1

    private fun matches(stack: ItemStack): Boolean {
        if (search.isBlank()) return true
        val q = search.lowercase()
        if (stack.hoverName.string.lowercase().contains(q)) return true
        val lore = stack.get(net.minecraft.core.component.DataComponents.LORE) ?: return false
        return lore.lines().any { it.string.lowercase().contains(q) }
    }

    // ── render ───────────────────────────────────────────────────────────────
    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screen: AbstractContainerScreen<*>) {
        if (!on(screen)) return
        slotHits.clear(); titleHits.clear()
        val mc = Minecraft.getInstance()

        runCatching { ctx.blurBeforeThisStratum() }
        runCatching { ctx.nextStratum() }
        ctx.fill(0, 0, screen.width, screen.height, BASE)

        val px = 16; val py = 14
        val px2 = screen.width - 16; val py2 = screen.height - 14
        panel(ctx, px, py, px2, py2)

        val active = activeIdx(screen)
        val pages = buildPages(screen, active)

        // header + search field
        ctx.text(mc.font, "§fStorage  §7${pages.size} pages", px + 12, py + 9, -1)
        val fw = 150; val fh = 14
        val fx = px2 - fw - 12; val fy = py + 6
        fieldHit = intArrayOf(fx, fy, fw, fh)
        ctx.fill(fx, fy, fx + fw, fy + fh, FIELD_BG)
        ctx.fill(fx, fy, fx + fw, fy + 1, if (searchFocused) EDGE_L else EDGE_D)
        val shown = if (search.isEmpty()) "§8search…" else "§f$search${if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0L) "_" else ""}"
        ctx.text(mc.font, shown, fx + 4, fy + 3, -1)
        // close X
        val cs = 12; val cx = px2 - cs - 4; val cy = py + 4
        closeHit = intArrayOf(cx, cy, cs, cs)
        ctx.text(mc.font, "§c✕", cx + 2, cy + 2, -1)

        val viewTop = py + 26
        val viewBot = py2 - 8
        runCatching { ctx.enableScissor(px + 8, viewTop, px2 - 8, viewBot) }

        val cols = FishSettings.storageViewerColumns.coerceIn(1, 6)
        val cellW = 9 * SLOT + 8
        val gridW = cellW * cols + 12 * (cols - 1)
        val gridLeft = max(px + 12, (px + px2) / 2 - gridW / 2)
        val topBase = viewTop + 6 - scroll
        var x = gridLeft; var y = topBase; var rowMax = 0; var col = 0
        var hoverStack: ItemStack? = null

        for ((idx, stacks) in pages) {
            val filtered = FishSettings.storageHideNonMatching && search.isNotBlank() && stacks.none { matches(it) }
            if (!filtered) {
                val rows = max(1, (stacks.size + 8) / 9)
                val cellH = 14 + rows * SLOT + 6
                if (y + cellH >= viewTop && y <= viewBot) {
                    hoverStack = drawPage(ctx, x, y, idx, idx == active, stacks, rows, mouseX, mouseY) ?: hoverStack
                }
                titleHits.add(intArrayOf(x, y, cellW, 14, idx))
                rowMax = max(rowMax, cellH)
                col++
                if (col >= cols) { col = 0; x = gridLeft; y += rowMax + 12; rowMax = 0 } else x += cellW + 12
            }
        }
        contentH = (y + rowMax) - topBase + 24
        runCatching { ctx.disableScissor() }

        val maxS = maxScroll(screen)
        if (maxS > 0) {
            val trackH = viewBot - viewTop
            val knobH = max(20, trackH * trackH / (trackH + maxS))
            val knobY = viewTop + (trackH - knobH) * scroll / maxS
            ctx.fill(px2 - 6, knobY, px2 - 3, knobY + knobH, KNOB)
        }
        if (pages.isEmpty()) ctx.text(mc.font, "§7Page through your ender chests / backpacks once and they'll cache here.", px + 16, viewTop + 16, -1)

        hoverStack?.let { ctx.setTooltipForNextFrame(mc.font, it, mouseX, mouseY) }
    }

    private fun buildPages(screen: AbstractContainerScreen<*>, active: Int): List<Pair<Int, List<ItemStack>>> {
        val out = sortedMapOf<Int, List<ItemStack>>()
        for ((i, inv) in StorageCache.view()) out[i] = inv.stacks
        if (active >= 0) {
            val menu = screen.menu
            val rc = (menu as? ChestMenu)?.rowCount ?: ((menu.slots.size - 36) / 9)
            if (rc > 1) out[active] = menu.slots.subList(9, rc * 9).map { it.item }
        }
        return out.entries.map { it.key to it.value }
    }

    private fun drawPage(ctx: GuiGraphicsExtractor, x: Int, y: Int, idx: Int, isActive: Boolean, stacks: List<ItemStack>, rows: Int, mouseX: Int, mouseY: Int): ItemStack? {
        val mc = Minecraft.getInstance()
        val page = StoragePage(idx)
        val cardX2 = x + 9 * SLOT + 8
        val cardY2 = y + 14 + rows * SLOT + 4
        ctx.fill(x - 2, y - 2, cardX2, cardY2, CARD)
        if (isActive) { // accent ring for the page you actually have open
            ctx.fill(x - 2, y - 2, cardX2, y - 1, EDGE_L)
            ctx.fill(x - 2, cardY2 - 1, cardX2, cardY2, EDGE_L)
        }
        val titleHover = mouseX in x..cardX2 && mouseY in y..(y + 14)
        ctx.text(mc.font, (if (titleHover) "§e" else if (isActive) "§a" else "§b") + page.name + (if (isActive) " §7(open)" else ""), x + 2, y + 2, -1)

        val gx = x + 3; val gy = y + 14
        ctx.fill(gx, gy, gx + 9 * SLOT, gy + rows * SLOT, CELL)
        var hovered: ItemStack? = null
        for (i in stacks.indices) {
            val sx = gx + (i % 9) * SLOT + 1
            val sy = gy + (i / 9) * SLOT + 1
            val stack = stacks[i]
            if (!stack.isEmpty) {
                ctx.item(stack, sx, sy)
                ctx.itemDecorations(mc.font, stack, sx, sy)
                if (search.isNotBlank() && !matches(stack)) ctx.fill(sx, sy, sx + 16, sy + 16, DIM)
            }
            slotHits.add(intArrayOf(sx, sy, idx, i))
            if (mouseX in sx..(sx + 16) && mouseY in sy..(sy + 16) && !stack.isEmpty) hovered = stack
        }
        for (c in 0..9) ctx.fill(gx + c * SLOT, gy, gx + c * SLOT + 1, gy + rows * SLOT, GRID)
        for (r in 0..rows) ctx.fill(gx, gy + r * SLOT, gx + 9 * SLOT, gy + r * SLOT + 1, GRID)
        return hovered
    }

    private fun panel(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int) {
        ctx.fill(x1, y1, x2, y2, PANEL)
        runCatching { ctx.fillGradient(x1, y1, x2, y1 + (y2 - y1) / 2, GLASS_TOP, GLASS_BOT) }
        ctx.fill(x1, y1, x2, y1 + 1, EDGE_L)
        ctx.fill(x1, y1, x1 + 1, y2, EDGE_L)
        ctx.fill(x1, y2 - 1, x2, y2, EDGE_D)
        ctx.fill(x2 - 1, y1, x2, y2, EDGE_D)
    }

    private fun maxScroll(screen: AbstractContainerScreen<*>) =
        (contentH - (screen.height - 2 * 14 - 26 - 8)).coerceAtLeast(0)

    // ── input ────────────────────────────────────────────────────────────────
    @JvmStatic
    fun mouseClicked(button: Int, mx: Double, my: Double, screen: AbstractContainerScreen<*>): Boolean {
        if (!on(screen)) return false
        val ix = mx.toInt(); val iy = my.toInt()

        if (hit(closeHit, ix, iy)) { screen.onClose(); return true }
        if (hit(fieldHit, ix, iy)) { searchFocused = true; return true }
        searchFocused = false

        val active = activeIdx(screen)
        for (s in slotHits) {
            if (ix in s[0]..(s[0] + 16) && iy in s[1]..(s[1] + 16)) {
                if (s[2] == active) {
                    val mc = Minecraft.getInstance()
                    val p = mc.player ?: return true
                    val shift = GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                        GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
                    val type = if (shift) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP
                    mc.gameMode?.handleContainerInput(p.containerMenu.containerId, 9 + s[3], button, type, p)
                } else {
                    StoragePage(s[2]).open()
                }
                return true
            }
        }
        for (t in titleHits) if (ix in t[0]..(t[0] + t[2]) && iy in t[1]..(t[1] + t[3])) { StoragePage(t[4]).open(); return true }
        return true // swallow anything else over the panel
    }

    @JvmStatic
    fun mouseScrolled(amount: Double, screen: AbstractContainerScreen<*>): Boolean {
        if (!on(screen)) return false
        scroll = (scroll - (amount * 30).toInt()).coerceIn(0, maxScroll(screen))
        return true
    }

    /** @return true to swallow the key. */
    @JvmStatic
    fun keyPressed(key: Int, screen: AbstractContainerScreen<*>): Boolean {
        if (!on(screen) || !searchFocused) return false
        when (key) {
            GLFW.GLFW_KEY_ESCAPE -> { searchFocused = false; return true }
            GLFW.GLFW_KEY_BACKSPACE -> { if (search.isNotEmpty()) search = search.dropLast(1); return true }
            GLFW.GLFW_KEY_SPACE -> { search += ' '; return true }
            in GLFW.GLFW_KEY_A..GLFW.GLFW_KEY_Z -> { search += ('a' + (key - GLFW.GLFW_KEY_A)); return true }
            in GLFW.GLFW_KEY_0..GLFW.GLFW_KEY_9 -> { search += ('0' + (key - GLFW.GLFW_KEY_0)); return true }
        }
        return true
    }

    @JvmStatic
    fun onClosed() { searchFocused = false }

    private fun hit(r: IntArray, x: Int, y: Int) = r.size == 4 && x in r[0]..(r[0] + r[2]) && y in r[1]..(r[1] + r[3])
}
