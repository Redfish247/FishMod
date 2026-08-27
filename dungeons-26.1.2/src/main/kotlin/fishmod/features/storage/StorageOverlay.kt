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

/**
 * Storage Overlay — a close port of NoammAddons' StorageOverlayScreen: while a Storage / ender-chest
 * / backpack GUI is open, draw every cached page (the open one live) in a scrollable panel with the
 * player inventory beneath, a search box and a scroll bar. Clicking a slot on the open page does a
 * real container click; clicking anything on another (or an un-cached) page opens it.
 */
object StorageOverlay {

    private const val SLOT = 17
    private const val PAD = 10
    private const val PAGE_W = SLOT * 9 + 4
    private const val SCROLL_W = 8
    private const val SCROLL_KNOB = 16
    private const val PLAYER_W = SLOT * 9 + 6
    private const val PLAYER_H = SLOT * 4 + 18

    // liquid-glass palette (translucent — the game is blurred behind)
    private val MENU_BG = 0x55_1B2130
    private val GLASS_TOP = 0x26_FFFFFF
    private val GLASS_BOT = 0x06_FFFFFF
    private val MENU_BORDER = 0x55_FFFFFF
    private val EDGE_D = 0x33_000000
    private val CELL_BG = 0x2A_10121C
    private val CELL_BORDER = 0x22_8FA0C0
    private val UNLOADED_BG = 0x33_2A3242
    private val ACTIVE_BORDER = 0xFF3BC9C0.toInt()
    private val SCROLL_BG = 0x33_101018
    private val SCROLL_FG = 0x66_C8D2E6
    private val FIELD_BG = 0x50_0A0C14
    private val BLACKOUT = 0x66_0A0A12
    private val DIM = 0xB0000000.toInt()

    @Volatile var search = ""
    private var searchFocused = false
    private var scroll = 0f
    private var innerHeight = 0

    private val slotHits = ArrayList<IntArray>()   // x, y, pageIdx, cacheIdx
    private val pageHits = ArrayList<IntArray>()    // x, y, w, h, pageIdx
    private val playerHits = ArrayList<IntArray>()  // x, y, playerSlot
    private var fieldHit = IntArray(4)
    private var scrollBar = IntArray(4)

    private fun on(screen: AbstractContainerScreen<*>): Boolean {
        if (!FishSettings.storageOverlayEnabled) return false
        val t = screen.title.string.replace(Regex("§."), "")
        return t == "Storage" || StoragePage.fromTitle(t) != null
    }

    private fun activeIdx(screen: AbstractContainerScreen<*>): Int =
        StoragePage.fromTitle(screen.title.string.replace(Regex("§."), ""))?.index ?: -1

    private fun matches(s: ItemStack): Boolean {
        if (search.isBlank()) return true
        val q = search.lowercase()
        if (s.hoverName.string.lowercase().contains(q)) return true
        val lore = s.get(net.minecraft.core.component.DataComponents.LORE) ?: return false
        return lore.lines().any { it.string.lowercase().contains(q) }
    }

    // ── render ───────────────────────────────────────────────────────────────
    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screen: AbstractContainerScreen<*>) {
        if (!on(screen)) return
        slotHits.clear(); pageHits.clear(); playerHits.clear()
        val mc = Minecraft.getInstance()
        val font = mc.font
        val W = screen.width; val H = screen.height

        // frost the game, then a translucent tint over the (now hidden) vanilla GUI
        runCatching { ctx.blurBeforeThisStratum() }
        runCatching { ctx.nextStratum() }
        ctx.fill(0, 0, W, H, BLACKOUT)

        val active = activeIdx(screen)
        val pages: List<Pair<Int, List<ItemStack>?>> = buildPages(screen, active)

        val cols = FishSettings.storageViewerColumns.coerceIn(1, 10)
            .coerceAtMost(max(1, (W - PAD) / (PAGE_W + PAD)))
        val innerW = PAGE_W * cols + (cols - 1) * PAD
        val overviewW = innerW + 3 * PAD + SCROLL_W
        val overviewH = (H - PLAYER_H - minOf(80, H / 10))
            .coerceAtMost(FishSettings.storageMaxHeight.coerceIn(120, 900)).coerceIn(120, 900)
        val innerH = overviewH - PAD * 2
        val ox = W / 2 - overviewW / 2
        val oy = H / 2 - (overviewH + PLAYER_H) / 2
        val playerX = W / 2 - PLAYER_W / 2
        val playerY = oy + overviewH + 2

        // main panel — glass: translucent base, top sheen, bright top/left lip, dark bottom/right
        ctx.fill(ox, oy, ox + overviewW, oy + overviewH, MENU_BG)
        runCatching { ctx.fillGradient(ox, oy, ox + overviewW, oy + overviewH / 2, GLASS_TOP, GLASS_BOT) }
        ctx.fill(ox, oy, ox + overviewW, oy + 1, MENU_BORDER)
        ctx.fill(ox, oy, ox + 1, oy + overviewH, MENU_BORDER)
        ctx.fill(ox, oy + overviewH - 1, ox + overviewW, oy + overviewH, EDGE_D)
        ctx.fill(ox + overviewW - 1, oy, ox + overviewW, oy + overviewH, EDGE_D)

        // header: title + search field
        ctx.text(font, "§fStorage  §7${pages.size} pages", ox + PAD, oy + 4, -1)
        val fw = 150; val fh = 12
        val fx = ox + overviewW - fw - PAD; val fy = oy + 2
        fieldHit = intArrayOf(fx, fy, fw, fh)
        ctx.fill(fx, fy, fx + fw, fy + fh, FIELD_BG)
        border(ctx, fx, fy, fw, fh, if (searchFocused) ACTIVE_BORDER else CELL_BORDER)
        ctx.text(font, if (search.isEmpty()) "§8search…" else "§f$search${if (searchFocused && blink()) "_" else ""}", fx + 3, fy + 2, -1)

        // scrolling page area
        val panelX = ox + PAD
        val panelY = oy + PAD + 12
        val panelH = innerH - 12
        runCatching { ctx.enableScissor(panelX, panelY, panelX + innerW + 2, panelY + panelH) }

        var y = panelY - scroll.toInt()
        var x = panelX
        var col = 0
        var rowMax = 0
        var hoverStack: ItemStack? = null

        for ((idx, stacks) in pages) {
            if (FishSettings.storageHideNonMatching && search.isNotBlank() && stacks != null && stacks.none { matches(it) }) continue
            val ph: Int
            if (stacks == null) {
                ctx.fill(x, y, x + PAGE_W, y + 18, UNLOADED_BG)
                border(ctx, x, y, PAGE_W, 18, MENU_BORDER)
                ctx.text(font, "§7${StoragePage(idx).name} §8— click to load", x + 4, y + 5, -1)
                pageHits.add(intArrayOf(x, y, PAGE_W, 18, idx))
                ph = 18
            } else {
                val rows = max(1, (stacks.size + 8) / 9)
                val slotsY = y + 5 + font.lineHeight
                ph = rows * SLOT + 8 + font.lineHeight
                val isActive = idx == active
                if (isActive) border(ctx, x, y, PAGE_W + 1, ph, ACTIVE_BORDER)
                ctx.text(font, (if (isActive) "§b" else "§f") + StoragePage(idx).name + (if (isActive) " §7(open)" else ""), x + 4, y + 3, -1)
                grid(ctx, x + 2, slotsY, rows)
                for (i in stacks.indices) {
                    val sx = x + 3 + (i % 9) * SLOT
                    val sy = slotsY + 1 + (i / 9) * SLOT
                    val st = stacks[i]
                    if (!st.isEmpty) {
                        ctx.item(st, sx, sy)
                        ctx.itemDecorations(font, st, sx, sy)
                        if (search.isNotBlank() && !matches(st)) ctx.fill(sx, sy, sx + 16, sy + 16, DIM)
                    }
                    slotHits.add(intArrayOf(sx, sy, idx, i))
                    if (mouseX in sx..(sx + 16) && mouseY in sy..(sy + 16) && !st.isEmpty) hoverStack = st
                }
                pageHits.add(intArrayOf(x, y, PAGE_W, 5 + font.lineHeight, idx))
            }
            rowMax = max(rowMax, ph + 6)
            col++
            if (col >= cols) { col = 0; x = panelX; y += rowMax; rowMax = 0 } else x += PAGE_W + PAD
        }
        innerHeight = (y + rowMax - (panelY - scroll.toInt()))
        runCatching { ctx.disableScissor() }

        // scroll bar
        val sbX = ox + PAD + innerW + PAD
        scrollBar = intArrayOf(sbX, panelY, SCROLL_W, panelH)
        ctx.fill(sbX, panelY, sbX + SCROLL_W, panelY + panelH, SCROLL_BG)
        val maxS = maxScroll(panelH)
        if (maxS > 0f) {
            val ky = panelY + ((scroll / maxS) * (panelH - SCROLL_KNOB)).toInt()
            ctx.fill(sbX, ky, sbX + SCROLL_W, ky + SCROLL_KNOB, SCROLL_FG)
        }

        // player inventory
        drawPlayerInv(ctx, screen, playerX, playerY, mouseX, mouseY)?.let { hoverStack = it }

        if (pages.isEmpty()) ctx.text(font, "§7Page through /storage once to cache your pages.", panelX + 6, panelY + 6, -1)
        hoverStack?.let { ctx.setTooltipForNextFrame(font, it, mouseX, mouseY) }
    }

    private fun drawPlayerInv(ctx: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>, px: Int, py: Int, mouseX: Int, mouseY: Int): ItemStack? {
        val items = Minecraft.getInstance().player?.inventory?.nonEquipmentItems ?: return null
        val font = Minecraft.getInstance().font
        val baseX = px + (PLAYER_W - 9 * SLOT) / 2
        val invY = py + 8
        val hotY = invY + 3 * SLOT + 4
        grid(ctx, baseX - 1, invY - 1, 3)
        grid(ctx, baseX - 1, hotY - 1, 1)
        var hover: ItemStack? = null
        for (i in 0 until 36) {
            val col = i % 9
            val sx = baseX + col * SLOT
            val sy = if (i < 9) hotY else invY + (i / 9 - 1) * SLOT
            val st = items[i]
            if (!st.isEmpty) { ctx.item(st, sx, sy); ctx.itemDecorations(font, st, sx, sy) }
            playerHits.add(intArrayOf(sx, sy, i))
            if (mouseX in sx..(sx + 16) && mouseY in sy..(sy + 16) && !st.isEmpty) hover = st
        }
        return hover
    }

    private fun buildPages(screen: AbstractContainerScreen<*>, active: Int): List<Pair<Int, List<ItemStack>?>> {
        val map = sortedMapOf<Int, List<ItemStack>?>()
        for ((i, inv) in StorageCache.view()) map[i] = inv.stacks
        // only offer "click to load" for pages we know exist (learned from the /storage overview)
        for (i in StorageCache.knownPages()) map.putIfAbsent(i, null)
        if (active >= 0) {
            val menu = screen.menu
            val rc = (menu as? ChestMenu)?.rowCount ?: ((menu.slots.size - 36) / 9)
            if (rc > 1) {
                val live = menu.slots.subList(9, rc * 9).map { it.item }
                map[active] = live
                StorageCache.put(active, live)   // keep the cache fresh even for a quick click-through
            }
        }
        return map.entries.map { it.key to it.value }
    }

    private fun grid(ctx: GuiGraphicsExtractor, x: Int, y: Int, rows: Int) {
        val w = 9 * SLOT; val h = rows * SLOT
        ctx.fill(x, y, x + w, y + h, CELL_BG)
        for (c in 0..9) ctx.fill(x + c * SLOT, y, x + c * SLOT + 1, y + h, CELL_BORDER)
        for (r in 0..rows) ctx.fill(x, y + r * SLOT, x + w, y + r * SLOT + 1, CELL_BORDER)
    }

    private fun border(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, c: Int) {
        ctx.fill(x, y, x + w, y + 1, c)
        ctx.fill(x, y + h - 1, x + w, y + h, c)
        ctx.fill(x, y, x + 1, y + h, c)
        ctx.fill(x + w - 1, y, x + w, y + h, c)
    }

    private fun blink() = (System.currentTimeMillis() / 500) % 2 == 0L
    private fun maxScroll(panelH: Int) = (innerHeight - panelH + 6).coerceAtLeast(0).toFloat()

    // ── input ────────────────────────────────────────────────────────────────
    @JvmStatic
    fun mouseClicked(button: Int, mx: Double, my: Double, screen: AbstractContainerScreen<*>): Boolean {
        if (!on(screen)) return false
        val ix = mx.toInt(); val iy = my.toInt()
        if (hit(fieldHit, ix, iy)) { searchFocused = true; return true }
        searchFocused = false

        val mc = Minecraft.getInstance()
        val p = mc.player ?: return true
        val menu = p.containerMenu
        val shift = GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
        val type = if (shift) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP
        val active = activeIdx(screen)

        for (s in slotHits) {
            if (ix in s[0]..(s[0] + 16) && iy in s[1]..(s[1] + 16)) {
                if (s[2] == active) mc.gameMode?.handleContainerInput(menu.containerId, 9 + s[3], button, type, p)
                else StoragePage(s[2]).open()
                return true
            }
        }
        for (h in playerHits) {
            if (ix in h[0]..(h[0] + 16) && iy in h[1]..(h[1] + 16)) {
                val chestSize = menu.slots.size - 36
                mc.gameMode?.handleContainerInput(menu.containerId, chestSize + h[2], button, type, p)
                return true
            }
        }
        for (t in pageHits) if (ix in t[0]..(t[0] + t[2]) && iy in t[1]..(t[1] + t[3])) {
            if (t[4] != active) StoragePage(t[4]).open()
            return true
        }
        return true // swallow clicks over the blackout
    }

    @JvmStatic
    fun mouseScrolled(amount: Double, screen: AbstractContainerScreen<*>): Boolean {
        if (!on(screen)) return false
        val panelH = ((screen.height - PLAYER_H - minOf(80, screen.height / 10)).coerceIn(120, 600) - 20 - 12)
        scroll = (scroll - amount.toFloat() * 24f).coerceIn(0f, maxScroll(panelH))
        return true
    }

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
    fun onClosed() { searchFocused = false; scroll = 0f }

    private fun hit(r: IntArray, x: Int, y: Int) = r.size == 4 && x in r[0]..(r[0] + r[2]) && y in r[1]..(r[1] + r[3])
}
