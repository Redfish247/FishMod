package fishmod.features.dungeon.f7.terminal

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * F7 P3 terminal solve logic. 1.8 `metadata` / item-id checks are translated to modern item
 * identities (`*_STAINED_GLASS_PANE`, `LIME_TERRACOTTA`, `ItemStack.count`, `ItemStack.hasFoil()`).
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

/** RUBIX cycle order: orange, yellow, green, blue, red (1.8 meta 1/4/13/11/14). */
private val RUBIX_ORDER: List<Item> = listOf(
    Items.ORANGE_STAINED_GLASS_PANE, Items.YELLOW_STAINED_GLASS_PANE,
    Items.GREEN_STAINED_GLASS_PANE, Items.BLUE_STAINED_GLASS_PANE, Items.RED_STAINED_GLASS_PANE,
)

private fun ItemStack?.isPane(): Boolean =
    this != null && !isEmpty && BuiltInRegistries.ITEM.getKey(item).path.endsWith("stained_glass_pane")

/** True when a glint-override component is present (whether it's true *or* false). */
private fun ItemStack.hasGlintOverride(): Boolean =
    !isEmpty && get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) != null

/** Registry items that carry a glint-override in their default components (enchanted golden apple,
 *  …) plus the plain golden apple. STARTS_WITH keeps these as valid answers even though they glint. */
private val ENCHANT_OVERRIDES: Set<Item> = buildSet {
    runCatching {
        for (item in BuiltInRegistries.ITEM)
            if (item.components().has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) add(item)
    }
    add(Items.GOLDEN_APPLE)
    add(Items.ENCHANTED_GOLDEN_APPLE)
}

abstract class TerminalHandler(val type: TerminalType) {
    val solution = java.util.concurrent.CopyOnWriteArrayList<Int>()
    val items: Array<ItemStack?> = arrayOfNulls(type.windowSize)
    val timeOpened = System.currentTimeMillis()

    /**
     * Recompute [solution] purely from the current [items] board: state is derived from the GUI's
     * own slot contents, never from click events, so a laggy / dropped click just leaves the slot
     * highlighted until the server actually updates it.
     */
    abstract fun handleSlotUpdate(slot: Int): Boolean

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
}

class StartsWithHandler(private val letter: String) : TerminalHandler(TerminalType.STARTS_WITH) {
    override fun handleSlotUpdate(slot: Int): Boolean {
        if (slot != type.windowSize - 1) return false
        solution.clear()
        // board items whose name starts with the letter and aren't glinting (except natural-glint overrides like enchanted golden apple)
        solution.addAll(items.mapIndexedNotNull { i, it ->
            if (it == null || it.isEmpty || it.isPane()) return@mapIndexedNotNull null
            if (it.hasGlintOverride() && it.item !in ENCHANT_OVERRIDES) return@mapIndexedNotNull null
            val name = it.hoverName.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim()
            if (name.startsWith(letter, ignoreCase = true)) i else null
        })
        return true
    }
}

class SelectAllHandler(colorName: String) : TerminalHandler(TerminalType.SELECT) {
    /** Matched against the item's display name, with the Hypixel/1.8 dye synonyms
     *  (ink=black, lapis=blue, rose=red, cactus=green, dandelion=yellow, …). */
    private val validPrefixes: Set<String> = when (colorName.lowercase()) {
        "black"                -> setOf("black", "ink")
        "blue"                 -> setOf("blue", "lapis")
        "brown"                -> setOf("brown", "cocoa")
        "white"                -> setOf("white", "bone", "wool")
        "green"                -> setOf("green", "cactus")
        "red"                  -> setOf("red", "rose")
        "yellow"               -> setOf("yellow", "dandelion")
        "light_gray", "silver" -> setOf("silver", "light gray")
        else                   -> setOf(colorName.lowercase().replace('_', ' '))
    }

    override fun handleSlotUpdate(slot: Int): Boolean {
        if (slot != type.windowSize - 1) return false
        solution.clear()
        // non-glinting, non-black-pane items whose name starts with the colour or a dye synonym (hasFoil() drops clicked ones)
        solution.addAll(items.mapIndexedNotNull { i, it ->
            if (it == null || it.isEmpty || it.hasFoil() || it.`is`(BLACK_PANE)) return@mapIndexedNotNull null
            val name = it.hoverName.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").lowercase().trim()
            if (validPrefixes.any { p -> name.startsWith(p) }) i else null
        })
        return true
    }
}

class RubixHandler : TerminalHandler(TerminalType.RUBIX) {
    private var lastColorIdx: Int? = null

    override fun handleSlotUpdate(slot: Int): Boolean {
        if (items.last() == null || slot != type.windowSize - 1) return false
        solution.clear()
        solution.addAll(solve())
        return true
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
        if (panes.isEmpty()) return emptyList()
        val candidates = if (lastColorIdx != null) listOf(lastColorIdx!!) else RUBIX_ORDER.indices.toList()
        // start from "no candidate yet", not a List(100){it} sentinel that would light the whole board if it survived
        var best: List<Int>? = null
        var bestTarget = -1
        for (target in candidates) {
            val attempt = panes.flatMap { (idx, stack) ->
                val ci = colorIdxOf(stack)
                if (ci != target && ci >= 0) List(dist(ci, target)) { idx } else emptyList()
            }
            if (best == null || realSize(attempt) < realSize(best!!)) {
                best = attempt
                bestTarget = target
            }
        }
        // lock the winning colour once so post-click recomputes don't re-pick a target and shuffle every indicator
        if (lastColorIdx == null && bestTarget >= 0) lastColorIdx = bestTarget
        return best ?: emptyList()
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
