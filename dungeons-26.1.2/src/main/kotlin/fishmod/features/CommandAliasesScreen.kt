package fishmod.features

import fishmod.features.other.CommandAliases
import fishmod.utils.rendering.UiRecorder
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

class CommandAliasesScreen : Screen(Component.literal("Command Aliases")), HasUiOverlay {

    companion object {
        private val ACCENT = ScreenTheme.ACCENT
        private val ACCENT_HOVER = ScreenTheme.ACCENT_HOVER
        private val TEXT_COLOR = ScreenTheme.TEXT_COLOR
        private val SUBTEXT_COLOR = ScreenTheme.SUBTEXT_COLOR
        private val DANGER = ScreenTheme.DANGER
        private val DANGER_HOVER = ScreenTheme.DANGER_HOVER

        private const val BG_PANEL = 0xF20E1016.toInt()
        private const val BG_SECTION = 0xFF171A22.toInt()
        private const val BORDER = 0xFF2A2D38.toInt()
        private val FIELD_BG = 0xFF1A1E26.toInt()
        private val FIELD_BORDER = 0xFF2E333D.toInt()
        private const val LIST_BG = 0xFF14161D.toInt()

        private const val ROW_H = 24
        private const val MAX_VISIBLE = 6
        private const val ALIAS_FIELD_W = 90
        private const val REMOVE_BTN_W = 20
    }

    private class ClickRect(var x: Int, var y: Int, var w: Int, var h: Int, val action: () -> Unit) {
        fun hit(mx: Int, my: Int): Boolean = mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    private val aliases: MutableList<String> = ArrayList()
    private val commands: MutableList<String> = ArrayList()
    private var scroll = 0

    private var panelX = 0
    private var panelY = 0
    private val panelW = 420
    private var panelH = 0
    private var listX = 0
    private var listY = 0
    private var listW = 0
    private var listH = 0
    private var cmdFieldX = 0
    private var cmdFieldW = 0
    private var removeBtnX = 0

    private val aliasFields: MutableList<EditBox> = ArrayList()
    private val cmdFields: MutableList<EditBox> = ArrayList()
    private val aliasFieldRects: MutableList<ClickRect> = ArrayList()
    private val cmdFieldRects: MutableList<ClickRect> = ArrayList()
    private val clickRects: MutableList<ClickRect> = ArrayList()
    private var addBtn: ClickRect? = null
    private var doneBtn: ClickRect? = null

    private var focusedRow = -1
    private var focusedCol = 0

    override fun init() {
        for (e in CommandAliases.all()) {
            aliases.add(e.alias())
            commands.add(e.command())
        }

        listH = ROW_H * MAX_VISIBLE
        panelH = 56 + listH + 46
        val vw = (this.width / fishmod.utils.rendering.UiScale.factor()).toInt()
        val vh = (this.height / fishmod.utils.rendering.UiScale.factor()).toInt()
        panelX = (vw - panelW) / 2
        panelY = max(8, (vh - panelH) / 2)

        listX = panelX + 14
        listY = panelY + 50
        listW = panelW - 28
        cmdFieldX = listX + ALIAS_FIELD_W + 6
        cmdFieldW = listW - ALIAS_FIELD_W - 6 - REMOVE_BTN_W - 6
        removeBtnX = listX + listW - REMOVE_BTN_W

        rebuildRows()
    }

    private fun persist() {
        val list = ArrayList<CommandAliases.Entry>()
        for (i in aliases.indices) list.add(CommandAliases.Entry(aliases[i], commands[i]))
        CommandAliases.replaceAll(list)
    }

    private fun rebuildRows() {
        aliasFields.clear(); cmdFields.clear()
        aliasFieldRects.clear(); cmdFieldRects.clear()
        clickRects.clear()

        for (i in aliases.indices) {
            val rowTop = listY + i * ROW_H - scroll
            if (rowTop + ROW_H < listY || rowTop > listY + listH) continue
            val idx = i

            val aliasField = EditBox(this.font, listX + 3, rowTop + 3, ALIAS_FIELD_W - 6, 18, Component.literal("Alias"))
            aliasField.setMaxLength(32)
            aliasField.setBordered(false)
            aliasField.setValue(aliases[i])
            if (focusedRow == idx && focusedCol == 0) aliasField.isFocused = true
            aliasFields.add(aliasField)
            aliasFieldRects.add(ClickRect(listX, rowTop + 3, ALIAS_FIELD_W, 18) { focusedRow = idx; focusedCol = 0 })

            val cmdField = EditBox(this.font, cmdFieldX + 4, rowTop + 3, cmdFieldW - 8, 18, Component.literal("Command"))
            cmdField.setMaxLength(256)
            cmdField.setBordered(false)
            cmdField.setValue(commands[i])
            if (focusedRow == idx && focusedCol == 1) cmdField.isFocused = true
            cmdFields.add(cmdField)
            cmdFieldRects.add(ClickRect(cmdFieldX, rowTop + 3, cmdFieldW, 18) { focusedRow = idx; focusedCol = 1 })

            clickRects.add(ClickRect(removeBtnX, rowTop + 3, REMOVE_BTN_W, 18) {
                aliases.removeAt(idx)
                commands.removeAt(idx)
                if (focusedRow == idx) focusedRow = -1
                persist()
                rebuildRows()
            })
        }

        val btnY = listY + listH + 8
        addBtn = ClickRect(panelX + 14, btnY, 120, 20) {
            aliases.add("")
            commands.add("")
            persist()
            rebuildRows()
        }
        doneBtn = ClickRect(panelX + panelW - 14 - 70, btnY, 70, 20) { onClose() }
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mouseX = fishmod.utils.rendering.UiScale.vx(mouseX)
        val mouseY = fishmod.utils.rendering.UiScale.vx(mouseY)
        UiRecorder.clear()
        ScreenTheme.nPanel(panelX, panelY, panelX + panelW, panelY + panelH, 8, BG_PANEL, BORDER)
        ScreenTheme.nRect(panelX, panelY, panelW, 22, BG_SECTION)
        ScreenTheme.nRect(panelX, panelY + 22, panelW, 1, ACCENT)
        ScreenTheme.nst("Command Aliases", panelX + 14, panelY + 7, TEXT_COLOR)
        ScreenTheme.nst(
            "Alias (no slash) -> Command it runs. New/edited aliases work right away;",
            panelX + 14, panelY + 30, SUBTEXT_COLOR, 0.5f
        )
        ScreenTheme.nst(
            "removing/renaming one fully clears after you rejoin.",
            panelX + 14, panelY + 39, SUBTEXT_COLOR, 0.5f
        )
        ScreenTheme.nRoundedRect(listX - 2, listY - 2, listW + 4, listH + 4, 6, LIST_BG)

        for (i in aliasFields.indices) {
            ScreenTheme.nTextField(aliasFields[i], aliasFields[i].isFocused, listX, aliasFieldRects[i].y, ALIAS_FIELD_W, 18)
            ScreenTheme.nTextField(cmdFields[i], cmdFields[i].isFocused, cmdFieldX, cmdFieldRects[i].y, cmdFieldW, 18)
        }

        for (r in clickRects) {
            val hover = r.hit(mouseX, mouseY)
            ScreenTheme.nPill(r.x, r.y, r.x + r.w, r.y + r.h, if (hover) DANGER_HOVER else DANGER)
            val label = "X"
            val tw = ScreenTheme.nstw(label)
            ScreenTheme.nst(label, r.x + (r.w - tw) / 2, r.y + (r.h - 8) / 2, 0xFF2A0808.toInt())
        }

        addBtn?.let { r ->
            val hover = r.hit(mouseX, mouseY)
            ScreenTheme.nPill(r.x, r.y, r.x + r.w, r.y + r.h, if (hover) ACCENT_HOVER else ACCENT)
            val label = "+ Add Alias"
            val tw = ScreenTheme.nstw(label)
            ScreenTheme.nst(label, r.x + (r.w - tw) / 2, r.y + (r.h - 8) / 2, 0xFF06302F.toInt())
        }
        doneBtn?.let { r ->
            val hover = r.hit(mouseX, mouseY)
            ScreenTheme.nRoundedRectRing(r.x, r.y, r.w, r.h, r.h / 2, 1, 0xFF14181D.toInt(), if (hover) ACCENT_HOVER else ACCENT)
            val label = "Done"
            val tw = ScreenTheme.nstw(label)
            ScreenTheme.nst(label, r.x + (r.w - tw) / 2, r.y + (r.h - 8) / 2, if (hover) ACCENT_HOVER else TEXT_COLOR)
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = fishmod.utils.rendering.UiScale.vx(click.x())
        val my = fishmod.utils.rendering.UiScale.vx(click.y())
        for (r in aliasFieldRects) if (r.hit(mx, my)) { r.action(); return true }
        for (r in cmdFieldRects) if (r.hit(mx, my)) { r.action(); return true }
        for (r in clickRects) {
            if (r.hit(mx, my)) { r.action(); return true }
        }
        addBtn?.let { if (it.hit(mx, my)) { it.action(); return true } }
        doneBtn?.let { if (it.hit(mx, my)) { it.action(); return true } }
        focusedRow = -1
        return super.mouseClicked(click, bl)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (focusedRow in aliases.indices) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) { focusedRow = -1; return true }
            val f = if (focusedCol == 0) aliasFields.getOrNull(focusedRow) else cmdFields.getOrNull(focusedRow)
            if (f != null) {
                f.keyPressed(input)
                if (focusedCol == 0) aliases[focusedRow] = f.value else commands[focusedRow] = f.value
                persist()
                return true
            }
        }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        if (focusedRow in aliases.indices) {
            val f = if (focusedCol == 0) aliasFields.getOrNull(focusedRow) else cmdFields.getOrNull(focusedRow)
            if (f != null) {
                f.charTyped(input)
                if (focusedCol == 0) aliases[focusedRow] = f.value else commands[focusedRow] = f.value
                persist()
                return true
            }
        }
        return super.charTyped(input)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val maxScroll = max(0, aliases.size * ROW_H - listH)
        scroll = max(0, min(maxScroll, scroll - (verticalAmount * ROW_H).toInt()))
        rebuildRows()
        return true
    }

    override fun paintUiOverlay() {
        fishmod.utils.rendering.UiRenderer.paint(this.width, this.height, fishmod.utils.rendering.UiScale.factor())
    }
}
