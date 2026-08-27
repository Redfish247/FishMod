package fishmod.features.dungeon.f7.terminal

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * F7 P3 terminal solve logic, ported 1:1 from Odin's terminalhandler package. The 1.8.9
 * `metadata` / `Item.getIdFromItem` checks are translated to modern item identities:
 *   - "stained glass pane, meta N"  -> the matching `*_STAINED_GLASS_PANE` item
 *   - "meta 15" (black pane)         -> `BLACK_STAINED_GLASS_PANE`
 *   - "stained clay, meta 5" (melody green) -> `LIME_TERRACOTTA`
 *   - stack size / count             -> `ItemStack.count`
 *   - `isItemEnchanted`              -> `ItemStack.hasFoil()`
 */
enum class TerminalType(val windowPrefix: String, val windowSize: Int) {
    PANES("Correct all the panes!", 45),
    RUBIX("Change all to same color!", 45),
    NUMBERS("Click in order!", 36),
    STARTS_WITH("What starts with:", 45),
    SELECT("Select all the", 54),
    MELODY("Click the button on time!", 54),
}

private val RED_PANE = Items.RED_STAINED_GLASS_PANE
private val BLACK_PANE = Items.BLACK_STAINED_GLASS_PANE
private val LIME_PANE = Items.LIME_STAINED_GLASS_PANE
private val MAGENTA_PANE = Items.MAGENTA_STAINED_GLASS_PANE
private val LIME_TERRACOTTA = Items.LIME_TERRACOTTA

/** [orange, yellow, green, blue, red] — Odin's `rubixColorOrder` (1.8 meta 1/4/13/11/14). */
private val RUBIX_ORDER: List<Item> = listOf(
    Items.ORANGE_STAINED_GLASS_PANE, Items.YELLOW_STAINED_GLASS_PANE,
    Items.GREEN_STAINED_GLASS_PANE, Items.BLUE_STAINED_GLASS_PANE, Items.RED_STAINED_GLASS_PANE,
)

private fun ItemStack?.isPane(): Boolean =
    this != null && !isEmpty && BuiltInRegistries.ITEM.getKey(item).path.endsWith("stained_glass_pane")

abstract class TerminalHandler(val type: TerminalType) {
    val solution = java.util.concurrent.CopyOnWriteArrayList<Int>()
    val items: Array<ItemStack?> = arrayOfNulls(type.windowSize)
    val timeOpened = System.currentTimeMillis()
    @Volatile var isClicked = false

    /** Called on every set-slot; return true when the solution should be recomputed + broadcast. */
    abstract fun handleSlotUpdate(slot: Int): Boolean

    /** Optimistic local update so the overlay reacts before the server round-trips. */
    open fun simulateClick(slotIndex: Int, right: Boolean) {}

    /** MELODY has fixed clickable columns; other terms allow any slot currently in [solution]. */
    fun canClick(slotIndex: Int, right: Boolean): Boolean {
        if (type == TerminalType.MELODY) return slotIndex == 16 || slotIndex == 25 || slotIndex == 34 || slotIndex == 43
        if (slotIndex !in solution) return false
        if (type == TerminalType.NUMBERS && slotIndex != solution.firstOrNull()) return false
        if (type == TerminalType.RUBIX) {
            val needed = solution.count { it == slotIndex }
            if ((needed < 3 && right) || ((needed == 3 || needed == 4) && !right)) return false
        }
        return true
    }
}

class PanesHandler : TerminalHandler(TerminalType.PANES) {
    override fun handleSlotUpdate(slot: Int): Boolean {
        if (slot != type.windowSize - 1) return false
        solution.clear()
        solution.addAll(items.mapIndexedNotNull { i, it -> if (it?.`is`(RED_PANE) == true) i else null })
        return true
    }
    override fun simulateClick(slotIndex: Int, right: Boolean) { solution.remove(slotIndex) }
}

class NumbersHandler : TerminalHandler(TerminalType.NUMBERS) {
    override fun handleSlotUpdate(slot: Int): Boolean {
        if (slot != type.windowSize - 1) return false
        solution.clear()
        solution.addAll(
            items.mapIndexedNotNull { i, it -> if (it?.`is`(RED_PANE) == true) i else null }
                .sortedBy { items[it]?.count ?: 0 }
        )
        return true
    }
    override fun simulateClick(slotIndex: Int, right: Boolean) {
        if (solution.indexOf(slotIndex) == 0) solution.removeAt(0)
    }
}

class StartsWithHandler(private val letter: String) : TerminalHandler(TerminalType.STARTS_WITH) {
    override fun handleSlotUpdate(slot: Int): Boolean {
        if (slot != type.windowSize - 1) return false
        solution.clear()
        solution.addAll(items.mapIndexedNotNull { i, it ->
            if (it != null && !it.isEmpty && !it.hasFoil() &&
                it.hoverName.string.replace(Regex("§."), "").trim().startsWith(letter, ignoreCase = true)
            ) i else null
        })
        return true
    }
    override fun simulateClick(slotIndex: Int, right: Boolean) { solution.remove(slotIndex) }
}

class SelectAllHandler(private val colorPath: String) : TerminalHandler(TerminalType.SELECT) {
    override fun handleSlotUpdate(slot: Int): Boolean {
        if (slot != type.windowSize - 1) return false
        solution.clear()
        solution.addAll(items.mapIndexedNotNull { i, it ->
            if (it != null && !it.isEmpty && !it.hasFoil() && !it.isPane()) {
                val path = BuiltInRegistries.ITEM.getKey(it.item).path
                val match = path.contains(colorPath) &&
                    (colorPath == "light_blue" || !path.contains("light_blue"))
                if (match) i else null
            } else null
        })
        return true
    }
    override fun simulateClick(slotIndex: Int, right: Boolean) { solution.remove(slotIndex) }
}

class RubixHandler : TerminalHandler(TerminalType.RUBIX) {
    private var lastColorIdx: Int? = null

    override fun handleSlotUpdate(slot: Int): Boolean {
        if (items.last() == null || slot != type.windowSize - 1) return false
        solution.clear()
        solution.addAll(solve())
        return true
    }

    override fun simulateClick(slotIndex: Int, right: Boolean) {
        if (slotIndex !in solution) return
        if (right) solution.add(slotIndex) else solution.remove(slotIndex)
    }

    private fun dist(pane: Int, target: Int): Int =
        if (pane > target) (target + RUBIX_ORDER.size) - pane else target - pane

    private fun realSize(list: List<Int>): Int {
        var size = 0
        list.distinct().forEach { p ->
            val c = list.count { it == p }
            size += if (c >= 3) 5 - c else c
        }
        return size
    }

    private fun colorIdxOf(stack: ItemStack?): Int = RUBIX_ORDER.indexOfFirst { stack?.`is`(it) == true }

    private fun solve(): List<Int> {
        val panes = items.withIndex().filter { (_, s) -> s.isPane() && s?.`is`(BLACK_PANE) == false }
        var best: List<Int> = List(100) { it }
        val candidates = if (lastColorIdx != null) listOf(lastColorIdx!!) else RUBIX_ORDER.indices.toList()
        for (target in candidates) {
            val attempt = panes.flatMap { (idx, stack) ->
                val ci = colorIdxOf(stack)
                if (ci != target && ci >= 0) List(dist(ci, target)) { idx } else emptyList()
            }
            if (realSize(attempt) < realSize(best)) {
                best = attempt
                if (lastColorIdx == null) lastColorIdx = target
            }
        }
        return best
    }
}

class MelodyHandler : TerminalHandler(TerminalType.MELODY) {
    /** Row (0-based) the green clay marker currently sits on — MelodyMessage reads this for progress %. */
    @Volatile var greenClayRow: Int = -1
        private set

    override fun handleSlotUpdate(slot: Int): Boolean {
        solution.clear()
        val greenPane = items.indexOfLast { it?.`is`(LIME_PANE) == true }.takeIf { it != -1 } ?: return true
        val magentaPane = items.indexOfFirst { it?.`is`(MAGENTA_PANE) == true }.takeIf { it != -1 } ?: return true
        val greenClay = items.indexOfLast { it?.`is`(LIME_TERRACOTTA) == true }.takeIf { it != -1 } ?: return true
        greenClayRow = greenClay / 9
        solution.addAll(items.mapIndexedNotNull { i, it ->
            when {
                i == greenPane || it?.`is`(MAGENTA_PANE) == true -> i
                i == greenClay && greenPane % 9 == magentaPane % 9 -> i
                else -> null
            }
        })
        return true
    }
}
