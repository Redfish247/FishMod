package fishmod.features.dungeon.f7.terminal

import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import kotlin.math.sqrt

object TermCustomGui {

    private val rects = HashMap<Int, IntArray>()

    private fun on() = FishSettings.terminalRenderMode == 1 && TerminalSolver.current != null

    @JvmStatic
    fun suppressVanilla(screen: Any?): Boolean {
        if (!on()) return false
        val t = TerminalSolver.current ?: return false
        if (t.type == TerminalType.MELODY && FishSettings.terminalStopMelody) return false
        if (screen is TermSimScreen) return TerminalSolver.simActive
        if (screen !is AbstractContainerScreen<*>) return false
        return FishSettings.terminalSolverEnabled && screen.menu.slots.size >= t.type.windowSize
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, screenW: Int, screenH: Int) {
        val t = TerminalSolver.current ?: return
        rects.clear()
        val mc = Minecraft.getInstance()
        val size = t.type.windowSize
        val cols = 9
        val scale = FishSettings.terminalCustomScale.coerceIn(0.5, 3.0)
        val cell = (30 * scale).toInt().coerceAtLeast(12)
        val gap = (FishSettings.terminalCustomGap * scale).toInt().coerceAtLeast(0)
        val round = (FishSettings.terminalCustomRoundness * scale).toInt().coerceIn(0, cell / 2)

        val sol = t.solution

        val pa = when (t.type) {
            TerminalType.RUBIX       -> intArrayOf(3, 5, 1, 3)
            TerminalType.NUMBERS     -> intArrayOf(1, 7, 1, 2)
            TerminalType.PANES       -> intArrayOf(1, 7, 1, 3)
            TerminalType.STARTS_WITH -> intArrayOf(1, 7, 1, 3)
            TerminalType.SELECT      -> intArrayOf(1, 7, 1, 4)
            TerminalType.MELODY      -> intArrayOf(1, 7, 0, 4)
        }
        val minC = pa[0]; val maxC = pa[1]; val minR = pa[2]; val maxR = pa[3]
        val gridCols = maxC - minC + 1
        val gridRows = maxR - minR + 1

        val boardW = gridCols * cell + (gridCols - 1) * gap
        val boardH = gridRows * cell + (gridRows - 1) * gap
        val ox = screenW / 2 - boardW / 2
        val oy = screenH / 2 - boardH / 2

        val pad = (8 * scale).toInt()
        roundFill(ctx, ox - pad, oy - pad, boardW + pad * 2, boardH + pad * 2, round + 3,
            FishSettings.terminalCustomBg or 0xFF000000.toInt())

        if (t.type == TerminalType.MELODY) {
            renderMelody(ctx, t, ox, oy, cell, gap, round)
            return
        }

        val itemScale = (cell - 4) / 16f
        val numbers = t.type == TerminalType.NUMBERS

        for (i in 0 until size) {
            val r = i / cols; val c = i % cols
            if (c < minC || c > maxC || r < minR || r > maxR) continue
            val cx = ox + (c - minC) * (cell + gap)
            val cy = oy + (r - minR) * (cell + gap)
            val st = t.items[i]
            val realItem = st != null && !st.isEmpty && !isFiller(st)
            val inSol = i in sol

            if (!realItem) continue
            rects[i] = intArrayOf(cx, cy, cell, cell)

            if (numbers) {
                val ord = sol.indexOf(i)
                val col = when (ord) {
                    0 -> FishSettings.terminalOrderColor1
                    1 -> FishSettings.terminalOrderColor2
                    2 -> FishSettings.terminalOrderColor3
                    else -> 0
                }
                if (col != 0) roundFill(ctx, cx, cy, cell, cell, round, col)
                val n = st?.count ?: 0
                if (n > 0) drawBig(ctx, mc, n.toString(), cx, cy, cell, if (ord in 0..2) -0x1 else -0x777778)
                continue
            }

            val solColor = if (inSol) TerminalSolver.slotColor(t, i) else 0
            if (inSol && solColor != 0) {
                roundFill(ctx, cx, cy, cell, cell, round, solColor)
            } else if (FishSettings.terminalHideWrong) {
                roundFill(ctx, cx, cy, cell, cell, round, FishSettings.terminalWrongCover)
                continue
            } else {
                roundFill(ctx, cx, cy, cell, cell, round, 0x40101820)
            }

            if (st != null && !st.isEmpty) {
                val ps = ctx.pose()
                ps.pushMatrix()
                ps.translate(cx + 2f, cy + 2f)
                ps.scale(itemScale, itemScale)
                ctx.item(st, 0, 0)
                ps.popMatrix()
                ctx.itemDecorations(mc.font, st, cx + 2, cy + 2)
            }

            if (inSol && t.type == TerminalType.RUBIX && FishSettings.terminalShowNumbers) {
                val n = sol.count { it == i }.let { if (it < 3) it else it - 5 }
                if (n != 0) drawCentered(ctx, mc, n.toString(), cx, cy, cell)
            }
        }
    }

    private fun renderMelody(ctx: GuiGraphicsExtractor, t: TerminalHandler, ox: Int, oy: Int, cell: Int, gap: Int, round: Int) {
        val sol = t.solution
        val colum = FishSettings.terminalMelodyColor
        val pointer = FishSettings.terminalMelodyPointerColor
        val bg = 0x66404040
        for (i in 0 until t.type.windowSize) {
            val r = i / 9
            val c = i % 9
            if (r > 4 || c < 1 || c > 7) continue
            val draw = r == 0 || (c == 7 && r in 1..4) || c in 1..5
            if (!draw) continue
            val inSol = i in sol
            val color = when {
                r == 0 -> if (inSol) colum else continue
                else -> if (inSol) pointer else bg
            }
            val cx = ox + (c - 1) * (cell + gap)
            val cy = oy + r * (cell + gap)
            roundFill(ctx, cx, cy, cell, cell, round, color)
            if (c == 7 && r in 1..4) rects[i] = intArrayOf(cx, cy, cell, cell)
        }
    }

    private fun drawCentered(ctx: GuiGraphicsExtractor, mc: Minecraft, s: String, cx: Int, cy: Int, cell: Int) {
        val w = mc.font.width(s)
        ctx.text(mc.font, s, cx + (cell - w) / 2, cy + cell / 2 - 4, -0x1, true)
    }

    private fun drawBig(ctx: GuiGraphicsExtractor, mc: Minecraft, s: String, cx: Int, cy: Int, cell: Int, color: Int) {
        val sc = (cell / 14f).coerceIn(1f, 2.5f)
        val w = mc.font.width(s) * sc
        val ps = ctx.pose()
        ps.pushMatrix()
        ps.translate(cx + (cell - w) / 2f, cy + cell / 2f - 4f * sc)
        ps.scale(sc, sc)
        ctx.text(mc.font, s, 0, 0, color, true)
        ps.popMatrix()
    }

    @JvmStatic
    fun slotAt(mx: Int, my: Int): Int {
        for ((idx, r) in rects) if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return idx
        return -1
    }

    @JvmStatic
    fun handleClick(screen: AbstractContainerScreen<*>, idx: Int, button: Int) {
        if (idx < 0) return
        val t = TerminalSolver.current ?: return
        val right = button == 1
        if (screen !is TermSimScreen &&
            System.currentTimeMillis() - t.timeOpened < FishSettings.terminalFirstClickProtMs) return
        if (FishSettings.terminalBlockWrongClicks && !t.canClick(idx, right)) return
        if (screen is TermSimScreen) { screen.simClick(idx, button); return }
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        val outBtn = if (FishSettings.terminalMiddleClickGui && button == 0) 2 else button
        val mode = if (outBtn == 2) ContainerInput.CLONE else ContainerInput.PICKUP
        mc.gameMode?.handleContainerInput(screen.menu.containerId, idx, outBtn, mode, p)
    }

    private val FILLER = setOf(
        "black_stained_glass_pane", "gray_stained_glass_pane", "light_gray_stained_glass_pane",
    )
    private fun isFiller(st: ItemStack): Boolean =
        BuiltInRegistries.ITEM.getKey(st.item).path in FILLER

    private fun roundFill(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, rad: Int, color: Int) {
        val r = rad.coerceIn(0, minOf(w, h) / 2)
        if (r <= 0) { ctx.fill(x, y, x + w, y + h, color); return }
        ctx.fill(x + r, y, x + w - r, y + h, color)
        ctx.fill(x, y + r, x + r, y + h - r, color)
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color)
        for (i in 0 until r) {
            val inset = r - sqrt((r * r - (r - 1 - i) * (r - 1 - i)).toDouble()).toInt()
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color)
            ctx.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color)
        }
    }
}
