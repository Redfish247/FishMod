package fishmod.features

import fishmod.features.other.CommandAliases
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min

/**
 * /fm aliases — map a short command (e.g. "dh") to a longer one (e.g. "warp dh"). Same convention
 * as [CommandKeysScreen]: `aliases`/`commands` are the source of truth, rows rebuilt from them on
 * every add/remove/scroll. Reskinned to match [FishModScreen]'s smooth pill/rounded-rect look via
 * [ScreenTheme] — the "+ Add Alias"/"Done"/remove-"X" buttons are custom click-region pills
 * (no vanilla [net.minecraft.client.gui.components.Button]), and the [EditBox] fields are
 * borderless with a hand-drawn rounded-rect container behind them.
 */
class CommandAliasesScreen : Screen(Component.literal("Command Aliases")) {

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

    /** A clickable pill region drawn+hit-tested by hand instead of a vanilla widget. */
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

    private val editBoxes: MutableList<EditBox> = ArrayList()
    private val clickRects: MutableList<ClickRect> = ArrayList()
    private var addBtn: ClickRect? = null
    private var doneBtn: ClickRect? = null

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
        editBoxes.clear()
        clickRects.clear()

        for (i in aliases.indices) {
            val rowTop = listY + i * ROW_H - scroll
            if (rowTop + ROW_H < listY || rowTop > listY + listH) continue
            val idx = i

            val aliasField = EditBox(this.font, listX + 3, rowTop + 3, ALIAS_FIELD_W - 6, 18, Component.literal("Alias"))
            aliasField.setMaxLength(32)
            aliasField.setBordered(false)
            aliasField.setValue(aliases[i])
            aliasField.setResponder { s ->
                aliases[idx] = s
                persist()
            }
            addRenderableWidget(aliasField)
            editBoxes.add(aliasField)

            val cmdField = EditBox(this.font, cmdFieldX + 4, rowTop + 3, cmdFieldW - 8, 18, Component.literal("Command"))
            cmdField.setMaxLength(256)
            cmdField.setBordered(false)
            cmdField.setValue(commands[i])
            cmdField.setResponder { s ->
                commands[idx] = s
                persist()
            }
            addRenderableWidget(cmdField)
            editBoxes.add(cmdField)

            clickRects.add(ClickRect(removeBtnX, rowTop + 3, REMOVE_BTN_W, 18) {
                aliases.removeAt(idx)
                commands.removeAt(idx)
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
        ctx.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, BORDER)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_PANEL)
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 22, BG_SECTION)
        ctx.fill(panelX, panelY + 22, panelX + panelW, panelY + 23, ACCENT)
        ctx.centeredText(this.font, "§b§lCommand Aliases", panelX + panelW / 2, panelY + 7, 0xFFFFFF)
        ctx.text(
            this.font,
            "§7Alias §f(no slash) §7→ Command it runs. New/edited aliases work right away;",
            panelX + 14, panelY + 30, SUBTEXT_COLOR
        )
        ctx.text(
            this.font,
            "§7removing/renaming one fully clears after you rejoin.",
            panelX + 14, panelY + 39, SUBTEXT_COLOR
        )
        ScreenTheme.roundedRect(ctx, listX - 2, listY - 2, listW + 4, listH + 4, 6, LIST_BG)

        // rounded-rect field backgrounds behind each row's EditBoxes
        for (i in aliases.indices) {
            val rowTop = listY + i * ROW_H - scroll
            if (rowTop + ROW_H < listY || rowTop > listY + listH) continue
            ScreenTheme.roundedRectRing(ctx, listX, rowTop + 3, ALIAS_FIELD_W, 18, 5, 1, FIELD_BG, FIELD_BORDER)
            ScreenTheme.roundedRectRing(ctx, cmdFieldX, rowTop + 3, cmdFieldW, 18, 5, 1, FIELD_BG, FIELD_BORDER)
        }

        for (r in clickRects) {
            val hover = r.hit(mouseX, mouseY)
            ScreenTheme.pill(ctx, r.x, r.y, r.x + r.w, r.y + r.h, if (hover) DANGER_HOVER else DANGER)
            val label = "X"
            val tw = ScreenTheme.stw(this.font, label)
            ScreenTheme.st(ctx, this.font, label, r.x + (r.w - tw) / 2, r.y + (r.h - 8) / 2, 0xFF2A0808.toInt())
        }

        addBtn?.let { r ->
            val hover = r.hit(mouseX, mouseY)
            ScreenTheme.pill(ctx, r.x, r.y, r.x + r.w, r.y + r.h, if (hover) ACCENT_HOVER else ACCENT)
            val label = "+ Add Alias"
            val tw = ScreenTheme.stw(this.font, label)
            ScreenTheme.st(ctx, this.font, label, r.x + (r.w - tw) / 2, r.y + (r.h - 8) / 2, 0xFF06302F.toInt())
        }
        doneBtn?.let { r ->
            val hover = r.hit(mouseX, mouseY)
            ScreenTheme.roundedRectRing(ctx, r.x, r.y, r.w, r.h, r.h / 2, 1, 0xFF14181D.toInt(), if (hover) ACCENT_HOVER else ACCENT)
            val label = "Done"
            val tw = ScreenTheme.stw(this.font, label)
            ScreenTheme.st(ctx, this.font, label, r.x + (r.w - tw) / 2, r.y + (r.h - 8) / 2, if (hover) ACCENT_HOVER else TEXT_COLOR)
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta)
    }

    override fun mouseClicked(click: MouseButtonEvent, bl: Boolean): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()
        for (r in clickRects) {
            if (r.hit(mx, my)) { r.action(); return true }
        }
        addBtn?.let { if (it.hit(mx, my)) { it.action(); return true } }
        doneBtn?.let { if (it.hit(mx, my)) { it.action(); return true } }
        return super.mouseClicked(click, bl)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        val maxScroll = max(0, aliases.size * ROW_H - listH)
        scroll = max(0, min(maxScroll, scroll - (verticalAmount * ROW_H).toInt()))
        rebuildRows()
        return true
    }
}
