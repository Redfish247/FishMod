package fishmod.features

import fishmod.compat.conflicts.ConflictEntry
import fishmod.compat.conflicts.FishFeatureRef
import fishmod.compat.conflicts.ModConflicts
import fishmod.compat.conflicts.ModGroup
import fishmod.compat.conflicts.ScanResult
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.UiRecorder
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.max

// "Conflicts" page inside the /fm window: other Skyblock mods' enabled features, and which ones FishMod duplicates
class ConflictsPage(private val fishProvider: () -> List<FishFeatureRef>) {

    private companion object {
        const val TOOL_H = 40
        const val HEAD_H = 26
        const val ITEM_H = 32
        const val GAP = 3
        const val RED = 0xFFE05A5A.toInt()
        const val AMBER = 0xFFE0A63A.toInt()
    }

    @Volatile private var result: ScanResult? = null
    @Volatile private var scanning = false
    private var scanStarted = false
    private val collapsed = HashSet<String>()
    private var dupesOnly = false

    var scroll = 0
    private var contentH = 0
    private var bodyY = 0
    private var bodyH = 0

    private class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val scrolls: Boolean, val act: () -> Unit)
    private val hits = ArrayList<Hit>()

    /** Duplicates still active (not yet queued for disable). -1 until the first scan finishes. */
    fun dupCount(): Int {
        val r = result ?: return -1
        return r.mods.sumOf { g -> g.entries.count { it.fish != null && !ModConflicts.isQueued(it.setting) } }
    }

    fun ensureScanned() { if (!scanStarted) rescan() }

    private fun rescan() {
        scanStarted = true
        if (scanning) return
        scanning = true
        val fish = try { fishProvider() } catch (t: Throwable) { FishDiag.fail("ConflictsPage.1", "feature list failed", t); emptyList() }
        val t = Thread({
            try { result = ModConflicts.scan(fish) } catch (e: Throwable) { FishDiag.fail("ConflictsPage.2", "conflict scan failed", e) } finally { scanning = false }
        }, "FishMod-ConflictScan")
        t.isDaemon = true
        t.start()
    }

    fun maxScroll(): Int = max(0, contentH - (bodyH))
    fun scrollBy(amount: Double) { scroll = (scroll - (amount * 24).toInt()).coerceIn(0, maxScroll()) }

    private sealed class Row {
        class Head(val g: ModGroup, val shown: Int) : Row()
        class Item(val g: ModGroup, val e: ConflictEntry) : Row()
        class Note(val text: String) : Row()
    }

    private fun buildRows(r: ScanResult): List<Row> {
        val rows = ArrayList<Row>()
        for (g in r.mods) {
            val list = if (dupesOnly) g.entries.filter { it.fish != null } else g.entries
            if (dupesOnly && list.isEmpty()) continue
            rows.add(Row.Head(g, list.size))
            if (g.id in collapsed) continue
            if (list.isEmpty()) g.note?.let { rows.add(Row.Note(it)) }
            for (e in list) rows.add(Row.Item(g, e))
        }
        return rows
    }

    private fun rowH(row: Row) = when (row) { is Row.Head -> HEAD_H; is Row.Item -> ITEM_H; is Row.Note -> 20 }

    private fun ellipsize(s: String, maxW: Float, size: Float): String {
        if (UiRecorder.textWidth(s, size) <= maxW) return s
        var t = s
        while (t.length > 1 && UiRecorder.textWidth("$t…", size) > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun button(x: Int, y: Int, w: Int, h: Int, label: String, mx: Int, my: Int, color: Int, act: () -> Unit) {
        val hov = ScreenTheme.inside(mx, my, x, y, w, h)
        ScreenTheme.nRoundedRect(x, y, w, h, 6, ScreenTheme.ink(if (hov) 0x22 else 0x12))
        ScreenTheme.nRoundedRectRing(x, y, w, h, 6, 1, 0, ScreenTheme.ink(0x26))
        val s = 9f
        UiRecorder.text(label, x + (w - UiRecorder.textWidth(label, s)) / 2f, y + (h - s) / 2f, s, color)
        hits.add(Hit(x, y, w, h, false, act))
    }

    fun render(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
        ensureScanned()
        hits.clear()
        val r = result
        val bx = x; val by = y + TOOL_H; bodyY = by; bodyH = h - TOOL_H

        // toolbar
        val summary = when {
            r == null -> "Scanning your other Skyblock mods…"
            r.mods.isEmpty() -> "No other Skyblock mods detected"
            else -> "${r.mods.size} Skyblock mod${if (r.mods.size == 1) "" else "s"} found · ${dupCount()} duplicate${if (dupCount() == 1) "" else "s"} with FishMod"
        }
        UiRecorder.textBold(summary, x.toFloat(), y.toFloat() + 2f, 10.5f, ScreenTheme.TEXT_COLOR)
        UiRecorder.text("Changes to other mods apply on the next game launch. FishMod toggles apply instantly.", x.toFloat(), y + 18f, 8.5f, ScreenTheme.SUBTEXT_COLOR)
        var tx = x + w
        tx -= 58; button(tx, y, 58, 17, "Rescan", mx, my, ScreenTheme.TEXT_COLOR) { scanStarted = false; rescan() }
        tx -= 6 + 104; button(tx, y, 104, 17, if (dupesOnly) "Duplicates only ✓" else "Duplicates only", mx, my, if (dupesOnly) ScreenTheme.ACCENT else ScreenTheme.TEXT_COLOR) { dupesOnly = !dupesOnly; scroll = 0 }
        if (r != null && dupCount() > 0) {
            tx -= 6 + 138
            button(tx, y, 138, 17, "Use FishMod for all", mx, my, RED) { useFishForAll(r) }
        }
        ScreenTheme.nRect(x, y + TOOL_H - 5, w, 1, ScreenTheme.ink(0x1F))

        if (r == null) return
        val rows = buildRows(r)
        contentH = rows.sumOf { rowH(it) + GAP }
        scroll = scroll.coerceIn(0, maxScroll())
        if (rows.isEmpty()) {
            UiRecorder.text(if (dupesOnly) "No duplicate features found." else "Nothing to show.", bx.toFloat(), by + 8f, 10f, ScreenTheme.SUBTEXT_COLOR)
            return
        }
        UiRecorder.pushScissor(bx.toFloat(), by.toFloat(), w.toFloat(), bodyH.toFloat())
        try {
            var cy = by - scroll
            for (row in rows) {
                val rh = rowH(row)
                if (cy + rh >= by && cy <= by + bodyH) when (row) {
                    is Row.Head -> drawHead(bx, cy, w, row, mx, my)
                    is Row.Item -> drawItem(ctx, bx, cy, w, row, mx, my)
                    is Row.Note -> UiRecorder.text(row.text, bx + 12f, cy + 5f, 9f, ScreenTheme.SUBTEXT_COLOR)
                }
                cy += rh + GAP
            }
        } finally { UiRecorder.popScissor() }
        if (maxScroll() > 0) {
            val trackH = bodyH - 8
            val thumbH = max(16, trackH * bodyH / contentH)
            val thumbY = by + 4 + (trackH - thumbH) * scroll / maxScroll()
            ScreenTheme.nRoundedRect(x + w - 3, thumbY, 3, thumbH, 1, ScreenTheme.ink(0x60))
        }
    }

    private fun drawHead(x: Int, y: Int, w: Int, row: Row.Head, mx: Int, my: Int) {
        val hov = ScreenTheme.inside(mx, my, x, y, w, HEAD_H)
        UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), HEAD_H.toFloat(), 6f, ScreenTheme.ink(if (hov) 0x16 else 0x0E))
        val open = row.g.id !in collapsed
        UiRecorder.text(if (open) "▾" else "▸", x + 9f, y + (HEAD_H - 10f) / 2f, 10f, ScreenTheme.SUBTEXT_COLOR)
        UiRecorder.textBold(row.g.name, x + 24f, y + (HEAD_H - 11f) / 2f, 11f, ScreenTheme.TEXT_COLOR)
        val dup = row.g.entries.count { it.fish != null }
        val info = "${row.shown} enabled" + if (dup > 0) " · $dup duplicate${if (dup == 1) "" else "s"}" else ""
        val iw = UiRecorder.textWidth(info, 9f)
        UiRecorder.text(info, x + w - 10 - iw, y + (HEAD_H - 9f) / 2f, 9f, if (dup > 0) RED else ScreenTheme.SUBTEXT_COLOR)
        hits.add(Hit(x, y, w, HEAD_H, true) { if (!collapsed.add(row.g.id)) collapsed.remove(row.g.id) })
    }

    private fun drawItem(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, row: Row.Item, mx: Int, my: Int) {
        val e = row.e
        val s = e.setting
        val fish = e.fish
        val queued = ModConflicts.isQueued(s)
        UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), ITEM_H.toFloat(), 6f, ScreenTheme.ink(0x0A))
        if (fish != null && !queued) ScreenTheme.nRoundedRectRing(x, y, w, ITEM_H, 6, 1, 0, (0x55 shl 24) or (RED and 0xFFFFFF))
        else ScreenTheme.nRoundedRectRing(x, y, w, ITEM_H, 6, 1, 0, ScreenTheme.ink(0x14))

        val pillW = 26; val pillH = 14
        val theirsX = x + w - 10 - pillW
        val fishX = theirsX - 96
        val pillY = y + (ITEM_H - pillH) / 2
        val labelRight = (if (fish != null) fishX - 62 else theirsX - 62).toFloat()

        UiRecorder.text(ellipsize(s.label, labelRight - (x + 12), 10f), x + 12f, y + 5f, 10f, ScreenTheme.TEXT_COLOR)
        val sub = if (fish != null) "≈ FishMod: ${fish.name}" else s.group.ifEmpty { s.file.substringAfterLast('/') }
        UiRecorder.text(ellipsize(sub, labelRight - (x + 12), 8.5f), x + 12f, y + 18f, 8.5f, if (fish != null) ScreenTheme.ACCENT else ScreenTheme.SUBTEXT_COLOR)

        // FishMod toggle (instant)
        if (fish != null) {
            val on = try { fish.get() } catch (_: Throwable) { false }
            UiRecorder.text("FishMod", fishX - 40f, pillY + (pillH - 8.5f) / 2f, 8.5f, ScreenTheme.SUBTEXT_COLOR)
            FishModScreen.drawTogglePill(ctx, fishX, pillY, pillW, pillH, on, if (on) 1f else 0f, ScreenTheme.inside(mx, my, fishX, pillY, pillW, pillH))
            hits.add(Hit(fishX, pillY, pillW, pillH, true) { try { fish.set(!on) } catch (t: Throwable) { FishDiag.fail("ConflictsPage.3", "fishmod toggle failed ${fish.name}", t) } })
        }

        // their toggle (applies next launch)
        val cap = if (queued) "off on restart" else if (!s.writable) "edit in mod" else row.g.name
        val capColor = if (queued) AMBER else ScreenTheme.SUBTEXT_COLOR
        val capText = ellipsize(cap, 56f, 8.5f)
        UiRecorder.text(capText, theirsX - 6f - UiRecorder.textWidth(capText, 8.5f), pillY + (pillH - 8.5f) / 2f, 8.5f, capColor)
        FishModScreen.drawTogglePill(ctx, theirsX, pillY, pillW, pillH, !queued, if (queued) 0f else 1f, s.writable && ScreenTheme.inside(mx, my, theirsX, pillY, pillW, pillH), if (s.writable) 1f else 0.4f)
        if (s.writable) hits.add(Hit(theirsX, pillY, pillW, pillH, true) { if (queued) ModConflicts.unqueue(s) else ModConflicts.queueDisable(s, row.g.name) })
    }

    private fun useFishForAll(r: ScanResult) {
        for (g in r.mods) for (e in g.entries) {
            val f = e.fish ?: continue
            try { f.set(true) } catch (t: Throwable) { FishDiag.fail("ConflictsPage.4", "fishmod enable failed ${f.name}", t) }
            if (e.setting.writable) ModConflicts.queueDisable(e.setting, g.name)
        }
    }

    fun click(mx: Int, my: Int): Boolean {
        for (h in hits.asReversed()) {
            if (!ScreenTheme.inside(mx, my, h.x, h.y, h.w, h.h)) continue
            if (h.scrolls && (my < bodyY || my > bodyY + bodyH)) continue
            h.act()
            return true
        }
        return false
    }
}
