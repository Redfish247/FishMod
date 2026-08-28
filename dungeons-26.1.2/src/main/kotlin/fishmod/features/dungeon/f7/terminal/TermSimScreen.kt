package fishmod.features.dungeon.f7.terminal

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
import kotlin.math.max
import kotlin.random.Random

private const val SLOT = 18
private const val BASE_TINT = 0x28_0A0A12
private const val PANEL_BG = 0x44_1B2130
private const val GLASS_TOP = 0x26_FFFFFF
private const val GLASS_BOT = 0x06_FFFFFF
private const val EDGE_LIGHT = 0x55_FFFFFF
private const val EDGE_DARK = 0x33_000000
private const val CELL_BG = 0x30_10121C
private const val HOVER = 0x28_FFFFFF

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

/**
 * `/fmtermsim` practice board for the F7 P3 terminals — Panes, Numbers, Select All and Starts With.
 * Reuses the real [TerminalHandler] solvers, so the solution highlight matches what you'd see live.
 * Rubix and Melody aren't simulated (multi-click / harp timing).
 */
class TermSimScreen(private val forced: TerminalType?) : Screen(Component.literal("Term Sim")) {

    private lateinit var handler: TerminalHandler
    private lateinit var type: TerminalType
    private var selectColor = "red"
    private var startLetter = "A"

    private var openedAt = 0L
    private var lastMs = 0L
    private var bestMs = Long.MAX_VALUE
    private var solved = 0
    private var misses = 0
    private var flashWrong = -1
    private var flashAt = 0L

    private var gridX = 0
    private var gridY = 0

    private val supported = listOf(TerminalType.PANES, TerminalType.NUMBERS, TerminalType.SELECT, TerminalType.STARTS_WITH)

    override fun init() { newRound() }
    override fun isPauseScreen() = false
    override fun extractBackground(ctx: GuiGraphicsExtractor, mx: Int, my: Int, d: Float) {}
    override fun extractTransparentBackground(ctx: GuiGraphicsExtractor) {}

    private fun newRound() {
        type = forced ?: supported.random()
        handler = when (type) {
            TerminalType.PANES -> PanesHandler()
            TerminalType.NUMBERS -> NumbersHandler()
            TerminalType.SELECT -> { selectColor = DYES.random().second; SelectAllHandler(selectColor) }
            TerminalType.STARTS_WITH -> { startLetter = ('A' + Random.nextInt(8)).toString(); StartsWithHandler(startLetter) }
            else -> PanesHandler()
        }
        generate()
        handler.handleSlotUpdate(handler.type.windowSize - 1)
        openedAt = System.currentTimeMillis()
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
                val slots = (0 until n).shuffled().take(count)
                slots.forEachIndexed { k, s -> handler.items[s] = ItemStack(RED_PANE, k + 1) }
            }
            TerminalType.SELECT -> {
                val fill = (0 until n).shuffled().take(20 + Random.nextInt(12))
                val targetItem = DYES.first { it.second == selectColor }.first
                for ((k, s) in fill.withIndex()) {
                    handler.items[s] = if (k < 5) ItemStack(targetItem) else ItemStack(DYES.random().first)
                }
            }
            TerminalType.STARTS_WITH -> {
                val slots = (0 until n).shuffled().take(14 + Random.nextInt(6))
                val hits = WORDS.filter { it.startsWith(startLetter, true) }
                for ((k, s) in slots.withIndex()) {
                    val word = if (k < 3 && hits.isNotEmpty()) hits.random() else WORDS.random()
                    handler.items[s] = ItemStack(Items.PAPER).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(word)) }
                }
            }
            else -> {}
        }
    }

    /** What the slot becomes after a correct click, so the recompute drops it from the solution. */
    private fun consume(idx: Int) {
        handler.items[idx] = when (type) {
            TerminalType.PANES -> ItemStack(GREEN_PANE)
            TerminalType.SELECT -> ItemStack(GREEN_PANE)
            else -> null
        }
    }

    // ── render ─────────────────────────────────────────────────────────────

    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        runCatching { ctx.blurBeforeThisStratum() }
        runCatching { ctx.nextStratum() }
        ctx.fill(0, 0, width, height, BASE_TINT)

        val cols = 9
        val rows = handler.type.windowSize / cols
        val gw = cols * SLOT
        val gh = rows * SLOT
        gridX = width / 2 - gw / 2
        gridY = height / 2 - gh / 2 + 6

        val px1 = gridX - 14; val py1 = gridY - 34
        val px2 = gridX + gw + 14; val py2 = gridY + gh + 34
        glass(ctx, px1, py1, px2, py2)

        val label = when (type) {
            TerminalType.SELECT -> "Select all the §e$selectColor"
            TerminalType.STARTS_WITH -> "What starts with: §e'$startLetter'"
            TerminalType.NUMBERS -> "Click in order"
            else -> "Correct all the panes"
        }
        ctx.text(font, "§fTerm Sim  §7— §b$label", px1 + 12, py1 + 8, -1)
        val t = (System.currentTimeMillis() - openedAt) / 1000.0
        val best = if (bestMs == Long.MAX_VALUE) "—" else "%.2fs".format(bestMs / 1000.0)
        val lastS = if (lastMs == 0L) "—" else "%.2fs".format(lastMs / 1000.0)
        ctx.text(font, "§7time §f%.2fs   §7last §f%s   §7best §a%s   §7solved §f%d   §7miss §c%d".format(t, lastS, best, solved, misses),
            px1 + 12, py2 - 14, -1)

        val solverOn = FishSettings.terminalSolverEnabled
        val sol = handler.solution
        for (i in 0 until handler.type.windowSize) {
            val sx = gridX + (i % cols) * SLOT
            val sy = gridY + (i / cols) * SLOT
            ctx.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, CELL_BG)

            if (solverOn && i in sol) {
                val col = if (type == TerminalType.NUMBERS && i == sol.firstOrNull()) FishSettings.terminalOrderColor1
                    else FishSettings.terminalHighlightColor
                ctx.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, col)
            }
            if (i == flashWrong && System.currentTimeMillis() - flashAt < 250) {
                ctx.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, 0x99_FF3030.toInt())
            }

            val st = handler.items[i]
            if (st != null && !st.isEmpty) {
                ctx.item(st, sx + 1, sy + 1)
                ctx.itemDecorations(font, st, sx + 1, sy + 1)
            }
            if (mouseX in sx until sx + SLOT && mouseY in sy until sy + SLOT) {
                ctx.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, HOVER)
            }
        }
    }

    private fun glass(ctx: GuiGraphicsExtractor, x1: Int, y1: Int, x2: Int, y2: Int) {
        ctx.fill(x1, y1, x2, y2, PANEL_BG)
        runCatching { ctx.fillGradient(x1, y1, x2, y1 + (y2 - y1) / 2, GLASS_TOP, GLASS_BOT) }
        ctx.fill(x1, y1, x2, y1 + 1, EDGE_LIGHT)
        ctx.fill(x1, y1, x1 + 1, y2, EDGE_LIGHT)
        ctx.fill(x1, y2 - 1, x2, y2, EDGE_DARK)
        ctx.fill(x2 - 1, y1, x2, y2, EDGE_DARK)
    }

    // ── input ──────────────────────────────────────────────────────────────

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = click.x().toInt(); val my = click.y().toInt()
        val cols = 9
        val col = (mx - gridX) / SLOT
        val row = (my - gridY) / SLOT
        if (mx < gridX || my < gridY || col !in 0 until cols) return super.mouseClicked(click, doubled)
        val idx = row * cols + col
        if (idx < 0 || idx >= handler.type.windowSize) return super.mouseClicked(click, doubled)
        val right = click.button() == 1

        if (handler.canClick(idx, right)) {
            consume(idx)
            handler.simulateClick(idx, right)
            handler.handleSlotUpdate(handler.type.windowSize - 1)
            if (handler.solution.isEmpty()) {
                lastMs = System.currentTimeMillis() - openedAt
                if (lastMs < bestMs) bestMs = lastMs
                solved++
                newRound()
            }
        } else {
            misses++
            flashWrong = idx
            flashAt = System.currentTimeMillis()
        }
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        when (input.key()) {
            GLFW.GLFW_KEY_ESCAPE -> { onClose(); return true }
            GLFW.GLFW_KEY_R -> { newRound(); return true }
        }
        return super.keyPressed(input)
    }

    companion object {
        @JvmStatic
        fun open(arg: String?) {
            val type = arg?.lowercase()?.let { a ->
                TerminalType.entries.firstOrNull { it.name.lowercase().startsWith(a) || it.name.lowercase().replace("_", "").startsWith(a) }
            }
            Minecraft.getInstance().setScreen(TermSimScreen(type))
        }
    }
}
