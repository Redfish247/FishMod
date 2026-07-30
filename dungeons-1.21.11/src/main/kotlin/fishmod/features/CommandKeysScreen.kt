package fishmod.features

import fishmod.features.other.CommandKeys
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

/**
 * /fm commandkeys — bind arbitrary keys/mouse buttons to slash commands.
 *
 * Entries are kept as plain `keys`/`commands` lists (the source of truth) and
 * widgets are rebuilt from them on every add/remove/scroll/rebind — simplest way to support a
 * variable-length, scrollable row list without hand-rolled widget recycling.
 *
 * Key capture mirrors [FishModScreen]'s existing keybind-rebind convention: click a key
 * box to arm capture, then the next key or mouse click is bound; Escape unbinds instead.
 */
class CommandKeysScreen : Screen(Text.literal("Command Keys")) {

    companion object {
        private const val BG_PANEL = 0xF20E1016.toInt()
        private const val BG_SECTION = 0xFF171A22.toInt()
        private const val BORDER = 0xFF2A2D38.toInt()
        private const val ACCENT = 0xFF55FFFF.toInt()
        private const val TEXT_HINT = 0xFF8A8F9C.toInt()
        private const val LIST_BG = 0xFF14161D.toInt()

        private const val ROW_H = 24
        private const val MAX_VISIBLE = 6
        private const val KEY_BTN_W = 120
        private const val REMOVE_BTN_W = 20
    }

    private val keys: MutableList<InputUtil.Key> = ArrayList()
    private val commands: MutableList<String> = ArrayList()
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

    override fun init() {
        for (e in CommandKeys.all()) {
            keys.add(e.key())
            commands.add(e.command())
        }

        listH = ROW_H * MAX_VISIBLE
        panelH = 50 + listH + 46
        panelX = (this.width - panelW) / 2
        panelY = max(8, (this.height - panelH) / 2)

        listX = panelX + 14
        listY = panelY + 44
        listW = panelW - 28
        cmdFieldX = listX + KEY_BTN_W + 6
        cmdFieldW = listW - KEY_BTN_W - 6 - REMOVE_BTN_W - 6
        removeBtnX = listX + listW - REMOVE_BTN_W

        rebuildRows()
    }

    private fun persist() {
        val list = ArrayList<CommandKeys.Entry>()
        for (i in keys.indices) list.add(CommandKeys.Entry(keys[i], commands[i]))
        CommandKeys.replaceAll(list)
    }

    private fun rebuildRows() {
        clearChildren()

        for (i in keys.indices) {
            val rowTop = listY + i * ROW_H - scroll
            if (rowTop + ROW_H < listY || rowTop > listY + listH) continue
            val idx = i

            val k = keys[i]
            val label = if (capturingIndex != null && capturingIndex == i) {
                "> Press a key <"
            } else if (k == InputUtil.UNKNOWN_KEY) {
                "Unbound"
            } else {
                k.localizedText.string
            }
            addDrawableChild(
                ButtonWidget.builder(Text.literal(label)) {
                    capturingIndex = idx
                    rebuildRows()
                }
                    .dimensions(listX, rowTop + 3, KEY_BTN_W, 18).build()
            )

            val cmdField = TextFieldWidget(this.textRenderer, cmdFieldX, rowTop + 3, cmdFieldW, 18, Text.literal("Command"))
            cmdField.setMaxLength(256)
            cmdField.text = commands[i]
            cmdField.setChangedListener { s ->
                commands[idx] = s
                persist()
            }
            addDrawableChild(cmdField)

            addDrawableChild(
                ButtonWidget.builder(Text.literal("§cX")) {
                    keys.removeAt(idx)
                    commands.removeAt(idx)
                    persist()
                    rebuildRows()
                }
                    .dimensions(removeBtnX, rowTop + 3, REMOVE_BTN_W, 18).build()
            )
        }

        val btnY = listY + listH + 8
        addDrawableChild(
            ButtonWidget.builder(Text.literal("+ Add Command Key")) {
                keys.add(InputUtil.UNKNOWN_KEY)
                commands.add("")
                persist()
                rebuildRows()
            }
                .dimensions(panelX + 14, btnY, 160, 20).build()
        )
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Done")) { close() }
                .dimensions(panelX + panelW - 14 - 70, btnY, 70, 20).build()
        )
    }

    override fun renderBackground(ctx: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderBackground(ctx, mouseX, mouseY, delta)
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, BORDER)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_PANEL)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 22, BG_SECTION)
        ctx.fill(panelX, panelY + 22, panelX + panelW, panelY + 23, ACCENT)
        ctx.drawCenteredTextWithShadow(this.textRenderer, "§b§lCommand Keys", panelX + panelW / 2, panelY + 7, 0xFFFFFF)
        ctx.drawTextWithShadow(
            this.textRenderer,
            "§7Click a key box, then press a key or click a mouse button §8(Esc to unbind)",
            panelX + 14, panelY + 30, TEXT_HINT
        )
        ctx.fill(listX - 2, listY - 2, listX + listW + 2, listY + listH + 2, LIST_BG)
    }

    override fun mouseClicked(click: Click, bl: Boolean): Boolean {
        val idx = capturingIndex
        if (idx != null) {
            keys[idx] = InputUtil.Type.MOUSE.createFromCode(click.button())
            capturingIndex = null
            persist()
            rebuildRows()
            return true
        }
        return super.mouseClicked(click, bl)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        val idx = capturingIndex
        if (idx != null) {
            keys[idx] = if (input.key() == GLFW.GLFW_KEY_ESCAPE) InputUtil.UNKNOWN_KEY else InputUtil.fromKeyCode(input)
            capturingIndex = null
            persist()
            rebuildRows()
            return true
        }
        return super.keyPressed(input)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val maxScroll = max(0, keys.size * ROW_H - listH)
        scroll = max(0, min(maxScroll, scroll - (verticalAmount * ROW_H).toInt()))
        rebuildRows()
        return true
    }
}
