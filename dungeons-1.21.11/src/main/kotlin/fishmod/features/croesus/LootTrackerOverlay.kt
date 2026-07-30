package fishmod.features.croesus

import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.mixin.accessors.KeyBindingAccessor
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import java.text.DecimalFormat

/** Loot/profit tracker drawn over the player's inventory in the Dungeon Hub; rows auto-populated by [CroesusLootDetector]. */
object LootTrackerOverlay {

    // palette (matches FishModScreen slate/teal, square corners)
    private const val ACCENT = 0xFF24B6B0.toInt()
    private const val ACCENT2 = 0xFF3AD8D1.toInt()
    private const val BG = 0xF00C1318.toInt()
    private const val PANEL2 = 0xF0101923.toInt() // slightly lighter than BG, for the stats footer
    private const val ROW_ALT = 0x14FFFFFF // faint stripe on odd rows
    private const val BORDER = 0xFF24333C.toInt()
    private const val DIVIDER = 0xFF18222C.toInt()
    private const val TEXT = 0xFFEDF1F5.toInt()
    private const val SUB = 0xFF7E8A98.toInt()
    private const val GOLD = 0xFFFFD479.toInt()
    private const val BTN_BG = 0xFF1B2228.toInt()
    private const val BTN_HOV = 0xFF24333C.toInt()
    private const val CLEAR_BG = 0xFF1B1414.toInt()
    private const val CLEAR_HOV = 0xFF3A1414.toInt()

    private val NUM = DecimalFormat("#,###")

    // text widget (lazy, like SearchBar). numberBox = active count/runs edit.
    private var numberBox: TextFieldWidget? = null

    // which numeric value numberBox is editing: 0 none, 1 runs, 2 a drop row
    private var editKind = 0
    private var editId = ""
    private var editName = ""

    // dragging
    private var dragging = false
    private var dragGrabX = 0
    private var dragGrabY = 0

    // geometry captured each frame for click hit-testing
    private var visible = false
    private var panelX = 0
    private var panelY = 0
    private var panelW = 0
    private var panelH = 0
    private var titleBarY = 0
    private var titleBarH = 0
    private var rowMinusX = 0
    private var rowCountX = 0
    private var rowPlusX = 0
    private var rowCountW = 26
    private var rowY = IntArray(0)
    private var runsMinusX = 0
    private var runsCountX = 0
    private var runsPlusX = 0
    private var runsRowY = 0
    private var clearX = 0
    private var clearY = 0
    private var clearW = 0
    private var clearH = 0
    private var numX = 0
    private var numY = 0
    private var numW = 0
    private var numH = 0 // last-rendered numberBox rect

    // layout constants
    private const val PAD = 7
    private const val BTN = 12
    private const val COUNT_H = 13
    private const val TITLE_H = 16
    private const val ROW_H = 16
    private const val DIV_GAP = 6
    private const val RUNS_H = 17
    private const val LINE_H = 11
    private const val CLEAR_H = 16
    private const val STATS_PAD = 5
    private const val PANEL_W = 200

    // True when driven by the standalone LootTrackerScreen (/fmloot) instead of the vanilla inventory screen.
    private var standalone = false

    @JvmStatic
    fun setStandalone(value: Boolean) { standalone = value }

    // ── gates ────────────────────────────────────────────────────────────────
    private fun active(): Boolean {
        // /fmloot always renders regardless of the background auto-tracking toggle.
        if (standalone) return true
        if (!FishSettings.lootTrackerEnabled) return false
        val mc = MinecraftClient.getInstance()
        if (mc.currentScreen !is InventoryScreen) return false
        return Location.getCurrentLocation() == Location.DUNGEON_HUB
    }

    private fun exists(): Boolean {
        if (numberBox != null) return true
        val mc = MinecraftClient.getInstance()
        if (mc.textRenderer == null || mc.window == null) return false
        val box = TextFieldWidget(mc.textRenderer, 0, 0, rowCountW, COUNT_H, Text.literal(""))
        box.setMaxLength(9)
        box.setTextPredicate { s -> s.isEmpty() || s.matches(Regex("\\d{1,9}")) }
        numberBox = box
        return true
    }

    // ── render ───────────────────────────────────────────────────────────────
    @JvmStatic
    fun renderInScreen(ctx: DrawContext, mx: Int, my: Int) {
        visible = false
        if (!active() || !exists()) return
        CroesusPrices.refreshIfStale() // fire-and-forget; warms price cache
        val mc = MinecraftClient.getInstance()
        val tr = mc.textRenderer
        val screenW = mc.window.scaledWidth
        val screenH = mc.window.scaledHeight

        val rows = LootTrackerStore.rows()
        val runsCount = LootTrackerStore.runs()
        val drawnRows = maxOf(rows.size, 1)

        panelW = PANEL_W
        panelH = PAD + TITLE_H + 2 +
            drawnRows * ROW_H + DIV_GAP + RUNS_H + DIV_GAP +
            STATS_PAD * 2 + LINE_H * 3 + STATS_PAD + CLEAR_H + PAD

        // stop a drag once the mouse button is released
        val mouseDown = GLFW.glfwGetMouseButton(mc.window.handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        if (dragging && !mouseDown) { dragging = false; fishmod.utils.config.FishConfig.manager.save() }

        // Position priority: dragging > saved position > auto-anchor.
        if (dragging) {
            panelX = mx - dragGrabX
            panelY = my - dragGrabY
        } else if (FishSettings.lootTrackerX >= 0) {
            panelX = FishSettings.lootTrackerX
            panelY = FishSettings.lootTrackerY
        } else if (standalone) {
            panelX = (screenW - panelW) / 2
            panelY = (screenH - panelH) / 2
        } else {
            val s = mc.currentScreen as HandledScreenAccessor
            val bgX = s.bgX
            val bgY = s.bgY
            val bgW = s.bgWidth
            panelX = bgX + bgW + 6
            if (panelX + panelW > screenW) panelX = bgX - panelW - 6
            panelY = bgY
        }
        panelX = clamp(panelX, 2, maxOf(2, screenW - panelW - 2))
        panelY = clamp(panelY, 2, maxOf(2, screenH - panelH - 2))
        if (dragging) { FishSettings.lootTrackerX = panelX; FishSettings.lootTrackerY = panelY }

        // background + frame + top accent
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, BORDER)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 2, ACCENT)

        var y = panelY + PAD
        // title bar (drag handle)
        titleBarY = panelY; titleBarH = PAD + TITLE_H - 3
        ctx.drawText(tr, "§l⠿ Loot Tracker", panelX + PAD, y + 2, ACCENT, true)
        val hdrCount = rows.size.toString() + " types"
        val hdrW = tr.getWidth(hdrCount)
        ctx.drawText(tr, "§8$hdrCount", panelX + panelW - PAD - hdrW, y + 2, SUB, true)
        y += TITLE_H
        ctx.fill(panelX + PAD, y, panelX + panelW - PAD, y + 1, DIVIDER)
        y += 2

        // drop rows: [-] [count] [+]  Name ............ value
        val x0 = panelX + PAD
        rowMinusX = x0
        rowCountX = x0 + BTN + 3
        rowPlusX = rowCountX + rowCountW + 3
        val nameX = rowPlusX + BTN + 5
        rowY = IntArray(rows.size)
        if (rows.isEmpty()) {
            ctx.drawText(tr, "§8no drops yet — open a Croesus chest", x0, y + 4, SUB, true)
            y += ROW_H
        } else {
            for (i in rows.indices) {
                val r = rows[i]
                if ((i and 1) == 1) ctx.fill(panelX + 1, y, panelX + panelW - 1, y + ROW_H, ROW_ALT)
                val ct = y + (ROW_H - BTN) / 2 // controls top, vertically centered in the row
                rowY[i] = ct
                drawMini(ctx, tr, rowMinusX, ct, "-", hit(mx.toDouble(), my.toDouble(), rowMinusX, ct, BTN, BTN))
                drawMini(ctx, tr, rowPlusX, ct, "+", hit(mx.toDouble(), my.toDouble(), rowPlusX, ct, BTN, BTN))
                if (isEditingRow(r)) {
                    renderNumberBox(ctx, mx, my, rowCountX, ct)
                } else {
                    drawCountCell(ctx, tr, rowCountX, ct, r.count.toString(),
                        hit(mx.toDouble(), my.toDouble(), rowCountX, ct, rowCountW, COUNT_H))
                }
                val v = rowValue(r)
                val valStr = if (v > 0) fmtCoins(v) else "—"
                val vw = tr.getWidth(valStr)
                val valX = panelX + panelW - PAD - vw
                val textY = y + (ROW_H - 8) / 2
                ctx.drawText(tr, valStr, valX, textY, if (v > 0) GOLD else SUB, true)
                val maxNameW = maxOf(10, valX - nameX - 4)
                ctx.drawText(tr, tr.trimToWidth(r.name, maxNameW), nameX, textY, TEXT, true)
                y += ROW_H
            }
        }

        // divider
        ctx.fill(panelX + PAD, y, panelX + panelW - PAD, y + 1, DIVIDER)
        y += DIV_GAP

        // runs row: Runs:  [-] [count] [+]
        runsRowY = y
        val rct = y + (RUNS_H - BTN) / 2
        ctx.drawText(tr, "§7Runs", x0, y + (RUNS_H - 8) / 2, TEXT, true)
        runsPlusX = panelX + panelW - PAD - BTN
        runsCountX = runsPlusX - 3 - rowCountW
        runsMinusX = runsCountX - 3 - BTN
        drawMini(ctx, tr, runsMinusX, rct, "-", hit(mx.toDouble(), my.toDouble(), runsMinusX, rct, BTN, BTN))
        drawMini(ctx, tr, runsPlusX, rct, "+", hit(mx.toDouble(), my.toDouble(), runsPlusX, rct, BTN, BTN))
        if (editKind == 1) renderNumberBox(ctx, mx, my, runsCountX, rct)
        else drawCountCell(ctx, tr, runsCountX, rct, runsCount.toString(),
            hit(mx.toDouble(), my.toDouble(), runsCountX, rct, rowCountW, COUNT_H))
        y += RUNS_H
        ctx.fill(panelX + PAD, y, panelX + panelW - PAD, y + 1, DIVIDER)
        y += DIV_GAP

        // stats footer — its own shaded card so totals read as a distinct block from the rows
        var totalDrops = 0
        for (r in rows) totalDrops += r.count
        val total = totalValue()
        val perRun = total / maxOf(1, runsCount)
        val statsH = STATS_PAD * 2 + LINE_H * 3
        ctx.fill(panelX + 1, y, panelX + panelW - 1, y + statsH, PANEL2)
        y += STATS_PAD
        ctx.drawText(tr, "§7Drops  §f$totalDrops §8· ${rows.size} types", x0, y, TEXT, true)
        y += LINE_H
        ctx.drawText(tr, "§7Total  §6${fmtCoins(total)}", x0, y, TEXT, true)
        y += LINE_H
        ctx.drawText(tr, "§7Per run  §6${fmtCoins(perRun)}", x0, y, TEXT, true)
        y += LINE_H + STATS_PAD

        // clear button
        clearX = x0; clearY = y; clearW = panelW - PAD * 2; clearH = CLEAR_H
        val ch = hit(mx.toDouble(), my.toDouble(), clearX, clearY, clearW, clearH)
        ctx.fill(clearX, clearY, clearX + clearW, clearY + clearH, if (ch) CLEAR_HOV else CLEAR_BG)
        val cl = "Clear"
        val clw = tr.getWidth(cl)
        ctx.drawText(tr, if (ch) "§c$cl" else "§7$cl", clearX + (clearW - clw) / 2,
            clearY + (clearH - 8) / 2, if (ch) 0xFFFF6B6B.toInt() else SUB, true)

        visible = true
    }

    private fun renderNumberBox(ctx: DrawContext, mx: Int, my: Int, x: Int, y: Int) {
        val box = numberBox!!
        box.x = x; box.y = y; box.width = rowCountW
        box.render(ctx, mx, my, 0f)
        numX = x; numY = y; numW = rowCountW; numH = COUNT_H
    }

    private fun drawCountCell(ctx: DrawContext, tr: TextRenderer, x: Int, y: Int, text: String, hov: Boolean) {
        ctx.fill(x, y, x + rowCountW, y + COUNT_H, BORDER)
        ctx.fill(x + 1, y + 1, x + rowCountW - 1, y + COUNT_H - 1, if (hov) BTN_HOV else BTN_BG)
        val tw = tr.getWidth(text)
        ctx.drawText(tr, text, x + (rowCountW - tw) / 2, y + (COUNT_H - 8) / 2, if (hov) ACCENT2 else TEXT, false)
    }

    private fun drawMini(ctx: DrawContext, tr: TextRenderer, x: Int, y: Int, glyph: String, hov: Boolean) {
        ctx.fill(x, y, x + BTN, y + BTN, BORDER)
        ctx.fill(x + 1, y + 1, x + BTN - 1, y + BTN - 1, if (hov) BTN_HOV else BTN_BG)
        val gw = tr.getWidth(glyph)
        ctx.drawText(tr, glyph, x + (BTN - gw) / 2, y + (BTN - 8) / 2, if (hov) ACCENT2 else SUB, false)
    }

    // ── click ────────────────────────────────────────────────────────────────
    @JvmStatic
    fun handleScreenClick(mx: Double, my: Double): Boolean {
        if (!visible) return false

        // commit a pending number edit if the click is outside the number box
        if (editKind != 0) {
            if (hit(mx, my, numX, numY, numW, numH)) return true // keep editing
            commitNumber()
        }

        // title bar -> start dragging
        if (hit(mx, my, panelX, titleBarY, panelW, titleBarH)) {
            dragging = true
            dragGrabX = mx.toInt() - panelX
            dragGrabY = my.toInt() - panelY
            return true
        }

        // runs controls
        if (hit(mx, my, runsMinusX, runsRowY + 2, BTN, BTN)) { LootTrackerStore.setRuns(LootTrackerStore.runs() - 1); return true }
        if (hit(mx, my, runsPlusX, runsRowY + 2, BTN, BTN)) { LootTrackerStore.setRuns(LootTrackerStore.runs() + 1); return true }
        if (hit(mx, my, runsCountX, runsRowY + 2, rowCountW, COUNT_H)) { openNumberEditor(1, "", "", LootTrackerStore.runs()); return true }

        // per-row controls
        val rows = LootTrackerStore.rows()
        var i = 0
        while (i < rowY.size && i < rows.size) {
            val r = rows[i]
            if (hit(mx, my, rowMinusX, rowY[i], BTN, BTN)) { LootTrackerStore.addOrIncrement(r.name, r.id, -1); return true }
            if (hit(mx, my, rowPlusX, rowY[i], BTN, BTN)) { LootTrackerStore.addOrIncrement(r.name, r.id, 1); return true }
            if (hit(mx, my, rowCountX, rowY[i], rowCountW, COUNT_H)) { openNumberEditor(2, r.id, r.name, r.count); return true }
            i++
        }

        // clear
        if (hit(mx, my, clearX, clearY, clearW, clearH)) { LootTrackerStore.clear(); return true }
        // anywhere else inside the panel -> consume
        if (hit(mx, my, panelX, panelY, panelW, panelH)) return true
        // outside -> let the click reach the inventory
        return false
    }

    // ── keyboard (mirrors SearchBar; routes to the number box when it's focused) ─────
    @JvmStatic
    fun keyPressed(input: KeyInput): Boolean {
        if (!active() || !exists()) return false
        val f = focusedField() ?: return false
        // never eat the drop key — fall through
        try {
            val dropCode = (MinecraftClient.getInstance().options.dropKey as KeyBindingAccessor).boundKey.code
            if (input.key() == dropCode) {
                commitNumber()
                return false
            }
        } catch (ignored: Exception) {}
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            cancelNumber()
            return false
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            commitNumber()
            return true
        }
        f.keyPressed(input)
        return true // consume -> mixin returns false -> no inventory close / hotbar swap
    }

    @JvmStatic
    fun charTyped(input: CharInput) {
        if (!active() || !exists()) return
        val f = focusedField()
        f?.charTyped(input)
    }

    private fun focusedField(): TextFieldWidget? {
        val box = numberBox
        return if (box != null && box.isFocused) box else null
    }

    // ── number editor ──────────────────────────────────────────────────────────
    private fun openNumberEditor(kind: Int, id: String?, name: String?, current: Int) {
        editKind = kind
        editId = id ?: ""
        editName = name ?: ""
        numberBox!!.text = current.toString()
        numberBox!!.isFocused = true
    }

    private fun commitNumber() {
        if (editKind == 0) return
        val t = numberBox!!.text.trim()
        try {
            val n = if (t.isEmpty()) 0 else t.toInt()
            if (editKind == 1) LootTrackerStore.setRuns(n)
            else if (editKind == 2) LootTrackerStore.setCount(editName, editId, n)
        } catch (ignored: NumberFormatException) {}
        editKind = 0
        numberBox!!.isFocused = false
    }

    private fun cancelNumber() {
        editKind = 0
        numberBox!!.isFocused = false
    }

    private fun isEditingRow(r: LootTrackerStore.Row): Boolean {
        if (editKind != 2) return false
        return if (editId.isNotEmpty()) editId == r.id else editName.equals(r.name, ignoreCase = true)
    }

    // ── helpers ────────────────────────────────────────────────────────────────
    private fun rowValue(r: LootTrackerStore.Row): Double {
        if (r.id.isNullOrEmpty()) return 0.0
        return CroesusPrices.price(r.id) * r.count
    }

    private fun totalValue(): Double {
        var sum = 0.0
        for (r in LootTrackerStore.rows()) sum += rowValue(r)
        return sum
    }

    private fun hit(mx: Double, my: Double, x: Int, y: Int, w: Int, h: Int): Boolean {
        return mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    private fun clamp(v: Int, lo: Int, hi: Int): Int {
        return if (v < lo) lo else (if (v > hi) hi else v)
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
    fun totalValueForChat(): Double = totalValue()

    @JvmStatic
    fun runsForChat(): Int = LootTrackerStore.runs()

    @JvmStatic
    fun fmtCoinsPublic(v: Double): String = fmtCoins(v)
}
