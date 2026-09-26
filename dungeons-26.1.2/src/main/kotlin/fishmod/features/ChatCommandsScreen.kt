package fishmod.features

import com.mojang.blaze3d.platform.InputConstants
import fishmod.features.chat.ChatRule
import fishmod.features.chat.ChatRuleHandler
import fishmod.features.chat.ChatRuleStore
import fishmod.features.other.CommandAliases
import fishmod.features.other.CommandKeys
import fishmod.utils.rendering.UiRecorder
import fishmod.utils.rendering.UiRenderer
import fishmod.utils.rendering.UiScale
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class ChatCommandsScreen(private var tab: Tab = Tab.NOTIFICATIONS) :
    Screen(Component.literal("Chat & Commands")), HasUiOverlay {

    enum class Tab(val label: String) { NOTIFICATIONS("Notifications"), ALIASES("Aliases"), KEYS("Keys") }

    private enum class Out(val label: String) { TITLE("Title"), ACTION_BAR("Action Bar"), SOUND("Sound"), CHAT("Chat Reply") }

    private companion object {
        val ACCENT = ScreenTheme.ACCENT
        val ACCENT_HOVER = ScreenTheme.ACCENT_HOVER
        val TEXT = ScreenTheme.TEXT_COLOR
        val SUB = ScreenTheme.SUBTEXT_COLOR
        val DANGER = ScreenTheme.DANGER
        val DANGER_HOVER = ScreenTheme.DANGER_HOVER
        val CARD_BG = ScreenTheme.CARD_BG

        val BG_PANEL = 0xF20E1016.toInt()
        val BG_SECTION = 0xFF171A22.toInt()
        val BORDER = 0xFF2A2D38.toInt()
        val FIELD_BG = 0xFF1A1E26.toInt()
        val FIELD_BORDER = 0xFF2E333D.toInt()
        val LIST_BG = 0xFF14161D.toInt()
        val ROW_HOVER = 0xFF1F232D.toInt()
        val ROW_SEL = 0xFF16292B.toInt()
        val CARD_ON = 0xFF15222A.toInt()
        val ACCENT_SOFT = 0x3324B6B0
        val ACCENT_RING = 0x8024B6B0.toInt()
        val ACCENT_GLOW = 0x4024B6B0
        val DIM = 0xFF5C6673.toInt()
        val DARK_TEXT = 0xFF06110F.toInt()
        val SWITCH_OFF = 0xFF3A3F4A.toInt()
        val TITLE_YELLOW = 0xFFFFFF55.toInt()

        const val HEADER_H = 44
        const val FOOTER_H = 32
        const val LIST_W = 172
        const val RULE_ROW_H = 30
        const val ROW_H = 24

        val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

        fun withAlpha(c: Int, a: Double): Int = ((a.coerceIn(0.0, 1.0) * 255).toInt() shl 24) or (c and 0xFFFFFF)
    }

    private class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val action: () -> Unit) {
        fun contains(mx: Int, my: Int) = mx >= x && mx < x + w && my >= y && my < y + h
    }
    private class AliasRow(val alias: EditBox, val cmd: EditBox)
    private class KeyRow(var key: InputConstants.Key, var enabled: Boolean, val cmd: EditBox)

    private var px = 0; private var py = 0; private var pw = 0; private var ph = 0
    private var bx = 0; private var by = 0; private var bw = 0; private var bh = 0
    private var mx = 0; private var my = 0

    private var loaded = false
    private val hits = ArrayList<Hit>()
    private var clip: IntArray? = null
    private var focused: EditBox? = null
    private val sinks = IdentityHashMap<EditBox, (String) -> Unit>()

    private var selected: ChatRule? = null
    private var listScroll = 0
    private var edScroll = 0
    private var edMax = 0
    private var listArea = IntArray(4)
    private var edArea = IntArray(4)
    private val openOuts = IdentityHashMap<ChatRule, MutableSet<Out>>()
    private val stash = IdentityHashMap<ChatRule, MutableMap<Out, String>>()
    private lateinit var nameField: EditBox
    private lateinit var filterField: EditBox
    private lateinit var testField: EditBox
    private lateinit var titleField: EditBox
    private lateinit var durationField: EditBox
    private lateinit var actionBarField: EditBox
    private lateinit var chatField: EditBox

    private val aliasRows = ArrayList<AliasRow>()
    private val keyRows = ArrayList<KeyRow>()
    private var rowsScroll = 0
    private var rowsArea = IntArray(4)
    private var capture: KeyRow? = null
    private var captureChip: IntArray? = null

    override fun init() {
        if (!loaded) {
            nameField = mkField(256, "").also { f -> sinks[f] = { v -> selected?.name = v } }
            filterField = mkField(256, "").also { f -> sinks[f] = { v -> selected?.filter = v } }
            testField = mkField(256, "")
            titleField = mkField(256, "").also { f -> sinks[f] = { v -> selected?.titleMessage = v } }
            durationField = mkField(6, "").also { f ->
                sinks[f] = { v -> v.toLongOrNull()?.let { s -> selected?.titleDurationMs = s.coerceIn(1, 30) * 1000L } }
            }
            actionBarField = mkField(256, "").also { f -> sinks[f] = { v -> selected?.actionBarMessage = v } }
            chatField = mkField(256, "").also { f -> sinks[f] = { v -> selected?.chatMessage = v } }

            for (e in CommandAliases.all()) aliasRows.add(AliasRow(mkField(32, e.alias()), mkField(256, e.command())))
            for (e in CommandKeys.all()) keyRows.add(KeyRow(e.key(), e.enabled(), mkField(256, e.command())))

            selected = ChatRuleStore.rules().firstOrNull()
            loadRuleFields()
            loaded = true
        }

        val f = UiScale.factor()
        val vw = (this.width / f).toInt()
        val vh = (this.height / f).toInt()
        pw = min(620, vw - 16)
        ph = min(440, vh - 16)
        px = (vw - pw) / 2
        py = max(8, (vh - ph) / 2)
        bx = px + 14
        by = py + HEADER_H + 10
        bw = pw - 28
        bh = py + ph - FOOTER_H - 8 - by
    }

    private fun mkField(maxLen: Int, value: String): EditBox {
        val f = EditBox(this.font, 0, 0, 100, 18, Component.literal(""))
        f.setMaxLength(maxLen)
        f.setBordered(false)
        f.setValue(value)
        return f
    }

    private fun saveAll() {
        ChatRuleStore.save()
        CommandAliases.replaceAll(aliasRows.map { CommandAliases.Entry(it.alias.value, it.cmd.value) })
        CommandKeys.replaceAll(keyRows.map { CommandKeys.Entry(it.key, it.cmd.value, it.enabled) })
    }

    override fun removed() {
        saveAll()
        super.removed()
    }

    private fun setFocus(f: EditBox?) {
        focused?.isFocused = false
        focused = f
        f?.isFocused = true
    }

    private fun switchTab(t: Tab) {
        setFocus(null)
        capture = null
        tab = t
        rowsScroll = 0
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        mx = UiScale.vx(mouseX)
        my = UiScale.vx(mouseY)
        UiRecorder.clear()
        hits.clear()
        captureChip = null
        drawFrame()
        when (tab) {
            Tab.NOTIFICATIONS -> drawNotifications()
            Tab.ALIASES -> drawAliases()
            Tab.KEYS -> drawKeys()
        }
        super.extractRenderState(ctx, mx, my, delta)
    }

    private fun countText(t: Tab): String = when (t) {
        Tab.NOTIFICATIONS -> ChatRuleStore.rules().let { r -> "${r.count { it.enabled }}/${r.size}" }
        Tab.ALIASES -> aliasRows.count { it.alias.value.isNotBlank() }.toString()
        Tab.KEYS -> keyRows.count { it.key != InputConstants.UNKNOWN }.toString()
    }

    private fun drawFrame() {
        UiRecorder.dropShadow(px.toFloat(), py.toFloat(), pw.toFloat(), ph.toFloat(), 10f, 16f, 0x70000000)
        ScreenTheme.nPanel(px, py, px + pw, py + ph, 10, BG_PANEL, BORDER)
        UiRecorder.fillRectTopRounded(px + 1f, py + 1f, pw - 2f, HEADER_H - 1f, 9f, BG_SECTION)
        ScreenTheme.nRect(px, py + HEADER_H, pw, 1, BORDER)

        ScreenTheme.nRoundedRect(px + 12, py + 10, 24, 24, 6, ACCENT_SOFT)
        val g = ">_"
        text(g, px + 12 + (24 - tw(g, 9f)) / 2f, py + 17f, 9f, ACCENT)
        UiRecorder.textBold("Chat & Commands", px + 44f, py + 11f, 10f, TEXT)

        val tabs = Tab.values()
        val counts = tabs.map { countText(it) }
        val widths = tabs.indices.map { i -> (tw(tabs[i].label, 8f) + 6 + tw(counts[i], 6.5f) + 8 + 16).toInt() }
        val segW = widths.sum() + 4
        val segH = 22
        val segX = px + pw - 14 - segW
        val segY = py + (HEADER_H - segH) / 2
        val sub = "Notifications, aliases and keybound commands"
        if (px + 44 + tw(sub, 7f) + 12 < segX) text(sub, px + 44f, py + 25f, 7f, SUB)
        ScreenTheme.nRoundedRectRing(segX, segY, segW, segH, 7, 1, FIELD_BG, FIELD_BORDER)
        var x = segX + 2
        for ((i, t) in tabs.withIndex()) {
            val w = widths[i]
            val sel = t == tab
            if (sel) ScreenTheme.nRoundedRect(x, segY + 2, w, segH - 4, 5, ACCENT)
            else if (inside(x, segY, w, segH)) ScreenTheme.nRoundedRect(x, segY + 2, w, segH - 4, 5, ROW_HOVER)
            text(t.label, x + 8f, segY + 7f, 8f, if (sel) DARK_TEXT else TEXT)
            val cw = (tw(counts[i], 6.5f) + 8).toInt()
            val cx = x + 8 + tw(t.label, 8f).toInt() + 6
            ScreenTheme.nRoundedRect(cx, segY + 6, cw, 11, 5, if (sel) 0x33000000 else 0xFF262B35.toInt())
            text(counts[i], cx + 4f, segY + 8f, 6.5f, if (sel) DARK_TEXT else SUB)
            hit(x, segY, w, segH) { switchTab(t) }
            x += w
        }

        val fy = py + ph - FOOTER_H
        ScreenTheme.nRect(px, fy, pw, 1, BORDER)
        text("Saved when you press Done", px + 14f, fy + 13f, 7f, DIM)
        button("Done", px + pw - 14 - 72, fy + 6, 72, 20, primary = true) { onClose() }
    }

    private fun drawNotifications() {
        val lx = bx
        val lw = LIST_W
        val master = ChatRuleStore.isMasterEnabled()
        ScreenTheme.nRoundedRectRing(lx, by, lw, 24, 6, 1, CARD_BG, BORDER)
        text("All notifications", lx + 8f, by + 8f, 7.5f, if (master) TEXT else SUB)
        switch(lx + lw - 30, by + 7, master) { ChatRuleStore.setMasterEnabled(!master) }

        val ly = by + 30
        val lh = bh - 30 - 28
        listArea = intArrayOf(lx, ly, lw, lh)
        ScreenTheme.nRoundedRectRing(lx, ly, lw, lh, 6, 1, LIST_BG, BORDER)
        val rules = ChatRuleStore.rules()
        listScroll = listScroll.coerceIn(0, max(0, rules.size * RULE_ROW_H + 8 - lh))
        pushClip(lx + 1, ly + 1, lw - 2, lh - 2)
        for ((i, r) in rules.withIndex()) {
            val rx = lx + 4
            val ry = ly + 4 + i * RULE_ROW_H - listScroll
            val rw = lw - 8
            val rh = RULE_ROW_H - 2
            if (ry + rh < ly || ry > ly + lh) continue
            if (r === selected) ScreenTheme.nRoundedRectRing(rx, ry, rw, rh, 5, 1, ROW_SEL, ACCENT_RING)
            else if (inside(rx, ry, rw, rh)) ScreenTheme.nRoundedRect(rx, ry, rw, rh, 5, ROW_HOVER)
            val dcx = rx + 10f
            val dcy = ry + rh / 2f
            if (r.enabled) { UiRecorder.disc(dcx, dcy, 6f, ACCENT_GLOW); UiRecorder.disc(dcx, dcy, 3.5f, ACCENT) }
            else UiRecorder.disc(dcx, dcy, 3.5f, DIM)
            text(clipText(r.name.ifBlank { "(unnamed)" }, rw - 26, 8f), rx + 20f, ry + 5f, 8f, if (r.enabled) TEXT else SUB)
            text(clipText(r.filter.ifBlank { "no filter" }, rw - 26, 6.5f), rx + 20f, ry + 16f, 6.5f, DIM)
            hit(rx, ry, rw, rh) { selectRule(r) }
            hit(rx, ry, 20, rh) { r.enabled = !r.enabled }
        }
        if (rules.isEmpty()) text("No rules yet.", lx + 10f, ly + 10f, 7.5f, DIM)
        popClip()
        addRow("+ New rule", lx, by + bh - 22, lw, 22) { addRule() }

        val ex = lx + lw + 12
        drawEditor(ex, by, bx + bw - ex, bh)
    }

    private fun selectRule(r: ChatRule) {
        if (r === selected) return
        selected = r
        edScroll = 0
        loadRuleFields()
    }

    private fun addRule() {
        selected = ChatRuleStore.addRule(selected)
        edScroll = 0
        loadRuleFields()
        setFocus(nameField)
    }

    private fun deleteSelected() {
        val r = selected ?: return
        ChatRuleStore.removeRule(r)
        openOuts.remove(r)
        stash.remove(r)
        selected = ChatRuleStore.rules().firstOrNull()
        edScroll = 0
        loadRuleFields()
    }

    private fun loadRuleFields() {
        val r = selected
        nameField.setValue(r?.name ?: "")
        filterField.setValue(r?.filter ?: "")
        titleField.setValue(r?.titleMessage ?: "")
        actionBarField.setValue(r?.actionBarMessage ?: "")
        chatField.setValue(r?.chatMessage ?: "")
        durationField.setValue(r?.let { (it.titleDurationMs / 1000L).toString() } ?: "3")
    }

    private fun drawEditor(ex: Int, ey: Int, ew: Int, eh: Int) {
        edArea = intArrayOf(ex, ey, ew, eh)
        val r = selected
        if (r == null) {
            text("Select or create a rule.", ex + 4f, ey + 8f, 8f, SUB)
            return
        }
        edScroll = edScroll.coerceIn(0, edMax)
        pushClip(ex, ey, ew, eh)
        val top = ey - edScroll
        var y = top

        label("Name", ex, y)
        field(nameField, ex, y + 10, ew - 78, 18, "Rule name")
        label("Enabled", ex + ew - 66, y)
        switch(ex + ew - 66, y + 14, r.enabled) { r.enabled = !r.enabled }
        y += 34

        label("Filter", ex, y)
        field(filterField, ex, y + 10, ew, 18, "Text to match in chat")
        y += 34

        segmented(ex, y, 120, 16, listOf("Text", "Regex"), if (r.regex) 1 else 0) { i -> r.regex = i == 1 }
        if (r.regex && r.filter.isNotBlank() && r.compiledPattern() == null) chip("invalid regex", ex + 128, y + 2, DANGER)
        y += 24

        var sx = ex
        sx += switchLabeled(sx, y, "Partial match", r.partialMatch) { r.partialMatch = !r.partialMatch } + 16
        sx += switchLabeled(sx, y, "Ignore case", r.ignoreCase) { r.ignoreCase = !r.ignoreCase } + 16
        switchLabeled(sx, y, "Hide original message", r.hideMessage) { r.hideMessage = !r.hideMessage }
        y += 22

        label("Test a chat line", ex, y)
        field(testField, ex, y + 10, ew - 92, 18, "Paste a chat message")
        if (testField.value.isNotBlank()) {
            val m = ChatRuleHandler.matches(r, testField.value.replace(COLOR, ""))
            chip(if (m) "matches" else "no match", ex + ew - 84, y + 13, if (m) ACCENT else DIM)
        }
        y += 36

        text("OUTPUTS", ex.toFloat(), y.toFloat(), 7f, ACCENT)
        y += 12
        val cw = (ew - 8) / 2
        y += cardRow(r, Out.TITLE, Out.ACTION_BAR, ex, y, cw) + 8
        y += cardRow(r, Out.SOUND, Out.CHAT, ex, y, cw) + 8

        if (isOn(r, Out.TITLE)) {
            label("Title preview", ex, y)
            val t = r.titleMessage.replace(COLOR, "").ifBlank { "Title text" }
            val w = tw(t, 8.5f).toInt() + 24
            ScreenTheme.nRoundedRect(ex, y + 11, min(w, ew), 22, 6, 0xCC000000.toInt())
            text(clipText(t, ew - 24, 8.5f), ex + 12f, y + 18f, 8.5f, TITLE_YELLOW)
            y += 40
        }

        button("Delete rule", ex + ew - 86, y, 86, 18, danger = true) { deleteSelected() }
        y += 26
        popClip()
        edMax = max(0, (y - top) - eh)
    }

    private fun fieldOf(o: Out): EditBox = when (o) {
        Out.TITLE -> titleField
        Out.ACTION_BAR -> actionBarField
        else -> chatField
    }

    private fun textOf(r: ChatRule, o: Out): String = when (o) {
        Out.TITLE -> r.titleMessage
        Out.ACTION_BAR -> r.actionBarMessage
        Out.CHAT -> r.chatMessage
        Out.SOUND -> ""
    }

    private fun setText(r: ChatRule, o: Out, v: String) {
        when (o) {
            Out.TITLE -> r.titleMessage = v
            Out.ACTION_BAR -> r.actionBarMessage = v
            Out.CHAT -> r.chatMessage = v
            Out.SOUND -> {}
        }
    }

    private fun isOn(r: ChatRule, o: Out): Boolean =
        if (o == Out.SOUND) r.soundEnabled
        else textOf(r, o).isNotBlank() || openOuts[r]?.contains(o) == true

    private fun toggleOut(r: ChatRule, o: Out) {
        if (o == Out.SOUND) { r.soundEnabled = !r.soundEnabled; return }
        val f = fieldOf(o)
        if (isOn(r, o)) {
            stash.getOrPut(r) { HashMap() }[o] = textOf(r, o)
            setText(r, o, "")
            f.setValue("")
            openOuts[r]?.remove(o)
            if (focused === f) setFocus(null)
        } else {
            openOuts.getOrPut(r) { HashSet() }.add(o)
            val s = stash[r]?.remove(o) ?: ""
            setText(r, o, s)
            f.setValue(s)
            setFocus(f)
        }
    }

    private fun cardH(r: ChatRule, o: Out): Int =
        if (!isOn(r, o)) 26 else when (o) { Out.TITLE -> 72; Out.SOUND -> 40; else -> 50 }

    private fun cardRow(r: ChatRule, a: Out, b: Out, x: Int, y: Int, cw: Int): Int {
        val h = max(cardH(r, a), cardH(r, b))
        card(r, a, x, y, cw, h)
        card(r, b, x + cw + 8, y, cw, h)
        return h
    }

    private fun card(r: ChatRule, o: Out, x: Int, y: Int, w: Int, h: Int) {
        val on = isOn(r, o)
        ScreenTheme.nRoundedRectRing(x, y, w, h, 6, 1, if (on) CARD_ON else CARD_BG, if (on) ACCENT_RING else BORDER)
        UiRecorder.textBold(o.label, x + 8f, y + 9f, 8f, if (on) TEXT else SUB)
        switch(x + w - 30, y + 8, on) { toggleOut(r, o) }
        if (!on) return
        when (o) {
            Out.TITLE -> {
                field(titleField, x + 8, y + 26, w - 16, 18, "Title text")
                text("Duration", x + 8f, y + 53f, 7f, SUB)
                field(durationField, x + w - 60, y + 48, 40, 18, "3")
                text("s", x + w - 15f, y + 53f, 7f, SUB)
            }
            Out.ACTION_BAR -> field(actionBarField, x + 8, y + 26, w - 16, 18, "Action bar text")
            Out.CHAT -> field(chatField, x + 8, y + 26, w - 16, 18, "Shown in your chat")
            Out.SOUND -> text("Plays a note block pling", x + 8f, y + 26f, 7f, SUB)
        }
    }

    private fun aliasKey(s: String) = s.trim().removePrefix("/")

    private fun drawAliases() {
        text("Type the short command, run the long one.", bx.toFloat(), by.toFloat(), 7.5f, SUB)
        val aw = 140
        val arrowW = 22
        val xw = 18
        val cx = bx + aw + arrowW
        val cw = bw - aw - arrowW - xw - 6
        label("ALIAS", bx, by + 16)
        label("RUNS", cx, by + 16)

        val dupes = aliasRows.map { aliasKey(it.alias.value) }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val ly = by + 28
        val reserved = 30 + 28 + (if (dupes.isNotEmpty()) 14 else 0)
        val lh = max(ROW_H, min(aliasRows.size * ROW_H, bh - (ly - by) - reserved))
        rowsArea = intArrayOf(bx, ly, bw, lh)
        rowsScroll = rowsScroll.coerceIn(0, max(0, aliasRows.size * ROW_H - lh))

        pushClip(bx, ly, bw, lh)
        for ((i, row) in aliasRows.withIndex()) {
            val ry = ly + i * ROW_H - rowsScroll
            if (ry + ROW_H < ly || ry > ly + lh) continue
            val dup = aliasKey(row.alias.value) in dupes
            slashField(row.alias, bx, ry + 2, aw, 18, "alias", if (dup) DANGER else null)
            text("→", bx + aw + (arrowW - tw("→", 8f)) / 2f, ry + 6f, 8f, DIM)
            slashField(row.cmd, cx, ry + 2, cw, 18, "command it runs")
            iconX(bx + bw - xw, ry + 2, xw, 18) { aliasRows.remove(row) }
        }
        if (aliasRows.isEmpty()) text("No aliases yet.", bx + 4f, ly + 7f, 7.5f, DIM)
        popClip()

        var y = ly + lh + 6
        if (dupes.isNotEmpty()) {
            text("Two aliases share a name. Only one of them will run.", bx.toFloat(), y.toFloat(), 7f, DANGER)
            y += 14
        }
        addRow("+ Add alias", bx, y, bw, 22) {
            val row = AliasRow(mkField(32, ""), mkField(256, ""))
            aliasRows.add(row)
            rowsScroll = Int.MAX_VALUE
            setFocus(row.alias)
        }
        y += 30
        ScreenTheme.nRoundedRectRing(bx, y, bw, 20, 5, 1, CARD_BG, BORDER)
        text("New or edited aliases work right away. Removing or renaming one fully clears after you rejoin.", bx + 8f, y + 6f, 7f, SUB)
    }

    private fun drawKeys() {
        text("Press a key in-game to run a command. Click a key box, then press a key or click the box with a mouse button. Esc unbinds.",
            bx.toFloat(), by.toFloat(), 7f, SUB)
        val chipW = 104
        val kx = bx + 28
        val midX = kx + chipW
        val cx = midX + 18
        val xw = 18
        val cw = bx + bw - xw - 6 - cx
        label("ON", bx, by + 16)
        label("KEY", kx, by + 16)
        label("COMMAND", cx, by + 16)

        val keyCounts = keyRows.filter { it.key != InputConstants.UNKNOWN }.groupingBy { it.key }.eachCount()
        val anyDup = keyCounts.values.any { it > 1 }
        val ly = by + 28
        val reserved = 30 + (if (anyDup) 14 else 0)
        val lh = max(ROW_H, min(keyRows.size * ROW_H, bh - (ly - by) - reserved))
        rowsArea = intArrayOf(bx, ly, bw, lh)
        rowsScroll = rowsScroll.coerceIn(0, max(0, keyRows.size * ROW_H - lh))

        pushClip(bx, ly, bw, lh)
        for ((i, row) in keyRows.withIndex()) {
            val ry = ly + i * ROW_H - rowsScroll
            if (ry + ROW_H < ly || ry > ly + lh) continue
            switch(bx, ry + 5, row.enabled) { row.enabled = !row.enabled }

            val capturing = capture === row
            val bound = row.key != InputConstants.UNKNOWN
            val label = when {
                capturing -> "Press a key…"
                !bound -> "Unbound"
                else -> row.key.displayName.string
            }
            val hov = inside(kx, ry + 2, chipW, 18)
            if (capturing) {
                val a = 0.55 + 0.45 * sin(System.currentTimeMillis() / 160.0)
                ScreenTheme.nRoundedRectRing(kx, ry + 2, chipW, 18, 5, 1, ACCENT_SOFT, withAlpha(ACCENT_HOVER, a))
                captureChip = intArrayOf(kx, ry + 2, chipW, 18)
            } else {
                ScreenTheme.nRoundedRectRing(kx, ry + 2, chipW, 18, 5, 1,
                    if (bound) 0xFF222733.toInt() else FIELD_BG, if (hov) ACCENT else FIELD_BORDER)
            }
            val t = clipText(label, chipW - 10, 7.5f)
            val col = when {
                capturing -> ACCENT_HOVER
                !bound -> DIM
                !row.enabled -> SUB
                else -> TEXT
            }
            text(t, kx + (chipW - tw(t, 7.5f)) / 2f, ry + 7f, 7.5f, col)
            hit(kx, ry + 2, chipW, 18) { setFocus(null); capture = row }

            if (bound && (keyCounts[row.key] ?: 0) > 1) UiRecorder.textBold("!", midX + 7f, ry + 6f, 8.5f, DANGER)
            else text("→", midX + (18 - tw("→", 8f)) / 2f, ry + 6f, 8f, DIM)

            slashField(row.cmd, cx, ry + 2, cw, 18, "command to run")
            iconX(bx + bw - xw, ry + 2, xw, 18) {
                keyRows.remove(row)
                if (capture === row) capture = null
            }
        }
        if (keyRows.isEmpty()) text("No command keys yet.", bx + 4f, ly + 7f, 7.5f, DIM)
        popClip()

        var y = ly + lh + 6
        if (anyDup) {
            text("! = this key runs more than one command.", bx.toFloat(), y.toFloat(), 7f, DANGER)
            y += 14
        }
        addRow("+ Add command key", bx, y, bw, 22) {
            val row = KeyRow(InputConstants.UNKNOWN, true, mkField(256, ""))
            keyRows.add(row)
            rowsScroll = Int.MAX_VALUE
            capture = row
        }
    }

    private fun text(s: String, x: Float, y: Float, size: Float, color: Int) = UiRecorder.text(s, x, y, size, color)
    private fun tw(s: String, size: Float): Float = UiRecorder.textWidth(s, size)
    private fun label(s: String, x: Int, y: Int) = text(s, x.toFloat(), y.toFloat(), 7f, SUB)

    private fun clipText(s: String, maxW: Int, size: Float): String {
        if (tw(s, size) <= maxW) return s
        var out = s
        while (out.length > 1 && tw("$out…", size) > maxW) out = out.substring(0, out.length - 1)
        return "$out…"
    }

    private fun inside(x: Int, y: Int, w: Int, h: Int): Boolean {
        val c = clip
        if (c != null && (mx < c[0] || my < c[1] || mx >= c[0] + c[2] || my >= c[1] + c[3])) return false
        return mx >= x && mx < x + w && my >= y && my < y + h
    }

    private fun inArea(x: Int, y: Int, a: IntArray) = x >= a[0] && x < a[0] + a[2] && y >= a[1] && y < a[1] + a[3]

    private fun hit(x: Int, y: Int, w: Int, h: Int, action: () -> Unit) {
        var x0 = x; var y0 = y; var x1 = x + w; var y1 = y + h
        clip?.let { c ->
            x0 = max(x0, c[0]); y0 = max(y0, c[1]); x1 = min(x1, c[0] + c[2]); y1 = min(y1, c[1] + c[3])
        }
        if (x1 <= x0 || y1 <= y0) return
        hits.add(Hit(x0, y0, x1 - x0, y1 - y0, action))
    }

    private fun pushClip(x: Int, y: Int, w: Int, h: Int) {
        clip = intArrayOf(x, y, w, h)
        UiRecorder.pushScissor(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())
    }

    private fun popClip() {
        clip = null
        UiRecorder.popScissor()
    }

    private fun field(f: EditBox, x: Int, y: Int, w: Int, h: Int, placeholder: String) {
        val foc = focused === f
        ScreenTheme.nRoundedRectRing(x, y, w, h, 4, 1, FIELD_BG, if (foc) ACCENT else FIELD_BORDER)
        if (f.value.isEmpty() && !foc) text(placeholder, x + 4f, y + (h - 7.5f) / 2f, 7.5f, DIM)
        else ScreenTheme.nTextFieldContent(f, foc, x, y, w, h, 8f)
        hit(x, y, w, h) { setFocus(f) }
    }

    private fun slashField(f: EditBox, x: Int, y: Int, w: Int, h: Int, placeholder: String, ring: Int? = null) {
        val foc = focused === f
        ScreenTheme.nRoundedRectRing(x, y, w, h, 4, 1, FIELD_BG, ring ?: if (foc) ACCENT else FIELD_BORDER)
        var tx = x
        if (!f.value.startsWith("/")) {
            text("/", x + 5f, y + (h - 8f) / 2f, 8f, DIM)
            tx = x + 7
        }
        if (f.value.isEmpty() && !foc) text(placeholder, tx + 4f, y + (h - 7.5f) / 2f, 7.5f, DIM)
        else ScreenTheme.nTextFieldContent(f, foc, tx, y, w - (tx - x), h, 8f)
        hit(x, y, w, h) { setFocus(f) }
    }

    private fun switch(x: Int, y: Int, on: Boolean, action: () -> Unit) {
        UiRecorder.fillPillBar(x.toFloat(), y.toFloat(), 20f, 11f, if (on) ACCENT else SWITCH_OFF)
        UiRecorder.disc(if (on) x + 14.5f else x + 5.5f, y + 5.5f, 4f, if (on) DARK_TEXT else 0xFFB8C0CA.toInt())
        hit(x - 2, y - 2, 24, 15, action)
    }

    private fun switchLabeled(x: Int, y: Int, label: String, on: Boolean, action: () -> Unit): Int {
        switch(x, y, on, action)
        text(label, x + 25f, y + 2f, 7.5f, if (on) TEXT else SUB)
        val w = 25 + tw(label, 7.5f).toInt()
        hit(x, y - 2, w, 15, action)
        return w
    }

    private fun segmented(x: Int, y: Int, w: Int, h: Int, options: List<String>, sel: Int, onPick: (Int) -> Unit) {
        ScreenTheme.nRoundedRectRing(x, y, w, h, 5, 1, FIELD_BG, FIELD_BORDER)
        val ow = w / options.size
        for ((i, o) in options.withIndex()) {
            val ox = x + i * ow
            if (i == sel) ScreenTheme.nRoundedRect(ox + 1, y + 1, ow - 2, h - 2, 4, ACCENT)
            else if (inside(ox, y, ow, h)) ScreenTheme.nRoundedRect(ox + 1, y + 1, ow - 2, h - 2, 4, ROW_HOVER)
            text(o, ox + (ow - tw(o, 7.5f)) / 2f, y + (h - 7.5f) / 2f, 7.5f, if (i == sel) DARK_TEXT else TEXT)
            hit(ox, y, ow, h) { onPick(i) }
        }
    }

    private fun chip(s: String, x: Int, y: Int, color: Int) {
        val w = tw(s, 6.5f).toInt() + 12
        ScreenTheme.nRoundedRect(x, y, w, 12, 6, withAlpha(color, 0.18))
        text(s, x + 6f, y + 3f, 6.5f, color)
    }

    private fun button(label: String, x: Int, y: Int, w: Int, h: Int, primary: Boolean = false, danger: Boolean = false, action: () -> Unit) {
        val hov = inside(x, y, w, h)
        when {
            primary -> ScreenTheme.nRoundedRect(x, y, w, h, h / 2, if (hov) ACCENT_HOVER else ACCENT)
            danger -> ScreenTheme.nRoundedRectRing(x, y, w, h, 5, 1, if (hov) withAlpha(DANGER, 0.18) else 0, if (hov) DANGER_HOVER else DANGER)
            else -> ScreenTheme.nRoundedRectRing(x, y, w, h, 5, 1, FIELD_BG, if (hov) ACCENT else FIELD_BORDER)
        }
        val col = when { primary -> DARK_TEXT; danger -> if (hov) DANGER_HOVER else DANGER; else -> TEXT }
        text(label, x + (w - tw(label, 7.5f)) / 2f, y + (h - 7.5f) / 2f, 7.5f, col)
        hit(x, y, w, h, action)
    }

    private fun addRow(label: String, x: Int, y: Int, w: Int, h: Int, action: () -> Unit) {
        val hov = inside(x, y, w, h)
        ScreenTheme.nRoundedRectRing(x, y, w, h, 6, 1, if (hov) ACCENT_SOFT else 0, if (hov) ACCENT else FIELD_BORDER)
        text(label, x + (w - tw(label, 7.5f)) / 2f, y + (h - 7.5f) / 2f, 7.5f, if (hov) ACCENT_HOVER else SUB)
        hit(x, y, w, h, action)
    }

    private fun iconX(x: Int, y: Int, w: Int, h: Int, action: () -> Unit) {
        val hov = inside(x, y, w, h)
        if (hov) ScreenTheme.nRoundedRect(x, y, w, h, 5, withAlpha(DANGER, 0.2))
        val s = "×"
        text(s, x + (w - tw(s, 9f)) / 2f, y + (h - 9f) / 2f, 9f, if (hov) DANGER_HOVER else SUB)
        hit(x, y, w, h, action)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val x = UiScale.vx(click.x())
        val y = UiScale.vx(click.y())

        val cap = capture
        if (cap != null) {
            val c = captureChip
            capture = null
            if (c != null && inArea(x, y, c)) {
                cap.key = InputConstants.Type.MOUSE.getOrCreate(click.button())
                return true
            }
        }

        setFocus(null)
        for (h in hits.asReversed()) {
            if (h.contains(x, y)) { h.action(); return true }
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val x = UiScale.vx(mouseX)
        val y = UiScale.vx(mouseY)
        val step = (verticalAmount * 18).toInt()
        if (tab == Tab.NOTIFICATIONS) {
            if (inArea(x, y, listArea)) { listScroll = max(0, listScroll - step); return true }
            if (inArea(x, y, edArea)) { edScroll = max(0, edScroll - step); return true }
        } else if (inArea(x, y, rowsArea)) {
            rowsScroll = max(0, rowsScroll - step)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val cap = capture
        if (cap != null) {
            cap.key = if (input.key() == GLFW.GLFW_KEY_ESCAPE) InputConstants.UNKNOWN else InputConstants.getKey(input)
            capture = null
            return true
        }
        val f = focused
        if (f != null) {
            val k = input.key()
            if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
                setFocus(null)
                return true
            }
            f.keyPressed(input)
            sinks[f]?.invoke(f.value)
            return true
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        val f = focused
        if (f != null) {
            f.charTyped(input)
            sinks[f]?.invoke(f.value)
            return true
        }
        return super.charTyped(input)
    }

    override fun isPauseScreen(): Boolean = false

    override fun paintUiOverlay() {
        UiRenderer.paint(this.width, this.height, UiScale.factor())
    }
}
