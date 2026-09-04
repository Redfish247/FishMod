package fishmod.features.dungeon.f7.terminal

import fishmod.utils.config.FishConfig
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.SimpleContainer
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW
import kotlin.random.Random

/**
 * `/fmtermsim [type]` — a Hypixel-accurate F7 P3 terminal practice board. Each terminal is built with
 * the real item layout Hypixel uses, backed by a local chest menu, and the live [TerminalSolver] is
 * pointed at it — so the actual solver highlight, click-blocking, tooltip hiding and colour settings
 * all apply exactly as they would in a real run.
 *
 *  keys:  1-6 switch terminal · R re-roll · Esc exit
 */
class TermSimScreen private constructor(
    private val menu: TermSimMenu,
    private var type: TerminalType,
) : ContainerScreen(menu, Minecraft.getInstance().player!!.inventory, Component.literal("Terminal Simulator")) {

    private val box: SimpleContainer get() = menu.box
    private lateinit var handler: TerminalHandler
    private var built = false

    private var startLetter = "A"
    private var selColor = "red"
    private var openedAt = 0L
    private var startedAt = 0L   // set on the first click of a round — that's when the clock starts
    private var lastMs = 0L
    private var misses = 0
    private var solvedCount = 0

    private var melRow = 1
    private var melTarget = 1
    private var melLime = 1
    private var melDir = 1
    private var melTick = 0

    private val pbs = DoubleArray(TerminalType.entries.size) { Double.MAX_VALUE }

    override fun init() {
        super.init()
        if (!built) { built = true; loadPbs(); newRound(type) }
    }

    override fun removed() {
        TerminalSolver.setSimTerminal(null)
        super.removed()
    }

    private fun loadPbs() = FishSettings.termSimPbs.split(',').forEachIndexed { i, s ->
        if (i < pbs.size) s.trim().toDoubleOrNull()?.let { pbs[i] = it }
    }

    private fun savePbs() {
        FishSettings.termSimPbs = pbs.joinToString(",") { if (it == Double.MAX_VALUE) "" else "%.3f".format(it) }
        runCatching { FishConfig.manager.save() }
    }

    private fun newRound(t: TerminalType) {
        type = t
        for (i in 0 until box.containerSize) box.setItem(i, ItemStack.EMPTY)
        openedAt = System.currentTimeMillis()
        startedAt = 0L
        misses = 0

        // pick per-type params first so the handler is built before generation (melody's generator calls sync() and needs handler set)
        if (t == TerminalType.STARTS_WITH) startLetter = "ABCDGMNRST"[Random.nextInt(10)].toString()
        if (t == TerminalType.SELECT) selColor = SELECT_COLORS.random()
        handler = when (t) {
            TerminalType.PANES -> PanesHandler()
            TerminalType.NUMBERS -> NumbersHandler()
            TerminalType.RUBIX -> RubixHandler()
            TerminalType.STARTS_WITH -> StartsWithHandler(startLetter)
            TerminalType.SELECT -> SelectAllHandler(selColor)
            TerminalType.MELODY -> MelodyHandler()
        }

        when (t) {
            TerminalType.PANES -> genPanes()
            TerminalType.NUMBERS -> genNumbers()
            TerminalType.RUBIX -> genRubix()
            TerminalType.STARTS_WITH -> genStartsWith()
            TerminalType.SELECT -> genSelect()
            TerminalType.MELODY -> genMelody()
        }
        sync()
    }

    /** Copy the board into the handler and re-point the solver at it. */
    private fun sync() {
        if (!::handler.isInitialized) return
        for (i in 0 until type.windowSize) handler.items[i] = box.getItem(i)
        handler.handleSlotUpdate(type.windowSize - 1)
        TerminalSolver.setSimTerminal(handler)
    }

    private fun win() {
        lastMs = System.currentTimeMillis() - (if (startedAt != 0L) startedAt else openedAt)
        val o = type.ordinal
        if (lastMs / 1000.0 < pbs[o]) { pbs[o] = lastMs / 1000.0; savePbs() }
        solvedCount++
        if (fishmod.utils.config.values.FishSettings.terminalSolverSound) {
            fishmod.utils.sound.SoundManager.ping("termSimSolved", 0)
        }
        newRound(type)
    }

    private fun ping() = Minecraft.getInstance().player?.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f)

    private fun genPanes() {
        for (i in 0 until 45) box.setItem(i, ItemStack(BLACK))
        for (row in 1..3) for (col in 2..6) {
            box.setItem(row * 9 + col, ItemStack(if (Random.nextFloat() > 0.75f) LIME else RED))
        }
        if ((10..34).none { box.getItem(it).`is`(RED) }) box.setItem(20, ItemStack(RED))
    }

    private fun genNumbers() {
        for (i in 0 until 36) box.setItem(i, ItemStack(BLACK))
        val nums = (1..14).shuffled().iterator()
        for (row in 1..2) for (col in 1..7) {
            box.setItem(row * 9 + col, ItemStack(RED).also { it.count = nums.next() })
        }
    }

    private fun genRubix() {
        for (i in 0 until 45) box.setItem(i, ItemStack(BLACK))
        for (c in RUBIX_CELLS) box.setItem(c, ItemStack(RUBIX_PANES.random()))
        if (RUBIX_CELLS.all { box.getItem(it).`is`(box.getItem(RUBIX_CELLS[0]).item) }) {
            box.setItem(RUBIX_CELLS[1], ItemStack(RUBIX_PANES[(RUBIX_PANES.indexOf(box.getItem(RUBIX_CELLS[0]).item) + 2) % 5]))
        }
    }

    private fun genStartsWith() {
        for (i in 0 until 45) box.setItem(i, ItemStack(BLACK))
        val hits = WORDS.filter { it.startsWith(startLetter, true) }
        var placed = 0
        for (row in 1..3) for (col in 1..7) {
            val i = row * 9 + col
            val word = if ((placed < 3 || Random.nextFloat() > 0.7f) && hits.isNotEmpty()) { placed++; hits.random() } else WORDS.random()
            box.setItem(i, ItemStack(Items.PAPER).also { it.set(DataComponents.CUSTOM_NAME, Component.literal(word)) })
        }
    }

    private fun genSelect() {
        for (i in 0 until 54) box.setItem(i, ItemStack(BLACK))
        val target = colorItems(selColor)
        val others = SELECT_COLORS.filter { it != selColor }
        var placed = 0
        for (row in 1..4) for (col in 1..7) {
            val i = row * 9 + col
            val correct = placed < 3 || Random.nextFloat() > 0.72f
            if (correct) { placed++; box.setItem(i, ItemStack(target.random())) }
            else box.setItem(i, ItemStack(colorItems(others.random()).random()))
        }
    }

    private fun genMelody() {
        melRow = 1; melTarget = 1 + Random.nextInt(5); melLime = 1; melDir = 1; melTick = 0
        rebuildMelody()
    }

    private fun rebuildMelody() {
        for (i in 0 until 54) {
            val col = i % 9; val row = i / 9
            box.setItem(i, ItemStack(when {
                col == melTarget && row !in 1..4 -> MAGENTA
                col == melLime && row == melRow -> LIME
                col in 1..5 && row == melRow -> RED
                col == 7 && row == melRow -> Items.LIME_TERRACOTTA
                col == 7 && row in 1..4 -> Items.RED_TERRACOTTA
                col in 1..5 && row in 1..4 -> WHITE
                else -> BLACK
            }))
        }
        sync()
    }

    override fun containerTick() {
        super.containerTick()
        if (!built || type != TerminalType.MELODY) return
        if (melTick++ % 6 != 0) return
        melLime += melDir
        if (melLime <= 1 || melLime >= 5) melDir = -melDir
        rebuildMelody()
    }

    override fun slotClicked(slot: Slot, slotId: Int, mouseButton: Int, input: ContainerInput) {
        if (slot.container !== box) return  // ignore player inventory
        simClick(slot.index, mouseButton)
    }

    /** Apply the Hypixel effect for a click on board slot [idx]. Also called from the Custom GUI path. */
    fun simClick(idx: Int, button: Int) {
        if (idx < 0 || idx >= type.windowSize) return
        if (startedAt == 0L) startedAt = System.currentTimeMillis()   // clock starts on the first click
        val right = button == 1
        val st = box.getItem(idx)

        when (type) {
            TerminalType.PANES -> {
                if (!st.`is`(RED)) { misses++; return }
                box.setItem(idx, ItemStack(LIME)); ping(); sync()
                if ((0 until 45).none { box.getItem(it).`is`(RED) }) win()
            }
            TerminalType.NUMBERS -> {
                val lowest = (0 until 36).filter { box.getItem(it).`is`(RED) }.minByOrNull { box.getItem(it).count }
                if (idx != lowest) { misses++; return }
                box.setItem(idx, ItemStack(LIME, st.count)); ping(); sync()
                if ((0 until 36).none { box.getItem(it).`is`(RED) }) win()
            }
            TerminalType.RUBIX -> {
                val cur = RUBIX_PANES.indexOfFirst { st.`is`(it) }
                if (cur < 0) { misses++; return }
                if (!handler.canClick(idx, right)) { misses++; return }
                box.setItem(idx, ItemStack(RUBIX_PANES[(cur + if (right) 4 else 1) % 5])); ping(); sync()
                val first = box.getItem(RUBIX_CELLS[0]).item
                if (RUBIX_CELLS.all { box.getItem(it).`is`(first) }) win()
            }
            TerminalType.STARTS_WITH -> {
                if (st.hasFoil() || !plain(st).startsWith(startLetter, true)) { misses++; return }
                st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true); ping(); sync()
                if ((0 until 45).none { val s = box.getItem(it); !s.hasFoil() && plain(s).startsWith(startLetter, true) && s.`is`(Items.PAPER) }) win()
            }
            TerminalType.SELECT -> {
                if (st.hasFoil() || !selectMatch(st)) { misses++; return }
                st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true); ping(); sync()
                if ((0 until 54).none { val s = box.getItem(it); !s.hasFoil() && selectMatch(s) }) win()
            }
            TerminalType.MELODY -> {
                if (idx % 9 != 7 || idx / 9 != melRow || melLime != melTarget) { misses++; return }
                melRow++
                melTarget = 1 + Random.nextInt(5)
                ping()
                if (melRow > 4) win() else rebuildMelody()
            }
        }
    }

    private fun selectMatch(s: ItemStack): Boolean {
        if (s.isEmpty || s.`is`(BLACK) || s.`is`(LIME) || s.`is`(RED)) return false
        val path = BuiltInRegistries.ITEM.getKey(s.item).path
        return path.contains(selColor) && (selColor == "light_blue" || !path.contains("light_blue"))
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val k = input.key()
        if (k in GLFW.GLFW_KEY_1..GLFW.GLFW_KEY_6) {
            newRound(TerminalType.entries[k - GLFW.GLFW_KEY_1]); return true
        }
        if (k == GLFW.GLFW_KEY_R) { newRound(type); return true }
        return super.keyPressed(input)
    }

    // called from HandledScreenMixin
    fun overlay(ctx: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        val t = if (startedAt == 0L) 0.0 else (System.currentTimeMillis() - startedAt) / 1000.0
        val best = pbs[type.ordinal].let { if (it == Double.MAX_VALUE) "--" else "%.2fs".format(it) }
        val last = if (lastMs == 0L) "--" else "%.2fs".format(lastMs / 1000.0)
        ctx.text(mc.font, "§e${hyTitle(type, startLetter, selColor)}", 6, 6, -1, true)
        ctx.text(mc.font, "§b%.2fs   §7last §f%s   §7best §a%s   §7miss §c%d   §7solved §f%d".format(t, last, best, misses, solvedCount), 6, 17, -1, true)
        ctx.text(mc.font, "§81-6 switch · R re-roll · Esc exit", 6, 28, -1, true)
    }

    companion object {
        private val BLACK = Items.BLACK_STAINED_GLASS_PANE
        private val RED = Items.RED_STAINED_GLASS_PANE
        private val LIME = Items.LIME_STAINED_GLASS_PANE
        private val WHITE = Items.WHITE_STAINED_GLASS_PANE
        private val MAGENTA = Items.MAGENTA_STAINED_GLASS_PANE
        private val RUBIX_PANES = listOf(
            Items.ORANGE_STAINED_GLASS_PANE, Items.YELLOW_STAINED_GLASS_PANE,
            Items.GREEN_STAINED_GLASS_PANE, Items.BLUE_STAINED_GLASS_PANE, Items.RED_STAINED_GLASS_PANE,
        )
        private val RUBIX_CELLS = intArrayOf(12, 13, 14, 21, 22, 23, 30, 31, 32)
        private val SELECT_COLORS = listOf(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black",
        )
        private val SELECT_MATS = listOf("wool", "terracotta", "concrete")
        private val WORDS = listOf(
            "Apple", "Arrow", "Axe", "Anvil", "Bread", "Bow", "Boat", "Bone", "Cake", "Chest", "Clock", "Coal",
            "Diamond", "Dirt", "Door", "Egg", "Emerald", "Feather", "Fish", "Flint", "Gold", "Grass", "Glass",
            "Melon", "Milk", "Map", "Netherrack", "Nugget", "Rail", "Redstone", "Rose", "Rod",
            "Saddle", "Sand", "Stick", "Stone", "String", "Sugar", "Torch", "Trident",
        )

        private fun colorItems(color: String): List<net.minecraft.world.item.Item> =
            SELECT_MATS.mapNotNull { BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", "${color}_$it")) }
                .ifEmpty { listOf(Items.WHITE_WOOL) }

        private fun plain(s: ItemStack): String = s.hoverName.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim()

        private fun hyTitle(t: TerminalType, letter: String, color: String): String = when (t) {
            TerminalType.PANES -> "Correct all the panes!"
            TerminalType.NUMBERS -> "Click in order!"
            TerminalType.RUBIX -> "Change all to same color!"
            TerminalType.STARTS_WITH -> "What starts with: '$letter'?"
            TerminalType.SELECT -> "Select all the ${color.replace("_", " ").uppercase()} items!"
            TerminalType.MELODY -> "Click the button on time!"
        }

        @JvmStatic
        fun open(arg: String?) {
            val mc = Minecraft.getInstance()
            if (mc.player == null) return
            val type = arg?.lowercase()?.let { a ->
                TerminalType.entries.firstOrNull { it.name.lowercase().replace("_", "").startsWith(a.replace("_", "")) }
            } ?: TerminalType.entries.random()
            val rows = type.windowSize / 9
            val menu = TermSimMenu(rows, mc.player!!.inventory, SimpleContainer(rows * 9))
            mc.setScreen(TermSimScreen(menu, type))
        }
    }
}
