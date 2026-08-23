package fishmod.features.croesus

import fishmod.features.HasNvgOverlay
import fishmod.features.ScreenTheme
import fishmod.utils.rendering.NvgContext
import fishmod.utils.rendering.NvgGlStateGuard
import fishmod.utils.rendering.NvgRecorder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.lwjgl.glfw.GLFW
import org.lwjgl.nanovg.NanoVG
import java.text.DecimalFormat

/** Full-page /fmloot screen; rows are populated by `CroesusLootDetector`, this is just the view. */
class LootTrackerScreen : Screen(Component.literal("Loot Tracker")), HasNvgOverlay {

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

    private lateinit var editBox: EditBox
    private var editBoxFiltering = false
    private var editKind = 0 // 0 none, 1 runs, 2 row
    private var editId = ""
    private var editName = ""

    private lateinit var searchField: EditBox

    override fun init() {
        CroesusPrices.refreshIfStale()
        editBox = EditBox(this.font, 0, 0, rowCountW, rowCountH, Component.literal(""))
        editBox.setMaxLength(9)
        editBox.setResponder { s ->
            if (editBoxFiltering || s.isEmpty() || s.matches(Regex("\\d{1,9}"))) return@setResponder
            editBoxFiltering = true
            editBox.setValue(s.replace(Regex("[^\\d]"), ""))
            editBoxFiltering = false
        }

        searchField = EditBox(this.font, 0, 0, 100, SEARCH_H - 8, Component.literal(""))
        searchField.setMaxLength(64)
        searchField.setBordered(false)
        searchField.setResponder { _ -> scroll = 0 }
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        curMx = fishmod.utils.rendering.UiScale.vx(mouseX); curMy = fishmod.utils.rendering.UiScale.vx(mouseY)
        NvgRecorder.clear()
        val vw = (this.width / fishmod.utils.rendering.UiScale.factor()).toInt()
        val vh = (this.height / fishmod.utils.rendering.UiScale.factor()).toInt()
        // Recorded in virtual space like everything else so replay()'s uniform scale brings it
        // back to exactly this.width/this.height — a real-space size here would get shrunk too.
        NvgRecorder.fillRectVGradient(0f, 0f, vw.toFloat(), vh.toFloat(), BG_TOP, BG_BOT)

        contentX0 = MARGIN
        contentX1 = vw - MARGIN
        contentY0 = MARGIN
        contentY1 = vh - MARGIN

        ScreenTheme.nRect(contentX0 - 1, contentY0 - 1, contentX1 - contentX0 + 2, contentY1 - contentY0 + 2, PANEL_BORDER)
        ScreenTheme.nRect(contentX0, contentY0, contentX1 - contentX0, contentY1 - contentY0, PANEL_BG)
        ScreenTheme.nRect(contentX0, contentY0, contentX1 - contentX0, 3, ACCENT)

        val allRows = LootTrackerStore.rows()
        val runs = LootTrackerStore.runs()
        val rows = filterRows(allRows)

        renderHeader()
        val statBottom = renderStats(allRows, runs)
        val searchBottom = renderSearch(statBottom + GAP)

        listTop = searchBottom + GAP
        listX0 = contentX0 + PAD
        listX1 = contentX1 - PAD
        listH = (contentY1 - PAD - FOOTER_H) - listTop
        renderList(rows)
        renderFooter(allRows, rows)

        if (clearArmed && System.currentTimeMillis() - clearArmedAt > 3000) clearArmed = false

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun filterRows(rows: List<LootTrackerStore.Row>): List<LootTrackerStore.Row> {
        val q = searchField.value.trim().lowercase()
        val matched = if (q.isEmpty()) rows else rows.filter { it.name.lowercase().contains(q) }
        return matched.sortedByDescending { rowValue(it) }
    }

    private fun renderHeader() {
        val y = contentY0 + PAD
        ScreenTheme.nst("Loot Tracker", contentX0 + PAD, y, ACCENT)
        ScreenTheme.nst("Auto-tracked from Croesus chests", contentX0 + PAD, y + 11, SUBTEXT, 0.5f)

        closeS = 18
        closeX = contentX1 - PAD - closeS
        closeY = contentY0 + PAD - 3
        val hov = hit(curMx.toDouble(), curMy.toDouble(), closeX, closeY, closeS, closeS)
        ScreenTheme.nRect(closeX, closeY, closeS, closeS, if (hov) BTN_HOV else BTN_BG)
        val label = "x"
        val tw = ScreenTheme.nstw(label)
        ScreenTheme.nst(label, closeX + closeS / 2 - tw / 2, closeY + 5, if (hov) 0xFFFFFFFF.toInt() else SUBTEXT)
    }

    private fun renderStats(rows: List<LootTrackerStore.Row>, runs: Int): Int {
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
        statTile(x, y, tileW, "TOTAL", fmtCoins(total), GOLD); x += tileW + 6
        statTile(x, y, tileW, "PER RUN", fmtCoins(perRun), GOLD); x += tileW + 6
        statTile(x, y, tileW, "DROPS", totalDrops.toString(), TEXT); x += tileW + 6

        // runs tile — click the value to edit it directly (no +/- steppers)
        runsY = y
        runsTileX = x
        val rx = x
        val rhov = hit(curMx.toDouble(), curMy.toDouble(), rx, y, tileW, STAT_H - 6)
        ScreenTheme.nRect(rx, y, tileW, STAT_H - 6, if (rhov) BTN_HOV else TILE_BG)
        ScreenTheme.nRect(rx, y, tileW, 1, TILE_BORDER)
        ScreenTheme.nst("RUNS", rx + 6, y + 5, SUBTEXT, 0.5f)
        if (editKind == 1) {
            renderEditBox(rx + 6, y + 17)
        } else {
            ScreenTheme.nst(runs.toString(), rx + 6, y + 18, TEXT)
        }

        return y + STAT_H
    }

    private fun statTile(x: Int, y: Int, w: Int, label: String, value: String, color: Int) {
        ScreenTheme.nRect(x, y, w, STAT_H - 6, TILE_BG)
        ScreenTheme.nRect(x, y, w, 1, TILE_BORDER)
        ScreenTheme.nst(label, x + 6, y + 5, SUBTEXT, 0.5f)
        ScreenTheme.nst(clip(value, w - 10), x + 6, y + 18, color)
    }

    private fun renderSearch(y: Int): Int {
        searchX = contentX0 + PAD
        searchY = y
        searchW = contentX1 - contentX0 - PAD * 2
        searchH = SEARCH_H

        val focused = searchField.isFocused
        ScreenTheme.nRect(searchX, searchY, searchW, searchH, if (focused) SEARCH_BG_FOCUS else SEARCH_BG)
        ScreenTheme.nRect(searchX, searchY, searchW, 1, if (focused) ACCENT else TILE_BORDER)

        ScreenTheme.nst("search:", searchX + 6, searchY + (searchH - 8) / 2, SUBTEXT, 0.5f)

        val fieldX = searchX + 34
        ScreenTheme.nTextFieldContent(searchField, focused, fieldX, searchY, searchW - 40, searchH)
        if (searchField.value.isEmpty() && !focused) {
            ScreenTheme.nst("Search drops...", fieldX + 2, searchY + (searchH - 8) / 2, SUBTEXT, 0.5f)
        }

        return searchY + searchH
    }

    private fun renderList(rows: List<LootTrackerStore.Row>) {
        val x0 = listX0
        val x1 = listX1
        NvgRecorder.pushScissor(x0.toFloat(), listTop.toFloat(), (x1 - x0).toFloat(), listH.toFloat())

        val searching = searchField.value.trim().isNotEmpty()

        if (rows.isEmpty()) {
            val msg = if (searching) "no drops match your search" else "no drops tracked yet - open a Croesus chest"
            ScreenTheme.nst(msg, x0, listTop + 6, SUBTEXT, 0.5f)
            rowY = IntArray(0)
        } else {
            val maxScroll = Math.max(0, rows.size * ROW_H - listH)
            scroll = Mth.clamp(scroll, 0, maxScroll)

            rowY = IntArray(rows.size)

            val y = listTop - scroll
            rowCountX = x0 + 4
            val nameX = rowCountX + rowCountW + 8

            for (i in rows.indices) {
                val r = rows[i]
                val rowTop = y + i * ROW_H
                if (rowTop + ROW_H < listTop || rowTop > listTop + listH) continue

                val hov = hit(curMx.toDouble(), curMy.toDouble(), x0, rowTop, x1 - x0, ROW_H)
                val bg = if (hov) ROW_HOVER else (if ((i and 1) == 1) ROW_BG_ALT else ROW_BG)
                ScreenTheme.nRect(x0, rowTop, x1 - x0, ROW_H - 1, bg)

                rowY[i] = rowTop

                val countY = rowTop + (ROW_H - rowCountH) / 2
                if (isEditingRow(r)) {
                    renderEditBox(rowCountX, countY)
                } else {
                    val chov = hit(curMx.toDouble(), curMy.toDouble(), rowCountX, countY, rowCountW, rowCountH)
                    ScreenTheme.nRect(rowCountX, countY, rowCountW, rowCountH, if (chov) BTN_HOV else BTN_BG)
                    val cs = r.count.toString()
                    val cw = ScreenTheme.nstw(cs)
                    ScreenTheme.nst(cs, rowCountX + (rowCountW - cw) / 2, countY + 4, if (chov) ACCENT else TEXT)
                }

                val v = rowValue(r)
                val value = if (v > 0) fmtCoins(v) else "-"
                val vw = ScreenTheme.nstw(value)
                val valX = x1 - 8 - vw
                val textY = rowTop + (ROW_H - 8) / 2
                ScreenTheme.nst(value, valX, textY, if (v > 0) GOLD else SUBTEXT)
                val maxNameW = Math.max(10, valX - nameX - 6)
                ScreenTheme.nst(clip(r.name, maxNameW), nameX, textY, TEXT)
            }

            if (maxScroll > 0) {
                val barX = x1 - 2
                val trackH = listH
                val barH = Math.max(10, trackH * listH / (rows.size * ROW_H))
                val barY = listTop + (trackH - barH) * scroll / Math.max(1, maxScroll)
                ScreenTheme.nRect(barX, listTop, 2, trackH, 0x33FFFFFF)
                ScreenTheme.nRect(barX, barY, 2, barH, ACCENT)
            }
        }

        NvgRecorder.popScissor()
    }

    private fun renderFooter(allRows: List<LootTrackerStore.Row>, shownRows: List<LootTrackerStore.Row>) {
        val y = contentY1 - PAD - FOOTER_H + 8

        val clearW0 = 140
        clearX = contentX0 + PAD; clearY = y; clearW = clearW0; clearH = FOOTER_H - 8
        val hov = hit(curMx.toDouble(), curMy.toDouble(), clearX, clearY, clearW, clearH)
        ScreenTheme.nRect(clearX, clearY, clearW, clearH, if (hov) DANGER_HOV else DANGER_BG)
        val label = if (clearArmed) "Click again to confirm" else "Clear All"
        val lw = ScreenTheme.nstw(label)
        ScreenTheme.nst(label, clearX + (clearW - lw) / 2, clearY + (clearH - 8) / 2, DANGER)

        if (shownRows.size != allRows.size) {
            val info = shownRows.size.toString() + " / " + allRows.size + " drops shown"
            val iw = ScreenTheme.nstw(info)
            ScreenTheme.nst(info, contentX1 - PAD - iw, y + (clearH - 8) / 2, SUBTEXT, 0.5f)
        }
    }

    private fun renderEditBox(x: Int, y: Int) {
        editBox.setX(x); editBox.setY(y); editBox.width = rowCountW
        ScreenTheme.nTextField(editBox, true, x, y, rowCountW, rowCountH)
    }

    private fun clip(s: String, maxW: Int): String {
        if (ScreenTheme.nstw(s) <= maxW) return s
        var out = s
        while (out.length > 1 && ScreenTheme.nstw("$out...") > maxW) out = out.substring(0, out.length - 1)
        return "$out..."
    }

    // ── input ────────────────────────────────────────────────────────────────
    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = fishmod.utils.rendering.UiScale.vx(click.x()).toDouble()
        val my = fishmod.utils.rendering.UiScale.vx(click.y()).toDouble()

        if (editKind != 0) {
            val ex = editBox.x
            val ey = editBox.y
            if (!hit(mx, my, ex, ey, rowCountW, rowCountH)) commitEdit()
        }

        if (hit(mx, my, closeX, closeY, closeS, closeS)) { onClose(); return true }

        if (hit(mx, my, searchX, searchY, searchW, searchH)) {
            searchField.isFocused = true
            scroll = 0
            return searchField.mouseClicked(click, doubled)
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

        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val mouseX = fishmod.utils.rendering.UiScale.vx(mouseX).toDouble()
        val mouseY = fishmod.utils.rendering.UiScale.vx(mouseY).toDouble()
        if (hit(mouseX, mouseY, listX0, listTop, listX1 - listX0, listH)) {
            scroll -= (verticalAmount * ROW_H).toInt()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (editKind != 0) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) { cancelEdit(); return true }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) { commitEdit(); return true }
            editBox.keyPressed(input)
            return true
        }
        if (searchField.isFocused) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                if (searchField.value.isNotEmpty()) { searchField.setValue(""); return true }
                searchField.isFocused = false
                return true
            }
            searchField.keyPressed(input)
            return true
        }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        if (editKind != 0) { editBox.charTyped(input); return true }
        if (searchField.isFocused) { searchField.charTyped(input); return true }
        return super.charTyped(input)
    }

    private fun openEdit(kind: Int, id: String?, name: String?, current: Int) {
        editKind = kind
        editId = id ?: ""
        editName = name ?: ""
        editBox.setValue(current.toString())
        editBox.isFocused = true
    }

    private fun commitEdit() {
        if (editKind == 0) return
        val t = editBox.value.trim()
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

    override fun onClose() {
        if (editKind != 0) commitEdit()
        Minecraft.getInstance().setScreen(null)
    }

    override fun isPauseScreen(): Boolean = false

    // ── NanoVG overlay ───────────────────────────────────────────────────────────

    private val nvgGlState = NvgGlStateGuard()
    private var nvgFailureLogged = false

    override fun paintNvgOverlay() {
        nvgGlState.capture()
        try {
            val ctx = NvgContext.get()
            val pixelRatio = Minecraft.getInstance().window.guiScale.toFloat()
            NanoVG.nvgBeginFrame(ctx, this.width.toFloat(), this.height.toFloat(), pixelRatio)
            NvgRecorder.replay(fishmod.utils.rendering.UiScale.factor())
            NanoVG.nvgEndFrame(ctx)
        } catch (t: Throwable) {
            if (!nvgFailureLogged) {
                nvgFailureLogged = true
                fishmod.utils.debug.Debug.LOGGER.error("[NanoVG] LootTrackerScreen paintNvgOverlay failed", t)
            }
        } finally {
            nvgGlState.restore()
        }
    }

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
