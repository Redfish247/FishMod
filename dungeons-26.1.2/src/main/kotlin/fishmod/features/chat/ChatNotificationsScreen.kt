package fishmod.features.chat

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
import org.lwjgl.glfw.GLFW
import org.lwjgl.nanovg.NanoVG
import kotlin.math.max
import kotlin.math.min

/**
 * /fm chatnotifications (/fm cn) — a dedicated rule-list editor for the chat-notification system
 * (see [ChatRuleStore]/[ChatRuleHandler]). Left pane lists rules (toggle/select/delete), right
 * pane edits the selected rule's filter + outputs. Painted through [NvgRecorder]: fields are kept
 * as bare [EditBox] state, never added as real Screen widgets, since a real widget's render would
 * flush before the NanoVG overlay and be invisible under it.
 */
class ChatNotificationsScreen : Screen(Component.literal("Chat Notifications")), HasNvgOverlay {

    private companion object {
        val BG_PANEL = 0xF20E1016.toInt()
        val BG_SECTION = 0xFF171A22.toInt()
        val PANEL_BORDER = 0xFF2A2D38.toInt()
        val FIELD_BG = 0xFF1B1E27.toInt()
        val FIELD_BORDER = 0xFF2E333D.toInt()
        val ACCENT = ScreenTheme.ACCENT
        val ACCENT_HOVER = ScreenTheme.ACCENT_HOVER
        val TEXT_PRIM = ScreenTheme.TEXT_COLOR
        val TEXT_HINT = ScreenTheme.SUBTEXT_COLOR
        val ROW_BG = 0xFF171A22.toInt()
        val ROW_HOVER = 0xFF232733.toInt()
        val ROW_SEL = 0xFF1E2A2A.toInt()
        val DANGER = ScreenTheme.DANGER
        val DANGER_HOVER = ScreenTheme.DANGER_HOVER

        // drawEditor() stacks fields with these pitches; mouseClicked() re-derives the same Ys — both must match
        const val ED_ROW = 24          // labelled-field row pitch
        const val ED_TOGGLE_ROW = 20   // Regex / Partial / Ignore-Case row
        const val ED_SECTION_HDR = 14  // "OUTPUTS" header height
        // editY -> first OUTPUTS field: Name + Filter + Hide-Original + toggles + header
        const val ED_OUTPUTS_DY = ED_ROW * 3 + ED_TOGGLE_ROW + ED_SECTION_HDR

        fun inBox(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean =
            mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    private var panelX = 0
    private var panelY = 0
    private val panelW = 560
    private val panelH = 420

    private var listX = 0
    private var listY = 0
    private val listW = 170
    private var listH = 0
    private val rowH = 22

    private var editX = 0
    private var editY = 0
    private var editW = 0

    private var scroll = 0
    private var selected: ChatRule? = null

    private lateinit var nameField: EditBox
    private lateinit var filterField: EditBox
    private lateinit var chatMessageField: EditBox
    private lateinit var actionBarField: EditBox
    private lateinit var titleField: EditBox
    private lateinit var durationField: EditBox
    private var focusedField: EditBox? = null

    private class ClickRect(val x: Int, val y: Int, val w: Int, val h: Int, val action: () -> Unit) {
        fun hit(mx: Int, my: Int) = inBox(mx, my, x, y, w, h)
    }

    private val toggleRects = ArrayList<Pair<ClickRect, () -> Boolean>>()
    private var newRect: ClickRect? = null
    private var deleteRect: ClickRect? = null
    private var doneRect: ClickRect? = null
    private var masterToggleRect: ClickRect? = null

    override fun init() {
        val vw = (this.width / fishmod.utils.rendering.UiScale.factor()).toInt()
        val vh = (this.height / fishmod.utils.rendering.UiScale.factor()).toInt()
        panelX = (vw - panelW) / 2
        panelY = max(8, (vh - panelH) / 2)

        listX = panelX + 14
        listY = panelY + 40
        listH = panelH - 40 - 40

        editX = listX + listW + 14
        editY = listY
        editW = panelX + panelW - 14 - editX

        nameField = mkField()
        filterField = mkField()
        chatMessageField = mkField()
        actionBarField = mkField()
        titleField = mkField()
        durationField = mkField()
        durationField.setMaxLength(6)

        if (selected == null || !ChatRuleStore.rules().contains(selected)) {
            selected = ChatRuleStore.rules().firstOrNull()
        }
        loadFields()

        val btnY = panelY + panelH - 30
        val btnW = 90
        newRect = ClickRect(listX, btnY, listW / 2 - 3, 20) { addRule() }
        deleteRect = ClickRect(listX + listW / 2 + 3, btnY, listW / 2 - 3, 20) { deleteSelected() }
        doneRect = ClickRect(panelX + panelW - 14 - btnW, btnY, btnW, 20) { onClose() }
        masterToggleRect = ClickRect(panelX + panelW - 14 - 90, panelY + 10, 90, 18) {
            ChatRuleStore.setMasterEnabled(!ChatRuleStore.isMasterEnabled())
        }
    }

    private fun mkField(): EditBox {
        val f = EditBox(this.font, 0, 0, 100, 18, Component.literal("field"))
        f.setMaxLength(256)
        f.setBordered(false)
        return f
    }

    private fun loadFields() {
        val r = selected
        nameField.setValue(r?.name ?: "")
        filterField.setValue(r?.filter ?: "")
        chatMessageField.setValue(r?.chatMessage ?: "")
        actionBarField.setValue(r?.actionBarMessage ?: "")
        titleField.setValue(r?.titleMessage ?: "")
        durationField.setValue(r?.let { (it.titleDurationMs / 1000L).toString() } ?: "3")
    }

    private fun addRule() {
        selected = ChatRuleStore.addRule(selected)
        scroll = 0
        loadFields()
    }

    private fun deleteSelected() {
        val r = selected ?: return
        ChatRuleStore.removeRule(r)
        selected = ChatRuleStore.rules().firstOrNull()
        loadFields()
    }

    private fun applyName() { selected?.let { it.name = nameField.value; ChatRuleStore.save() } }
    private fun applyFilter() { selected?.let { it.filter = filterField.value; ChatRuleStore.save() } }
    private fun applyChatMessage() { selected?.let { it.chatMessage = chatMessageField.value; ChatRuleStore.save() } }
    private fun applyActionBar() { selected?.let { it.actionBarMessage = actionBarField.value; ChatRuleStore.save() } }
    private fun applyTitle() { selected?.let { it.titleMessage = titleField.value; ChatRuleStore.save() } }
    private fun applyDuration() {
        selected?.let {
            val secs = durationField.value.toLongOrNull() ?: return
            it.titleDurationMs = (secs.coerceIn(1, 30)) * 1000L
            ChatRuleStore.save()
        }
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mouseX = fishmod.utils.rendering.UiScale.vx(mouseX)
        val mouseY = fishmod.utils.rendering.UiScale.vx(mouseY)
        NvgRecorder.clear()
        drawChrome(mouseX, mouseY)
        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    private fun drawChrome(mouseX: Int, mouseY: Int) {
        ScreenTheme.nPanel(panelX, panelY, panelX + panelW, panelY + panelH, 8, BG_PANEL, PANEL_BORDER)
        ScreenTheme.nRect(panelX, panelY, panelW, 22, BG_SECTION)
        ScreenTheme.nRect(panelX, panelY + 22, panelW, 1, ACCENT)
        ScreenTheme.nst("Chat Notifications", panelX + 14, panelY + 7, TEXT_PRIM)

        val on = ChatRuleStore.isMasterEnabled()
        val mtr = masterToggleRect
        if (mtr != null) {
            val hov = inBox(mouseX, mouseY, mtr.x, mtr.y, mtr.w, mtr.h)
            val bg = if (on) (if (hov) ACCENT_HOVER else ACCENT) else (if (hov) 0xFF3A3F4A.toInt() else FIELD_BG)
            ScreenTheme.nRoundedRect(mtr.x, mtr.y, mtr.w, mtr.h, 4, bg)
            val label = if (on) "ENABLED" else "DISABLED"
            val col = if (on) 0xFF06110F.toInt() else TEXT_HINT
            val tw = ScreenTheme.nstw(label, 0.62f)
            ScreenTheme.nst(label, mtr.x + (mtr.w - tw) / 2, mtr.y + 4, col, 0.62f)
        }

        drawList(mouseX, mouseY)
        drawEditor()

        val nr = newRect; val dr = deleteRect; val doneR = doneRect
        if (nr != null) drawButton("+ New", nr, mouseX, mouseY, ACCENT, ACCENT_HOVER, 0xFF06110F.toInt())
        if (dr != null) drawButton("Delete", dr, mouseX, mouseY, DANGER, DANGER_HOVER, 0xFFFFFFFF.toInt())
        if (doneR != null) drawButton("Done", doneR, mouseX, mouseY, ACCENT, ACCENT_HOVER, 0xFF06110F.toInt())
    }

    private fun drawButton(label: String, r: ClickRect, mx: Int, my: Int, base: Int, hoverC: Int, textC: Int) {
        val hov = inBox(mx, my, r.x, r.y, r.w, r.h)
        ScreenTheme.nRoundedRect(r.x, r.y, r.w, r.h, 4, if (hov) hoverC else base)
        val tw = ScreenTheme.nstw(label, 0.62f)
        ScreenTheme.nst(label, r.x + (r.w - tw) / 2, r.y + (r.h - 9) / 2, textC, 0.62f)
    }

    private fun drawList(mouseX: Int, mouseY: Int) {
        ScreenTheme.nRoundedRectRing(listX, listY, listW, listH, 4, 1, ROW_BG, FIELD_BORDER)
        NvgRecorder.pushScissor(listX.toFloat(), listY.toFloat(), listW.toFloat(), listH.toFloat())

        val rules = ChatRuleStore.rules()
        val visible = listH / rowH
        val maxScroll = max(0, rules.size - visible)
        scroll = scroll.coerceIn(0, maxScroll)

        for (i in 0 until visible) {
            val idx = scroll + i
            if (idx >= rules.size) break
            val rule = rules[idx]
            val ry = listY + i * rowH
            val hov = inBox(mouseX, mouseY, listX, ry, listW, rowH)
            val bg = if (rule === selected) ROW_SEL else if (hov) ROW_HOVER else ROW_BG
            ScreenTheme.nRect(listX, ry, listW, rowH - 1, bg)

            val ckSize = 12
            val ckX = listX + 6
            val ckY = ry + (rowH - ckSize) / 2
            ScreenTheme.nRoundedRectRing(ckX, ckY, ckSize, ckSize, 2, 1, FIELD_BG, FIELD_BORDER)
            if (rule.enabled) ScreenTheme.nRoundedRect(ckX + 2, ckY + 2, ckSize - 4, ckSize - 4, 1, ACCENT)

            val nameCol = if (rule.enabled) TEXT_PRIM else TEXT_HINT
            val label = clip(rule.name.ifBlank { "(unnamed)" }, listW - 26)
            ScreenTheme.nst(label, ckX + ckSize + 6, ry + 6, nameCol, 0.62f)
        }
        NvgRecorder.popScissor()

        if (rules.isEmpty()) {
            ScreenTheme.nst("no rules yet", listX + 8, listY + 8, TEXT_HINT, 0.6f)
        }
    }

    private fun drawEditor() {
        val r = selected
        if (r == null) {
            ScreenTheme.nst("Select or create a rule.", editX, editY + 8, TEXT_HINT)
            return
        }

        toggleRects.clear()
        var y = editY

        drawLabel("Name", editX, y)
        ScreenTheme.nTextField(nameField, focusedField === nameField, editX + 60, y - 2, editW - 60, 18, 8f)
        y += ED_ROW

        drawLabel("Filter", editX, y)
        ScreenTheme.nTextField(filterField, focusedField === filterField, editX + 60, y - 2, editW - 60, 18, 8f)
        y += ED_ROW

        val third = editW / 3
        drawToggle("Regex", editX, y, third - 4, r.regex) { r.regex = !r.regex; ChatRuleStore.save() }
        drawToggle("Partial", editX + third, y, third - 4, r.partialMatch) { r.partialMatch = !r.partialMatch; ChatRuleStore.save() }
        drawToggle("Ignore Case", editX + third * 2, y, third - 4, r.ignoreCase) { r.ignoreCase = !r.ignoreCase; ChatRuleStore.save() }
        y += ED_TOGGLE_ROW

        drawToggle("Hide Original Message", editX, y, editW, r.hideMessage) { r.hideMessage = !r.hideMessage; ChatRuleStore.save() }
        y += ED_ROW

        ScreenTheme.nst("OUTPUTS", editX, y, ACCENT, 0.6f)
        y += ED_SECTION_HDR

        drawLabel("Chat Reply", editX, y)
        ScreenTheme.nTextField(chatMessageField, focusedField === chatMessageField, editX + 74, y - 2, editW - 74, 18, 8f)
        y += ED_ROW

        drawLabel("Action Bar", editX, y)
        ScreenTheme.nTextField(actionBarField, focusedField === actionBarField, editX + 74, y - 2, editW - 74, 18, 8f)
        y += ED_ROW

        drawLabel("Title", editX, y)
        ScreenTheme.nTextField(titleField, focusedField === titleField, editX + 74, y - 2, editW - 74, 18, 8f)
        y += ED_ROW

        drawLabel("Duration (s)", editX, y)
        ScreenTheme.nTextField(durationField, focusedField === durationField, editX + 74, y - 2, 40, 18, 8f)
        drawToggle("Sound", editX + 130, y - 2, editW - 130, r.soundEnabled) { r.soundEnabled = !r.soundEnabled; ChatRuleStore.save() }
        y += ED_ROW
    }

    private fun drawLabel(s: String, x: Int, y: Int) {
        ScreenTheme.nst(s, x, y + 4, TEXT_HINT, 0.6f)
    }

    private fun drawToggle(label: String, x: Int, y: Int, w: Int, checked: Boolean, onToggle: () -> Unit) {
        val ckSize = 12
        ScreenTheme.nRoundedRectRing(x, y + 2, ckSize, ckSize, 2, 1, FIELD_BG, FIELD_BORDER)
        if (checked) ScreenTheme.nRoundedRect(x + 2, y + 4, ckSize - 4, ckSize - 4, 1, ACCENT)
        ScreenTheme.nst(label, x + ckSize + 5, y + 3, TEXT_PRIM, 0.6f)
        toggleRects.add(ClickRect(x, y, w, 16) { onToggle() } to { checked })
    }

    private fun clip(s: String, maxW: Int): String {
        if (ScreenTheme.nstw(s, 0.62f) <= maxW) return s
        var out = s
        while (out.length > 1 && ScreenTheme.nstw("$out...", 0.62f) > maxW) out = out.substring(0, out.length - 1)
        return "$out..."
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = fishmod.utils.rendering.UiScale.vx(click.x())
        val my = fishmod.utils.rendering.UiScale.vx(click.y())

        masterToggleRect?.let { if (it.hit(mx, my)) { it.action(); return true } }
        newRect?.let { if (it.hit(mx, my)) { focusField(null); it.action(); return true } }
        deleteRect?.let { if (it.hit(mx, my)) { focusField(null); it.action(); return true } }
        doneRect?.let { if (it.hit(mx, my)) { it.action(); return true } }

        for ((rect, _) in toggleRects) {
            if (rect.hit(mx, my)) { focusField(null); rect.action(); return true }
        }

        val rules = ChatRuleStore.rules()
        if (inBox(mx, my, listX, listY, listW, listH)) {
            val visible = listH / rowH
            val row = (my - listY) / rowH
            val idx = scroll + row
            if (idx in rules.indices) {
                val rule = rules[idx]
                val ckX = listX + 6
                val ry = listY + row * rowH
                if (inBox(mx, my, ckX, ry + (rowH - 12) / 2, 12, 12)) {
                    rule.enabled = !rule.enabled
                    ChatRuleStore.save()
                } else {
                    selected = rule
                    loadFields()
                }
                focusField(null)
            }
            return true
        }

        if (inBox(mx, my, editX + 60, editY - 2, editW - 60, 18)) { focusField(nameField); return true }
        if (inBox(mx, my, editX + 60, editY + ED_ROW - 2, editW - 60, 18)) { focusField(filterField); return true }

        var y = editY + ED_OUTPUTS_DY
        if (inBox(mx, my, editX + 74, y - 2, editW - 74, 18)) { focusField(chatMessageField); return true }
        y += ED_ROW
        if (inBox(mx, my, editX + 74, y - 2, editW - 74, 18)) { focusField(actionBarField); return true }
        y += ED_ROW
        if (inBox(mx, my, editX + 74, y - 2, editW - 74, 18)) { focusField(titleField); return true }
        y += ED_ROW
        if (inBox(mx, my, editX + 74, y - 2, 40, 18)) { focusField(durationField); return true }

        focusField(null)
        return super.mouseClicked(click, bl)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val mx = fishmod.utils.rendering.UiScale.vx(mouseX); val my = fishmod.utils.rendering.UiScale.vx(mouseY)
        if (inBox(mx, my, listX, listY, listW, listH)) {
            scroll -= Math.signum(verticalAmount).toInt()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    private fun focusField(f: EditBox?) {
        focusedField?.isFocused = false
        focusedField = f
        focusedField?.isFocused = true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val f = focusedField
        if (f != null) {
            f.keyPressed(input)
            applyFocused(f)
            return true
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        val f = focusedField
        if (f != null) {
            f.charTyped(input)
            applyFocused(f)
            return true
        }
        return super.charTyped(input)
    }

    private fun applyFocused(f: EditBox) {
        when (f) {
            nameField -> applyName()
            filterField -> applyFilter()
            chatMessageField -> applyChatMessage()
            actionBarField -> applyActionBar()
            titleField -> applyTitle()
            durationField -> applyDuration()
        }
    }

    override fun isPauseScreen(): Boolean = false

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
                fishmod.utils.debug.Debug.LOGGER.error("[NanoVG] ChatNotificationsScreen paintNvgOverlay failed", t)
            }
        } finally {
            nvgGlState.restore()
        }
    }
}
