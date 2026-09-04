package fishmod.features

import com.mojang.blaze3d.platform.InputConstants
import fishmod.features.other.CommandKeys
import fishmod.utils.rendering.NvgContext
import fishmod.utils.rendering.NvgGlStateGuard
import fishmod.utils.rendering.NvgRecorder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import org.lwjgl.nanovg.NanoVG
import kotlin.math.max
import kotlin.math.min

/**
 * /fm commandkeys — bind arbitrary keys/mouse buttons to slash commands.
 *
 * `keys`/`commands` lists are the source of truth; rows are rebuilt from them on every
 * add/remove/scroll/rebind. Key capture mirrors [FishModScreen]'s rebind convention: click a
 * key box to arm capture, then the next key or mouse click is bound; Escape unbinds instead.
 * Reskinned to match [FishModScreen]'s smooth pill/rounded-rect look via [ScreenTheme] — the
 * key-capture/"+ Add Command Key"/"Done"/remove-"X" buttons are custom click-region pills
 * (no vanilla [net.minecraft.client.gui.components.Button]), and the command [EditBox] fields
 * are borderless with a hand-drawn rounded-rect container behind them.
 */
class CommandKeysScreen : Screen(Component.literal("Command Keys")), HasNvgOverlay {

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
        private const val TOGGLE_BTN_W = 30
        private const val KEY_BTN_W = 120
        private const val REMOVE_BTN_W = 20
    }

    /** A clickable pill region drawn+hit-tested by hand instead of a vanilla widget. */
    private class ClickRect(var x: Int, var y: Int, var w: Int, var h: Int, val action: () -> Unit) {
        fun hit(mx: Int, my: Int): Boolean = mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    private val keys: MutableList<InputConstants.Key> = ArrayList()
    private val commands: MutableList<String> = ArrayList()
    private val enabled: MutableList<Boolean> = ArrayList()
    private var capturingIndex: Int? = null
    private var scroll = 0

    private var panelX = 0
    private var panelY = 0
    private val panelW = 400
    private var panelH = 0
    private var listX = 0
    private var listY = 0
    private var listW = 0
    private var listH = 0
    private var cmdFieldX = 0
    private var cmdFieldW = 0
    private var removeBtnX = 0

    /** One key-capture pill per row, index-tagged so we know which row it belongs to. */
    private data class KeyRect(val idx: Int, val x: Int, val y: Int, val w: Int, val h: Int) {
        fun hit(mx: Int, my: Int): Boolean = mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    private val keyRects: MutableList<KeyRect> = ArrayList()
    private val toggleRects: MutableList<ClickRect> = ArrayList()
    private val removeRects: MutableList<ClickRect> = ArrayList()
    private val cmdFields: MutableList<EditBox> = ArrayList()
    private val cmdFieldRects: MutableList<ClickRect> = ArrayList()
    private var addBtn: ClickRect? = null
    private var doneBtn: ClickRect? = null
    private var focusedRow = -1

    override fun init() {
        for (e in CommandKeys.all()) {
            keys.add(e.key())
            commands.add(e.command())
            enabled.add(e.enabled())
        }

        listH = ROW_H * MAX_VISIBLE
        panelH = 50 + listH + 46
        val vw = (this.width / fishmod.utils.rendering.UiScale.factor()).toInt()
        val vh = (this.height / fishmod.utils.rendering.UiScale.factor()).toInt()
        panelX = (vw - panelW) / 2
        panelY = max(8, (vh - panelH) / 2)

        listX = panelX + 14
        listY = panelY + 44
        listW = panelW - 28
        cmdFieldX = listX + TOGGLE_BTN_W + 6 + KEY_BTN_W + 6
        cmdFieldW = listW - TOGGLE_BTN_W - 6 - KEY_BTN_W - 6 - REMOVE_BTN_W - 6
        removeBtnX = listX + listW - REMOVE_BTN_W

        rebuildRows()
    }

    private fun persist() {
        val list = ArrayList<CommandKeys.Entry>()
        for (i in keys.indices) list.add(CommandKeys.Entry(keys[i], commands[i], enabled[i]))
        CommandKeys.replaceAll(list)
    }

    private fun rebuildRows() {
        keyRects.clear()
        toggleRects.clear()
        removeRects.clear()
        cmdFields.clear()
        cmdFieldRects.clear()

        for (i in keys.indices) {
            val rowTop = listY + i * ROW_H - scroll
            if (rowTop + ROW_H < listY || rowTop > listY + listH) continue
            val idx = i

            toggleRects.add(ClickRect(listX, rowTop + 3, TOGGLE_BTN_W, 18) {
                enabled[idx] = !enabled[idx]
                persist()
                rebuildRows()
            })

            keyRects.add(KeyRect(idx, listX + TOGGLE_BTN_W + 6, rowTop + 3, KEY_BTN_W, 18))

            // value/cursor state only — not a Screen widget (its extractRenderState would flush before the NanoVG overlay)
            val cmdField = EditBox(this.font, cmdFieldX + 4, rowTop + 3, cmdFieldW - 8, 18, Component.literal("Command"))
            cmdField.setMaxLength(256)
            cmdField.setBordered(false)
            cmdField.setValue(commands[i])
            if (focusedRow == idx) cmdField.isFocused = true
            cmdFields.add(cmdField)
            cmdFieldRects.add(ClickRect(cmdFieldX, rowTop + 3, cmdFieldW, 18) {
                focusedRow = idx
                cmdFields.forEach { it.isFocused = false }
                cmdField.isFocused = true
            })

            removeRects.add(ClickRect(removeBtnX, rowTop + 3, REMOVE_BTN_W, 18) {
                keys.removeAt(idx)
                commands.removeAt(idx)
                enabled.removeAt(idx)
                if (focusedRow == idx) focusedRow = -1
                persist()
                rebuildRows()
            })
        }

        val btnY = listY + listH + 8
        addBtn = ClickRect(panelX + 14, btnY, 160, 20) {
            keys.add(InputConstants.UNKNOWN)
            commands.add("")
            enabled.add(true)
            persist()
            rebuildRows()
        }
        doneBtn = ClickRect(panelX + panelW - 14 - 70, btnY, 70, 20) { onClose() }
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val mouseX = fishmod.utils.rendering.UiScale.vx(mouseX)
        val mouseY = fishmod.utils.rendering.UiScale.vx(mouseY)
        NvgRecorder.clear()
        ScreenTheme.nPanel(panelX, panelY, panelX + panelW, panelY + panelH, 8, BG_PANEL, BORDER)
        ScreenTheme.nRect(panelX, panelY, panelW, 22, BG_SECTION)
        ScreenTheme.nRect(panelX, panelY + 22, panelW, 1, ACCENT)
        ScreenTheme.nst("Command Keys", panelX + 14, panelY + 7, TEXT_COLOR)
        ScreenTheme.nst(
            "Click a key box, then press a key or click a mouse button (Esc to unbind)",
            panelX + 14, panelY + 30, SUBTEXT_COLOR, 0.5f
        )
        ScreenTheme.nRoundedRect(listX - 2, listY - 2, listW + 4, listH + 4, 6, LIST_BG)

        for ((i, r) in keyRects.withIndex()) {
            val hover = r.hit(mouseX, mouseY)
            val capturing = capturingIndex == r.idx
            val k = keys[r.idx]
            val on = enabled[r.idx]

            if (i < toggleRects.size) {
                val t = toggleRects[i]
                val tHover = t.hit(mouseX, mouseY)
                val tFill = if (on) (if (tHover) ACCENT_HOVER else ACCENT) else (if (tHover) 0xFF4A505C.toInt() else 0xFF3A3F4A.toInt())
                ScreenTheme.nPill(t.x, t.y, t.x + t.w, t.y + t.h, tFill)
                val tLabel = if (on) "On" else "Off"
                val ttw = ScreenTheme.nstw(tLabel)
                ScreenTheme.nst(tLabel, t.x + (t.w - ttw) / 2, t.y + (t.h - 8) / 2, if (on) 0xFF06302F.toInt() else SUBTEXT_COLOR)
            }
            val label = when {
                capturing -> "> Press a key <"
                k == InputConstants.UNKNOWN -> "Unbound"
                else -> k.displayName.string
            }
            val ring = if (capturing) ACCENT_HOVER else if (hover) ACCENT else FIELD_BORDER
            ScreenTheme.nRoundedRectRing(r.x, r.y, r.w, r.h, 5, 1, FIELD_BG, ring)
            var tw = ScreenTheme.nstw(label)
            var text = label
            val maxTextW = r.w - 8
            if (tw > maxTextW) {
                while (text.length > 1 && ScreenTheme.nstw("$text...") > maxTextW) text = text.substring(0, text.length - 1)
                text = "$text..."
                tw = ScreenTheme.nstw(text)
            }
            ScreenTheme.nst(text, r.x + (r.w - tw) / 2, r.y + (r.h - 8) / 2, if (capturing) ACCENT_HOVER else if (!on) SUBTEXT_COLOR else TEXT_COLOR)

            if (i < cmdFields.size) ScreenTheme.nTextField(cmdFields[i], cmdFields[i].isFocused, cmdFieldX, r.y, cmdFieldW, 18)
        }

        for (r in removeRects) {
            val hover = r.hit(mouseX, mouseY)
            ScreenTheme.nPill(r.x, r.y, r.x + r.w, r.y + r.h, if (hover) DANGER_HOVER else DANGER)
            val label = "X"
            val tw = ScreenTheme.nstw(label)
            ScreenTheme.nst(label, r.x + (r.w - tw) / 2, r.y + (r.h - 8) / 2, 0xFF2A0808.toInt())
        }

        addBtn?.let { r ->
            val hover = r.hit(mouseX, mouseY)
            ScreenTheme.nPill(r.x, r.y, r.x + r.w, r.y + r.h, if (hover) ACCENT_HOVER else ACCENT)
            val label = "+ Add Command Key"
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

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = fishmod.utils.rendering.UiScale.vx(click.x())
        val my = fishmod.utils.rendering.UiScale.vx(click.y())

        val idx = capturingIndex
        if (idx != null) {
            keys[idx] = InputConstants.Type.MOUSE.getOrCreate(click.button())
            capturingIndex = null
            persist()
            rebuildRows()
            return true
        }

        for (r in toggleRects) {
            if (r.hit(mx, my)) { r.action(); return true }
        }
        for (r in keyRects) {
            if (r.hit(mx, my)) { capturingIndex = r.idx; focusedRow = -1; return true }
        }
        for (r in cmdFieldRects) {
            if (r.hit(mx, my)) { r.action(); return true }
        }
        for (r in removeRects) {
            if (r.hit(mx, my)) { r.action(); return true }
        }
        addBtn?.let { if (it.hit(mx, my)) { it.action(); return true } }
        doneBtn?.let { if (it.hit(mx, my)) { it.action(); return true } }

        focusedRow = -1
        cmdFields.forEach { it.isFocused = false }
        return super.mouseClicked(click, doubled)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val idx = capturingIndex
        if (idx != null) {
            keys[idx] = if (input.key() == GLFW.GLFW_KEY_ESCAPE) InputConstants.UNKNOWN else InputConstants.getKey(input)
            capturingIndex = null
            persist()
            rebuildRows()
            return true
        }
        if (focusedRow in commands.indices) {
            val fi = cmdFields.indexOfFirst { it.isFocused }
            if (fi >= 0) {
                if (input.key() == GLFW.GLFW_KEY_ESCAPE) { focusedRow = -1; cmdFields[fi].isFocused = false; return true }
                cmdFields[fi].keyPressed(input)
                commands[focusedRow] = cmdFields[fi].value
                persist()
                return true
            }
        }
        return super.keyPressed(input)
    }

    override fun charTyped(input: net.minecraft.client.input.CharacterEvent): Boolean {
        if (focusedRow in commands.indices) {
            val fi = cmdFields.indexOfFirst { it.isFocused }
            if (fi >= 0) {
                cmdFields[fi].charTyped(input)
                commands[focusedRow] = cmdFields[fi].value
                persist()
                return true
            }
        }
        return super.charTyped(input)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val maxScroll = max(0, keys.size * ROW_H - listH)
        scroll = max(0, min(maxScroll, scroll - (verticalAmount * ROW_H).toInt()))
        rebuildRows()
        return true
    }

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
                fishmod.utils.debug.Debug.LOGGER.error("[NanoVG] CommandKeysScreen paintNvgOverlay failed", t)
            }
        } finally {
            nvgGlState.restore()
        }
    }
}
