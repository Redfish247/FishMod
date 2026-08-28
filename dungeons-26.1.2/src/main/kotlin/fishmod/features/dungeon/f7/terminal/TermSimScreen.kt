package fishmod.features.dungeon.f7.terminal

import fishmod.utils.config.FishConfig
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW
import kotlin.random.Random

private const val SLOT = 22
private const val BASE_TINT = 0x2E_0A0A12
private const val PANEL_BG = 0x50_161B27
private const val GLASS_TOP = 0x22_FFFFFF
private const val GLASS_BOT = 0x05_FFFFFF
private const val EDGE_LIGHT = 0x50_FFFFFF
private const val EDGE_DARK = 0x38_000000
private const val CELL_BG = 0x34_0E1018
private const val CELL_EDGE = 0x1C_FFFFFF
private const val HOVER = 0x26_FFFFFF
private const val BTN_BG = 0x40_23304A
private const val BTN_HOVER = 0x66_2E3E5E

private val RED_PANE = Items.RED_STAINED_GLASS_PANE
private val BLACK_PANE = Items.BLACK_STAINED_GLASS_PANE
private val GREEN_PANE = Items.LIME_STAINED_GLASS_PANE

private val DYES = listOf(
    Items.RED_DYE to "red", Items.BLUE_DYE to "blue", Items.LIME_DYE to "lime",
    Items.YELLOW_DYE to "yellow", Items.PURPLE_DYE to "purple", Items.ORANGE_DYE to "orange",
    Items.PINK_DYE to "pink", Items.CYAN_DYE to "cyan", Items.WHITE_DYE to "white",
)

private val WORDS = listOf(
    "Apple", "Arrow", "Axe", "Bread", "Bow", "Boat", "Cake", "Chest", "Clock", "Coal",
    "Diamond", "Dirt", "Egg", "Emerald", "Feather", "Fish", "Flint", "Gold", "Grass", "Ice",
    "Iron", "Kelp", "Ladder", "Lava", "Leaf", "Melon", "Milk", "Oak", "Paper", "Potato",
    "Quartz", "Rail", "Redstone", "Rose", "Saddle", "Sand", "Stick", "Stone", "String", "Sugar",
    "Torch", "Wheat", "Wood", "Wool",
)

/** All six terminals; ordinal matches [TerminalType] for PB storage. */
private val SIMMABLE = listOf(
    TerminalType.PANES, TerminalType.NUMBERS, TerminalType.SELECT,
    TerminalType.STARTS_WITH, TerminalType.RUBIX, TerminalType.MELODY,
)

// Rubix cycle order (RubixHandler's RUBIX_ORDER is file-private, so mirror it here).
private val RUBIX_PANES = listOf(
    Items.ORANGE_STAINED_GLASS_PANE, Items.YELLOW_STAINED_GLASS_PANE,
    Items.GREEN_STAINED_GLASS_PANE, Items.BLUE_STAINED_GLASS_PANE, Items.RED_STAINED_GLASS_PANE,
)
// The 3x3 clickable grid of a 45-slot chest.
private val RUBIX_CELLS = intArrayOf(10, 11, 12, 19, 20, 21, 28, 29, 30)

private const val MELODY_MS_PER_COL = 190L
private val MELODY_BUTTONS = intArrayOf(16, 25, 34, 43)

/**
 * `/fmtermsim [type]` — an Odin-style terminal practice board. A start menu picks the terminal (or
 * Random), each shows a personal best; solving one drops you back to the menu with the PB updated.
 * Panes / Numbers / Select / Starts With / Rubix reuse the real [TerminalHandler] solvers; Melody is
 * a self-contained timing sim.
 */
class TermSimScreen(private val forced: TerminalType?) : Screen(Component.literal("Term Sim")) {

    private enum class Mode { MENU, SIM }
    private var mode = Mode.MENU

    private lateinit var handler: TerminalHandler
    private lateinit var type: TerminalType
    private var selectColor = "red"
    private var startLetter = "A"

    private var openedAt = 0L
    private var lastMs = 0L
    private var misses = 0
    private var flashWrong = -1
    private var flashAt = 0L

    // melody sim state
    private val melTarget = IntArray(4)
    private val melDone = BooleanArray(4)
    private var melRow = 0
    private var melRowStart = 0L

    private var gridX = 0
    private var gridY = 0
    private val menuRects = ArrayList<IntArray>() // x,y,w,h,action  (action: 0..3 sim, 8 random, 9 reset)

    private val pbs = DoubleArray(TerminalType.entries.size) { Double.MAX_VALUE }

    override fun init() {
        loadPbs()
        if (forced != null && forced in SIMMABLE) startSim(forced) else mode = Mode.MENU
    }

    override fun isPauseScreen() = false
    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    // ── PB persistence ─────────────────────────────────────────────────────

    private fun loadPbs() {
        FishSettings.termSimPbs.split(',').forEachIndexed { i, s ->
            if (i < pbs.size) s.trim().toDoubleOrNull()?.let { pbs[i] = it }
        }
    }

    private fun savePbs() {
        FishSettings.termSimPbs = pbs.joinToString(",") { if (it == Double.MAX_VALUE) "" else "%.3f".format(it) }
        runCatching { FishConfig.manager.save() }
    }

    // ── flow ───────────────────────────────────────────────────────────────

    private fun startSim(t: TerminalType) {
        mode = Mode.SIM
        type = t
        openedAt = System.currentTimeMillis()
        misses = 0
        if (t == TerminalType.MELODY) {
            for (i in 0..3) { melTarget[i] = 1 + Random.nextInt(5); melDone[i] = false }
            melRow = 0
            melRowStart = openedAt
            return
        }
        handler = when (t) {
            TerminalType.PANES -> PanesHandler()
            TerminalType.NUMBERS -> NumbersHandler()
            TerminalType.RUBIX -> RubixHandler()
            TerminalType.SELECT -> { selectColor = DYES.random().second; SelectAllHandler(selectColor) }
            TerminalType.STARTS_WITH -> { startLetter = ('A' + Random.nextInt(8)).toString(); StartsWithHandler(startLetter) }
            else -> PanesHandler()
        }
        generate()
        handler.handleSlotUpdate(handler.type.windowSize - 1)
    }

    private fun finishSim() {
        lastMs = System.currentTimeMillis() - openedAt
        val o = type.ordinal
        if (lastMs / 1000.0 < pbs[o]) { pbs[o] = lastMs / 1000.0; savePbs() }
        mode = Mode.MENU
    }

    // ── puzzle generation ──────────────────────────────────────────────────

    private fun generate() {
        val n = handler.type.windowSize
        when (type) {
            TerminalType.PANES -> {
                for (i in 0 until n) handler.items[i] = ItemStack(BLACK_PANE)
                repeat(6 + Random.nextInt(9)) { handler.items[Random.nextInt(n)] = ItemStack(RED_PANE) }
            }
            TerminalType.NUMBERS -> {
                val count = 5 + Random.nextInt(3)
                (0 until n).shuffled().take(count).forEachIndexed { k, s -> handler.items[s] = ItemStack(RED_PANE, k + 1) }
            }
            TerminalType.SELECT -> {
                val fill = (0 until n).shuffled().take(20 + Random.nextInt(12))
                val target = DYES.first { it.second == selectColor }.first
                for ((k, s) in fill.withIndex()) handler.items[s] = if (k < 5) ItemStack(target) else ItemStack(DYES.random().first)
            }
            TerminalType.STARTS_WITH -> {
                val slots = (0 until n).shuffled().take(14 + Random.nextInt(6))
                val hits = WORDS.filter { it.startsWith(startLetter, true) }
                for ((k, s) in slots.withIndex()) {
                    val word = if (k < 3 && hits.isNotEmpty()) hits.random() else WORDS.random()
                    handler.items[s] = ItemStack(Items.PAPER).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(word)) }
                }
            }
            TerminalType.RUBIX -> {
                for (i in 0 until n) handler.items[i] = ItemStack(BLACK_PANE)
                for (c in RUBIX_CELLS) handler.items[c] = ItemStack(RUBIX_PANES.random())
            }
            else -> {}
        }
    }

    private fun consume(idx: Int, right: Boolean) {
        handler.items[idx] = when (type) {
            TerminalType.PANES, TerminalType.SELECT -> ItemStack(GREEN_PANE)
            TerminalType.RUBIX -> {
                val cur = RUBIX_PANES.indexOfFirst { handler.items[idx]?.`is`(it) == true }.coerceAtLeast(0)
                ItemStack(RUBIX_PANES[(cur + (if (right) 4 else 1)) % 5])
            }
            else -> null
        }
    }

    // ── render ─────────────────────────────────────────────────────────────

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        runCatching { ctx.blurBeforeThisStratum() }
        runCatching { ctx.nextStratum() }
        ctx.fill(0, 0, width, height, BASE_TINT)
        if (mode == Mode.MENU) renderMenu(ctx, mouseX, mouseY) else renderSim(ctx, mouseX, mouseY)
    }

    private fun renderMenu(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
        menuRects.clear()
        val pw = 300; val ph = 214
        val x = width / 2 - pw / 2; val y = height / 2 - ph / 2
        glass(ctx, x, y, x + pw, y + ph)
        ctx.text(font, "§fTerminal Simulator", x + 16, y + 12, -1)
        ctx.text(font, "§8click a terminal · §7R§8 re-rolls · §7Esc§8 exits", x + 16, y + 24, -1)

        val labels = listOf("§aPanes", "§3Numbers", "§bSelect All", "§5Starts With", "§6Rubix", "§dMelody")
        val bw = 128; val bh = 26
        for (i in SIMMABLE.indices) {
            val bx = x + 20 + (i % 2) * (bw + 12)
            val by = y + 42 + (i / 2) * (bh + 10)
            val hov = mx in bx..(bx + bw) && my in by..(by + bh)
            rr(ctx, bx, by, bw, bh, 5, if (hov) BTN_HOVER else BTN_BG)
            ctx.text(font, labels[i], bx + 10, by + 7, -1)
            val pb = pbs[SIMMABLE[i].ordinal]
            ctx.text(font, if (pb == Double.MAX_VALUE) "§8-- s" else "§d%.2fs".format(pb), bx + bw - 46, by + 7, -1)
            menuRects.add(intArrayOf(bx, by, bw, bh, i))
        }

        val ry = y + ph - 34
        val randX = x + 20; val resX = x + pw - 20 - 96
        rr(ctx, randX, ry, 96, 24, 5, if (mx in randX..(randX + 96) && my in ry..(ry + 24)) BTN_HOVER else BTN_BG)
        ctx.text(font, "§7Random", randX + 26, ry + 8, -1)
        menuRects.add(intArrayOf(randX, ry, 96, 24, 8))
        rr(ctx, resX, ry, 96, 24, 5, if (mx in resX..(resX + 96) && my in ry..(ry + 24)) 0x66_5E2E2E else 0x40_4A2323)
        ctx.text(font, "§cReset PBs", resX + 22, ry + 8, -1)
        menuRects.add(intArrayOf(resX, ry, 96, 24, 9))
    }

    private fun melodyMarkerCol(): Int {
        val phase = ((System.currentTimeMillis() - melRowStart) / MELODY_MS_PER_COL).toInt()
        return 1 + phase % 5
    }

    private fun renderSim(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
        if (type == TerminalType.MELODY) { renderMelody(ctx, mx, my); return }
        val cols = 9
        val rows = handler.type.windowSize / cols
        val gw = cols * SLOT; val gh = rows * SLOT
        gridX = width / 2 - gw / 2
        gridY = height / 2 - gh / 2 + 8
        val px1 = gridX - 16; val py1 = gridY - 40
        val px2 = gridX + gw + 16; val py2 = gridY + gh + 24
        glass(ctx, px1, py1, px2, py2)

        val label = when (type) {
            TerminalType.SELECT -> "Select all the §e$selectColor"
            TerminalType.STARTS_WITH -> "What starts with §e'$startLetter'"
            TerminalType.NUMBERS -> "Click in order"
            else -> "Correct all the panes"
        }
        ctx.text(font, "§b$label", px1 + 12, py1 + 10, -1)
        val t = (System.currentTimeMillis() - openedAt) / 1000.0
        val best = pbs[type.ordinal].let { if (it == Double.MAX_VALUE) "--" else "%.2fs".format(it) }
        ctx.text(font, "§7%.2fs   §7last §f%s   §7best §a%s   §7miss §c%d".format(t, if (lastMs == 0L) "--" else "%.2fs".format(lastMs / 1000.0), best, misses),
            px1 + 12, py1 + 22, -1)

        val solverOn = FishSettings.terminalSolverEnabled
        val sol = handler.solution
        for (i in 0 until handler.type.windowSize) {
            val sx = gridX + (i % cols) * SLOT
            val sy = gridY + (i / cols) * SLOT
            rr(ctx, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3, CELL_BG)
            ctx.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + 2, CELL_EDGE)

            var rubixN = 0
            if (solverOn && i in sol) {
                val col = when {
                    type == TerminalType.NUMBERS -> when (sol.indexOf(i)) {
                        0 -> FishSettings.terminalOrderColor1; 1 -> FishSettings.terminalOrderColor2; else -> FishSettings.terminalOrderColor3
                    }
                    type == TerminalType.SELECT -> FishSettings.terminalSelectColor
                    type == TerminalType.STARTS_WITH -> FishSettings.terminalStartsWithColor
                    type == TerminalType.RUBIX -> {
                        val needed = sol.count { it == i }
                        rubixN = if (needed < 3) needed else needed - 5
                        when (rubixN) {
                            1 -> FishSettings.terminalRubixColor; 2 -> FishSettings.terminalRubixColor2
                            -1 -> FishSettings.terminalRubixNeg1; else -> FishSettings.terminalRubixNeg2
                        }
                    }
                    else -> FishSettings.terminalHighlightColor
                }
                rr(ctx, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3, col)
            }
            if (i == flashWrong && System.currentTimeMillis() - flashAt < 250) {
                rr(ctx, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3, 0x99_FF3030.toInt())
            }

            val st = handler.items[i]
            if (st != null && !st.isEmpty) {
                ctx.item(st, sx + 3, sy + 3)
                ctx.itemDecorations(font, st, sx + 3, sy + 3)
            }
            if (FishSettings.terminalShowNumbers && rubixN != 0) ctx.text(font, rubixN.toString(), sx + 7, sy + 7, -1, true)
            else if (FishSettings.terminalShowNumbers && type == TerminalType.NUMBERS && i in sol) {
                ctx.text(font, (sol.indexOf(i) + 1).toString(), sx + 7, sy + 7, -1, true)
            }
            if (mx in sx until sx + SLOT && my in sy until sy + SLOT) rr(ctx, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3, HOVER)
        }
    }

    private fun renderMelody(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
        val cols = 9; val rows = 6
        val gw = cols * SLOT; val gh = rows * SLOT
        gridX = width / 2 - gw / 2
        gridY = height / 2 - gh / 2 + 8
        val px1 = gridX - 16; val py1 = gridY - 40
        val px2 = gridX + gw + 16; val py2 = gridY + gh + 24
        glass(ctx, px1, py1, px2, py2)
        ctx.text(font, "§dClick the button on time  §8— row ${(melRow + 1).coerceAtMost(4)}/4", px1 + 12, py1 + 10, -1)
        val t = (System.currentTimeMillis() - openedAt) / 1000.0
        val best = pbs[TerminalType.MELODY.ordinal].let { if (it == Double.MAX_VALUE) "--" else "%.2fs".format(it) }
        ctx.text(font, "§7%.2fs   §7best §a%s   §7miss §c%d".format(t, best, misses), px1 + 12, py1 + 22, -1)

        val marker = melodyMarkerCol()
        for (gr in 0 until rows) for (gc in 0 until cols) {
            val sx = gridX + gc * SLOT; val sy = gridY + gr * SLOT
            rr(ctx, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3, CELL_BG)
            val r = gr - 1
            if (r in 0..3) {
                when {
                    melDone[r] && gc in 1..5 -> rr(ctx, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3, 0x9955FF55.toInt())
                    r == melRow && gc == melTarget[r] -> rr(ctx, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3, FishSettings.terminalMelodyPointerColor and 0x66FFFFFF)
                    r == melRow && gc == marker -> rr(ctx, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3, FishSettings.terminalMelodyColor)
                    r > melRow && gc == melTarget[r] -> rr(ctx, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3, 0x3355FF55)
                    gc == 7 && r == melRow -> rr(ctx, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 3,
                        if (marker == melTarget[r]) FishSettings.terminalMelodyPointerColor else 0x33FFFFFF)
                }
            }
        }
    }

    private fun glass(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int) {
        rr(ctx, x1, y1, x2 - x1, y2 - y1, 8, PANEL_BG)
        runCatching { ctx.fillGradient(x1 + 2, y1 + 2, x2 - 2, y1 + (y2 - y1) / 2, GLASS_TOP, GLASS_BOT) }
        ctx.fill(x1 + 3, y1 + 1, x2 - 3, y1 + 2, EDGE_LIGHT)
        ctx.fill(x1 + 1, y1 + 3, x1 + 2, y2 - 3, EDGE_LIGHT)
        ctx.fill(x1 + 3, y2 - 2, x2 - 3, y2 - 1, EDGE_DARK)
        ctx.fill(x2 - 2, y1 + 3, x2 - 1, y2 - 3, EDGE_DARK)
    }

    /** Cheap rounded rect: body minus stepped corners. */
    private fun rr(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, r: Int, color: Int) {
        val rr = r.coerceAtMost(minOf(w, h) / 2)
        ctx.fill(x + rr, y, x + w - rr, y + h, color)
        ctx.fill(x, y + rr, x + rr, y + h - rr, color)
        ctx.fill(x + w - rr, y + rr, x + w, y + h - rr, color)
        var i = 0
        while (i < rr) {
            val inset = rr - Math.round(Math.sqrt((rr * rr - (rr - 1 - i) * (rr - 1 - i)).toDouble())).toInt()
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color)
            ctx.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color)
            i++
        }
    }

    // ── input ──────────────────────────────────────────────────────────────

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = click.x().toInt(); val my = click.y().toInt()
        if (mode == Mode.MENU) {
            for (rrc in menuRects) {
                if (mx in rrc[0]..(rrc[0] + rrc[2]) && my in rrc[1]..(rrc[1] + rrc[3])) {
                    when (val a = rrc[4]) {
                        8 -> startSim(SIMMABLE.random())
                        9 -> { pbs.fill(Double.MAX_VALUE); savePbs() }
                        else -> startSim(SIMMABLE[a])
                    }
                    return true
                }
            }
            return super.mouseClicked(click, doubled)
        }

        val col = (mx - gridX) / SLOT
        val row = (my - gridY) / SLOT

        if (type == TerminalType.MELODY) {
            val r = row - 1
            if (mx in gridX until gridX + 9 * SLOT && r == melRow && r in 0..3) {
                if (melodyMarkerCol() == melTarget[melRow]) {
                    melDone[melRow] = true
                    melRow++
                    melRowStart = System.currentTimeMillis()
                    if (melRow >= 4) finishSim()
                } else { misses++; flashAt = System.currentTimeMillis() }
            }
            return true
        }

        if (mx < gridX || my < gridY || col !in 0 until 9) return super.mouseClicked(click, doubled)
        val idx = row * 9 + col
        if (idx < 0 || idx >= handler.type.windowSize) return super.mouseClicked(click, doubled)
        val right = click.button() == 1

        if (handler.canClick(idx, right)) {
            consume(idx, right)
            handler.simulateClick(idx, right)
            handler.handleSlotUpdate(handler.type.windowSize - 1)
            if (handler.solution.isEmpty()) finishSim()
        } else {
            misses++; flashWrong = idx; flashAt = System.currentTimeMillis()
        }
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        when (input.key()) {
            GLFW.GLFW_KEY_ESCAPE -> { if (mode == Mode.SIM) { mode = Mode.MENU; return true }; onClose(); return true }
            GLFW.GLFW_KEY_R -> { if (mode == Mode.SIM) startSim(type); return true }
        }
        return super.keyPressed(input)
    }

    companion object {
        @JvmStatic
        fun open(arg: String?) {
            val type = arg?.lowercase()?.let { a ->
                TerminalType.entries.firstOrNull { it.name.lowercase().replace("_", "").startsWith(a.replace("_", "")) }
            }
            Minecraft.getInstance().setScreen(TermSimScreen(type))
        }
    }
}
