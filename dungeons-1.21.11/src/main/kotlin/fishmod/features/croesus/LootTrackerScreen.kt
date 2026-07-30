package fishmod.features.croesus

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
import java.text.DecimalFormat

/**
 * Full-page /fmloot screen — a real dedicated page (fills the window, like the main FishMod
 * config screen) instead of a small centered card. Adds a search bar to filter tracked drops
 * by name. Rows are populated automatically by `CroesusLootDetector` from real Croesus
 * chest opens; everything here is just the view.
 */
class LootTrackerScreen : Screen(Text.literal("Loot Tracker")) {

    // computed each frame
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

    // click hit-rects captured each frame
    private var closeX = 0
    private var closeY = 0
    private var closeS = 0
    private var clearX = 0
    private var clearY = 0
    private var clearW = 0
    private var clearH = 0
    private var runsY = 0
    private var runsTileX = 0
    private var rowY = IntArray(0)
    private var rowCountX = 0
    private var rowCountW = 30
    private var rowCountH = 16
    private var searchX = 0
    private var searchY = 0
    private var searchW = 0
    private var searchH = 0

    private var clearArmed = false
    private var clearArmedAt = 0L

    private lateinit var editBox: TextFieldWidget
    private var editKind = 0 // 0 none, 1 runs, 2 row
    private var editId = ""
    private var editName = ""

    private lateinit var searchField: TextFieldWidget

    override fun init() {
        CroesusPrices.refreshIfStale()
        editBox = TextFieldWidget(this.textRenderer, 0, 0, rowCountW, rowCountH, Text.literal(""))
        editBox.setMaxLength(9)
        editBox.setTextPredicate { s -> s.isEmpty() || s.matches(Regex("\\d{1,9}")) }

        searchField = TextFieldWidget(this.textRenderer, 0, 0, 100, SEARCH_H - 8, Text.literal(""))
        searchField.setMaxLength(64)
        searchField.setDrawsBackground(false)
        searchField.setChangedListener { _ -> scroll = 0 }
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

        val allRows = LootTrackerStore.rows()
        val runs = LootTrackerStore.runs()
        val rows = filterRows(allRows)

        renderHeader(ctx, mouseX, mouseY)
        val statBottom = renderStats(ctx, allRows, runs)
        val searchBottom = renderSearch(ctx, statBottom + GAP)

        listTop = searchBottom + GAP
        listX0 = contentX0 + PAD
        listX1 = contentX1 - PAD
        listH = (contentY1 - PAD - FOOTER_H) - listTop
        renderList(ctx, mouseX, mouseY, rows)
        renderFooter(ctx, mouseX, mouseY, allRows, rows)

        if (clearArmed && System.currentTimeMillis() - clearArmedAt > 3000) clearArmed = false

        super.render(ctx, mouseX, mouseY, delta)
    }

    private fun filterRows(rows: List<LootTrackerStore.Row>): List<LootTrackerStore.Row> {
        val q = searchField.text.trim().lowercase()
        if (q.isEmpty()) return rows
        val out = ArrayList<LootTrackerStore.Row>()
        for (r in rows) {
            if (r.name.lowercase().contains(q)) out.add(r)
        }
        return out
    }

    private fun renderHeader(ctx: DrawContext, mx: Int, my: Int) {
        val y = contentY0 + PAD
        ctx.drawText(this.textRenderer, "§l§bLoot Tracker", contentX0 + PAD, y, TEXT, true)
        ctx.drawText(this.textRenderer, "§8Auto-tracked from Croesus chests", contentX0 + PAD, y + 11, SUBTEXT, true)

        closeS = 18
        closeX = contentX1 - PAD - closeS
        closeY = contentY0 + PAD - 3
        val hov = hit(mx.toDouble(), my.toDouble(), closeX, closeY, closeS, closeS)
        ctx.fill(closeX, closeY, closeX + closeS, closeY + closeS, if (hov) BTN_HOV else BTN_BG)
        ctx.drawCenteredTextWithShadow(this.textRenderer, "§7x", closeX + closeS / 2, closeY + 5, if (hov) 0xFFFFFFFF.toInt() else SUBTEXT)
    }

    private fun renderStats(ctx: DrawContext, rows: List<LootTrackerStore.Row>, runs: Int): Int {
        val y = contentY0 + HEADER_H
        var totalDrops = 0
        var total = 0.0
        for (r in rows) {
            totalDrops += r.count
            total += rowValue(r)
        }
        val perRun = total / Math.max(1, runs)

        val usableW = contentX1 - contentX0 - PAD * 2
        val tileW = (usableW - 6 * 3) / 4
        var x = contentX0 + PAD
        statTile(ctx, x, y, tileW, "TOTAL", fmtCoins(total), GOLD); x += tileW + 6
        statTile(ctx, x, y, tileW, "PER RUN", fmtCoins(perRun), GOLD); x += tileW + 6
        statTile(ctx, x, y, tileW, "DROPS", totalDrops.toString(), TEXT); x += tileW + 6

        // runs tile — click the value to edit it directly (no +/- steppers)
        runsY = y
        runsTileX = x
        val rx = x
        val rhov = hit(curMx.toDouble(), curMy.toDouble(), rx, y, tileW, STAT_H - 6)
        ctx.fill(rx, y, rx + tileW, y + STAT_H - 6, if (rhov) BTN_HOV else TILE_BG)
        ctx.fill(rx, y, rx + tileW, y + 1, TILE_BORDER)
        ctx.drawText(this.textRenderer, "§8RUNS", rx + 6, y + 5, SUBTEXT, false)
        if (editKind == 1) {
            renderEditBox(ctx, rx + 6, y + 17)
        } else {
            val rs = runs.toString()
            ctx.drawText(this.textRenderer, "§f$rs", rx + 6, y + 18, TEXT, false)
        }

        return y + STAT_H
    }

    private fun statTile(ctx: DrawContext, x: Int, y: Int, w: Int, label: String, value: String, color: Int) {
        ctx.fill(x, y, x + w, y + STAT_H - 6, TILE_BG)
        ctx.fill(x, y, x + w, y + 1, TILE_BORDER)
        ctx.drawText(this.textRenderer, "§8$label", x + 6, y + 5, SUBTEXT, false)
        val v = this.textRenderer.trimToWidth(value, w - 10)
        ctx.drawText(this.textRenderer, v, x + 6, y + 18, color, false)
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
        searchField.render(ctx, 0, 0, 0f)
        if (searchField.text.isEmpty() && !searchField.isFocused) {
            ctx.drawText(this.textRenderer, "§8Search drops...", searchX + 16 + 2, searchY + (searchH - 8) / 2, SUBTEXT, false)
        }

        return searchY + searchH
    }

    private fun renderList(ctx: DrawContext, mx: Int, my: Int, rows: List<LootTrackerStore.Row>) {
        val x0 = listX0
        val x1 = listX1
        ctx.enableScissor(x0, listTop, x1, listTop + listH)

        val searching = searchField.text.trim().isNotEmpty()

        if (rows.isEmpty()) {
            val msg = if (searching) "§8no drops match your search" else "§8no drops tracked yet — open a Croesus chest"
            ctx.drawText(this.textRenderer, msg, x0, listTop + 6, SUBTEXT, true)
            rowY = IntArray(0)
        } else {
            val maxScroll = Math.max(0, rows.size * ROW_H - listH)
            scroll = MathHelper.clamp(scroll, 0, maxScroll)

            rowY = IntArray(rows.size)

            val y = listTop - scroll
            rowCountX = x0 + 4
            val nameX = rowCountX + rowCountW + 8

            for (i in rows.indices) {
                val r = rows[i]
                val rowTop = y + i * ROW_H
                if (rowTop + ROW_H < listTop || rowTop > listTop + listH) continue

                val hov = hit(mx.toDouble(), my.toDouble(), x0, rowTop, x1 - x0, ROW_H)
                val bg = if (hov) ROW_HOVER else (if ((i and 1) == 1) ROW_BG_ALT else ROW_BG)
                ctx.fill(x0, rowTop, x1, rowTop + ROW_H - 1, bg)

                rowY[i] = rowTop

                val countY = rowTop + (ROW_H - rowCountH) / 2
                if (isEditingRow(r)) {
                    renderEditBox(ctx, rowCountX, countY)
                } else {
                    val chov = hit(mx.toDouble(), my.toDouble(), rowCountX, countY, rowCountW, rowCountH)
                    ctx.fill(rowCountX, countY, rowCountX + rowCountW, countY + rowCountH, if (chov) BTN_HOV else BTN_BG)
                    val cs = r.count.toString()
                    val cw = this.textRenderer.getWidth(cs)
                    ctx.drawText(this.textRenderer, cs, rowCountX + (rowCountW - cw) / 2, countY + 4, if (chov) ACCENT else TEXT, false)
                }

                val v = rowValue(r)
                val value = if (v > 0) fmtCoins(v) else "—"
                val vw = this.textRenderer.getWidth(value)
                val valX = x1 - 8 - vw
                val textY = rowTop + (ROW_H - 8) / 2
                ctx.drawText(this.textRenderer, value, valX, textY, if (v > 0) GOLD else SUBTEXT, false)
                val maxNameW = Math.max(10, valX - nameX - 6)
                ctx.drawText(this.textRenderer, this.textRenderer.trimToWidth(r.name, maxNameW), nameX, textY, TEXT, false)
            }

            if (maxScroll > 0) {
                val barX = x1 - 2
                val trackH = listH
                val barH = Math.max(10, trackH * listH / (rows.size * ROW_H))
                val barY = listTop + (trackH - barH) * scroll / Math.max(1, maxScroll)
                ctx.fill(barX, listTop, barX + 2, listTop + trackH, 0x33FFFFFF)
                ctx.fill(barX, barY, barX + 2, barY + barH, ACCENT)
            }
        }

        ctx.disableScissor()
    }

    private fun renderFooter(ctx: DrawContext, mx: Int, my: Int, allRows: List<LootTrackerStore.Row>, shownRows: List<LootTrackerStore.Row>) {
        val y = contentY1 - PAD - FOOTER_H + 8

        val clearW0 = 140
        clearX = contentX0 + PAD; clearY = y; clearW = clearW0; clearH = FOOTER_H - 8
        val hov = hit(mx.toDouble(), my.toDouble(), clearX, clearY, clearW, clearH)
        ctx.fill(clearX, clearY, clearX + clearW, clearY + clearH, if (hov) DANGER_HOV else DANGER_BG)
        val label = if (clearArmed) "Click again to confirm" else "Clear All"
        val lw = this.textRenderer.getWidth(label)
        ctx.drawText(this.textRenderer, label, clearX + (clearW - lw) / 2, clearY + (clearH - 8) / 2, DANGER, false)

        if (shownRows.size != allRows.size) {
            val info = shownRows.size.toString() + " / " + allRows.size + " drops shown"
            val iw = this.textRenderer.getWidth(info)
            ctx.drawText(this.textRenderer, "§8$info", contentX1 - PAD - iw, y + (clearH - 8) / 2, SUBTEXT, false)
        }
    }

    private fun renderEditBox(ctx: DrawContext, x: Int, y: Int) {
        editBox.x = x; editBox.y = y; editBox.width = rowCountW
        editBox.render(ctx, 0, 0, 0f)
    }

    // ── input ────────────────────────────────────────────────────────────────
    override fun mouseClicked(click: Click, bl: Boolean): Boolean {
        val mx = click.x()
        val my = click.y()

        if (editKind != 0) {
            val ex = editBox.x
            val ey = editBox.y
            if (!hit(mx, my, ex, ey, rowCountW, rowCountH)) commitEdit()
        }

        if (hit(mx, my, closeX, closeY, closeS, closeS)) { close(); return true }

        if (hit(mx, my, searchX, searchY, searchW, searchH)) {
            searchField.isFocused = true
            scroll = 0
            return searchField.mouseClicked(click, bl)
        } else {
            searchField.isFocused = false
        }

        if (hit(mx, my, runsTileX, runsY, (contentX1 - contentX0 - PAD * 2 - 6 * 3) / 4, STAT_H - 6)) {
            openEdit(1, "", "", LootTrackerStore.runs()); return true
        }

        val rows = filterRows(LootTrackerStore.rows())
        var i = 0
        while (i < rowY.size && i < rows.size) {
            val r = rows[i]
            val countY = rowY[i] + (ROW_H - rowCountH) / 2
            if (hit(mx, my, rowCountX, countY, rowCountW, rowCountH)) { openEdit(2, r.id, r.name, r.count); return true }
            i++
        }

        if (hit(mx, my, clearX, clearY, clearW, clearH)) {
            if (clearArmed) {
                LootTrackerStore.clear()
                clearArmed = false
            } else {
                clearArmed = true
                clearArmedAt = System.currentTimeMillis()
            }
            return true
        }

        return super.mouseClicked(click, bl)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (hit(mouseX, mouseY, listX0, listTop, listX1 - listX0, listH)) {
            scroll -= (verticalAmount * ROW_H).toInt()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (editKind != 0) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) { cancelEdit(); return true }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) { commitEdit(); return true }
            editBox.keyPressed(input)
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
        if (editKind != 0) { editBox.charTyped(input); return true }
        if (searchField.isFocused) { searchField.charTyped(input); return true }
        return super.charTyped(input)
    }

    private fun openEdit(kind: Int, id: String?, name: String?, current: Int) {
        editKind = kind
        editId = id ?: ""
        editName = name ?: ""
        editBox.text = current.toString()
        editBox.isFocused = true
    }

    private fun commitEdit() {
        if (editKind == 0) return
        val t = editBox.text.trim()
        try {
            val n = if (t.isEmpty()) 0 else t.toInt()
            if (editKind == 1) LootTrackerStore.setRuns(n)
            else if (editKind == 2) LootTrackerStore.setCount(editName, editId, n)
        } catch (ignored: NumberFormatException) {
        }
        editKind = 0
        editBox.isFocused = false
    }

    private fun cancelEdit() {
        editKind = 0
        editBox.isFocused = false
    }

    private fun isEditingRow(r: LootTrackerStore.Row): Boolean {
        if (editKind != 2) return false
        return if (editId.isNotEmpty()) editId == r.id else editName.equals(r.name, ignoreCase = true)
    }

    override fun close() {
        if (editKind != 0) commitEdit()
        MinecraftClient.getInstance().setScreen(null)
    }

    override fun shouldPause(): Boolean = false

    companion object {
        // palette — dark slate with a teal accent, matches the rest of FishMod's screens
        private val BG_TOP = 0xEE0A0E12.toInt()
        private val BG_BOT = 0xF2050709.toInt()
        private val PANEL_BG = 0xFF11161C.toInt()
        private val PANEL_BORDER = 0xFF232D36.toInt()
        private val TILE_BG = 0xFF161D24.toInt()
        private val TILE_BORDER = 0xFF232D36.toInt()
        private val ROW_BG = 0xFF14191F.toInt()
        private val ROW_BG_ALT = 0xFF171D24.toInt()
        private val ROW_HOVER = 0xFF1E262E.toInt()
        private val ACCENT = 0xFF2FD1C4.toInt()
        private val ACCENT_DIM = 0xFF1C7A72.toInt()
        private val TEXT = 0xFFEFF4F7.toInt()
        private val SUBTEXT = 0xFF8A97A3.toInt()
        private val GOLD = 0xFFFFD479.toInt()
        private val DANGER = 0xFFE0574B.toInt()
        private val DANGER_BG = 0xFF241213.toInt()
        private val DANGER_HOV = 0xFF3A1517.toInt()
        private val BTN_BG = 0xFF1B2229.toInt()
        private val BTN_HOV = 0xFF25313A.toInt()
        private val SEARCH_BG = 0xFF161D24.toInt()
        private val SEARCH_BG_FOCUS = 0xFF1B2530.toInt()

        private val NUM = DecimalFormat("#,###")

        private const val MARGIN = 20
        private const val HEADER_H = 40
        private const val STAT_H = 50
        private const val SEARCH_H = 24
        private const val ROW_H = 24
        private const val FOOTER_H = 26
        private const val GAP = 10
        private const val PAD = 14

        // ── helpers ──────────────────────────────────────────────────────────────
        private fun rowValue(r: LootTrackerStore.Row): Double {
            if (r.id.isEmpty()) return 0.0
            return CroesusPrices.price(r.id) * r.count
        }

        private fun hit(mx: Double, my: Double, x: Int, y: Int, w: Int, h: Int): Boolean {
            return mx >= x && mx <= x + w && my >= y && my <= y + h
        }

        private fun fmtCoins(v: Double): String {
            if (v < 0) return "—"
            if (v == 0.0) return "0"
            if (v >= 1_000_000_000.0) return String.format("%.2fB", v / 1_000_000_000.0)
            if (v >= 1_000_000.0) return String.format("%.2fM", v / 1_000_000.0)
            if (v >= 1_000.0) return String.format("%.1fk", v / 1_000.0)
            return NUM.format(v)
        }

        // public helpers for the .dprofit party command
        @JvmStatic
        fun totalValueForChat(): Double {
            var sum = 0.0
            for (r in LootTrackerStore.rows()) sum += rowValue(r)
            return sum
        }

        @JvmStatic
        fun runsForChat(): Int = LootTrackerStore.runs()

        @JvmStatic
        fun fmtCoinsPublic(v: Double): String = fmtCoins(v)
    }
}
