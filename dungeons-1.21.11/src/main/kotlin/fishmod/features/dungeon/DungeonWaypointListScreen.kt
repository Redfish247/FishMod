package fishmod.features.dungeon

import fishmod.utils.dungeon.waypoints.DungeonWaypointStore
import fishmod.utils.dungeon.waypoints.StoredWaypoint
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import net.minecraft.util.math.MathHelper
import org.lwjgl.glfw.GLFW

/**
 * /fmwp gui — full-page list of every waypoint in [DungeonWaypointStore], grouped into
 * folders by where they live: one folder per dungeon room signature, one per Skyblock island /
 * server+dimension freeform bucket ([DungeonWaypoints.globalKey]-style keys), and one per
 * route. Lets you rename or delete waypoints without needing to stand in the room / at the location
 * where they were placed. Follows the same look as [fishmod.features.croesus.LootTrackerScreen].
 */
class DungeonWaypointListScreen : Screen(Text.literal("Dungeon Waypoints")) {

    private class Entry(val key: String, val index: Int, val wp: StoredWaypoint)

    /** One display row: either a folder header (no entry) or a waypoint (entry set). */
    private class Row {
        val folderLabel: String?
        val folderCount: Int
        val entry: Entry?

        constructor(folderLabel: String, folderCount: Int) {
            this.folderLabel = folderLabel
            this.folderCount = folderCount
            this.entry = null
        }

        constructor(entry: Entry) {
            this.folderLabel = null
            this.folderCount = 0
            this.entry = entry
        }

        fun isFolder(): Boolean = entry == null
    }

    private var contentX0 = 0
    private var contentX1 = 0
    private var contentY0 = 0
    private var contentY1 = 0
    private var listTop = 0
    private var listH = 0
    private var listX0 = 0
    private var listX1 = 0
    private var scroll = 0
    private var curMx = 0
    private var curMy = 0

    private var closeX = 0
    private var closeY = 0
    private var closeS = 0
    private var searchX = 0
    private var searchY = 0
    private var searchW = 0
    private var searchH = 0
    private var rowY = IntArray(0)
    private var rowH = IntArray(0)
    private var delX = IntArray(0)

    private lateinit var searchField: TextFieldWidget
    private lateinit var renameBox: TextFieldWidget
    private var renameRow = -1

    override fun init() {
        searchField = TextFieldWidget(this.textRenderer, 0, 0, 100, SEARCH_H - 8, Text.literal(""))
        searchField.setMaxLength(64)
        searchField.setDrawsBackground(false)
        searchField.setChangedListener { _ -> scroll = 0 }

        renameBox = TextFieldWidget(this.textRenderer, 0, 0, 100, 14, Text.literal(""))
        renameBox.setMaxLength(48)
        renameBox.setDrawsBackground(false)
    }

    private fun allEntries(): List<Entry> {
        val out = ArrayList<Entry>()
        for (e in DungeonWaypointStore.allData().entries) {
            val list: List<StoredWaypoint> = e.value
            for (i in list.indices) out.add(Entry(e.key, i, list[i]))
        }
        return out
    }

    private fun folderKey(e: Entry): String {
        return if (e.wp.routeId != null) "route:" + e.wp.routeId else "loc:" + e.key
    }

    private fun folderLabel(e: Entry): String {
        return if (e.wp.routeId != null) "§b🔗 Route '" + e.wp.routeId + "'" else "§e" + locationLabel(e.key)
    }

    /** Builds the folder-grouped, search-filtered display list. Routes first, then locations, each in first-seen order. */
    private fun buildRows(): List<Row> {
        var all = allEntries()
        val q = searchField.text.trim().lowercase()
        if (q.isNotEmpty()) {
            val filtered = ArrayList<Entry>()
            for (e in all) {
                val title = e.wp.title?.lowercase() ?: ""
                if (title.contains(q) || locationLabel(e.key).lowercase().contains(q) ||
                    (e.wp.routeId != null && e.wp.routeId.lowercase().contains(q))
                ) filtered.add(e)
            }
            all = filtered
        }

        val groups = LinkedHashMap<String, MutableList<Entry>>()
        for (e in all) groups.computeIfAbsent(folderKey(e)) { ArrayList() }.add(e)
        for (group in groups.values) {
            group.sortWith(Comparator { a, b ->
                if (a.wp.routeId != null) Integer.compare(a.wp.routeOrder, b.wp.routeOrder) else 0
            })
        }

        val rows = ArrayList<Row>()
        for (group in groups.values) {
            rows.add(Row(folderLabel(group[0]), group.size))
            for (e in group) rows.add(Row(e))
        }
        return rows
    }

    override fun renderBackground(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {}
    override fun renderInGameBackground(ctx: DrawContext) {}

    override fun render(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        curMx = mouseX; curMy = mouseY
        ctx.fillGradient(0, 0, this.width, this.height, BG_TOP, BG_BOT)

        contentX0 = MARGIN
        contentX1 = this.width - MARGIN
        contentY0 = MARGIN
        contentY1 = this.height - MARGIN

        ctx.fill(contentX0 - 1, contentY0 - 1, contentX1 + 1, contentY1 + 1, PANEL_BORDER)
        ctx.fill(contentX0, contentY0, contentX1, contentY1, PANEL_BG)
        ctx.fill(contentX0, contentY0, contentX1, contentY0 + 3, ACCENT)

        renderHeader(ctx)
        val searchBottom = renderSearch(ctx, contentY0 + HEADER_H)

        listTop = searchBottom + GAP
        listX0 = contentX0 + PAD
        listX1 = contentX1 - PAD
        listH = contentY1 - PAD - listTop

        renderList(ctx, buildRows())

        super.render(ctx, mouseX, mouseY, delta)
    }

    private fun renderHeader(ctx: DrawContext) {
        val y = contentY0 + PAD
        ctx.drawText(this.textRenderer, "§l§bDungeon Waypoints", contentX0 + PAD, y, TEXT, true)
        ctx.drawText(
            this.textRenderer,
            "§8" + allEntries().size + " waypoint(s) — click a name to rename, §c×§8 to delete §8(deletes the whole route for §7🔗§8 entries)",
            contentX0 + PAD, y + 11, SUBTEXT, true
        )

        closeS = 18
        closeX = contentX1 - PAD - closeS
        closeY = contentY0 + PAD - 3
        val hov = hit(curMx.toDouble(), curMy.toDouble(), closeX, closeY, closeS, closeS)
        ctx.fill(closeX, closeY, closeX + closeS, closeY + closeS, if (hov) BTN_HOV else BTN_BG)
        ctx.drawCenteredTextWithShadow(this.textRenderer, "§7x", closeX + closeS / 2, closeY + 5, if (hov) 0xFFFFFFFF.toInt() else SUBTEXT)
    }

    private fun renderSearch(ctx: DrawContext, y: Int): Int {
        searchX = contentX0 + PAD
        searchY = y
        searchW = contentX1 - contentX0 - PAD * 2
        searchH = SEARCH_H

        val focused = searchField.isFocused
        ctx.fill(searchX, searchY, searchX + searchW, searchY + searchH, if (focused) SEARCH_BG_FOCUS else SEARCH_BG)
        ctx.fill(searchX, searchY, searchX + searchW, searchY + 1, if (focused) ACCENT else TILE_BORDER)
        ctx.drawText(this.textRenderer, "§8🔍", searchX + 6, searchY + (searchH - 8) / 2, SUBTEXT, false)

        searchField.x = searchX + 16
        searchField.y = searchY + (searchH - (searchH - 8)) / 2
        searchField.width = searchW - 22
        searchField.render(ctx, curMx, curMy, 0f)
        if (searchField.text.isEmpty() && !searchField.isFocused) {
            ctx.drawText(this.textRenderer, "§8Search waypoints...", searchX + 16 + 2, searchY + (searchH - 8) / 2, SUBTEXT, false)
        }
        return searchY + searchH
    }

    private fun renderList(ctx: DrawContext, rows: List<Row>) {
        val x0 = listX0
        val x1 = listX1
        ctx.enableScissor(x0, listTop, x1, listTop + listH)

        val searching = searchField.text.trim().isNotEmpty()

        if (rows.isEmpty()) {
            val msg = if (searching) "§8no waypoints match your search" else "§8no waypoints placed yet — /fmwp edit, then right-click to place"
            ctx.drawText(this.textRenderer, msg, x0, listTop + 6, SUBTEXT, true)
            rowY = IntArray(0)
            rowH = IntArray(0)
            delX = IntArray(0)
        } else {
            var totalH = 0
            for (r in rows) totalH += if (r.isFolder()) FOLDER_ROW_H else ROW_H
            val maxScroll = Math.max(0, totalH - listH)
            scroll = MathHelper.clamp(scroll, 0, maxScroll)

            rowY = IntArray(rows.size)
            rowH = IntArray(rows.size)
            delX = IntArray(rows.size)

            var y = listTop - scroll
            for (i in rows.indices) {
                val r = rows[i]
                val h = if (r.isFolder()) FOLDER_ROW_H else ROW_H
                val rowTop = y
                rowY[i] = rowTop
                rowH[i] = h
                y += h
                if (rowTop + h < listTop || rowTop > listTop + listH) continue

                if (r.isFolder()) {
                    ctx.fill(x0, rowTop, x1, rowTop + h - 1, FOLDER_BG)
                    val label = r.folderLabel + " §8(" + r.folderCount + ")"
                    ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(label, x1 - x0 - 8), x0 + 6, rowTop + (h - 8) / 2, TEXT, false)
                    continue
                }

                val e = r.entry!!
                val hov = hit(curMx.toDouble(), curMy.toDouble(), x0, rowTop, x1 - x0, h)
                val bg = if (hov) ROW_HOVER else (if ((i and 1) == 1) ROW_BG_ALT else ROW_BG)
                ctx.fill(x0, rowTop, x1, rowTop + h - 1, bg)

                val delXHere = x1 - DEL_W
                delX[i] = delXHere
                val dhov = hit(curMx.toDouble(), curMy.toDouble(), delXHere, rowTop, DEL_W, h - 1)
                ctx.fill(delXHere, rowTop, delXHere + DEL_W, rowTop + h - 1, if (dhov) DANGER_HOV else DANGER_BG)
                val xw = this.textRenderer.getWidth("×")
                ctx.drawText(this.textRenderer, "×", delXHere + (DEL_W - xw) / 2, rowTop + (h - 8) / 2, DANGER, false)

                val nameX = x0 + 16
                val nameMaxW = delXHere - nameX - 8

                if (renameRow == i) {
                    renameBox.x = nameX
                    renameBox.y = rowTop + 4
                    renameBox.width = nameMaxW
                    renameBox.render(ctx, curMx, curMy, 0f)
                } else {
                    val name = if (e.wp.title.isNullOrBlank()) "(unnamed)" else e.wp.title
                    ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth("§f$name", nameMaxW), nameX, rowTop + 4, TEXT, false)
                }

                val loc = if (e.wp.routeId != null)
                    "§8#" + (e.wp.routeOrder + 1) + "/" + routeSize(e.wp.routeId)
                else
                    "§8@ §7" + fmt(e.wp.x) + ", " + fmt(e.wp.y) + ", " + fmt(e.wp.z)
                ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(loc, nameMaxW), nameX, rowTop + 16, SUBTEXT, false)
            }

            if (maxScroll > 0) {
                val barX = x1 - 2
                val barH = Math.max(10, listH * listH / Math.max(1, totalH))
                val barY = listTop + (listH - barH) * scroll / Math.max(1, maxScroll)
                ctx.fill(barX, listTop, barX + 2, listTop + listH, 0x33FFFFFF)
                ctx.fill(barX, barY, barX + 2, barY + barH, ACCENT)
            }
        }

        ctx.disableScissor()
    }

    // ── input ────────────────────────────────────────────────────────────────
    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mx = click.x()
        val my = click.y()

        if (renameRow != -1) {
            val rx = renameBox.x
            val ry = renameBox.y
            val rw = renameBox.width
            if (!hit(mx, my, rx, ry, rw, 14)) commitRename()
        }

        if (hit(mx, my, closeX, closeY, closeS, closeS)) { close(); return true }

        if (hit(mx, my, searchX, searchY, searchW, searchH)) {
            searchField.isFocused = true
            scroll = 0
            return searchField.mouseClicked(click, doubled)
        } else {
            searchField.isFocused = false
        }

        val rows = buildRows()
        var i = 0
        while (i < rowY.size && i < rows.size) {
            val r = rows[i]
            if (r.isFolder()) { i++; continue }
            val e = r.entry!!
            if (hit(mx, my, delX[i], rowY[i], DEL_W, rowH[i] - 1)) {
                if (e.wp.routeId != null) {
                    DungeonWaypoints.deleteRoute(e.wp.routeId)
                } else {
                    DungeonWaypointStore.removeAt(e.key, e.index)
                    DungeonWaypoints.refreshLive()
                }
                if (renameRow == i) renameRow = -1
                return true
            }
            val nameX = listX0 + 16
            val nameMaxW = delX[i] - nameX - 8
            if (hit(mx, my, nameX, rowY[i] + 4, nameMaxW, 14)) {
                openRename(i, e)
                return true
            }
            i++
        }

        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (hit(mouseX, mouseY, listX0, listTop, listX1 - listX0, listH)) {
            scroll -= (verticalAmount * ROW_H).toInt()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (renameRow != -1) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) { renameRow = -1; return true }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) { commitRename(); return true }
            renameBox.keyPressed(input)
            return true
        }
        if (searchField.isFocused) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                if (searchField.text.isNotEmpty()) { searchField.text = ""; return true }
                searchField.isFocused = false
                return true
            }
            searchField.keyPressed(input)
            return true
        }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharInput): Boolean {
        if (renameRow != -1) { renameBox.charTyped(input); return true }
        if (searchField.isFocused) { searchField.charTyped(input); return true }
        return super.charTyped(input)
    }

    private fun openRename(rowIndex: Int, e: Entry) {
        renameRow = rowIndex
        renameBox.text = e.wp.title ?: ""
        renameBox.isFocused = true
    }

    private fun commitRename() {
        if (renameRow == -1) return
        val rows = buildRows()
        if (renameRow < rows.size && !rows[renameRow].isFolder()) {
            val e = rows[renameRow].entry!!
            DungeonWaypointStore.setTitle(e.key, e.index, renameBox.text.trim())
            DungeonWaypoints.refreshLive()
        }
        renameRow = -1
        renameBox.isFocused = false
    }

    override fun close() {
        if (renameRow != -1) commitRename()
        MinecraftClient.getInstance().setScreen(null)
    }

    override fun shouldPause(): Boolean = false

    companion object {
        private val BG_TOP = 0xEE0A0E12.toInt()
        private val BG_BOT = 0xF2050709.toInt()
        private val PANEL_BG = 0xFF11161C.toInt()
        private val PANEL_BORDER = 0xFF232D36.toInt()
        private val TILE_BORDER = 0xFF232D36.toInt()
        private val ROW_BG = 0xFF14191F.toInt()
        private val ROW_BG_ALT = 0xFF171D24.toInt()
        private val ROW_HOVER = 0xFF1E262E.toInt()
        private val FOLDER_BG = 0xFF1B222B.toInt()
        private val ACCENT = 0xFF2FD1C4.toInt()
        private val TEXT = 0xFFEFF4F7.toInt()
        private val SUBTEXT = 0xFF8A97A3.toInt()
        private val DANGER = 0xFFE0574B.toInt()
        private val DANGER_BG = 0xFF241213.toInt()
        private val DANGER_HOV = 0xFF3A1517.toInt()
        private val BTN_BG = 0xFF1B2229.toInt()
        private val BTN_HOV = 0xFF25313A.toInt()
        private val SEARCH_BG = 0xFF161D24.toInt()
        private val SEARCH_BG_FOCUS = 0xFF1B2530.toInt()

        private const val MARGIN = 20
        private const val HEADER_H = 34
        private const val SEARCH_H = 24
        private const val ROW_H = 30
        private const val FOLDER_ROW_H = 20
        private const val GAP = 10
        private const val PAD = 14
        private const val DEL_W = 44

        private fun routeSize(routeId: String): Int {
            var n = 0
            for (list in DungeonWaypointStore.allData().values)
                for (w in list) if (routeId == w.routeId) n++
            return n
        }

        private fun locationLabel(key: String): String {
            if (key.startsWith("global:")) {
                val rest = key.substring("global:".length)
                val idx = rest.lastIndexOf(':')
                if (idx > 0) return rest.substring(0, idx) + " §8/ " + rest.substring(idx + 1)
                return rest
            }
            return "Room " + (if (key.length > 16) key.substring(0, 16) + "…" else key)
        }

        private fun fmt(v: Double): String {
            return (Math.round(v * 10) / 10.0).toString()
        }

        private fun hit(mx: Double, my: Double, x: Int, y: Int, w: Int, h: Int): Boolean {
            return mx >= x && mx <= x + w && my >= y && my <= y + h
        }
    }
}
