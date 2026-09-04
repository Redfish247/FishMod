package fishmod.features.dungeon.f7.terminal

import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import kotlin.math.sqrt

/**
 * "Custom GUI" render mode for the terminal solver — replaces the vanilla chest with a big
 * rounded-slot board drawn from [TerminalSolver.current]. Used by both real terminals (via
 * HandledScreenMixin) and [TermSimScreen]. Scale / roundness / gap / background are configurable.
 */
object TermCustomGui {

    /** idx -> [x, y, w, h] of the last rendered board, for click hit-testing. */
    private val rects = HashMap<Int, IntArray>()

    private fun on() = FishSettings.terminalRenderMode == 1 && TerminalSolver.current != null

    /** True when the vanilla chest for the currently-open screen should be hidden and replaced. */
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

        // fixed play area per type: [minCol, maxCol, minRow, maxRow]; Hypixel's puzzle region is constant, so the board never resizes or pulls in the border
        val pa = when (t.type) {
            TerminalType.RUBIX       -> intArrayOf(3, 5, 1, 3)   // 3 x 3
            TerminalType.NUMBERS     -> intArrayOf(1, 7, 1, 2)   // 7 x 2
            TerminalType.PANES       -> intArrayOf(1, 7, 1, 3)   // 7 x 3
            TerminalType.STARTS_WITH -> intArrayOf(1, 7, 1, 3)   // 7 x 3
            TerminalType.SELECT      -> intArrayOf(1, 7, 1, 4)   // 7 x 4
            TerminalType.MELODY      -> intArrayOf(1, 7, 0, 4)   // 7 x 5 — include row 0 (the magenta target marker above the grid)
        }
        val minC = pa[0]; val maxC = pa[1]; val minR = pa[2]; val maxR = pa[3]
        val gridCols = maxC - minC + 1
        val gridRows = maxR - minR + 1

        val boardW = gridCols * cell + (gridCols - 1) * gap
        val boardH = gridRows * cell + (gridRows - 1) * gap
        val ox = screenW / 2 - boardW / 2
        val oy = screenH / 2 - boardH / 2

        val pad = (8 * scale).toInt()
        // Force a fully opaque panel — the board must not be see-through onto the screen behind it.
        roundFill(ctx, ox - pad, oy - pad, boardW + pad * 2, boardH + pad * 2, round + 3,
            FishSettings.terminalCustomBg or 0xFF000000.toInt())

        // melody isn't a "highlight these slots" board - it's a target-column marker row, a 5-wide note grid, and 4 buttons in column 7
        if (t.type == TerminalType.MELODY) {
            renderMelody(ctx, t, ox, oy, cell, gap, round)
            return
        }

        val itemScale = (cell - 4) / 16f
        val numbers = t.type == TerminalType.NUMBERS

        for (i in 0 until size) {
            val r = i / cols; val c = i % cols
            // Only the fixed play-area cells — the pane border and decoration slots are outside it.
            if (c < minC || c > maxC || r < minR || r > maxR) continue
            val cx = ox + (c - minC) * (cell + gap)
            val cy = oy + (r - minR) * (cell + gap)
            val st = t.items[i]
            val realItem = st != null && !st.isEmpty && !isFiller(st)
            val inSol = i in sol

            // filler slots (pane border/background) are never a target and must never be a click target - clicking one on a live terminal makes Hypixel close it
            if (!realItem) continue
            rects[i] = intArrayOf(cx, cy, cell, cell)

            if (numbers) {
                // numbers: never draw the red pane; only the next 3 clicks get a coloured cell, the rest stay a dim digit so they can't be mistaken for targets
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

            // slotColor returns 0 for "in the solution list but no longer a real target" (e.g. a Rubix pane over-clicked back to goal); don't paint those
            val solColor = if (inSol) TerminalSolver.slotColor(t, i) else 0
            if (inSol && solColor != 0) {
                roundFill(ctx, cx, cy, cell, cell, round, solColor)
            } else if (FishSettings.terminalHideWrong) {
                roundFill(ctx, cx, cy, cell, cell, round, FishSettings.terminalWrongCover)
                continue
            } else {
                roundFill(ctx, cx, cy, cell, cell, round, 0x40101820)  // neutral cell for a non-click item
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

    /**
     * Melody board layout — 5 rows x 7 cols, window index `i` -> `row = i/9`, `col = i%9`,
     * offset so col 1 is the left edge of the board.
     *  - row 0: only the columns in the solution, painted [terminalMelodyColor] (the target marker)
     *  - cols 1-5 / rows 1-4: the note grid — [terminalMelodyPointerColor] if in solution, else a dim cell
     *  - col 7 / rows 1-4: the four click buttons — same pointer/dim colours, and the only click targets
     */
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
                r == 0 -> if (inSol) colum else continue      // top row: only show target columns
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

    /** Centred digit scaled up to fill the cell — used for the Numbers board so counts read clearly. */
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
        // "First Click Protection" — ignore clicks in the first N ms after the terminal opens.
        if (screen !is TermSimScreen &&
            System.currentTimeMillis() - t.timeOpened < FishSettings.terminalFirstClickProtMs) return
        if (FishSettings.terminalBlockWrongClicks && !t.canClick(idx, right)) return
        if (screen is TermSimScreen) { screen.simClick(idx, button); return }
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        // "Middle Click GUI": left->middle-click (CLONE) so the item never lands on the cursor; right stays right (rubix reverse)
        val outBtn = if (FishSettings.terminalMiddleClickGui && button == 0) 2 else button
        val mode = if (outBtn == 2) ContainerInput.CLONE else ContainerInput.PICKUP
        mc.gameMode?.handleContainerInput(screen.menu.containerId, idx, outBtn, mode, p)
        // No optimistic update — TerminalSolver.tickSync recomputes from the real menu slots.
    }

    private val FILLER = setOf(
        "black_stained_glass_pane", "gray_stained_glass_pane", "light_gray_stained_glass_pane",
    )
    private fun isFiller(st: ItemStack): Boolean =
        BuiltInRegistries.ITEM.getKey(st.item).path in FILLER

    /** Stepped rounded rect (no NVG dependency), same idea as LeapMenu.roundFill. */
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
