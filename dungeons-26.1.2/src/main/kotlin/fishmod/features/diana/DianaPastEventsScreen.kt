package fishmod.features.diana

import fishmod.features.HasUiOverlay
import fishmod.features.ScreenTheme
import fishmod.features.croesus.CroesusPrices
import fishmod.features.croesus.LootIcons
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiScale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

// Diana profit across the events you pick (current event = index -1)
class DianaPastEventsScreen(private val parent: Screen? = null) :
    Screen(Component.literal("Diana Profit")), HasUiOverlay {

    private class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val action: () -> Unit) {
        fun contains(mx: Int, my: Int) = mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    private class Row(val key: String, val name: String, val count: Long, val value: Double)

    private class Ev(val idx: Int, val label: String, val items: Map<String, Long>, val mobs: Map<String, Long>, val timeMs: Long)

    private val hits = ArrayList<Hit>()
    private var curMx = 0
    private var curMy = 0
    private val selected = HashSet<Int>().apply { add(-1) }
    private var scroll = 0
    private var listX = 0
    private var listY = 0
    private var listW = 0
    private var listH = 0
    private lateinit var searchField: EditBox

    override fun init() {
        try { CroesusPrices.refreshIfStale() } catch (t: Throwable) { FishDiag.fail("DianaProfit.1", "price refresh failed", t) }
        searchField = EditBox(this.font, 0, 0, 100, 16, Component.literal(""))
        searchField.setMaxLength(64)
        searchField.setBordered(false)
        searchField.setResponder { _ -> scroll = 0 }
    }

    private fun events(): List<Ev> {
        val cur = DianaTracker.event
        val list = ArrayList<Ev>()
        list += Ev(-1, "Year ${cur.year} (now)", cur.items, cur.mobs, cur.timeMs)
        val past = DianaTracker.pastEvents
        for (i in past.indices.reversed()) list += Ev(i, "Year ${past[i].year}", past[i].items, past[i].mobs, past[i].timeMs)
        return list
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        try { renderScreen(ctx, mouseX, mouseY, delta) } catch (t: Throwable) { FishDiag.fail("DianaProfit.2", "diana profit render failed", t) }
    }

    private fun renderScreen(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        curMx = UiScale.vx(mouseX); curMy = UiScale.vx(mouseY)
        UiRecorder.clear()
        hits.clear()
        val vw = (this.width / UiScale.factor()).toInt()
        val vh = (this.height / UiScale.factor()).toInt()
        val sc = UiScale.factor()
        ctx.pose().pushMatrix()
        ctx.pose().scale(sc, sc)
        ctx.fill(0, 0, vw + 1, vh + 1, 0x99000000.toInt())

        val winW = min(640, vw - 32)
        val winH = min(400, vh - 32)
        val winX = (vw - winW) / 2
        val winY = (vh - winH) / 2
        for (i in 8 downTo 2 step 2) ScreenTheme.roundedRect(ctx, winX - i, winY - i + 4, winW + i * 2, winH + i * 2, 12 + i, 0x12000000)
        ScreenTheme.roundedRect(ctx, winX, winY, winW, winH, 12, PANEL)
        UiRecorder.roundedRectRing(winX.toFloat(), winY.toFloat(), winW.toFloat(), winH.toFloat(), 12f, 1f, 0, LINE)

        val gx = winX + 14
        val gy = winY + 11
        UiRecorder.fillRoundedRect(gx.toFloat(), gy.toFloat(), 22f, 22f, 6f, ACC_SOFT)
        UiRecorder.textBold("$", gx + 11 - tw("$", S_LG) / 2f, gy + (22 - S_LG) / 2f, S_LG, ACCENT)
        UiRecorder.textBold("Diana Profit", (gx + 30).toFloat(), (gy + 1).toFloat(), S_LG, ScreenTheme.TEXT_COLOR)
        UiRecorder.text("Pick events to add them up (live prices)", (gx + 30).toFloat(), (gy + 13).toFloat(), S_XS, ScreenTheme.SUBTEXT_COLOR)
        UiRecorder.fillRect(winX.toFloat(), (winY + 44).toFloat(), winW.toFloat(), 1f, LINE)

        val footY = winY + winH - FOOT_H
        UiRecorder.fillRoundedRect((winX + 1).toFloat(), footY.toFloat(), (winW - 2).toFloat(), (FOOT_H - 1).toFloat(), 11f, PANEL2)
        UiRecorder.fillRect((winX + 1).toFloat(), footY.toFloat(), (winW - 2).toFloat(), 12f, PANEL2)
        UiRecorder.fillRect(winX.toFloat(), footY.toFloat(), winW.toFloat(), 1f, LINE)
        UiRecorder.text("/fm diana pastevents", (winX + 14).toFloat(), footY + (FOOT_H - S_XS) / 2f, S_XS, ScreenTheme.SUBTEXT_COLOR)
        button(winX + winW - 74, footY + (FOOT_H - 18) / 2, 60, 18, "Done") { onClose() }

        renderBody(ctx, winX + 14, winY + 56, winW - 28, footY - 10 - (winY + 56))
        ctx.pose().popMatrix()
        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun renderBody(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        val evs = events()
        selected.retainAll(evs.map { it.idx }.toSet())

        // event chips, wrapping
        val chipH = 16
        var cx = x
        var cy = y
        val allOn = evs.all { it.idx in selected }
        fun chip(label: String, on: Boolean, action: () -> Unit) {
            val cw = tw(label, S_SM) + 16
            if (cx + cw > x + w) { cx = x; cy += chipH + 4 }
            val hov = over(cx, cy, cw, chipH)
            if (on) UiRecorder.fillPillBar(cx.toFloat(), cy.toFloat(), cw.toFloat(), chipH.toFloat(), ACCENT)
            else UiRecorder.roundedRectRing(cx.toFloat(), cy.toFloat(), cw.toFloat(), chipH.toFloat(), chipH / 2f, 1f, if (hov) RAISE else PANEL2, if (hov) ACCENT else LINE2)
            val fg = if (on) ACC_INK else if (hov) ScreenTheme.TEXT_COLOR else ScreenTheme.SUBTEXT_COLOR
            if (on) UiRecorder.textBold(label, (cx + 8).toFloat(), cy + (chipH - S_SM) / 2f, S_SM, fg)
            else UiRecorder.text(label, (cx + 8).toFloat(), cy + (chipH - S_SM) / 2f, S_SM, fg)
            hit(cx, cy, cw, chipH, action)
            cx += cw + 4
        }
        chip("All", allOn) { if (allOn) { selected.clear(); selected.add(-1) } else selected.addAll(evs.map { it.idx }); scroll = 0 }
        for (e in evs) chip(e.label, e.idx in selected) {
            if (e.idx in selected) selected.remove(e.idx) else selected.add(e.idx)
            scroll = 0
        }

        // aggregate the picked events
        val items = HashMap<String, Long>()
        val mobs = HashMap<String, Long>()
        var time = 0L
        for (e in evs) if (e.idx in selected) {
            e.items.forEach { (k, n) -> items.merge(k, n, Long::plus) }
            e.mobs.forEach { (k, n) -> mobs.merge(k, n, Long::plus) }
            time += e.timeMs
        }
        val all = items.filterKeys { it != "TOTAL_BURROWS" }.filterValues { it > 0 }
            .map { (k, n) -> Row(k, nameOf(k), n, if (k == "COINS") n.toDouble() else DianaTracker.priceOf(k) * n) }
        val total = all.sumOf { it.value }
        val q = searchField.value.trim().lowercase()
        val rows = (if (q.isEmpty()) all else all.filter { it.name.lowercase().contains(q) }).sortedByDescending { it.value }

        val ty = cy + chipH + 10
        val tileH = 40
        val tileW = (w - 3 * 8) / 4
        val burrows = items["TOTAL_BURROWS"] ?: 0L
        tile(x, ty, tileW, tileH, "TOTAL PROFIT", fmtCoins(total), GOLD, "${selected.size} event" + if (selected.size == 1) "" else "s")
        tile(x + (tileW + 8), ty, tileW, tileH, "PER HOUR", if (time < 60_000) "—" else fmtCoins(total * 3_600_000.0 / time), ScreenTheme.TEXT_COLOR, DianaTracker.fmtTime(time))
        tile(x + (tileW + 8) * 2, ty, tileW, tileH, "BURROWS", "%,d".format(burrows), ScreenTheme.TEXT_COLOR,
            if (burrows > 0) fmtCoins(total / burrows) + "/ea" else null)
        tile(x + (tileW + 8) * 3, ty, tileW, tileH, "MOBS", "%,d".format(mobs["TOTAL_MOBS"] ?: 0L), ScreenTheme.TEXT_COLOR,
            "${mobs["MINOS_INQUISITOR"] ?: 0L} inq")

        val sy = ty + tileH + 10
        val sh = 20
        val sw = min(220, w / 2)
        val focused = searchField.isFocused
        UiRecorder.roundedRectRing(x.toFloat(), sy.toFloat(), sw.toFloat(), sh.toFloat(), 6f, 1f, PANEL2, if (focused) ACCENT else LINE2)
        ScreenTheme.nTextFieldContent(searchField, focused, x + 5, sy, sw - 10, sh, S_SM)
        if (searchField.value.isEmpty() && !focused) UiRecorder.text("Search drops...", (x + 8).toFloat(), sy + (sh - S_SM) / 2f, S_SM, DIM)
        hit(x, sy, sw, sh) { searchField.isFocused = true }
        UiRecorder.text("${rows.size} / ${all.size} drops shown", (x + sw + 10).toFloat(), sy + (sh - S_XS) / 2f, S_XS, ScreenTheme.SUBTEXT_COLOR)

        listX = x; listY = sy + sh + 8; listW = w; listH = y + h - listY
        renderList(ctx, rows, all.isEmpty(), total)
    }

    private fun renderList(ctx: GuiGraphicsExtractor, rows: List<Row>, none: Boolean, total: Double) {
        val x0 = listX; val top = listY; val lw = listW; val lh = listH
        if (rows.isEmpty()) {
            val msg = if (selected.isEmpty()) "Pick an event above." else if (none) "No drops tracked for these events." else "No drops match your search."
            UiRecorder.text(msg, x0 + (lw - tw(msg, S_SM)) / 2f, (top + 24).toFloat(), S_SM, DIM)
            return
        }
        val maxScroll = max(0, rows.size * ROW_H - lh)
        scroll = scroll.coerceIn(0, maxScroll)
        UiRecorder.pushScissor(x0.toFloat(), top.toFloat(), lw.toFloat(), lh.toFloat())
        runCatching { ctx.enableScissor(x0, top, x0 + lw, top + lh) }.onFailure { FishDiag.fail("DianaProfit.3", "enableScissor failed", it) }
        val valW = 70
        val cntW = 50
        val rowW = if (maxScroll > 0) lw - 6 else lw
        for (i in rows.indices) {
            val r = rows[i]
            val ry = top + i * ROW_H - scroll
            if (ry + ROW_H < top || ry > top + lh) continue
            if (over(x0, ry, rowW, ROW_H - 2) && curMy in top..(top + lh)) ScreenTheme.roundedRect(ctx, x0, ry, rowW, ROW_H - 2, 6, RAISE)
            val iy = ry + (ROW_H - 2 - 20) / 2
            val icon = LootIcons.icon(r.key.removeSuffix("_LS"))
            if (icon != null) {
                ScreenTheme.roundedRect(ctx, x0 + 6, iy, 20, 20, 5, LINE)
                ctx.item(icon, x0 + 8, iy + 2)
            } else {
                UiRecorder.roundedRectRing((x0 + 6).toFloat(), iy.toFloat(), 20f, 20f, 5f, 1f, LINE, 0x1FFFFFFF)
                val ab = r.name.filter { it.isLetter() }.take(2).uppercase().ifEmpty { "?" }
                UiRecorder.textBold(ab, x0 + 16 - tw(ab, S_XS) / 2f, iy + (20 - S_XS) / 2f, S_XS, 0xFFFFFFFF.toInt())
            }
            val nameX = x0 + 34
            val valX = x0 + rowW - 8 - valW
            val cntX = valX - 8 - cntW
            val nameW = cntX - 10 - nameX
            UiRecorder.text(clip(r.name, nameW, S_MD), nameX.toFloat(), (ry + 4).toFloat(), S_MD, ScreenTheme.TEXT_COLOR)
            val share = if (total > 0) r.value / total else 0.0
            UiRecorder.fillRoundedRect(nameX.toFloat(), (ry + 17).toFloat(), nameW.toFloat(), 3f, 1.5f, LINE)
            if (share > 0) UiRecorder.fillRoundedRect(nameX.toFloat(), (ry + 17).toFloat(), max(2f, (nameW * share).toFloat()), 3f, 1.5f, ACCENT)
            val cs = if (r.key == "COINS") "" else "×" + "%,d".format(r.count)
            UiRecorder.text(cs, (cntX + cntW - tw(cs, S_MD)).toFloat(), ry + (ROW_H - 2 - S_MD) / 2f, S_MD, ScreenTheme.SUBTEXT_COLOR)
            val vs = if (r.value > 0) fmtCoins(r.value) else "-"
            UiRecorder.text(vs, (valX + valW - tw(vs, S_MD)).toFloat(), ry + (ROW_H - 2 - S_MD) / 2f, S_MD, if (r.value > 0) GOLD else DIM)
        }
        runCatching { ctx.disableScissor() }.onFailure { FishDiag.fail("DianaProfit.4", "disableScissor failed", it) }
        UiRecorder.popScissor()
        if (maxScroll > 0) {
            val barH = max(12, lh * lh / (rows.size * ROW_H))
            val barY = top + (lh - barH) * scroll / max(1, maxScroll)
            UiRecorder.fillPillBar((x0 + lw - 3).toFloat(), top.toFloat(), 3f, lh.toFloat(), 0x22FFFFFF)
            UiRecorder.fillPillBar((x0 + lw - 3).toFloat(), barY.toFloat(), 3f, barH.toFloat(), ACCENT)
        }
    }

    private fun tile(x: Int, y: Int, w: Int, h: Int, label: String, value: String, color: Int, side: String?) {
        UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 8f, 1f, PANEL2, LINE)
        UiRecorder.textBold(label, (x + 10).toFloat(), (y + 7).toFloat(), S_XS, ScreenTheme.SUBTEXT_COLOR)
        if (side != null) UiRecorder.text(side, (x + w - 10 - tw(side, S_XS)).toFloat(), (y + 7).toFloat(), S_XS, DIM)
        UiRecorder.textBold(clip(value, w - 20, S_VAL), (x + 10).toFloat(), y + 18f, S_VAL, color)
    }

    private fun button(x: Int, y: Int, w: Int, h: Int, label: String, action: () -> Unit) {
        val hov = over(x, y, w, h)
        UiRecorder.fillPillBar(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), if (hov) ScreenTheme.ACCENT_HOVER else ACCENT)
        UiRecorder.textBold(label, x + (w - tw(label, S_SM)) / 2f, y + (h - S_SM) / 2f, S_SM, ACC_INK)
        hit(x, y, w, h, action)
    }

    private fun hit(x: Int, y: Int, w: Int, h: Int, action: () -> Unit) { hits.add(Hit(x, y, w, h, action)) }

    private fun over(x: Int, y: Int, w: Int, h: Int) = curMx >= x && curMx <= x + w && curMy >= y && curMy <= y + h

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        searchField.isFocused = false
        val h = hits.lastOrNull { it.contains(mx, my) } ?: return super.mouseClicked(click, doubled)
        try { h.action() } catch (t: Throwable) { FishDiag.fail("DianaProfit.5", "click failed at $mx,$my", t) }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val mx = UiScale.vx(mouseX)
        val my = UiScale.vx(mouseY)
        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            scroll = max(0, scroll - (verticalAmount * ROW_H).toInt())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (searchField.isFocused) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                if (searchField.value.isNotEmpty()) searchField.setValue("") else searchField.isFocused = false
                return true
            }
            searchField.keyPressed(input)
            return true
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        if (searchField.isFocused) { searchField.charTyped(input); return true }
        return super.charTyped(input)
    }

    override fun onClose() { Minecraft.getInstance().setScreen(parent) }

    override fun isPauseScreen(): Boolean = false

    override fun paintUiOverlay() {
        try {
            fishmod.utils.rendering.UiRenderer.paint(this.width, this.height, UiScale.factor())
        } catch (t: Throwable) {
            FishDiag.fail("DianaProfit.6", "diana profit overlay paint failed", t)
        }
    }

    companion object {
        private val ACCENT = ScreenTheme.ACCENT
        private val PANEL = 0xF014181D.toInt()
        private val PANEL2 = 0xFF0F1317.toInt()
        private val RAISE = 0xFF1B2027.toInt()
        private val LINE = 0xFF262D36.toInt()
        private val LINE2 = 0xFF3A3F48.toInt()
        private val DIM = 0xFF5D6873.toInt()
        private val GOLD = 0xFFF2C14E.toInt()
        private const val ACC_SOFT = 0x2424B6B0
        private val ACC_INK = 0xFF06302F.toInt()

        private const val S_XS = 6.5f
        private const val S_SM = 7.5f
        private const val S_MD = 8f
        private const val S_LG = 9.5f
        private const val S_VAL = 12f
        private const val FOOT_H = 32
        private const val ROW_H = 28

        private val NAMES = DianaTracker.DROPS.associate { it.key to it.name }

        private fun nameOf(k: String): String {
            val ls = k.endsWith("_LS")
            val base = k.removeSuffix("_LS")
            val n = NAMES[base] ?: if (base == "COINS") "Coins" else
                base.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            return if (ls) "$n (Loot Share)" else n
        }

        private fun tw(s: String, size: Float): Int = Math.ceil(UiRecorder.textWidth(s, size).toDouble()).toInt()

        private fun clip(s: String, maxW: Int, size: Float): String {
            if (tw(s, size) <= maxW) return s
            val n = fishmod.utils.rendering.TextFit.prefixLength(s, "…", maxW.toFloat(), 1) { tw(it, size).toFloat() }
            return s.substring(0, n) + "…"
        }

        private fun fmtCoins(v: Double): String = if (v <= 0) "0" else DianaTracker.short(v)
    }
}
