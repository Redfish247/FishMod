package fishmod.features.storage

import fishmod.features.ScreenTheme
import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.TreeMap

// Vanilla layer: square fills, slot grids, items. UI overlay: rounded frames, text, tooltip.
// Rounded corners = opaque ring in the fill colour over a vanilla rect inset from the edge, so no seam shows.
object StorageOverlay {

    private const val SLOT_SIZE = 17
    private const val PADDING = 10
    private const val HEADER_H = 16
    private const val PAGE_WIDTH = SLOT_SIZE * 9 + 4
    private const val CARD_W = PAGE_WIDTH + 1
    private const val GRID_TOP = 14
    private const val PAGE_GAP = 4
    private const val ACTIVE_PAGE_BORDER_THICKNESS = 2
    private const val SCROLL_BAR_WIDTH = 8
    private const val SCROLL_BAR_HEIGHT = 16
    private const val PLAYER_WIDTH = SLOT_SIZE * 9 + 6
    private const val PLAYER_HEIGHT = SLOT_SIZE * 4 + 18

    private const val PANEL_R = 8f
    private const val PANEL_BAND = 6f
    private const val CARD_R = 3f
    private const val PLAYER_R = 5f
    private const val TITLE_SIZE = 8f
    private const val TEXT_SIZE = 7.5f

    private const val MENU_BG = 0xFF18181B.toInt()
    private const val MENU_BORDER = 0xFF3C3C41.toInt()
    private const val CARD_BG = 0xFF222227.toInt()
    private const val CARD_BORDER = 0xFF303036.toInt()
    private const val CARD_BORDER_HOVER = 0xFF4A4A52.toInt()
    private const val SLOT_BG = 0xC8323237.toInt()
    private const val SLOT_CELL_BG = 0xFF1E1E22.toInt()
    private const val SLOT_CELL_BORDER = 0xFF37373C.toInt()
    private const val FIELD_BG = 0xFF121215.toInt()
    private const val SCROLL_BG = 0xB41E1E23.toInt()
    private const val SCROLL_KNOB = 0xFF787882.toInt()
    private const val HOVER_WHITE = 0x32FFFFFF
    private const val TEXT_DIM = 0xFFB4B4B4.toInt()
    private const val SEARCH_MATCH = 0x5533C9C0
    private val ACCENT get() = ScreenTheme.ACCENT

    private var scroll = 0f
    private var lastRenderedInnerHeight = 0
    private var pageWidthCount = 3
    private var knobGrabbed = false
    private var tooltipStack: ItemStack? = null
    private var pendingPaint = false

    private var dragType = 0
    private var dragStartSlot: Slot? = null
    private val dragSlots = LinkedHashSet<Int>()
    private var dragPreview: DragPreview? = null
    private val dragArmed get() = dragStartSlot != null
    private val dragActive get() = dragSlots.size >= 2

    var search = ""
    private var searchFocused = false

    private val mc get() = Minecraft.getInstance()
    private val font get() = mc.font
    private val scale get() = FishSettings.storageOverlayScale.coerceIn(0.5, 2.0).toFloat()

    private var titleFor: net.minecraft.network.chat.Component? = null
    private var titleIsStorage = false
    private var titlePage: StoragePage? = null

    private fun classify(screen: AbstractContainerScreen<*>) {
        val title = screen.title
        if (title === titleFor) return
        titleFor = title
        val t = title.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")
        titlePage = StoragePage.fromTitle(t)
        titleIsStorage = t == "Storage" || titlePage != null
    }

    private fun on(screen: AbstractContainerScreen<*>): Boolean {
        if (!FishSettings.storageOverlayEnabled) return false
        classify(screen)
        return titleIsStorage
    }

    @JvmStatic
    fun isActive(screen: AbstractContainerScreen<*>): Boolean = on(screen)

    @JvmStatic
    fun panelTopScreenY(): Int = (my0 * scale).toInt()

    // Called from GameRendererUiMixin after vanilla's GUI pass; replays this frame's recording once.
    @JvmStatic
    fun paintUiOverlay() {
        if (!pendingPaint) return
        pendingPaint = false
        val w = mc.window
        UiRenderer.paint(w.guiScaledWidth, w.guiScaledHeight, scale)
        UiRecorder.clear()
    }

    private fun activePage(screen: AbstractContainerScreen<*>): StoragePage? {
        classify(screen)
        return titlePage
    }

    private fun allData(): TreeMap<StoragePage, NBTInventory?> {
        val out = TreeMap<StoragePage, NBTInventory?>()
        val view = StorageCache.view()
        for (i in (StorageCache.knownPages() + view.keys).sorted()) out[StoragePage(i)] = view[i]
        return out
    }

    private val isSearching get() = search.isNotBlank()
    private val shouldFilterPages get() = FishSettings.storageHideNonMatching && isSearching

    private val searchText = java.util.WeakHashMap<ItemStack, String>()

    private fun matches(s: ItemStack): Boolean {
        if (s.isEmpty) return false
        val text = searchText.getOrPut(s) {
            val sb = StringBuilder(s.hoverName.string.lowercase())
            s.get(DataComponents.LORE)?.lines()?.forEach { sb.append('\n').append(it.string.lowercase()) }
            sb.toString()
        }
        return text.contains(search.lowercase())
    }

    private fun visibleData(activePage: StoragePage?, activeSlots: List<Slot>?, all: TreeMap<StoragePage, NBTInventory?> = allData()): TreeMap<StoragePage, NBTInventory?> {
        val data = all
        if (!shouldFilterPages) return data
        return TreeMap<StoragePage, NBTInventory?>().apply {
            for ((page, inv) in data) {
                val hit = if (page == activePage && activeSlots != null) activeSlots.any { matches(it.item) }
                else inv?.stacks?.any(::matches) == true
                if (hit) this[page] = inv
            }
        }
    }

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
        val avail = vh - PLAYER_HEIGHT - 12
        overviewH = minOf(avail, FishSettings.storageMaxHeight.coerceIn(80, 900)).coerceAtLeast(80)
        innerH = overviewH - PADDING * 2 - HEADER_H
        my0 = (vh / 2 - (overviewH + PLAYER_HEIGHT) / 2).coerceAtLeast(6)
        playerX0 = vw / 2 - PLAYER_WIDTH / 2
        playerY0 = my0 + overviewH + 2

        val sidebar = (fishmod.features.item.ContainerValue.storageSidebarWidthGuiPx() / scale).toInt()
        if (sidebar > 0) {
            val shift = (sidebar + 8 - mx0).coerceAtLeast(0)
            mx0 += shift
            playerX0 += shift
        }
    }

    private val scrollPanelX get() = mx0 + PADDING
    private val scrollPanelY get() = my0 + PADDING + HEADER_H
    private val scrollPanelW get() = innerW
    private val scrollPanelH get() = innerH
    private val scrollBarX get() = mx0 + PADDING + innerW + PADDING
    private val scrollBarY get() = my0 + PADDING + HEADER_H
    private val scrollBarH get() = innerH
    private val maxScroll get() = (lastRenderedInnerHeight.toFloat() + 6 - innerH).coerceAtLeast(0f)

    private fun screenMenu(): AbstractContainerMenu? =
        (mc.screen as? AbstractContainerScreen<*>)?.menu

    private fun rowCountOf(menu: AbstractContainerMenu): Int =
        (menu as? ChestMenu)?.rowCount ?: ((menu.slots.size - 36) / 9)

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, screen: AbstractContainerScreen<*>) {
        if (!on(screen)) return
        UiRecorder.clear()
        pendingPaint = false
        recomputeGeometry()
        dragPreview = computeDragPreview()
        tooltipStack = null

        val s = scale
        ctx.pose().pushMatrix()
        ctx.pose().scale(s, s)
        val smx = (mouseX / s).toInt()
        val smy = (mouseY / s).toInt()
        lastMouseX = mouseX / s.toDouble()
        lastMouseY = mouseY / s.toDouble()

        runCatching { ctx.blurBeforeThisStratum() }
        runCatching { ctx.nextStratum() }
        rect(ctx, 0, 0, vw + 2, vh + 2, 0x66_0A0A12)

        val menu = screen.menu
        val chestEnd = rowCountOf(menu) * 9
        val chestSlots = if (chestEnd > 9) menu.slots.subList(9, chestEnd) else emptyList()
        val active = activePage(screen)
        val all = allData()
        val data = visibleData(active, chestSlots, all)
        if (shouldFilterPages) updateLayoutHeight(data)

        rect(ctx, mx0 + 3, my0 + 3, overviewW - 6, overviewH - 6, MENU_BG)
        UiRecorder.roundedRectRing(mx0.toFloat(), my0.toFloat(), overviewW.toFloat(), overviewH.toFloat(), PANEL_R, PANEL_BAND, 0, MENU_BG)
        UiRecorder.roundedRectRing(mx0.toFloat(), my0.toFloat(), overviewW.toFloat(), overviewH.toFloat(), PANEL_R, 1f, 0, MENU_BORDER)

        drawHeader(all.size)
        drawPages(ctx, data, smx, smy, active, chestSlots)
        drawScrollBar()
        drawPlayerInventory(ctx, smx, smy)
        drawPagesDecorations(ctx, data, active, chestSlots)
        drawPlayerInventoryDecorations(ctx)
        drawCarriedItem(ctx, smx, smy)

        ctx.pose().popMatrix()

        // replaces the vanilla tooltip, which would sit under the overlay text
        tooltipStack?.let {
            val lines = runCatching { Screen.getTooltipFromItem(mc, it) }.getOrNull()
            if (!lines.isNullOrEmpty()) ScreenTheme.nItemTooltip(lines, smx, smy, vw, vh, 1f / s)
        }
        pendingPaint = true
    }

    private fun drawHeader(pageCount: Int) {
        val tx = (mx0 + PADDING).toFloat()
        val ty = my0 + 9f
        UiRecorder.textBold("Storage", tx, ty, TITLE_SIZE, ScreenTheme.TEXT_COLOR)
        val lw = UiRecorder.textWidth("Storage", TITLE_SIZE) + 6f
        UiRecorder.text("$pageCount pages", tx + lw, ty + 0.5f, TEXT_SIZE, ScreenTheme.SUBTEXT_COLOR)

        val fw = 130; val fh = 13
        val fx = mx0 + overviewW - fw - PADDING; val fy = my0 + 7
        UiRecorder.roundedRectRing(fx.toFloat(), fy.toFloat(), fw.toFloat(), fh.toFloat(), 4f, 1f, FIELD_BG, if (searchFocused) ACCENT else SLOT_CELL_BORDER)
        val size = 7f
        val pad = 4f
        val textY = fy + (fh - size) / 2f
        val tw = UiRecorder.textWidth(search, size)
        val shift = (tw - (fw - pad * 2 - 2)).coerceAtLeast(0f)
        UiRecorder.pushScissor(fx + 1f, fy + 1f, fw - 2f, fh - 2f)
        if (search.isEmpty()) UiRecorder.text("Search…", fx + pad, textY, size, ScreenTheme.SUBTEXT_COLOR)
        else UiRecorder.text(search, fx + pad - shift, textY, size, ScreenTheme.TEXT_COLOR)
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0L) {
            UiRecorder.fillRect(fx + pad + tw - shift + 0.5f, fy + 3f, 1f, fh - 6f, ScreenTheme.TEXT_COLOR)
        }
        UiRecorder.popScissor()
        searchFieldRect = intArrayOf(fx, fy, fw, fh)
    }

    private var searchFieldRect = IntArray(4)

    private fun cardHeight(inv: NBTInventory?): Int = inv?.let { it.rows * SLOT_SIZE + GRID_TOP + 3 } ?: 18

    private inline fun layoutedForEach(
        data: TreeMap<StoragePage, NBTInventory?>,
        func: (x: Int, y: Int, pageWidth: Int, pageHeight: Int, page: StoragePage, inv: NBTInventory?) -> Unit,
    ) {
        var yOffset = -scroll.toInt()
        var xOffset = 0
        var maxHeight = 0
        for ((page, inv) in data.entries) {
            val h = cardHeight(inv)
            maxHeight = maxOf(maxHeight, h + PAGE_GAP)
            val rectX = mx0 + PADDING + (PAGE_WIDTH + PADDING) * xOffset
            val rectY = yOffset + scrollPanelY
            func(rectX, rectY, PAGE_WIDTH, h, page, inv)
            xOffset++
            if (xOffset >= pageWidthCount) { yOffset += maxHeight; xOffset = 0; maxHeight = 0 }
        }
        lastRenderedInnerHeight = maxHeight + yOffset + scroll.toInt()
    }

    private fun updateLayoutHeight(data: TreeMap<StoragePage, NBTInventory?>) {
        lastRenderedInnerHeight = data.entries.chunked(pageWidthCount)
            .sumOf { row -> row.maxOf { (_, inv) -> cardHeight(inv) + PAGE_GAP } }
        scroll = scroll.coerceIn(0f, maxScroll)
    }

    private fun drawPages(
        ctx: GuiGraphicsExtractor, data: TreeMap<StoragePage, NBTInventory?>,
        mouseX: Int, mouseY: Int, excluding: StoragePage?, slots: List<Slot>?,
    ) {
        val clipW = scrollPanelW + ACTIVE_PAGE_BORDER_THICKNESS
        scissor(ctx, scrollPanelX, scrollPanelY, clipW, scrollPanelH)
        UiRecorder.pushScissor(scrollPanelX.toFloat(), scrollPanelY.toFloat(), clipW.toFloat(), scrollPanelH.toFloat())
        val viewTop = scrollPanelY
        val viewBot = scrollPanelY + scrollPanelH
        layoutedForEach(data) { x, y, _, ph, page, inv ->
            if (y + ph < viewTop || y > viewBot) return@layoutedForEach
            drawPage(ctx, x, y, page, inv, if (excluding == page) slots else null, mouseX, mouseY)
        }
        UiRecorder.popScissor()
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
            val slotsY = y + GRID_TOP
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
        mouseX: Int, mouseY: Int,
    ) {
        val inView = inRect(mouseX, mouseY, scrollPanelX, scrollPanelY, scrollPanelW, scrollPanelH)
        if (inv == null && slots == null) {
            val hot = inView && inRect(mouseX, mouseY, x, y, CARD_W, 18)
            UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), CARD_W.toFloat(), 18f, CARD_R, 1f, SLOT_BG, if (hot) CARD_BORDER_HOVER else MENU_BORDER)
            val ty = y + (18 - TEXT_SIZE) / 2f
            UiRecorder.text(page.name, x + 5f, ty, TEXT_SIZE, ScreenTheme.TEXT_COLOR)
            val nw = UiRecorder.textWidth(page.name, TEXT_SIZE)
            UiRecorder.text("·  Click to load", x + 5f + nw + 4f, ty, TEXT_SIZE, TEXT_DIM)
            return
        }
        val rows = inv?.rows ?: (slots?.size?.div(9)?.coerceIn(1, 5) ?: 3)
        val isActive = slots != null
        val slotsY = y + GRID_TOP
        val cardH = rows * SLOT_SIZE + GRID_TOP + 3
        val hot = inView && inRect(mouseX, mouseY, x, y, CARD_W, cardH)

        // vanilla body under the items; the overlay ring rounds its corners in the same colour
        rect(ctx, x + 1, y + 1, CARD_W - 2, cardH - 2, CARD_BG)
        UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), CARD_W.toFloat(), cardH.toFloat(), CARD_R, 2f, 0, CARD_BG)
        val edge = if (isActive) ACCENT else if (hot) CARD_BORDER_HOVER else CARD_BORDER
        UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), CARD_W.toFloat(), cardH.toFloat(), CARD_R, if (isActive) 1.5f else 1f, 0, edge)
        val titleY = y + 2 + (GRID_TOP - 2 - TITLE_SIZE) / 2f
        if (isActive) UiRecorder.textBold(page.name, x + 5f, titleY, TITLE_SIZE, ACCENT)
        else UiRecorder.text(page.name, x + 5f, titleY, TITLE_SIZE, ScreenTheme.TEXT_COLOR)

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
            val slotHot = inView && inRect(mouseX, mouseY, sx - 1, sy - 1, 18, 18)
            if (!renderStack.isEmpty) {
                if (isSearching && matches(renderStack)) rect(ctx, sx, sy, 16, 16, SEARCH_MATCH)
                ctx.item(renderStack, sx, sy)
                if (slotHot && hovered == null && !display.isEmpty) hovered = display
            }
            if (slotHot) rect(ctx, sx, sy, 16, 16, HOVER_WHITE)
        }
        if (hovered != null) {
            tooltipStack = hovered
        }
    }

    private fun drawSlotGrid(ctx: GuiGraphicsExtractor, x: Int, y: Int, rows: Int) {
        val w = 9 * SLOT_SIZE
        val h = rows * SLOT_SIZE
        ctx.fill(x, y, x + w, y + h, SLOT_CELL_BG)
        for (c in 0..9) ctx.fill(x + c * SLOT_SIZE, y, x + c * SLOT_SIZE + 1, y + h, SLOT_CELL_BORDER)
        for (r in 0..rows) ctx.fill(x, y + r * SLOT_SIZE, x + w, y + r * SLOT_SIZE + 1, SLOT_CELL_BORDER)
    }

    private fun drawScrollBar() {
        val tx = scrollBarX + 2f
        val tw = SCROLL_BAR_WIDTH - 4f
        UiRecorder.fillPillBar(tx, scrollBarY.toFloat(), tw, scrollBarH.toFloat(), SCROLL_BG)
        val ms = maxScroll
        val pct = if (ms > 0) scroll / ms else 0f
        val knobY = scrollBarY + (pct * (scrollBarH - SCROLL_BAR_HEIGHT)).toInt()
        UiRecorder.fillPillBar(tx, knobY.toFloat(), tw, SCROLL_BAR_HEIGHT.toFloat(), if (knobGrabbed) ScreenTheme.TEXT_COLOR else SCROLL_KNOB)
    }

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

    private fun drawPlayerInventory(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val items = mc.player?.inventory?.nonEquipmentItems ?: return
        val (invX, invY) = playerSlotPos(9)
        val (hotX, hotY) = playerSlotPos(0)
        val gx = invX - 1; val gy = invY - 1
        val gw = 9 * SLOT_SIZE + 1; val gh = hotY + SLOT_SIZE - gy
        rect(ctx, gx - 2, gy - 2, gw + 4, gh + 4, CARD_BG)
        UiRecorder.roundedRectRing(gx - 4f, gy - 4f, gw + 8f, gh + 8f, PLAYER_R, 4f, 0, CARD_BG)
        UiRecorder.roundedRectRing(gx - 4f, gy - 4f, gw + 8f, gh + 8f, PLAYER_R, 1f, 0, MENU_BORDER)
        drawSlotGrid(ctx, gx, gy, 3)
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
            tooltipStack = hovered
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
        return true
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
        return true
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

    private var lastMouseX = 0.0
    private var lastMouseY = 0.0

    @JvmStatic
    fun keyPressed(input: KeyEvent, screen: AbstractContainerScreen<*>): Boolean {
        if (!on(screen)) return false
        if (searchFocused) {
            when (input.key()) {
                GLFW.GLFW_KEY_ESCAPE -> searchFocused = false
                GLFW.GLFW_KEY_BACKSPACE -> if (search.isNotEmpty()) search = search.dropLast(1)
            }
            return true
        }
        val options = mc.options
        val hotbar = options.keyHotbarSlots.indexOfFirst { it.matches(input) }
        val drop = options.keyDrop.matches(input)
        if (hotbar < 0 && !drop) return false
        val slot = resolveSlotUnder(lastMouseX, lastMouseY, activePage(screen)) ?: return true
        if (hotbar >= 0) dispatchSlotClick(slot, hotbar, 0, ContainerInput.SWAP)
        else dispatchSlotClick(slot, if (input.modifiers() and GLFW.GLFW_MOD_CONTROL != 0) 1 else 0, 0, ContainerInput.THROW)
        return true
    }

    @JvmStatic
    fun charTyped(input: CharacterEvent, screen: AbstractContainerScreen<*>): Boolean {
        if (!on(screen) || !searchFocused) return false
        val cp = input.codepoint()
        if (Character.isISOControl(cp)) return true
        search += String(Character.toChars(cp))
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
        tooltipStack = null
        pendingPaint = false
    }

    private fun rect(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) =
        ctx.fill(x, y, x + w, y + h, color)

    // enableScissor already maps through the pose (which carries the scale), so pass virtual coords
    private fun scissor(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        runCatching { ctx.enableScissor(x, y, x + w, y + h) }
    }

    private fun inRect(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx < x + w && my >= y && my < y + h
    private fun inRect(mx: Double, my: Double, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx < x + w && my >= y && my < y + h
}
