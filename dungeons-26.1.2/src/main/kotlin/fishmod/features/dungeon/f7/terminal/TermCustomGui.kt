package fishmod.features.dungeon.f7.terminal

import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ContainerInput
import kotlin.math.sqrt

/**
 * Odin "Custom GUI" render mode for the terminal solver — replaces the vanilla chest with a big
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
        val rows = size / cols
        val scale = FishSettings.terminalCustomScale.coerceIn(0.5, 3.0)
        val cell = (30 * scale).toInt().coerceAtLeast(12)
        val gap = (FishSettings.terminalCustomGap * scale).toInt().coerceAtLeast(0)
        val round = (FishSettings.terminalCustomRoundness * scale).toInt().coerceIn(0, cell / 2)
        val boardW = cols * cell + (cols - 1) * gap
        val boardH = rows * cell + (rows - 1) * gap
        val ox = screenW / 2 - boardW / 2
        val oy = screenH / 2 - boardH / 2

        val pad = (8 * scale).toInt()
        roundFill(ctx, ox - pad, oy - pad, boardW + pad * 2, boardH + pad * 2, round + 3, FishSettings.terminalCustomBg)

        val sol = t.solution
        val itemScale = (cell - 4) / 16f
        for (i in 0 until size) {
            val cx = ox + (i % cols) * (cell + gap)
            val cy = oy + (i / cols) * (cell + gap)
            rects[i] = intArrayOf(cx, cy, cell, cell)

            roundFill(ctx, cx, cy, cell, cell, round, 0x40101218)
            val inSol = i in sol
            if (inSol) roundFill(ctx, cx, cy, cell, cell, round, TerminalSolver.slotColor(t, i))
            else if (FishSettings.terminalHideWrong && t.type != TerminalType.NUMBERS) {
                roundFill(ctx, cx, cy, cell, cell, round, FishSettings.terminalWrongCover)
                continue
            }

            val st = t.items[i]
            if (st != null && !st.isEmpty) {
                val ps = ctx.pose()
                ps.pushMatrix()
                ps.translate(cx + 2f, cy + 2f)
                ps.scale(itemScale, itemScale)
                ctx.item(st, 0, 0)
                ps.popMatrix()
                ctx.itemDecorations(mc.font, st, cx + 2, cy + 2)
            }

            if (inSol && FishSettings.terminalShowNumbers) {
                val label = when (t.type) {
                    TerminalType.RUBIX -> {
                        val n = t.solution.count { it == i }.let { if (it < 3) it else it - 5 }
                        if (n != 0) n.toString() else ""
                    }
                    TerminalType.NUMBERS -> t.solution.indexOf(i).takeIf { it in 0..2 }?.plus(1)?.toString() ?: ""
                    else -> ""
                }
                if (label.isNotEmpty()) ctx.text(mc.font, label, cx + cell / 2 - 3, cy + cell / 2 - 4, -0x1, true)
            }
        }
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
        if (FishSettings.terminalBlockWrongClicks && !t.canClick(idx, right)) return
        if (screen is TermSimScreen) { screen.simClick(idx, button); return }
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        mc.gameMode?.handleContainerInput(screen.menu.containerId, idx, button, ContainerInput.PICKUP, p)
        t.simulateClick(idx, right)
        t.isClicked = true
    }

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
