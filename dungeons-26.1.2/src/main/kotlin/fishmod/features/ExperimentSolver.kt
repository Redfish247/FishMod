package fishmod.features

import fishmod.compat.curScreen
import fishmod.utils.TextStrip
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

// Experimentation Table solver: highlights only, the player does every click.
object ExperimentSolver {

    private enum class Game { NONE, CHRONO, ULTRA, PAIRS }

    private var game = Game.NONE
    private var lastTitle = ""
    private val chronoOrder = mutableListOf<Int>()
    private var lastGlint = -1
    private val ultraOrder = sortedMapOf<Int, Int>()
    private var clicks = 0
    private var wasShowing = false
    private val pairs = HashMap<Int, String>()

    private const val NEXT = 0x9900FF00.toInt()
    private const val SECOND = 0x66FFFF00.toInt()
    private val PAIR_COLORS = intArrayOf(0x9955FF55.toInt(), 0x995555FF.toInt(), 0x99FF55FF.toInt(), 0x99FFAA00.toInt(), 0x9955FFFF.toInt(), 0x99FF5555.toInt())

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            FishDiag.guard("ExperimentSolver.1", "experiment solver tick failed") { tick(mc) }
        }
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, _, x, y ->
            if (game == Game.NONE || !FishSettings.experimentSolverEnabled) return@register
            val slot = DrawEvents.currentSlot ?: return@register
            if (slot.container is net.minecraft.world.entity.player.Inventory) return@register
            val c = colorFor(slot.index)
            if (c != 0) ctx.fill(x, y, x + 16, y + 16, c)
        }
    }

    @JvmStatic
    fun onSlotClicked(slotId: Int) {
        if (game == Game.CHRONO || game == Game.ULTRA) { if (slotId in 0..53 && !showing()) clicks++ }
    }

    private fun reset() {
        chronoOrder.clear(); lastGlint = -1; ultraOrder.clear(); clicks = 0; wasShowing = false; pairs.clear()
    }

    private fun items(): List<ItemStack>? {
        val s = Minecraft.getInstance().curScreen as? AbstractContainerScreen<*> ?: return null
        return s.menu.slots.filter { it.container !is net.minecraft.world.entity.player.Inventory }.map { it.item }
    }

    // Glowstone in slot 49 = "Remember the pattern!", clock = your turn
    private fun showing(): Boolean = items()?.getOrNull(49)?.`is`(Items.GLOWSTONE) == true

    private fun tick(mc: Minecraft) {
        val screen = mc.curScreen as? AbstractContainerScreen<*>
        val title = if (screen == null) "" else TextStrip.plain(screen.title.string)
        if (title != lastTitle) {
            lastTitle = title
            reset()
            game = when {
                !FishSettings.experimentSolverEnabled -> Game.NONE
                title.startsWith("Chronomatron (") -> Game.CHRONO
                title.startsWith("Ultrasequencer (") -> Game.ULTRA
                title.startsWith("Superpairs (") -> Game.PAIRS
                else -> Game.NONE
            }
        }
        if (game == Game.NONE) return
        val inv = items() ?: return
        when (game) {
            Game.CHRONO -> {
                val show = showing()
                if (show && !wasShowing) { clicks = 0; lastGlint = -1; chronoReplay = 0 }
                wasShowing = show
                if (!show) return
                // each round replays the whole pattern; append only past the known length
                val glint = (0 until minOf(inv.size, 45)).firstOrNull { inv[it].hasFoil() } ?: run { lastGlint = -1; return }
                if (glint == lastGlint) return
                lastGlint = glint
                val replayed = chronoReplay++
                if (replayed >= chronoOrder.size) chronoOrder.add(glint)
            }
            Game.ULTRA -> {
                val show = showing()
                if (show && !wasShowing) { clicks = 0; ultraOrder.clear() }
                wasShowing = show
                if (!show) return
                for (i in 0 until minOf(inv.size, 45)) {
                    val st = inv[i]
                    if (!st.isEmpty && st.item.toString().endsWith("_dye")) ultraOrder[st.count] = i
                }
            }
            Game.PAIRS -> for (i in 0 until minOf(inv.size, 45)) {
                val st = inv[i]
                if (st.isEmpty || st.item.toString().endsWith("stained_glass") || st.item.toString().endsWith("glass_pane")) continue
                pairs[i] = TextStrip.plain(st.hoverName.string)
            }
            Game.NONE -> {}
        }
    }

    private var chronoReplay = 0

    private fun colorFor(idx: Int): Int = when (game) {
        Game.CHRONO -> if (showing()) 0 else when (idx) {
            chronoOrder.getOrNull(clicks) -> NEXT
            chronoOrder.getOrNull(clicks + 1) -> SECOND
            else -> 0
        }
        Game.ULTRA -> if (showing()) 0 else when (idx) {
            ultraOrder[clicks + 1] -> NEXT
            ultraOrder[clicks + 2] -> SECOND
            else -> 0
        }
        Game.PAIRS -> {
            val name = pairs[idx]
            if (name == null) 0 else {
                val names = pairs.values.groupingBy { it }.eachCount().filter { it.value >= 2 }.keys.sorted()
                val n = names.indexOf(name)
                if (n < 0) 0 else PAIR_COLORS[n % PAIR_COLORS.size]
            }
        }
        Game.NONE -> 0
    }
}
