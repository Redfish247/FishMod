package fishmod.features

import com.mojang.blaze3d.platform.InputConstants
import fishmod.features.other.CommandKeys
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

/**
 * /fm commandkeys — bind arbitrary keys/mouse buttons to slash commands.
 *
 * `keys`/`commands` lists are the source of truth; widgets are rebuilt from them on every
 * add/remove/scroll/rebind. Key capture mirrors [FishModScreen]'s rebind convention: click a
 * key box to arm capture, then the next key or mouse click is bound; Escape unbinds instead.
 */
class CommandKeysScreen : Screen(Component.literal("Command Keys")) {

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

    private val keys: MutableList<InputConstants.Key> = ArrayList()
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
        clearWidgets()

        for (i in keys.indices) {
            val rowTop = listY + i * ROW_H - scroll
            if (rowTop + ROW_H < listY || rowTop > listY + listH) continue
            val idx = i

            val k = keys[i]
            val label = if (capturingIndex != null && capturingIndex == i) {
                "> Press a key <"
            } else if (k == InputConstants.UNKNOWN) {
                "Unbound"
            } else {
                k.displayName.string
            }
            addRenderableWidget(
                Button.builder(Component.literal(label)) {
                    capturingIndex = idx
                    rebuildRows()
                }
                    .bounds(listX, rowTop + 3, KEY_BTN_W, 18).build()
            )

            val cmdField = EditBox(this.font, cmdFieldX, rowTop + 3, cmdFieldW, 18, Component.literal("Command"))
            cmdField.setMaxLength(256)
            cmdField.setValue(commands[i])
            cmdField.setResponder { s ->
                commands[idx] = s
                persist()
            }
            addRenderableWidget(cmdField)

            addRenderableWidget(
                Button.builder(Component.literal("§cX")) {
                    keys.removeAt(idx)
                    commands.removeAt(idx)
                    persist()
                    rebuildRows()
                }
                    .bounds(removeBtnX, rowTop + 3, REMOVE_BTN_W, 18).build()
            )
        }

        val btnY = listY + listH + 8
        addRenderableWidget(
            Button.builder(Component.literal("+ Add Command Key")) {
                keys.add(InputConstants.UNKNOWN)
                commands.add("")
                persist()
                rebuildRows()
            }
                .bounds(panelX + 14, btnY, 160, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Done")) { onClose() }
                .bounds(panelX + panelW - 14 - 70, btnY, 70, 20).build()
        )
    }

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(ctx, mouseX, mouseY, delta)
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, BORDER)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_PANEL)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 22, BG_SECTION)
        ctx.fill(panelX, panelY + 22, panelX + panelW, panelY + 23, ACCENT)
        ctx.centeredText(this.font, "§b§lCommand Keys", panelX + panelW / 2, panelY + 7, 0xFFFFFF)
        ctx.text(
            this.font,
            "§7Click a key box, then press a key or click a mouse button §8(Esc to unbind)",
            panelX + 14, panelY + 30, TEXT_HINT
        )
        ctx.fill(listX - 2, listY - 2, listX + listW + 2, listY + listH + 2, LIST_BG)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val idx = capturingIndex
        if (idx != null) {
            keys[idx] = InputConstants.Type.MOUSE.getOrCreate(click.button())
            capturingIndex = null
            persist()
            rebuildRows()
            return true
        }
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
        return super.keyPressed(input)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val maxScroll = max(0, keys.size * ROW_H - listH)
        scroll = max(0, min(maxScroll, scroll - (verticalAmount * ROW_H).toInt()))
        rebuildRows()
        return true
    }
}
