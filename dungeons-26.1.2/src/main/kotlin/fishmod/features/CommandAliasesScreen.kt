package fishmod.features

import fishmod.features.other.CommandAliases
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min

/** /fm aliases — map a short command (e.g. "dh") to a longer one (e.g. "warp dh"). Same convention as [CommandKeysScreen]: `aliases`/`commands` are the source of truth, widgets rebuilt from them on every add/remove/scroll. */
class CommandAliasesScreen : Screen(Component.literal("Command Aliases")) {

    companion object {
        private const val BG_PANEL = 0xF20E1016.toInt()
        private const val BG_SECTION = 0xFF171A22.toInt()
        private const val BORDER = 0xFF2A2D38.toInt()
        private const val ACCENT = 0xFF55FFFF.toInt()
        private const val TEXT_HINT = 0xFF8A8F9C.toInt()
        private const val LIST_BG = 0xFF14161D.toInt()

        private const val ROW_H = 24
        private const val MAX_VISIBLE = 6
        private const val ALIAS_FIELD_W = 90
        private const val REMOVE_BTN_W = 20
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

    override fun init() {
        for (e in CommandAliases.all()) {
            aliases.add(e.alias())
            commands.add(e.command())
        }

        listH = ROW_H * MAX_VISIBLE
        panelH = 56 + listH + 46
        panelX = (this.width - panelW) / 2
        panelY = max(8, (this.height - panelH) / 2)

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
        clearWidgets()

        for (i in aliases.indices) {
            val rowTop = listY + i * ROW_H - scroll
            if (rowTop + ROW_H < listY || rowTop > listY + listH) continue
            val idx = i

            val aliasField = EditBox(this.font, listX, rowTop + 3, ALIAS_FIELD_W, 18, Component.literal("Alias"))
            aliasField.setMaxLength(32)
            aliasField.setValue(aliases[i])
            aliasField.setResponder { s ->
                aliases[idx] = s
                persist()
            }
            addRenderableWidget(aliasField)

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
                    aliases.removeAt(idx)
                    commands.removeAt(idx)
                    persist()
                    rebuildRows()
                }
                    .bounds(removeBtnX, rowTop + 3, REMOVE_BTN_W, 18).build()
            )
        }

        val btnY = listY + listH + 8
        addRenderableWidget(
            Button.builder(Component.literal("+ Add Alias")) {
                aliases.add("")
                commands.add("")
                persist()
                rebuildRows()
            }
                .bounds(panelX + 14, btnY, 120, 20).build()
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
        ctx.centeredText(this.font, "§b§lCommand Aliases", panelX + panelW / 2, panelY + 7, 0xFFFFFF)
        ctx.text(
            this.font,
            "§7Alias §f(no slash) §7→ Command it runs. New/edited aliases work right away;",
            panelX + 14, panelY + 30, TEXT_HINT
        )
        ctx.text(
            this.font,
            "§7removing/renaming one fully clears after you rejoin.",
            panelX + 14, panelY + 39, TEXT_HINT
        )
        ctx.fill(listX - 2, listY - 2, listX + listW + 2, listY + listH + 2, LIST_BG)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val maxScroll = max(0, aliases.size * ROW_H - listH)
        scroll = max(0, min(maxScroll, scroll - (verticalAmount * ROW_H).toInt()))
        rebuildRows()
        return true
    }
}
