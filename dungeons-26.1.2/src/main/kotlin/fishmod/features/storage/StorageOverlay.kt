package fishmod.features.storage

import fishmod.features.ScreenTheme
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.TreeMap

/**
 * Storage overlay — a faithful port of NoammAddons' `StorageOverlayScreen` (Noamm9/NoammAddons,
 * branch 26.1.2), wired to FishMod's mixin hooks and render helpers instead of Noamm's Render2D /
 * ItemRenderer / Resolution / config stack. Page contents come from [StorageCache] (captured as you
 * page through `/storage`). Layout math, drag-to-place, scrollbar grab and page centering match the
 * original.
 */
object StorageOverlay {

    // ── layout constants (Noamm) ─────────────────────────────────────────────
    private const val SLOT_SIZE = 17           // 17 not 16 — 1px border
    private const val PADDING = 10
    private const val PAGE_WIDTH = SLOT_SIZE * 9 + 4
    private const val ACTIVE_PAGE_BORDER_THICKNESS = 2
    private const val SCROLL_BAR_WIDTH = 8
    private const val SCROLL_BAR_HEIGHT = 16
    private const val PLAYER_WIDTH = SLOT_SIZE * 9 + 6
    private const val PLAYER_HEIGHT = SLOT_SIZE * 4 + 18

    // ── colours (Noamm's java.awt.Color values -> ARGB) ──────────────────────
    private const val MENU_BG = 0xFF18181B.toInt()
    private const val MENU_BORDER = 0xFF3C3C41.toInt()
    private const val SLOT_BG = 0xC8323237.toInt()
    private const val SLOT_CELL_BG = 0xFF1E1E22.toInt()
    private const val SLOT_CELL_BORDER = 0xFF37373C.toInt()
    private const val SCROLL_BG = 0xB41E1E23.toInt()
    private const val SCROLL_KNOB = 0xFF787882.toInt()
    private const val HOVER_WHITE = 0x32FFFFFF
    private const val TEXT_DIM = 0xFFB4B4B4.toInt()
    private const val SEARCH_MATCH = 0x5533C9C0
    private val ACCENT get() = ScreenTheme.ACCENT

    // ── per-open state ───────────────────────────────────────────────────────
    private var scroll = 0f
    private var lastRenderedInnerHeight = 0
    private var pageWidthCount = 3
    private var knobGrabbed = false
    private var hoveredOverlayItem: ItemStack? = null

    private var dragType = 0
    private var dragStartSlot: Slot? = null
    private val dragSlots = LinkedHashSet<Int>()
    private var dragPreview: DragPreview? = null
    private val dragArmed get() = dragStartSlot != null
    private val dragActive get() = dragSlots.size >= 2

    // search (FishMod keeps a small field — Noamm relies on a global InventorySearch we don't have)
    var search = ""
    private var searchFocused = false

    private val mc get() = Minecraft.getInstance()
    private val font get() = mc.font
    private val scale get() = FishSettings.storageOverlayScale.coerceIn(0.5, 2.0).toFloat()

    private fun on(screen: AbstractContainerScreen<*>): Boolean {
        if (!FishSettings.storageOverlayEnabled) return false
        val t = screen.title.string.replace(Regex("§."), "")
        return t == "Storage" || StoragePage.fromTitle(t) != null
    }

    /** True while the overlay covers this screen — used by the mixin to suppress vanilla slot draw. */
    @JvmStatic
    fun isActive(screen: AbstractContainerScreen<*>): Boolean = on(screen)

    private fun activePage(screen: AbstractContainerScreen<*>): StoragePage? =
        StoragePage.fromTitle(screen.title.string.replace(Regex("§."), ""))

    // ── data: build Noamm's SortedMap<StoragePage, NBTInventory?> from StorageCache ──
    private fun allData(): TreeMap<StoragePage, NBTInventory?> {
        val out = TreeMap<StoragePage, NBTInventory?>()
        val view = StorageCache.view()
        for (i in (StorageCache.knownPages() + view.keys).sorted()) {
            val inv = view[i]
            // If the API told us this page's real size, drop a cached snapshot that's smaller than
            // that (a stale partial capture) and show "click to load" instead.
            val exp = StorageCache.expectedRows(i)
            out[StoragePage(i)] = if (inv != null && exp != null && inv.rows < exp) null else inv
        }
        return out
    }

    private val isSearching get() = search.isNotBlank()
    private val shouldFilterPages get() = FishSettings.storageHideNonMatching && isSearching

    private fun matches(s: ItemStack): Boolean {
        if (s.isEmpty) return false
        val q = search.lowercase()
        if (s.hoverName.string.lowercase().contains(q)) return true
        val lore = s.get(DataComponents.LORE) ?: return false
        return lore.lines().any { it.string.lowercase().contains(q) }
    }

    private fun visibleData(activePage: StoragePage?, activeSlots: List<Slot>?): TreeMap<StoragePage, NBTInventory?> {
        val data = allData()
        if (!shouldFilterPages) return data
        return TreeMap<StoragePage, NBTInventory?>().apply {
            for ((page, inv) in data) {
                val hit = if (page == activePage && activeSlots != null) activeSlots.any { matches(it.item) }
                else inv?.stacks?.any(::matches) == true
                if (hit) this[page] = inv
            }
        }
    }

    // ── geometry (Noamm Measurements) ────────────────────────────────────────
    private var vw = 0
    private var vh = 0
    private var mx0 = 0; private var my0 = 0
    private var overviewW = 0; private var overviewH = 0
    private var innerW = 0; private var innerH = 0
    private var playerX0 = 0; private var playerY0 = 0

    private fun recomputeGeometry() {
        vw = (mc.window.guiScaledWidth / scale).toInt()
        vh = (mc.window.guiScaledHeight / scale).toInt()
        pageWidthCount = FishSettings.storageViewerColumns.coerceIn(1, 10)
            .coerceAtMost(((vw - PADDING) / (PAGE_WIDTH + PADDING)).coerceAtLeast(1))
        innerW = PAGE_WIDTH * pageWidthCount + (pageWidthCount - 1) * PADDING
        overviewW = innerW + 3 * PADDING + SCROLL_BAR_WIDTH
        mx0 = vw / 2 - overviewW / 2
        // leave a margin at top and bottom so the panel + player inv never touch the screen edge
        val avail = vh - PLAYER_HEIGHT - 12
        overviewH = minOf(avail, FishSettings.storageMaxHeight.coerceIn(80, 900)).coerceAtLeast(80)
        innerH = overviewH - PADDING * 2
        my0 = (vh / 2 - (overviewH + PLAYER_HEIGHT) / 2).coerceAtLeast(6)
        playerX0 = vw / 2 - PLAYER_WIDTH / 2
        playerY0 = my0 + overviewH + 2
    }

    private val scrollPanelX get() = mx0 + PADDING
    private val scrollPanelY get() = my0 + PADDING
    private val scrollPanelW get() = innerW
    private val scrollPanelH get() = innerH
    private val scrollBarX get() = mx0 + PADDING + innerW + PADDING
    private val scrollBarY get() = my0 + PADDING
    private val scrollBarH get() = innerH
    private val maxScroll get() = (lastRenderedInnerHeight.toFloat() + 6 - innerH).coerceAtLeast(0f)

    private fun screenMenu(): AbstractContainerMenu? =
        (mc.screen as? AbstractContainerScreen<*>)?.menu

    private fun rowCountOf(menu: AbstractContainerMenu): Int =
        (menu as? ChestMenu)?.rowCount ?: ((menu.slots.size - 36) / 9)

    // ── entry points (called from HandledScreenMixin) ───────────────────────
    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screen: AbstractContainerScreen<*>) {
        if (!on(screen)) return
        recomputeGeometry()
        dragPreview = computeDragPreview()
        val prevHovered = hoveredOverlayItem
        hoveredOverlayItem = null

        val s = scale
        ctx.pose().pushMatrix()
        ctx.pose().scale(s, s)
        val smx = (mouseX / s).toInt()
        val smy = (mouseY / s).toInt()

        // frost + a light dim; the vanilla slots are suppressed by the mixin (extractSlots cancel),
        // so the game stays visible around the panel without the "weird boxes".
        runCatching { ctx.blurBeforeThisStratum() }
        runCatching { ctx.nextStratum() }
        rect(ctx, 0, 0, vw + 2, vh + 2, 0x66_0A0A12)

        val menu = screen.menu
        val chestEnd = rowCountOf(menu) * 9
        val chestSlots = if (chestEnd > 9) menu.slots.subList(9, chestEnd) else emptyList()
        val active = activePage(screen)
        val data = visibleData(active, chestSlots)
        if (shouldFilterPages) updateLayoutHeight(data)

        // main panel
        rect(ctx, mx0, my0, overviewW, overviewH, MENU_BG)
        border(ctx, mx0, my0, overviewW, overviewH, MENU_BORDER, 1)

        drawHeader(ctx, smx, smy)
        drawPages(ctx, data, smx, smy, active, chestSlots, mouseX, mouseY)
        drawScrollBar(ctx)
        drawPlayerInventory(ctx, smx, smy, mouseX, mouseY)
        drawPagesDecorations(ctx, data, active, chestSlots)
        drawPlayerInventoryDecorations(ctx)
        drawCarriedItem(ctx, smx, smy)

        ctx.pose().popMatrix()

        if (hoveredOverlayItem !== prevHovered) { /* tooltip-scroll reset hook — no-op here */ }
    }

    private fun drawHeader(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        ctx.text(font, "§fStorage  §7${allData().size} pages", mx0 + PADDING, my0 + 4, -1, false)
        // small search field, right side of the header
        val fw = 130; val fh = 12
        val fx = mx0 + overviewW - fw - PADDING; val fy = my0 + 2
        rect(ctx, fx, fy, fw, fh, 0x500A0C14)
        border(ctx, fx, fy, fw, fh, if (searchFocused) ACCENT else SLOT_CELL_BORDER, 1)
        val shown = if (search.isEmpty()) "§8search…" else "§f$search${if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0L) "_" else ""}"
        ctx.text(font, shown, fx + 3, fy + 2, -1, false)
        searchFieldRect = intArrayOf(fx, fy, fw, fh)
    }

    private var searchFieldRect = IntArray(4)

    // ── page grid ───────────────────────────────────────────────────────────
    private inline fun layoutedForEach(
        data: TreeMap<StoragePage, NBTInventory?>,
        func: (x: Int, y: Int, pageWidth: Int, pageHeight: Int, page: StoragePage, inv: NBTInventory?) -> Unit,
    ) {
        var yOffset = -scroll.toInt()
        var xOffset = 0
        var maxHeight = 0
        for ((page, inv) in data.entries) {
            val h = inv?.let { it.rows * SLOT_SIZE + 6 + font.lineHeight } ?: 18
            maxHeight = maxOf(maxHeight, h)
            val rectX = mx0 + PADDING + (PAGE_WIDTH + PADDING) * xOffset
            val rectY = yOffset + my0 + PADDING
            func(rectX, rectY, PAGE_WIDTH, h, page, inv)
            xOffset++
            if (xOffset >= pageWidthCount) { yOffset += maxHeight; xOffset = 0; maxHeight = 0 }
        }
        lastRenderedInnerHeight = maxHeight + yOffset + scroll.toInt()
    }

    private fun updateLayoutHeight(data: TreeMap<StoragePage, NBTInventory?>) {
        lastRenderedInnerHeight = data.entries.chunked(pageWidthCount)
            .sumOf { row -> row.maxOf { (_, inv) -> inv?.let { it.rows * SLOT_SIZE + 6 + font.lineHeight } ?: 18 } }
        scroll = scroll.coerceIn(0f, maxScroll)
    }

    private fun drawPages(
        ctx: GuiGraphicsExtractor, data: TreeMap<StoragePage, NBTInventory?>,
        mouseX: Int, mouseY: Int, excluding: StoragePage?, slots: List<Slot>?, origMx: Int, origMy: Int,
    ) {
        scissor(ctx, scrollPanelX, scrollPanelY, scrollPanelW + ACTIVE_PAGE_BORDER_THICKNESS, scrollPanelH)
        val viewTop = scrollPanelY
        val viewBot = scrollPanelY + scrollPanelH
        layoutedForEach(data) { x, y, _, ph, page, inv ->
            if (y + ph < viewTop || y > viewBot) return@layoutedForEach
            drawPage(ctx, x, y, page, inv, if (excluding == page) slots else null, mouseX, mouseY, origMx, origMy)
        }
        ctx.disableScissor()
    }

    private fun drawPagesDecorations(
        ctx: GuiGraphicsExtractor, data: TreeMap<StoragePage, NBTInventory?>, excluding: StoragePage?, slots: List<Slot>?,
    ) {
        scissor(ctx, scrollPanelX, scrollPanelY, scrollPanelW + ACTIVE_PAGE_BORDER_THICKNESS, scrollPanelH)
        val viewTop = scrollPanelY
        val viewBot = scrollPanelY + scrollPanelH
        layoutedForEach(data) { x, y, _, ph, page, inv ->
            if (y + ph < viewTop || y > viewBot) return@layoutedForEach
            val rows = inv?.rows ?: (if (excluding == page) (slots?.size?.div(9)?.coerceIn(1, 5) ?: 3) else 0)
            if (rows == 0 && inv == null) return@layoutedForEach
            val slotsY = y + 5 + font.lineHeight
            val invStacks = inv?.stacks
            val count = invStacks?.size ?: (if (excluding == page) slots?.size ?: (rows * 9) else 0)
            for (i in 0 until count) {
                val sx = (i % 9) * SLOT_SIZE + x + 3
                val sy = (i / 9) * SLOT_SIZE + slotsY + 1
                if (sy + 16 < viewTop || sy > viewBot) continue
                val menuSlot = if (excluding == page && slots != null && i < slots.size) slots[i] else null
                val deco = menuSlot?.let { dragPreview?.stacks?.get(it.index) } ?: menuSlot?.item ?: invStacks?.getOrNull(i) ?: continue
                if (!deco.isEmpty) ctx.itemDecorations(font, deco, sx, sy)
            }
        }
        ctx.disableScissor()
    }

    private fun drawPage(
        ctx: GuiGraphicsExtractor, x: Int, y: Int, page: StoragePage, inv: NBTInventory?, slots: List<Slot>?,
        mouseX: Int, mouseY: Int, origMx: Int, origMy: Int,
    ): Int {
        if (inv == null && slots == null) {
            rect(ctx, x, y, PAGE_WIDTH, 18, SLOT_BG)
            border(ctx, x, y, PAGE_WIDTH, 18, MENU_BORDER, 1)
            ctx.text(font, "${page.name} - Click to load", x + 4, y + 5, TEXT_DIM, false)
            return 18
        }
        val rows = inv?.rows ?: (slots?.size?.div(9)?.coerceIn(1, 5) ?: 3)
        val isActive = slots != null
        val slotsY = y + 5 + font.lineHeight
        val pageHeight = rows * SLOT_SIZE + 8 + font.lineHeight

        if (isActive) border(ctx, x, y, PAGE_WIDTH + 1, pageHeight, ACCENT, ACTIVE_PAGE_BORDER_THICKNESS)
        ctx.text(font, Component.literal(page.name), x + 6, y + 3, if (isActive) ACCENT else -1, true)

        drawSlotGrid(ctx, x + 2, slotsY, rows)

        val invStacks = inv?.stacks
        val count = invStacks?.size ?: (slots?.size ?: (rows * 9))
        var hovered: ItemStack? = null
        for (i in 0 until count) {
            val sx = (i % 9) * SLOT_SIZE + x + 3
            val sy = (i / 9) * SLOT_SIZE + slotsY + 1
            if (sy + 16 < scrollPanelY || sy > scrollPanelY + scrollPanelH) continue
            val menuSlot = if (slots != null && i < slots.size) slots[i] else null
            val display = menuSlot?.item ?: invStacks?.getOrNull(i) ?: continue
            val renderStack = menuSlot?.let { dragPreview?.stacks?.get(it.index) } ?: display
            val hot = inRect(mouseX, mouseY, sx - 1, sy - 1, 18, 18) &&
                inRect(mouseX, mouseY, scrollPanelX, scrollPanelY, scrollPanelW, scrollPanelH)
            if (!renderStack.isEmpty) {
                if (isSearching && matches(renderStack)) rect(ctx, sx, sy, 16, 16, SEARCH_MATCH)
                ctx.item(renderStack, sx, sy)
                if (hot && hovered == null && !display.isEmpty) hovered = display
            }
            if (hot) rect(ctx, sx, sy, 16, 16, HOVER_WHITE)
        }
        if (hovered != null) {
            if (isActive) hoveredOverlayItem = hovered
            ctx.setTooltipForNextFrame(font, hovered, origMx, origMy)
        }
        return pageHeight + 6
    }

    private fun drawSlotGrid(ctx: GuiGraphicsExtractor, x: Int, y: Int, rows: Int) {
        val w = 9 * SLOT_SIZE
        val h = rows * SLOT_SIZE
        ctx.fill(x, y, x + w, y + h, SLOT_CELL_BG)
        for (c in 0..9) ctx.fill(x + c * SLOT_SIZE, y, x + c * SLOT_SIZE + 1, y + h, SLOT_CELL_BORDER)
        for (r in 0..rows) ctx.fill(x, y + r * SLOT_SIZE, x + w, y + r * SLOT_SIZE + 1, SLOT_CELL_BORDER)
    }

    private fun drawScrollBar(ctx: GuiGraphicsExtractor) {
        rect(ctx, scrollBarX, scrollBarY, SCROLL_BAR_WIDTH, scrollBarH, SCROLL_BG)
        val ms = maxScroll
        val pct = if (ms > 0) scroll / ms else 0f
        val knobY = scrollBarY + (pct * (scrollBarH - SCROLL_BAR_HEIGHT)).toInt()
        rect(ctx, scrollBarX, knobY, SCROLL_BAR_WIDTH, SCROLL_BAR_HEIGHT, SCROLL_KNOB)
    }

    // ── player inventory ────────────────────────────────────────────────────
    private fun playerSlotPos(index: Int): Pair<Int, Int> {
        val slotsWidth = 9 * SLOT_SIZE
        val baseX = playerX0 + (PLAYER_WIDTH - slotsWidth) / 2 - SLOT_SIZE / 2 + 1
        val baseY = playerY0 + 8
        return if (index < 9) Pair(baseX + index * SLOT_SIZE, baseY + 3 * SLOT_SIZE + 4)
        else Pair(baseX + (index % 9) * SLOT_SIZE, baseY + (index / 9 - 1) * SLOT_SIZE)
    }

    private fun playerSlotIndexAt(mouseX: Int, mouseY: Int): Int? {
        for (i in 0 until 36) {
            val (sx, sy) = playerSlotPos(i)
            if (inRect(mouseX, mouseY, sx, sy, 17, 17)) return i
        }
        return null
    }

    private fun drawPlayerInventory(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, origMx: Int, origMy: Int) {
        val items = mc.player?.inventory?.nonEquipmentItems ?: return
        val (invX, invY) = playerSlotPos(9)
        val (hotX, hotY) = playerSlotPos(0)
        drawSlotGrid(ctx, invX - 1, invY - 1, 3)
        drawSlotGrid(ctx, hotX - 1, hotY - 1, 1)
        var hovered: ItemStack? = null
        for (i in 0 until 36) {
            val item = items[i]
            val renderStack = dragPreview?.playerStacks?.get(i) ?: item
            val (sx, sy) = playerSlotPos(i)
            val hot = inRect(mouseX, mouseY, sx - 1, sy - 1, 18, 18)
            if (!renderStack.isEmpty) {
                if (isSearching && matches(renderStack)) rect(ctx, sx, sy, 16, 16, SEARCH_MATCH)
                ctx.item(renderStack, sx, sy)
                if (hovered == null && hot && !item.isEmpty) hovered = item
            }
            if (hot) rect(ctx, sx, sy, 16, 16, HOVER_WHITE)
        }
        if (hovered != null) {
            hoveredOverlayItem = hovered
            ctx.setTooltipForNextFrame(font, hovered, origMx, origMy)
        }
    }

    private fun drawPlayerInventoryDecorations(ctx: GuiGraphicsExtractor) {
        val items = mc.player?.inventory?.nonEquipmentItems ?: return
        for (i in 0 until 36) {
            val deco = dragPreview?.playerStacks?.get(i) ?: items[i]
            val (sx, sy) = playerSlotPos(i)
            if (!deco.isEmpty) ctx.itemDecorations(font, deco, sx, sy)
        }
    }

    /** The overlay covers the vanilla screen, so it has to draw the cursor-carried stack itself. */
    private fun drawCarriedItem(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val carried = screenMenu()?.carried ?: return
        if (carried.isEmpty) return
        val shown = dragPreview?.let { carried.copyWithCount(it.carriedCount) } ?: carried
        if (shown.isEmpty) return
        val x = mouseX - 8
        val y = mouseY - 8
        ctx.item(shown, x, y)
        ctx.itemDecorations(font, shown, x, y)
    }

    // ── slot resolution + click dispatch (Noamm) ────────────────────────────
    private fun activePageSlotAt(mouseX: Double, mouseY: Double, activePage: StoragePage, data: TreeMap<StoragePage, NBTInventory?>): Slot? {
        val menu = screenMenu() ?: return null
        val chestEnd = menu.slots.size - 36
        if (chestEnd <= 9) return null
        val chestSlots = menu.slots.subList(9, chestEnd)
        var hit = -1
        layoutedForEach(data) { x, y, _, _, page, inv ->
            if (page != activePage) return@layoutedForEach
            val v = inv ?: return@layoutedForEach
            val rows = v.rows
            val gx = x + 3
            val gy = y + 5 + font.lineHeight + 1
            if (!inRect(mouseX, mouseY, gx, gy, 9 * SLOT_SIZE, rows * SLOT_SIZE)) return@layoutedForEach
            val col = ((mouseX - gx) / SLOT_SIZE).toInt().coerceIn(0, 8)
            val row = ((mouseY - gy) / SLOT_SIZE).toInt().coerceIn(0, rows - 1)
            hit = row * 9 + col
        }
        return chestSlots.getOrNull(hit)
    }

    private fun playerSlotAt(mouseX: Int, mouseY: Int): Slot? {
        val idx = playerSlotIndexAt(mouseX, mouseY) ?: return null
        val menu = screenMenu() ?: return null
        return menu.slots.firstOrNull { it.container is Inventory && it.containerSlot == idx }
    }

    private fun resolveSlotUnder(mouseX: Double, mouseY: Double, activePage: StoragePage?): Slot? {
        if (activePage != null) activePageSlotAt(mouseX, mouseY, activePage, visibleData(activePage, null))?.let { return it }
        return playerSlotAt(mouseX.toInt(), mouseY.toInt())
    }

    private fun dispatchSlotClick(slot: Slot, button: Int, modifiers: Int, input: ContainerInput? = null): Boolean {
        val menu = screenMenu() ?: return false
        val player = mc.player ?: return false
        val gm = mc.gameMode ?: return false
        val shift = modifiers and GLFW.GLFW_MOD_SHIFT != 0
        val type = input ?: if (shift) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP
        gm.handleContainerInput(menu.containerId, slot.index, button, type, player)
        return true
    }

    // ── drag-to-place (Noamm) ───────────────────────────────────────────────
    private class DragPreview(val stacks: Map<Int, ItemStack>, val playerStacks: Map<Int, ItemStack>, val carriedCount: Int)

    private fun canDragInto(slot: Slot, carried: ItemStack) =
        slot.mayPlace(carried) && AbstractContainerMenu.canItemQuickReplace(slot, carried, true)

    private fun computeDragPreview(): DragPreview? {
        if (!dragActive) return null
        val menu = screenMenu() ?: return null
        val carried = menu.carried
        if (carried.isEmpty) return null
        val eligible = dragSlots.mapNotNull { menu.slots.getOrNull(it) }.filter { canDragInto(it, carried) }.take(carried.count)
        if (eligible.size < 2) return null
        val base = AbstractContainerMenu.getQuickCraftPlaceCount(eligible.size, dragType, carried)
        var remaining = carried.count
        val stacks = HashMap<Int, ItemStack>()
        val playerStacks = HashMap<Int, ItemStack>()
        for (slot in eligible) {
            val existing = slot.item
            val existingCount = if (existing.isEmpty) 0 else existing.count
            val max = minOf(carried.maxStackSize, slot.getMaxStackSize(carried))
            val amount = (existingCount + base).coerceAtMost(max)
            remaining -= amount - existingCount
            val ghost = carried.copyWithCount(amount)
            stacks[slot.index] = ghost
            if (slot.container is Inventory) playerStacks[slot.containerSlot] = ghost
        }
        return DragPreview(stacks, playerStacks, remaining.coerceAtLeast(0))
    }

    private fun endDrag() {
        val menu = screenMenu() ?: return
        val player = mc.player ?: return
        val gm = mc.gameMode ?: return
        val id = menu.containerId
        gm.handleContainerInput(id, -999, AbstractContainerMenu.getQuickcraftMask(0, dragType), ContainerInput.QUICK_CRAFT, player)
        for (index in dragSlots) gm.handleContainerInput(id, index, AbstractContainerMenu.getQuickcraftMask(1, dragType), ContainerInput.QUICK_CRAFT, player)
        gm.handleContainerInput(id, -999, AbstractContainerMenu.getQuickcraftMask(2, dragType), ContainerInput.QUICK_CRAFT, player)
    }

    // ── input entry points ──────────────────────────────────────────────────
    @JvmStatic
    fun onOverlayClick(click: MouseButtonEvent, doubled: Boolean, screen: AbstractContainerScreen<*>): Boolean {
        if (!on(screen)) return false
        val s = scale
        val rx = click.x() / s
        val ry = click.y() / s
        val button = click.button()
        val modifiers = click.modifiers()

        if (inRect(rx, ry, searchFieldRect[0], searchFieldRect[1], searchFieldRect[2], searchFieldRect[3])) {
            searchFocused = true; return true
        }
        searchFocused = false

        val activePage = activePage(screen)
        val carried = screenMenu()?.carried
        if (carried != null && !carried.isEmpty && (button == 0 || button == 1)) {
            val slot = resolveSlotUnder(rx, ry, activePage)
            if (slot != null) {
                if (doubled && button == 0) return dispatchSlotClick(slot, 0, 0, ContainerInput.PICKUP_ALL)
                dragType = button
                dragStartSlot = slot
                dragSlots.clear()
                dragSlots.add(slot.index)
                return true
            }
        }

        if (inRect(rx, ry, scrollPanelX, scrollPanelY, scrollPanelW, scrollPanelH)) {
            val data = visibleData(activePage, null)
            if (activePage != null) activePageSlotAt(rx, ry, activePage, data)?.let { dispatchSlotClick(it, button, modifiers); return true }
            layoutedForEach(data) { x, y, pw, ph, page, _ ->
                if (inRect(rx, ry, x, y, pw, ph) && activePage != page && button == 0) page.open()
            }
            return true
        }

        if (inRect(rx, ry, scrollBarX, scrollBarY, SCROLL_BAR_WIDTH, scrollBarH)) {
            val pct = ((ry - scrollBarY) / scrollBarH.toDouble()).coerceIn(0.0, 1.0)
            scroll = (maxScroll * pct).toFloat()
            knobGrabbed = true
            return true
        }

        playerSlotAt(rx.toInt(), ry.toInt())?.let { dispatchSlotClick(it, button, modifiers) }
        return true   // overlay owns all mouse input while it's up — never let vanilla see the click
    }

    @JvmStatic
    fun mouseReleased(screen: AbstractContainerScreen<*>): Boolean {
        if (!on(screen)) return false
        if (dragArmed) {
            if (dragActive) endDrag()
            else dragStartSlot?.let {
                val shift = GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                    GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
                dispatchSlotClick(it, dragType, if (shift) GLFW.GLFW_MOD_SHIFT else 0)
            }
            dragSlots.clear()
            dragStartSlot = null
            return true
        }
        knobGrabbed = false
        return true   // swallow the release so vanilla doesn't treat it as a drop
    }

    @JvmStatic
    fun mouseDragged(mouseX: Double, mouseY: Double, screen: AbstractContainerScreen<*>): Boolean {
        if (!on(screen)) return false
        val s = scale
        val rx = mouseX / s
        val ry = mouseY / s
        if (dragArmed) {
            resolveSlotUnder(rx, ry, activePage(screen))?.let { dragSlots.add(it.index) }
            return true
        }
        if (knobGrabbed) {
            val pct = ((ry - scrollBarY) / scrollBarH.toDouble()).coerceIn(0.0, 1.0)
            scroll = (maxScroll * pct).toFloat()
        }
        return true
    }

    @JvmStatic
    fun mouseScrolled(verticalAmount: Double, screen: AbstractContainerScreen<*>): Boolean {
        if (!on(screen)) return false
        val speed = verticalAmount * FishSettings.storageScrollSpeed.coerceIn(1, 50) * -1
        scroll = (scroll + speed.toFloat()).coerceIn(0f, maxScroll)
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
    fun onClosed() {
        if (!FishSettings.storageRetainScroll) scroll = 0f
        searchFocused = false
        knobGrabbed = false
        dragStartSlot = null
        dragSlots.clear()
        dragPreview = null
        hoveredOverlayItem = null
    }

    // ── render helpers (Render2D equivalents) ───────────────────────────────
    private fun rect(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) =
        ctx.fill(x, y, x + w, y + h, color)

    private fun border(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int, t: Int) {
        ctx.fill(x, y, x + w, y + t, color)
        ctx.fill(x, y + h - t, x + w, y + h, color)
        ctx.fill(x, y, x + t, y + h, color)
        ctx.fill(x + w - t, y, x + w, y + h, color)
    }

    private fun scissor(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        val s = scale
        runCatching {
            ctx.enableScissor((x * s).toInt(), (y * s).toInt(), ((x + w) * s).toInt(), ((y + h) * s).toInt())
        }
    }

    private fun inRect(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx < x + w && my >= y && my < y + h
    private fun inRect(mx: Double, my: Double, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx < x + w && my >= y && my < y + h
}
