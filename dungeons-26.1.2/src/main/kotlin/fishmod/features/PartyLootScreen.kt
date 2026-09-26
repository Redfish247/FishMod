package fishmod.features

import fishmod.features.croesus.CroesusPrices
import fishmod.features.croesus.LootIcons
import fishmod.features.croesus.LootTrackerStore
import fishmod.utils.NameList
import fishmod.utils.config.FishConfig
import fishmod.utils.config.values.FishSettings
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
import java.text.DecimalFormat
import kotlin.math.max
import kotlin.math.min

private val NON_WORD_RE = Regex("[^A-Za-z0-9 ]")

private val NON_DIGIT_RE = Regex("[^\\d]")

private val DIGITS_RE = Regex("\\d{1,9}")

class PartyLootScreen(initialTab: Tab = Tab.LOOT, private val parent: Screen? = null) :
    Screen(Component.literal("Party & Loot")), HasUiOverlay {

    enum class Tab(val label: String) { LOOT("Loot Tracker"), KICK("Kick List"), WHITELIST("Whitelist"), BLACKLIST("Blacklist") }

    private class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val popup: Boolean, val action: () -> Unit) {
        fun contains(mx: Int, my: Int) = mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    private var tab = initialTab
    private val hits = ArrayList<Hit>()
    private var curMx = 0
    private var curMy = 0

    private lateinit var searchField: EditBox
    private lateinit var editBox: EditBox
    private var editBoxFiltering = false
    private var editKind = 0
    private var editId = ""
    private var editName = ""
    private var editX = 0
    private var editY = 0
    private var editW = 0
    private var editH = 0
    private var clearArmed = false
    private var clearArmedAt = 0L
    private var lootScroll = 0
    private var lootListX = 0
    private var lootListY = 0
    private var lootListW = 0
    private var lootListH = 0

    private lateinit var nameField: EditBox
    private var errorMsg = ""
    private var modeOpen = false
    private var namesScroll = 0
    private var namesBoxX = 0
    private var namesBoxY = 0
    private var namesBoxW = 0
    private var namesBoxH = 0

    override fun init() {
        CroesusPrices.refreshIfStale()
        searchField = EditBox(this.font, 0, 0, 100, 16, Component.literal(""))
        searchField.setMaxLength(64)
        searchField.setBordered(false)
        searchField.setResponder { _ -> lootScroll = 0 }

        editBox = EditBox(this.font, 0, 0, 40, 16, Component.literal(""))
        editBox.setMaxLength(9)
        editBox.setResponder { s ->
            if (editBoxFiltering || s.isEmpty() || s.matches(DIGITS_RE)) return@setResponder
            editBoxFiltering = true
            editBox.setValue(s.replace(NON_DIGIT_RE, ""))
            editBoxFiltering = false
        }

        nameField = EditBox(this.font, 0, 0, 100, 16, Component.literal(""))
        nameField.setMaxLength(16)
        nameField.setBordered(false)
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        curMx = UiScale.vx(mouseX); curMy = UiScale.vx(mouseY)
        UiRecorder.clear()
        hits.clear()
        if (clearArmed && System.currentTimeMillis() - clearArmedAt > 3000) clearArmed = false

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
        // panel is drawn in the vanilla layer so item icons render on top of it
        for (i in 8 downTo 2 step 2) ScreenTheme.roundedRect(ctx, winX - i, winY - i + 4, winW + i * 2, winH + i * 2, 12 + i, 0x12000000)
        ScreenTheme.roundedRect(ctx, winX, winY, winW, winH, 12, PANEL)
        UiRecorder.roundedRectRing(winX.toFloat(), winY.toFloat(), winW.toFloat(), winH.toFloat(), 12f, 1f, 0, LINE)

        val hdrH = renderHeader(winX, winY, winW)
        UiRecorder.fillRect(winX.toFloat(), (winY + hdrH).toFloat(), winW.toFloat(), 1f, LINE)

        val footY = winY + winH - FOOT_H
        renderFooter(winX, footY, winW)

        val bx = winX + 14
        val by = winY + hdrH + 12
        val bw = winW - 28
        val bh = footY - 10 - by
        if (tab == Tab.LOOT) renderLoot(ctx, bx, by, bw, bh) else renderNames(bx, by, bw, bh)
        ctx.pose().popMatrix()

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun renderHeader(x: Int, y: Int, w: Int): Int {
        val gx = x + 14
        val gy = y + 11
        UiRecorder.fillRoundedRect(gx.toFloat(), gy.toFloat(), 22f, 22f, 6f, ACC_SOFT)
        UiRecorder.fillRect((gx + 7).toFloat(), (gy + 5).toFloat(), 1.5f, 12f, ACCENT)
        UiRecorder.fillRect((gx + 8).toFloat(), (gy + 5).toFloat(), 8f, 6f, ACCENT)

        val tx = gx + 30
        UiRecorder.textBold("Party & Loot", tx.toFloat(), (gy + 1).toFloat(), S_LG, ScreenTheme.TEXT_COLOR)
        UiRecorder.text("Croesus loot and your party name lists", tx.toFloat(), (gy + 13).toFloat(), S_XS, ScreenTheme.SUBTEXT_COLOR)
        val titleRight = tx + max(tw("Party & Loot", S_LG), tw("Croesus loot and your party name lists", S_XS))

        val labels = Tab.values().map { it to tabCount(it) }
        val widths = labels.map { (t, c) -> 16 + tw(t.label, S_SM) + 4 + tw(c, S_XS) }
        val segW = widths.sum() + 4
        val segH = 20
        val oneRow = titleRight + 16 + segW <= x + w - 14
        val sx = if (oneRow) x + w - 14 - segW else x + 14
        val sy = if (oneRow) y + 12 else y + 42
        UiRecorder.roundedRectRing(sx.toFloat(), sy.toFloat(), segW.toFloat(), segH.toFloat(), segH / 2f, 1f, PANEL2, LINE2)
        var bx = sx + 2
        for (i in labels.indices) {
            val (t, c) = labels[i]
            val bw = widths[i]
            val on = t == tab
            val hov = !on && over(bx, sy + 2, bw, segH - 4)
            if (on) UiRecorder.fillPillBar(bx.toFloat(), (sy + 2).toFloat(), bw.toFloat(), (segH - 4).toFloat(), ACCENT)
            else if (hov) UiRecorder.fillPillBar(bx.toFloat(), (sy + 2).toFloat(), bw.toFloat(), (segH - 4).toFloat(), RAISE)
            val fg = if (on) ACC_INK else if (hov) ScreenTheme.TEXT_COLOR else ScreenTheme.SUBTEXT_COLOR
            val ty = sy + (segH - S_SM) / 2f
            if (on) UiRecorder.textBold(t.label, (bx + 8).toFloat(), ty, S_SM, fg)
            else UiRecorder.text(t.label, (bx + 8).toFloat(), ty, S_SM, fg)
            UiRecorder.text(c, (bx + 8 + tw(t.label, S_SM) + 4).toFloat(), sy + (segH - S_XS) / 2f, S_XS, withAlpha(fg, 0xB0))
            hit(bx, sy + 2, bw, segH - 4) { switchTab(t) }
            bx += bw
        }
        return if (oneRow) 44 else 70
    }

    private fun renderFooter(x: Int, y: Int, w: Int) {
        UiRecorder.fillRoundedRect((x + 1).toFloat(), y.toFloat(), (w - 2).toFloat(), (FOOT_H - 1).toFloat(), 11f, PANEL2)
        UiRecorder.fillRect((x + 1).toFloat(), y.toFloat(), (w - 2).toFloat(), 12f, PANEL2)
        UiRecorder.fillRect(x.toFloat(), y.toFloat(), w.toFloat(), 1f, LINE)
        UiRecorder.text("Changes save automatically", (x + 14).toFloat(), y + (FOOT_H - S_XS) / 2f, S_XS, ScreenTheme.SUBTEXT_COLOR)
        val bw = 60
        val bh = 18
        val bx = x + w - 14 - bw
        val by = y + (FOOT_H - bh) / 2
        primaryButton(bx, by, bw, bh, "Done") { onClose() }
    }

    private fun tabCount(t: Tab): String = when (t) {
        Tab.LOOT -> LootTrackerStore.runs().toString() + " runs"
        else -> nameCount(t).toString()
    }

    private val nameCounts = HashMap<Tab, Pair<String, Int>>()

    private fun nameCount(t: Tab): Int {
        val csv = when (t) {
            Tab.KICK -> FishSettings.pcKickList
            Tab.WHITELIST -> FishSettings.pcPartyActionsWhitelist
            Tab.BLACKLIST -> FishSettings.pcPartyActionsBlacklist
            Tab.LOOT -> ""
        }
        val cached = nameCounts[t]
        if (cached != null && cached.first == csv) return cached.second
        return NameList.toList(csv).size.also { nameCounts[t] = csv to it }
    }

    private var lootRowsQuery: String? = null
    private var lootRowsAt = 0L
    private var lootRowsAll: List<LootTrackerStore.Row> = emptyList()
    private var lootRowsSorted: List<LootTrackerStore.Row> = emptyList()

    private fun switchTab(t: Tab) {
        if (t == tab) return
        commitEdit()
        tab = t
        clearArmed = false
        modeOpen = false
        errorMsg = ""
        searchField.isFocused = false
        nameField.isFocused = false
        nameField.setValue("")
        lootScroll = 0
        namesScroll = 0
    }

    private fun renderLoot(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        val runs = LootTrackerStore.runs()
        val q = searchField.value.trim().lowercase()
        val now = System.currentTimeMillis()
        if (q != lootRowsQuery || now - lootRowsAt >= 500L) {
            lootRowsQuery = q
            lootRowsAt = now
            lootRowsAll = LootTrackerStore.rows().toList()
            lootRowsSorted = (if (q.isEmpty()) lootRowsAll else lootRowsAll.filter { it.name.lowercase().contains(q) })
                .sortedByDescending { rowValue(it) }
        }
        val allRows = lootRowsAll
        val rows = lootRowsSorted

        UiRecorder.text("Auto-tracked from Croesus chests.", x.toFloat(), y.toFloat(), S_SM, ScreenTheme.SUBTEXT_COLOR)
        val hint = "Click Runs or a count to edit it"
        UiRecorder.text(hint, (x + w - tw(hint, S_XS)).toFloat(), (y + 1).toFloat(), S_XS, DIM)

        var total = 0.0
        var drops = 0
        for (r in allRows) { total += rowValue(r); drops += r.count }
        val best = allRows.filter { it.id.isNotEmpty() && CroesusPrices.price(it.id) > 0 }
            .maxByOrNull { CroesusPrices.price(it.id) }

        val ty = y + 16
        val tileH = 40
        val tileW = (w - 3 * 8) / 4
        val runsHov = editKind != 1 && over(x, ty, tileW, tileH)
        tile(x, ty, tileW, tileH, "RUNS", if (editKind == 1) "" else runs.toString(), ScreenTheme.TEXT_COLOR, null, runsHov)
        if (editKind == 1) placeEditBox(x + 10, ty + 18, tileW - 20, 16)
        else hit(x, ty, tileW, tileH) { openEdit(1, "", "", LootTrackerStore.runs(), x + 10, ty + 18, tileW - 20, 16) }
        var tx = x + tileW + 8
        tile(tx, ty, tileW, tileH, "TOTAL VALUE", fmtCoins(total), GOLD, "$drops items", false)
        tx += tileW + 8
        tile(tx, ty, tileW, tileH, "PER RUN", fmtCoins(total / max(1, runs)), ScreenTheme.TEXT_COLOR, null, false)
        tx += tileW + 8
        tile(tx, ty, tileW, tileH, "BEST DROP", best?.name ?: "—", ScreenTheme.TEXT_COLOR, null, false, small = true)

        val sy = ty + tileH + 10
        val sh = 20
        val sw = min(220, w / 2)
        val focused = searchField.isFocused
        UiRecorder.roundedRectRing(x.toFloat(), sy.toFloat(), sw.toFloat(), sh.toFloat(), 6f, 1f, PANEL2, if (focused) ACCENT else LINE2)
        ScreenTheme.nTextFieldContent(searchField, focused, x + 5, sy, sw - 10, sh, S_SM)
        if (searchField.value.isEmpty() && !focused) {
            UiRecorder.text("Search drops...", (x + 8).toFloat(), sy + (sh - S_SM) / 2f, S_SM, DIM)
        }
        hit(x, sy, sw, sh) { searchField.isFocused = true }
        val info = "${rows.size} / ${allRows.size} drops shown"
        UiRecorder.text(info, (x + sw + 10).toFloat(), sy + (sh - S_XS) / 2f, S_XS, ScreenTheme.SUBTEXT_COLOR)

        val clearLabel = if (clearArmed) "Click again to clear" else "Clear all"
        val cw = tw(clearLabel, S_SM) + 24
        val cx = x + w - cw
        val chov = over(cx, sy, cw, sh)
        if (clearArmed) {
            UiRecorder.fillPillBar(cx.toFloat(), sy.toFloat(), cw.toFloat(), sh.toFloat(), if (chov) ScreenTheme.DANGER_HOVER else ScreenTheme.DANGER)
            UiRecorder.textBold(clearLabel, (cx + 12).toFloat(), sy + (sh - S_SM) / 2f, S_SM, 0xFF2A0B0B.toInt())
        } else {
            UiRecorder.roundedRectRing(cx.toFloat(), sy.toFloat(), cw.toFloat(), sh.toFloat(), sh / 2f, 1f,
                if (chov) 0x26E05A5A else 0x00000000, if (chov) ScreenTheme.DANGER else 0x66E05A5A)
            UiRecorder.textBold(clearLabel, (cx + 12).toFloat(), sy + (sh - S_SM) / 2f, S_SM, ScreenTheme.DANGER)
        }
        hit(cx, sy, cw, sh) {
            if (clearArmed) { cancelEdit(); LootTrackerStore.clear(); lootRowsQuery = null; clearArmed = false }
            else { clearArmed = true; clearArmedAt = System.currentTimeMillis() }
        }

        lootListX = x; lootListY = sy + sh + 8; lootListW = w; lootListH = y + h - lootListY
        renderDropList(ctx, rows, allRows.isEmpty(), total, drops)
    }

    private fun tile(x: Int, y: Int, w: Int, h: Int, label: String, value: String, color: Int, side: String?, hov: Boolean, small: Boolean = false) {
        UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 8f, 1f, if (hov) RAISE else PANEL2, if (hov) ACCENT else LINE)
        UiRecorder.textBold(label, (x + 10).toFloat(), (y + 7).toFloat(), S_XS, ScreenTheme.SUBTEXT_COLOR)
        if (side != null) UiRecorder.text(side, (x + w - 10 - tw(side, S_XS)).toFloat(), (y + 7).toFloat(), S_XS, DIM)
        val size = if (small) S_MD else S_VAL
        val vy = if (small) y + 22f else y + 18f
        UiRecorder.textBold(clip(value, w - 20, size), (x + 10).toFloat(), vy, size, color)
    }

    private fun renderDropList(ctx: GuiGraphicsExtractor, rows: List<LootTrackerStore.Row>, none: Boolean, total: Double, drops: Int) {
        val x0 = lootListX
        val top = lootListY
        val lw = lootListW
        val lh = lootListH
        if (rows.isEmpty()) {
            val msg = if (none) "No drops tracked yet. Open a Croesus chest." else "No drops match your search."
            UiRecorder.text(msg, x0 + (lw - tw(msg, S_SM)) / 2f, (top + 24).toFloat(), S_SM, DIM)
            return
        }
        val maxScroll = max(0, rows.size * DROP_H - lh)
        lootScroll = lootScroll.coerceIn(0, maxScroll)
        UiRecorder.pushScissor(x0.toFloat(), top.toFloat(), lw.toFloat(), lh.toFloat())
        runCatching { ctx.enableScissor(x0, top, x0 + lw, top + lh) }
        val valW = 70
        val cntW = 44
        val rowW = if (maxScroll > 0) lw - 6 else lw
        for (i in rows.indices) {
            val r = rows[i]
            val ry = top + i * DROP_H - lootScroll
            if (ry + DROP_H < top || ry > top + lh) continue
            val visible = ry >= top - 1 && ry + DROP_H <= top + lh + 1
            if (over(x0, ry, rowW, DROP_H - 2) && curMy in top..(top + lh)) {
                ScreenTheme.roundedRect(ctx, x0, ry, rowW, DROP_H - 2, 6, RAISE)
            }
            val iy = ry + (DROP_H - 2 - 20) / 2
            val icon = LootIcons.icon(r.id)
            if (icon != null) {
                ScreenTheme.roundedRect(ctx, x0 + 6, iy, 20, 20, 5, LINE)
                UiRecorder.roundedRectRing((x0 + 6).toFloat(), iy.toFloat(), 20f, 20f, 5f, 1f, 0, 0x1FFFFFFF)
                ctx.item(icon, x0 + 8, iy + 2)
            } else {
                UiRecorder.roundedRectRing((x0 + 6).toFloat(), iy.toFloat(), 20f, 20f, 5f, 1f, iconColor(r.id.ifEmpty { r.name }), 0x1FFFFFFF)
                val ab = abbrev(r.name)
                UiRecorder.textBold(ab, x0 + 16 - tw(ab, S_XS) / 2f, iy + (20 - S_XS) / 2f, S_XS, 0xFFFFFFFF.toInt())
            }

            val v = rowValue(r)
            val nameX = x0 + 34
            val valX = x0 + rowW - 8 - valW
            val cntX = valX - 8 - cntW
            val nameW = cntX - 10 - nameX
            UiRecorder.text(clip(r.name, nameW, S_MD), nameX.toFloat(), (ry + 4).toFloat(), S_MD, ScreenTheme.TEXT_COLOR)
            val share = if (total > 0) v / total else if (drops > 0) r.count.toDouble() / drops else 0.0
            UiRecorder.fillRoundedRect(nameX.toFloat(), (ry + 17).toFloat(), nameW.toFloat(), 3f, 1.5f, LINE)
            if (share > 0) UiRecorder.fillRoundedRect(nameX.toFloat(), (ry + 17).toFloat(), max(2f, (nameW * share).toFloat()), 3f, 1.5f, ACCENT)

            val cy = ry + (DROP_H - 2 - 16) / 2
            if (isEditingRow(r)) {
                placeEditBox(cntX, cy, cntW, 16)
            } else {
                val cs = "×" + r.count
                val chov = over(cntX, cy, cntW, 16) && curMy in top..(top + lh)
                if (chov) UiRecorder.roundedRectRing(cntX.toFloat(), cy.toFloat(), cntW.toFloat(), 16f, 5f, 1f, PANEL2, ACCENT)
                UiRecorder.text(cs, (cntX + cntW - 6 - tw(cs, S_MD)).toFloat(), cy + (16 - S_MD) / 2f, S_MD, if (chov) ACCENT else ScreenTheme.SUBTEXT_COLOR)
                if (visible) hit(cntX, cy, cntW, 16) { openEdit(2, r.id, r.name, r.count, cntX, cy, cntW, 16) }
            }
            val vs = if (v > 0) fmtCoins(v) else "-"
            UiRecorder.text(vs, (valX + valW - tw(vs, S_MD)).toFloat(), ry + (DROP_H - 2 - S_MD) / 2f, S_MD, if (v > 0) GOLD else DIM)
        }
        runCatching { ctx.disableScissor() }
        UiRecorder.popScissor()
        if (maxScroll > 0) {
            val barH = max(12, lh * lh / (rows.size * DROP_H))
            val barY = top + (lh - barH) * lootScroll / max(1, maxScroll)
            UiRecorder.fillPillBar((x0 + lw - 3).toFloat(), top.toFloat(), 3f, lh.toFloat(), 0x22FFFFFF)
            UiRecorder.fillPillBar((x0 + lw - 3).toFloat(), barY.toFloat(), 3f, barH.toFloat(), ACCENT)
        }
    }

    private fun placeEditBox(x: Int, y: Int, w: Int, h: Int) {
        editX = x; editY = y; editW = w; editH = h
        ScreenTheme.nTextField(editBox, true, x, y, w, h, S_MD)
        hit(x, y, w, h) {}
    }

    private fun openEdit(kind: Int, id: String, name: String, current: Int, x: Int, y: Int, w: Int, h: Int) {
        commitEdit()
        editKind = kind; editId = id; editName = name
        editX = x; editY = y; editW = w; editH = h
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
            lootRowsQuery = null
        } catch (ignored: NumberFormatException) {
        }
        cancelEdit()
    }

    private fun cancelEdit() {
        editKind = 0
        editBox.isFocused = false
    }

    private fun isEditingRow(r: LootTrackerStore.Row): Boolean {
        if (editKind != 2) return false
        return if (editId.isNotEmpty()) editId == r.id else editName.equals(r.name, ignoreCase = true)
    }

    private fun renderNames(x: Int, y: Int, w: Int, h: Int) {
        val list = names(tab)
        val other = otherOf(tab)
        val desc = when (tab) {
            Tab.KICK -> "Auto-kicked from your party whenever you are leader."
            Tab.WHITELIST -> "Who may trigger .kick / .warp / .transfer / .promote / .demote."
            else -> "Always blocked from triggering party actions."
        }
        val ctlH = 18
        UiRecorder.text(desc, x.toFloat(), y + (ctlH - S_SM) / 2f, S_SM, ScreenTheme.SUBTEXT_COLOR)

        var popup: (() -> Unit)? = null
        if (tab == Tab.KICK) {
            val on = FishSettings.pcKickListEnabled
            val lbl = "Auto-kick enabled"
            val rowW = 26 + 6 + tw(lbl, S_SM)
            val sx = x + w - rowW
            switch(sx, y + 2, on)
            UiRecorder.text(lbl, (sx + 32).toFloat(), y + (ctlH - S_SM) / 2f, S_SM, ScreenTheme.TEXT_COLOR)
            hit(sx, y, rowW, ctlH) {
                FishSettings.pcKickListEnabled = !FishSettings.pcKickListEnabled
                saveConfig()
            }
        } else {
            val mode = FishSettings.pcPartyActionsMode
            val ddW = 86
            val ddX = x + w - ddW
            val lbl = "WHO CAN TRIGGER"
            UiRecorder.textBold(lbl, (ddX - 8 - tw(lbl, S_XS)).toFloat(), y + (ctlH - S_XS) / 2f, S_XS, ScreenTheme.SUBTEXT_COLOR)
            val hov = over(ddX, y, ddW, ctlH)
            UiRecorder.roundedRectRing(ddX.toFloat(), y.toFloat(), ddW.toFloat(), ctlH.toFloat(), 6f, 1f, PANEL2, if (modeOpen || hov) ACCENT else LINE2)
            UiRecorder.text(mode, (ddX + 8).toFloat(), y + (ctlH - S_SM) / 2f, S_SM, ScreenTheme.TEXT_COLOR)
            UiRecorder.chevron((ddX + ddW - 14).toFloat(), y + ctlH / 2f, true, ScreenTheme.SUBTEXT_COLOR)
            hit(ddX, y, ddW, ctlH) { modeOpen = !modeOpen }
            if (modeOpen) popup = { modePopup(ddX, y + ctlH + 2, ddW, mode) }
        }

        var cy = y + ctlH + 8
        val inactive = when (tab) {
            Tab.KICK -> !FishSettings.pcKickListEnabled
            Tab.WHITELIST -> FishSettings.pcPartyActionsMode != "whitelist"
            else -> FishSettings.pcPartyActionsMode != "blacklist"
        }
        if (inactive) {
            val why = if (tab == Tab.KICK) " (auto-kick is off)" else " (Who can trigger is set to ${FishSettings.pcPartyActionsMode})"
            UiRecorder.roundedRectRing((x + 1).toFloat(), (cy + 1).toFloat(), 10f, 10f, 5f, 1f, PANEL, ScreenTheme.SUBTEXT_COLOR)
            UiRecorder.textBold("i", x + 6 - tw("i", 6f) / 2f, cy + 3f, 6f, ScreenTheme.SUBTEXT_COLOR)
            UiRecorder.text("This list is saved but not in use right now$why.", (x + 16).toFloat(), cy + (12 - S_SM) / 2f, S_SM, ScreenTheme.SUBTEXT_COLOR)
            cy += 20
        }

        val ih = 20
        val addW = 50
        val iw = w - addW - 8
        val focused = nameField.isFocused
        UiRecorder.roundedRectRing(x.toFloat(), cy.toFloat(), iw.toFloat(), ih.toFloat(), 6f, 1f, PANEL2, if (focused) ACCENT else LINE2)
        ScreenTheme.nTextFieldContent(nameField, focused, x + 5, cy, iw - 10, ih, S_MD)
        if (nameField.value.isEmpty() && !focused) {
            UiRecorder.text("Add a name and press Enter", (x + 8).toFloat(), cy + (ih - S_MD) / 2f, S_MD, DIM)
        }
        hit(x, cy, iw, ih) { nameField.isFocused = true }
        primaryButton(x + w - addW, cy, addW, ih, "Add") { addName() }
        cy += ih + 4
        if (errorMsg.isNotEmpty()) UiRecorder.text(clip(errorMsg, w, S_XS), x.toFloat(), cy.toFloat(), S_XS, ScreenTheme.DANGER)
        cy += 12

        namesBoxX = x; namesBoxY = cy; namesBoxW = w; namesBoxH = y + h - cy
        UiRecorder.roundedRectRing(x.toFloat(), cy.toFloat(), w.toFloat(), namesBoxH.toFloat(), 8f, 1f, PANEL2, LINE)
        if (list.isEmpty()) {
            val msg = "No names yet."
            UiRecorder.text(msg, x + (w - tw(msg, S_SM)) / 2f, (cy + 24).toFloat(), S_SM, DIM)
        } else {
            renderChips(list, other)
        }

        popup?.invoke()
    }

    private fun renderChips(list: List<String>, other: Tab?) {
        val pad = 8
        val gap = 5
        val chipH = 18
        val innerX = namesBoxX + pad
        val innerW = namesBoxW - pad * 2
        val btnW = 14
        val nBtns = if (other != null) 2 else 1
        val pos = ArrayList<IntArray>()
        var cx = 0
        var cy = 0
        for (n in list) {
            val cw = min(innerW, 20 + tw(n, S_MD) + 4 + nBtns * btnW + 3)
            if (cx > 0 && cx + cw > innerW) { cx = 0; cy += chipH + gap }
            pos.add(intArrayOf(cx, cy, cw))
            cx += cw + gap
        }
        val contentH = cy + chipH
        val viewH = namesBoxH - pad * 2
        val maxScroll = max(0, contentH - viewH)
        namesScroll = namesScroll.coerceIn(0, maxScroll)
        val top = namesBoxY + pad
        UiRecorder.pushScissor((namesBoxX + 1).toFloat(), (namesBoxY + 1).toFloat(), (namesBoxW - 2).toFloat(), (namesBoxH - 2).toFloat())
        for (i in list.indices) {
            val n = list[i]
            val p = pos[i]
            val x = innerX + p[0]
            val y = top + p[1] - namesScroll
            val w = p[2]
            if (y + chipH < namesBoxY || y > namesBoxY + namesBoxH) continue
            val visible = y >= namesBoxY && y + chipH <= namesBoxY + namesBoxH
            UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), chipH.toFloat(), chipH / 2f, 1f, RAISE, LINE2)
            UiRecorder.fillRoundedRect((x + 4).toFloat(), (y + 4).toFloat(), 10f, 10f, 3f, hueColor(n))
            val nameW = w - 20 - 4 - nBtns * btnW - 3
            UiRecorder.text(clip(n, nameW, S_MD), (x + 20).toFloat(), y + (chipH - S_MD) / 2f, S_MD, ScreenTheme.TEXT_COLOR)
            var bx = x + w - 3 - nBtns * btnW
            if (other != null) {
                val hov = over(bx, y + 2, btnW, chipH - 4)
                if (hov) UiRecorder.disc(bx + btnW / 2f, y + chipH / 2f, 7f, ACC_SOFT)
                UiRecorder.text("↔", bx + (btnW - tw("↔", S_MD)) / 2f, y + (chipH - S_MD) / 2f, S_MD, if (hov) ACCENT else DIM)
                if (visible) hit(bx, y + 2, btnW, chipH - 4) { moveName(n, other) }
                bx += btnW
            }
            val hov = over(bx, y + 2, btnW, chipH - 4)
            if (hov) UiRecorder.disc(bx + btnW / 2f, y + chipH / 2f, 7f, 0x26E05A5A)
            UiRecorder.text("×", bx + (btnW - tw("×", S_LG)) / 2f, y + (chipH - S_LG) / 2f - 0.5f, S_LG, if (hov) ScreenTheme.DANGER else DIM)
            if (visible) hit(bx, y + 2, btnW, chipH - 4) { removeName(n) }
        }
        UiRecorder.popScissor()
        if (maxScroll > 0) {
            val barH = max(12, viewH * viewH / contentH)
            val barY = top + (viewH - barH) * namesScroll / max(1, maxScroll)
            UiRecorder.fillPillBar((namesBoxX + namesBoxW - 5).toFloat(), barY.toFloat(), 3f, barH.toFloat(), ACCENT)
        }
    }

    private fun modePopup(x: Int, y: Int, w: Int, current: String) {
        val rowH = 16
        val h = MODES.size * rowH + 4
        UiRecorder.dropShadow(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 6f, 8f, 0x60000000)
        UiRecorder.roundedRectRing(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), 6f, 1f, RAISE, LINE2)
        for (i in MODES.indices) {
            val m = MODES[i]
            val ry = y + 2 + i * rowH
            val hov = curMx >= x + 2 && curMx <= x + w - 2 && curMy >= ry && curMy <= ry + rowH
            if (m == current) UiRecorder.fillRoundedRect((x + 2).toFloat(), ry.toFloat(), (w - 4).toFloat(), rowH.toFloat(), 4f, ACC_SOFT)
            else if (hov) UiRecorder.fillRoundedRect((x + 2).toFloat(), ry.toFloat(), (w - 4).toFloat(), rowH.toFloat(), 4f, PANEL2)
            UiRecorder.text(m, (x + 8).toFloat(), ry + (rowH - S_SM) / 2f, S_SM, if (m == current) ACCENT else ScreenTheme.TEXT_COLOR)
            hits.add(Hit(x + 2, ry, w - 4, rowH, true) {
                FishSettings.pcPartyActionsMode = m
                saveConfig()
                modeOpen = false
            })
        }
    }

    private fun switch(x: Int, y: Int, on: Boolean) {
        UiRecorder.fillPillBar(x.toFloat(), y.toFloat(), 26f, 14f, if (on) ACCENT else LINE2)
        UiRecorder.disc((if (on) x + 19 else x + 7).toFloat(), y + 7f, 5f, 0xFFFFFFFF.toInt())
    }

    private fun names(t: Tab): MutableList<String> = NameList.toList(when (t) {
        Tab.KICK -> FishSettings.pcKickList
        Tab.WHITELIST -> FishSettings.pcPartyActionsWhitelist
        Tab.BLACKLIST -> FishSettings.pcPartyActionsBlacklist
        Tab.LOOT -> ""
    })

    private fun setNames(t: Tab, list: List<String>) {
        val csv = list.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
        when (t) {
            Tab.KICK -> FishSettings.pcKickList = csv
            Tab.WHITELIST -> FishSettings.pcPartyActionsWhitelist = csv
            Tab.BLACKLIST -> FishSettings.pcPartyActionsBlacklist = csv
            Tab.LOOT -> {}
        }
    }

    private fun otherOf(t: Tab): Tab? = when (t) {
        Tab.WHITELIST -> Tab.BLACKLIST
        Tab.BLACKLIST -> Tab.WHITELIST
        else -> null
    }

    private fun addName() {
        val v = nameField.value.trim()
        if (v.isEmpty()) return
        if (!NAME_RE.matches(v)) { errorMsg = "Names are 3 to 16 letters, numbers or underscores."; return }
        val list = names(tab)
        if (list.any { it.equals(v, ignoreCase = true) }) { errorMsg = "$v is already on the list."; return }
        val o = otherOf(tab)
        if (o != null && names(o).any { it.equals(v, ignoreCase = true) }) {
            errorMsg = "$v is on the ${o.label}. Use ↔ there to move them."
            return
        }
        list.add(v)
        setNames(tab, list)
        saveConfig()
        nameField.setValue("")
        errorMsg = ""
    }

    private fun removeName(n: String) {
        val list = names(tab)
        list.removeIf { it.equals(n, ignoreCase = true) }
        setNames(tab, list)
        saveConfig()
        errorMsg = ""
    }

    private fun moveName(n: String, to: Tab) {
        val list = names(tab)
        list.removeIf { it.equals(n, ignoreCase = true) }
        setNames(tab, list)
        val dest = names(to)
        if (dest.none { it.equals(n, ignoreCase = true) }) dest.add(n)
        setNames(to, dest)
        saveConfig()
        errorMsg = ""
    }

    private fun saveConfig() {
        runCatching { FishConfig.manager.save() }
    }

    private fun primaryButton(x: Int, y: Int, w: Int, h: Int, label: String, action: () -> Unit) {
        val hov = over(x, y, w, h)
        UiRecorder.fillPillBar(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), if (hov) ScreenTheme.ACCENT_HOVER else ACCENT)
        UiRecorder.textBold(label, x + (w - tw(label, S_SM)) / 2f, y + (h - S_SM) / 2f, S_SM, ACC_INK)
        hit(x, y, w, h, action)
    }

    private fun hit(x: Int, y: Int, w: Int, h: Int, action: () -> Unit) {
        hits.add(Hit(x, y, w, h, false, action))
    }

    private fun over(x: Int, y: Int, w: Int, h: Int): Boolean {
        if (modeOpen && tab != Tab.LOOT) return false
        return curMx >= x && curMx <= x + w && curMy >= y && curMy <= y + h
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = UiScale.vx(click.x())
        val my = UiScale.vx(click.y())
        if (editKind != 0 && !(mx >= editX && mx <= editX + editW && my >= editY && my <= editY + editH)) commitEdit()
        val h = hits.lastOrNull { it.contains(mx, my) }
        if (modeOpen && (h == null || !h.popup)) {
            modeOpen = false
            return true
        }
        searchField.isFocused = false
        nameField.isFocused = false
        if (h != null) { h.action(); return true }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val mx = UiScale.vx(mouseX)
        val my = UiScale.vx(mouseY)
        if (tab == Tab.LOOT) {
            if (mx >= lootListX && mx <= lootListX + lootListW && my >= lootListY && my <= lootListY + lootListH) {
                commitEdit()
                lootScroll = max(0, lootScroll - (verticalAmount * DROP_H).toInt())
                return true
            }
        } else if (mx >= namesBoxX && mx <= namesBoxX + namesBoxW && my >= namesBoxY && my <= namesBoxY + namesBoxH) {
            namesScroll = max(0, namesScroll - (verticalAmount * 23).toInt())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val key = input.key()
        val enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
        if (editKind != 0) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { cancelEdit(); return true }
            if (enter) { commitEdit(); return true }
            editBox.keyPressed(input)
            return true
        }
        if (modeOpen && key == GLFW.GLFW_KEY_ESCAPE) { modeOpen = false; return true }
        if (searchField.isFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (searchField.value.isNotEmpty()) searchField.setValue("") else searchField.isFocused = false
                return true
            }
            searchField.keyPressed(input)
            return true
        }
        if (nameField.isFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { nameField.isFocused = false; return true }
            if (enter) { addName(); return true }
            val before = nameField.value
            nameField.keyPressed(input)
            if (nameField.value != before) errorMsg = ""
            return true
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        if (editKind != 0) { editBox.charTyped(input); return true }
        if (searchField.isFocused) { searchField.charTyped(input); return true }
        if (nameField.isFocused) { nameField.charTyped(input); errorMsg = ""; return true }
        return super.charTyped(input)
    }

    override fun onClose() {
        commitEdit()
        Minecraft.getInstance().setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false

    override fun paintUiOverlay() {
        fishmod.utils.rendering.UiRenderer.paint(this.width, this.height, UiScale.factor())
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
        private const val DROP_H = 28

        private val MODES = arrayOf("off", "self", "whitelist", "blacklist", "everyone")
        private val NAME_RE = Regex("^\\w{3,16}$")
        private val NUM = DecimalFormat("#,###")

        private fun tw(s: String, size: Float): Int = Math.ceil(UiRecorder.textWidth(s, size).toDouble()).toInt()

        private fun clip(s: String, maxW: Int, size: Float): String {
            if (tw(s, size) <= maxW) return s
            val n = fishmod.utils.rendering.TextFit.prefixLength(s, "…", maxW.toFloat(), 1) { tw(it, size).toFloat() }
            return s.substring(0, n) + "…"
        }

        private fun withAlpha(c: Int, a: Int): Int = (a shl 24) or (c and 0x00FFFFFF)

        private fun rowValue(r: LootTrackerStore.Row): Double {
            if (r.id.isEmpty()) return 0.0
            return CroesusPrices.price(r.id) * r.count
        }

        private fun abbrev(name: String): String {
            val words = name.replace(NON_WORD_RE, "").split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) return "?"
            if (words.size == 1) return words[0].take(2).uppercase()
            return (words[0].take(1) + words[1].take(1)).uppercase()
        }

        private fun hsl(hue: Int, s: Float, l: Float): Int {
            val c = (1 - Math.abs(2 * l - 1)) * s
            val hp = (hue % 360) / 60f
            val x = c * (1 - Math.abs(hp % 2 - 1))
            val (r1, g1, b1) = when {
                hp < 1 -> Triple(c, x, 0f)
                hp < 2 -> Triple(x, c, 0f)
                hp < 3 -> Triple(0f, c, x)
                hp < 4 -> Triple(0f, x, c)
                hp < 5 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            val m = l - c / 2
            fun ch(v: Float) = ((v + m) * 255).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
        }

        private fun hueColor(s: String): Int = hsl(s.sumOf { it.code } % 360, 0.45f, 0.45f)

        private fun iconColor(s: String): Int = hsl(Math.floorMod(s.hashCode(), 360), 0.35f, 0.32f)

        private fun fmtCoins(v: Double): String {
            if (v < 0) return "—"
            if (v == 0.0) return "0"
            if (v >= 1_000_000_000.0) return String.format("%.2fB", v / 1_000_000_000.0)
            if (v >= 1_000_000.0) return String.format("%.2fM", v / 1_000_000.0)
            if (v >= 1_000.0) return String.format("%.1fk", v / 1_000.0)
            return NUM.format(v)
        }

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
