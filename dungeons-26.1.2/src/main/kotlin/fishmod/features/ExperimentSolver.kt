package fishmod.features

import fishmod.compat.curScreen
import fishmod.utils.TextStrip
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.DrawHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.DyeItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

// Experimentation Table solver (NoFrills logic): highlights only, the player does every click.
object ExperimentSolver {

    private enum class Game { NONE, CHRONO, ULTRA, PAIRS }

    private var game = Game.NONE
    private var lastTitle = ""
    private var remember = true
    private val prev = HashMap<Int, ItemStack>()
    private val chrono = ArrayList<MutableList<Int>>()
    private val ultra = ArrayList<Pair<Int, Int>>()
    private val pairsSeen = LinkedHashMap<Int, ItemStack>()
    private var lastRevealed = -1
    private val pairColor = HashMap<Int, Int>()

    private const val FIRST = 0x9900FF00.toInt()
    private const val SECOND = 0x66FFFF00.toInt()
    private const val MATCHED = 0x8000FF00.toInt()
    private const val MATCH = 0x80FFFF00.toInt()
    private const val POWERUP = 0x80FF00FF.toInt()

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            FishDiag.guard("ExperimentSolver.1", "experiment solver tick failed") { tick(mc) }
        }
        DrawHooks.INVENTORY_SLOT_AFTER.register { ctx, _, x, y ->
            if (game == Game.NONE || !FishSettings.experimentSolverEnabled) return@register
            val slot = DrawHooks.currentSlot ?: return@register
            if (slot.container is Inventory) return@register
            val c = colorFor(slot.index)
            if (c != 0) ctx.fill(x, y, x + 16, y + 16, c)
        }
    }

    // Only a correct click advances the solution
    @JvmStatic
    fun onSlotClicked(slotId: Int) {
        if (remember || slotId < 0) return
        if (game == Game.CHRONO && chrono.isNotEmpty() && slotId in chrono.first()) chrono.removeAt(0)
        if (game == Game.ULTRA && ultra.isNotEmpty() && ultra.first().second == slotId) ultra.removeAt(0)
    }

    private fun reset() {
        remember = true; prev.clear(); chrono.clear(); ultra.clear()
        pairsSeen.clear(); lastRevealed = -1; pairColor.clear()
    }

    private fun isStatus(st: ItemStack): Boolean =
        st.`is`(Items.CLOCK) || st.`is`(Items.BOOKSHELF) || st.`is`(Items.CAULDRON) ||
            (st.`is`(Items.GLOWSTONE) && TextStrip.plain(st.hoverName.string) != "Enchanted Book")

    private fun isDye(st: ItemStack): Boolean = st.item is DyeItem || st.`is`(Items.INK_SAC) ||
        st.`is`(Items.BONE_MEAL) || st.`is`(Items.LAPIS_LAZULI) || st.`is`(Items.COCOA_BEANS)

    private fun id(st: ItemStack) = st.item.toString()
    private fun isTerracotta(st: ItemStack) = id(st).endsWith("terracotta")
    private fun isGlass(st: ItemStack) = id(st).endsWith("stained_glass")
    private fun isPane(st: ItemStack) = id(st).endsWith("stained_glass_pane")

    private fun isPowerup(st: ItemStack): Boolean {
        val lore = st.get(DataComponents.LORE) ?: return false
        return lore.lines().any { it.string.lowercase().contains("powerup") }
    }

    private fun same(a: ItemStack, b: ItemStack) =
        a.item == b.item && a.hoverName.string == b.hoverName.string && a.count == b.count &&
            a.get(DataComponents.PROFILE) == b.get(DataComponents.PROFILE)

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
        if (game == Game.NONE || screen == null) return
        for (slot in screen.menu.slots) {
            if (slot.container is Inventory) continue
            val st = slot.item
            val old = prev[slot.index]
            if (old != null && ItemStack.matches(old, st)) continue
            prev[slot.index] = st.copy()
            onUpdate(screen, slot, st)
        }
    }

    // NoFrills' slot-update handler, fed by per-tick slot diffs
    private fun onUpdate(screen: AbstractContainerScreen<*>, slot: Slot, st: ItemStack) {
        if (!remember && st.`is`(Items.GLOWSTONE)) remember = true
        if (remember && st.`is`(Items.CLOCK)) remember = false
        when (game) {
            Game.CHRONO -> if (remember) {
                if (isTerracotta(st)) {
                    if (chrono.isEmpty()) chrono.add(ArrayList())
                    chrono.last().add(slot.index)
                } else if (isGlass(st) && chrono.isNotEmpty() && slot.index in chrono.last()) {
                    chrono.add(ArrayList())
                }
            }
            Game.ULTRA -> if (st.`is`(Items.CLOCK)) {
                ultra.sortBy { it.first }
            } else if (st.`is`(Items.GLOWSTONE)) {
                ultra.clear()
                for (s in screen.menu.slots) {
                    if (s.container !is Inventory && isDye(s.item)) ultra.add(s.item.count to s.index)
                }
            }
            Game.PAIRS -> {
                if (isStatus(st)) return
                if (isPowerup(st)) { pairColor[slot.index] = POWERUP; return }
                if (isGlass(st) || isPane(st) || st.isEmpty) return
                val last = pairsSeen[lastRevealed]
                if (lastRevealed != slot.index && last != null && same(st, last)) {
                    pairColor[slot.index] = MATCHED; pairColor[lastRevealed] = MATCHED
                }
                for ((i, seen) in pairsSeen) {
                    if (!pairColor.containsKey(slot.index) && i != slot.index && same(st, seen)) {
                        pairColor[slot.index] = MATCH; pairColor[i] = MATCH
                    }
                }
                pairsSeen[slot.index] = st.copy()
                lastRevealed = slot.index
            }
            Game.NONE -> {}
        }
    }

    private fun colorFor(idx: Int): Int = when (game) {
        Game.CHRONO -> when {
            remember -> 0
            chrono.isNotEmpty() && idx in chrono[0] -> FIRST
            chrono.size > 1 && idx in chrono[1] -> SECOND
            else -> 0
        }
        Game.ULTRA -> when {
            remember -> 0
            ultra.isNotEmpty() && ultra[0].second == idx -> FIRST
            ultra.size > 1 && ultra[1].second == idx -> SECOND
            else -> 0
        }
        Game.PAIRS -> pairColor[idx] ?: 0
        Game.NONE -> 0
    }
}
