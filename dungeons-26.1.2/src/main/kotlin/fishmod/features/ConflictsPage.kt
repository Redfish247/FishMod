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
    // 0 = unresolved (default), 1 = all duplicates, 2 = everything
    private var view = 0
    private var bulkArmed = false
    private var status: String? = null
    private val viewNames = arrayOf("Unresolved only", "All duplicates", "Show everything")

    var scroll = 0
    private var contentH = 0
    private var bodyY = 0
    private var bodyH = 0

    private class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val scrolls: Boolean, val act: () -> Unit)
    private val hits = ArrayList<Hit>()

    /** Duplicates still active (not yet queued for disable). -1 until the first scan finishes. */
    fun dupCount(): Int {
        val r = result ?: return -1
        // one conflict per (other mod, FishMod feature) pair, however many settings or files matched it
        val seen = HashSet<String>()
        for (g in r.mods) {
            if (ModConflicts.isModIgnored(g.id)) continue
            for (e in g.entries) if (unresolved(e)) seen.add(g.id + "|" + e.fish!!.name)
        }
        return seen.size
    }

    private fun unresolved(e: ConflictEntry) = e.fish != null && !ModConflicts.isQueued(e.setting) && !ModConflicts.isIgnored(e.setting)

    private fun ignoredCount(r: ScanResult): Int = r.mods.sumOf { g ->
        if (ModConflicts.isModIgnored(g.id)) g.entries.count { it.fish != null } else g.entries.count { it.fish != null && ModConflicts.isIgnored(it.setting) }
    }

    private fun queuedCount(r: ScanResult): Int = r.mods.sumOf { g -> g.entries.count { it.fish != null && ModConflicts.isQueued(it.setting) } }

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
        class Item(val g: ModGroup, val e: ConflictEntry, val sib: List<ConflictEntry>) : Row()
        class Note(val text: String) : Row()
    }

    private fun buildRows(r: ScanResult): List<Row> {
        val rows = ArrayList<Row>()
        for (g in r.mods) {
            val modIgn = ModConflicts.isModIgnored(g.id)
            val list = when (view) {
                0 -> if (modIgn) emptyList() else g.entries.filter { unresolved(it) }
                1 -> g.entries.filter { it.fish != null }
                else -> g.entries
            }
            if (view < 2 && list.isEmpty()) continue
            // Many settings of one mod can match the same FishMod feature: show one row per feature, acting on the whole group.
            val clusters: List<List<ConflictEntry>> = if (view < 2) {
                val by = LinkedHashMap<String, MutableList<ConflictEntry>>()
                for (e in list) by.getOrPut(e.fish!!.name) { ArrayList() }.add(e)
                by.values.map { c -> c.sortedBy { it.setting.label.length } }
            } else list.map { listOf(it) }
            rows.add(Row.Head(g, clusters.size))
            if (g.id in collapsed) continue
            if (clusters.isEmpty()) g.note?.let { rows.add(Row.Note(it)) }
            for (c in clusters) rows.add(Row.Item(g, c[0], c))
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

    private fun small(x: Int, y: Int, w: Int, h: Int, label: String, mx: Int, my: Int, act: () -> Unit) {
        val hov = ScreenTheme.inside(mx, my, x, y, w, h)
        ScreenTheme.nRoundedRect(x, y, w, h, 5, ScreenTheme.ink(if (hov) 0x26 else 0x14))
        val s = 8.5f
        UiRecorder.text(label, x + (w - UiRecorder.textWidth(label, s)) / 2f, y + (h - s) / 2f, s, ScreenTheme.SUBTEXT_COLOR)
        hits.add(Hit(x, y, w, h, true, act))
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
            else -> {
                val ic = ignoredCount(r)
                "${r.mods.size} Skyblock mod${if (r.mods.size == 1) "" else "s"} found · ${dupCount()} unresolved conflict${if (dupCount() == 1) "" else "s"}" + (queuedCount(r).let { if (it > 0) " · $it off on restart" else "" }) + if (ic > 0) " · $ic ignored" else ""
            }
        }
        UiRecorder.textBold(summary, x.toFloat(), y.toFloat() + 2f, 10.5f, ScreenTheme.TEXT_COLOR)
        val st = status
        if (st != null) UiRecorder.text(ellipsize(st, w - 250f, 8.5f), x.toFloat(), y + 18f, 8.5f, AMBER)
        else UiRecorder.text("Changes to other mods apply on the next game launch. FishMod toggles apply instantly.", x.toFloat(), y + 18f, 8.5f, ScreenTheme.SUBTEXT_COLOR)
        var tx = x + w
        tx -= 58; button(tx, y, 58, 17, "Rescan", mx, my, ScreenTheme.TEXT_COLOR) { scanStarted = false; bulkArmed = false; rescan() }
        tx -= 6 + 104; button(tx, y, 104, 17, viewNames[view], mx, my, if (view == 0) ScreenTheme.ACCENT else ScreenTheme.TEXT_COLOR) { view = (view + 1) % viewNames.size; scroll = 0; bulkArmed = false }
        if (r != null && dupCount() > 0) {
            tx -= 6 + 138
            val n = bulkTargets(r).size
            button(tx, y, 138, 17, if (bulkArmed) "Confirm: $n exact?" else "Use FishMod for all ($n)", mx, my, RED) {
                if (n == 0) { bulkArmed = false; useFishForAll(r) }
                else if (!bulkArmed) bulkArmed = true else { bulkArmed = false; useFishForAll(r) }
            }
        }
        ScreenTheme.nRect(x, y + TOOL_H - 5, w, 1, ScreenTheme.ink(0x1F))

        if (r == null) return
        val rows = buildRows(r)
        contentH = rows.sumOf { rowH(it) + GAP }
        scroll = scroll.coerceIn(0, maxScroll())
        if (rows.isEmpty()) {
            UiRecorder.text(if (view == 0) "No unresolved conflicts. Ignored ones are hidden." else if (view == 1) "No duplicate features found." else "Nothing to show.", bx.toFloat(), by + 8f, 10f, ScreenTheme.SUBTEXT_COLOR)
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
        val modIgn = ModConflicts.isModIgnored(row.g.id)
        val dup = if (modIgn) 0 else row.g.entries.filter { unresolved(it) }.map { it.fish!!.name }.toSet().size
        val info = "${row.shown} shown" + if (modIgn) " · mod ignored" else if (dup > 0) " · $dup unresolved" else ""
        val iw = UiRecorder.textWidth(info, 9f)
        UiRecorder.text(info, x + w - 10 - iw, y + (HEAD_H - 9f) / 2f, 9f, if (dup > 0) RED else ScreenTheme.SUBTEXT_COLOR)
        hits.add(Hit(x, y, w, HEAD_H, true) { if (!collapsed.add(row.g.id)) collapsed.remove(row.g.id) })
        val bw = 78
        val bx = x + w - 10 - iw.toInt() - 10 - bw
        small(bx, y + (HEAD_H - 15) / 2, bw, 15, if (modIgn) "Stop ignoring" else "Ignore mod", mx, my) { ModConflicts.setModIgnored(row.g.id, !modIgn) }
    }

    private fun drawItem(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, row: Row.Item, mx: Int, my: Int) {
        val e = row.e
        val s = e.setting
        val fish = e.fish
        val sib = row.sib
        val writableSib = sib.filter { it.setting.writable }
        val writable = writableSib.isNotEmpty()
        val queued = writable && writableSib.all { ModConflicts.isQueued(it.setting) }
        val allIgnored = sib.all { ModConflicts.isIgnored(it.setting) }
        val ign = allIgnored || ModConflicts.isModIgnored(row.g.id)
        UiRecorder.fillRoundedRect(x.toFloat(), y.toFloat(), w.toFloat(), ITEM_H.toFloat(), 6f, ScreenTheme.ink(0x0A))
        if (fish != null && !queued && !ign) ScreenTheme.nRoundedRectRing(x, y, w, ITEM_H, 6, 1, 0, (0x55 shl 24) or (RED and 0xFFFFFF))
        else ScreenTheme.nRoundedRectRing(x, y, w, ITEM_H, 6, 1, 0, ScreenTheme.ink(0x14))

        val pillW = 26; val pillH = 14
        val ignW = 46
        val ignX = x + w - 8 - ignW
        val theirsX = (if (fish != null) ignX - 8 else x + w - 10) - pillW
        val fishX = theirsX - 96
        val pillY = y + (ITEM_H - pillH) / 2
        val hudE = fish?.let { FishHudEditor.findEntry(it.name) }
        val pos = sib.firstNotNullOfOrNull { it.setting.pos }
        val canCopy = hudE != null && pos != null
        val copyW = 58
        val copyX = fishX - 46 - copyW
        val labelRight = (if (fish != null) (if (canCopy) copyX - 8 else fishX - 62) else theirsX - 62).toFloat()

        UiRecorder.text(ellipsize(s.label, labelRight - (x + 12), 10f), x + 12f, y + 5f, 10f, ScreenTheme.TEXT_COLOR)
        val sub = if (fish != null) "≈ FishMod: ${fish.name}" + (if (sib.size > 1) " · ${sib.size} settings" else "") else s.group.ifEmpty { s.file.substringAfterLast('/') }
        UiRecorder.text(ellipsize(sub, labelRight - (x + 12), 8.5f), x + 12f, y + 18f, 8.5f, if (fish != null) ScreenTheme.ACCENT else ScreenTheme.SUBTEXT_COLOR)

        if (fish != null) small(ignX, y + (ITEM_H - 15) / 2, ignW, 15, if (allIgnored) "Restore" else "Ignore", mx, my) {
            for (x in sib) ModConflicts.setIgnored(x.setting, !allIgnored)
        }

        if (canCopy && hudE != null && pos != null) small(copyX, y + (ITEM_H - 15) / 2, copyW, 15, "Copy HUD pos", mx, my) {
            hudE.setX().accept(pos.x.toInt().coerceAtLeast(0))
            hudE.setY().accept(pos.y.toInt().coerceAtLeast(0))
            val sc = pos.scale
            if (sc != null && sc > 0.0) hudE.setScale()?.accept(sc.coerceIn(0.3, 5.0))
            status = "Copied ${row.g.name} HUD position to FishMod ${hudE.name()} (${pos.x.toInt()}, ${pos.y.toInt()}). Fine-tune it in Edit HUD."
        }

        // FishMod toggle (instant)
        if (fish != null) {
            val on = try { fish.get() } catch (_: Throwable) { false }
            UiRecorder.text("FishMod", fishX - 40f, pillY + (pillH - 8.5f) / 2f, 8.5f, ScreenTheme.SUBTEXT_COLOR)
            FishModScreen.drawTogglePill(ctx, fishX, pillY, pillW, pillH, on, if (on) 1f else 0f, ScreenTheme.inside(mx, my, fishX, pillY, pillW, pillH))
            hits.add(Hit(fishX, pillY, pillW, pillH, true) { try { fish.set(!on) } catch (t: Throwable) { FishDiag.fail("ConflictsPage.3", "fishmod toggle failed ${fish.name}", t) } })
        }

        // their toggle (applies next launch)
        val cap = if (queued) "off on restart" else if (!writable) "edit in mod" else row.g.name
        val capColor = if (queued) AMBER else ScreenTheme.SUBTEXT_COLOR
        val capText = ellipsize(cap, 56f, 8.5f)
        UiRecorder.text(capText, theirsX - 6f - UiRecorder.textWidth(capText, 8.5f), pillY + (pillH - 8.5f) / 2f, 8.5f, capColor)
        FishModScreen.drawTogglePill(ctx, theirsX, pillY, pillW, pillH, !queued, if (queued) 0f else 1f, writable && ScreenTheme.inside(mx, my, theirsX, pillY, pillW, pillH), if (writable) 1f else 0.4f)
        if (writable) hits.add(Hit(theirsX, pillY, pillW, pillH, true) { if (queued) writableSib.forEach { ModConflicts.unqueue(it.setting) } else writableSib.forEach { ModConflicts.queueDisable(it.setting, row.g.name) } })
    }

    // Bulk action only touches exact name matches: loose matches turned on unrelated FishMod features (item rarity, soul waypoints).
    private fun bulkTargets(r: ScanResult): List<Pair<ModGroup, ConflictEntry>> {
        val out = ArrayList<Pair<ModGroup, ConflictEntry>>()
        for (g in r.mods) for (e in g.entries) {
            if (e.fish == null || !e.exact || !unresolved(e) || ModConflicts.isModIgnored(g.id)) continue
            out.add(g to e)
        }
        return out
    }

    private fun useFishForAll(r: ScanResult) {
        val targets = bulkTargets(r)
        var queuedN = 0
        var readOnly = 0
        for ((g, e) in targets) {
            val f = e.fish ?: continue
            try { f.set(true) } catch (t: Throwable) { FishDiag.fail("ConflictsPage.4", "fishmod enable failed ${f.name}", t) }
            if (e.setting.writable) { ModConflicts.queueDisable(e.setting, g.name); queuedN++ } else readOnly++
        }
        val loose = r.mods.sumOf { g -> if (ModConflicts.isModIgnored(g.id)) 0 else g.entries.count { it.fish != null && !it.exact && unresolved(it) } }
        val parts = ArrayList<String>()
        parts.add(if (targets.isEmpty()) "No exact matches to switch" else "FishMod on for ${targets.size}, $queuedN queued off in other mods")
        if (readOnly > 0) parts.add("$readOnly in files FishMod cannot edit")
        if (loose > 0) parts.add("$loose loose matches left, use each row")
        status = parts.joinToString(" · ")
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
