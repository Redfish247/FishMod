package fishmod.features.other

import fishmod.utils.Keybinds
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.util.function.Predicate

/**
 * Wardrobe/Loadouts quick-swap: FishMod's slot-N hotkey clicks the matching slot in the open
 * Wardrobe or Loadouts GUI. Loadouts uses a fixed, hardcoded slot layout; Wardrobe's "select
 * this set" icon moves per page, so it's found by scanning for Hypixel's wool/dye/barrier icon.
 * The click is deferred one tick — firing synchronously caused visual glitches in Hypixel's GUI.
 */
object WardrobeHotkeys {

    private const val PLAYER_INV_SLOTS = 36

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    /** Raw slot index for Loadout hotkeys 1-12, in the same row-major order as Keybinds.wardrobeSlots. */
    private val LOADOUT_SLOTS = intArrayOf(14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43)

    private var pendingClick: Runnable? = null
    private var pendingTicks = 0

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            val click = pendingClick
            if (click == null) return@register
            if (pendingTicks > 0) {
                pendingTicks--
                return@register
            }
            pendingClick = null
            click.run()
        }
    }

    @JvmStatic
    fun keyPressed(input: KeyEvent, screen: AbstractContainerScreen<*>): Boolean {
        return tryActivate(screen) { mapping -> mapping.matches(input) }
    }

    @JvmStatic
    fun mouseClicked(click: MouseButtonEvent, screen: AbstractContainerScreen<*>): Boolean {
        return tryActivate(screen) { mapping -> mapping.matchesMouse(click) }
    }

    private fun tryActivate(screen: AbstractContainerScreen<*>, matches: Predicate<KeyMapping>): Boolean {
        if (!FishSettings.wardrobeHotkeysEnabled) return false
        val slots = Keybinds.wardrobeSlots ?: return false

        val title = screen.title.string.replace(COLOR, "").trim()
        val isWardrobe = title.contains("Armor Sets") || title == "Wardrobe"
        val isLoadout = title.contains("Loadouts")
        if (!isWardrobe && !isLoadout) return false

        val handler: AbstractContainerMenu = screen.menu
        val containerSize = handler.slots.size - PLAYER_INV_SLOTS
        // Must actually be a chest-style GUI, not some other screen sharing a title substring.
        if (containerSize < 27 || containerSize % 9 != 0) return false

        if (tryPageTurn(handler, containerSize, screen, Keybinds.wardrobeNextPage, matches, "next page")) return true
        if (tryPageTurn(handler, containerSize, screen, Keybinds.wardrobePrevPage, matches, "previous page")) return true

        for (i in slots.indices) {
            val mapping = slots[i]
            if (mapping == null || mapping.isUnbound || !matches.test(mapping)) continue

            val target = resolveTarget(handler, containerSize, isWardrobe, i) ?: return false

            val containerId = handler.containerId
            val slotId = target.index
            pendingClick = Runnable {
                val mc = Minecraft.getInstance()
                val mcPlayer = mc.player
                if (mcPlayer == null || mc.gameMode == null) return@Runnable
                // button 0 = left click.
                mc.gameMode!!.handleContainerInput(containerId, slotId, 0, ContainerInput.PICKUP, mcPlayer)
                // screen.onClose(), not closeContainer() — the latter desynced the GUI widget from the menu and flickered
                if (FishSettings.wardrobeHotkeysAutoClose && mc.screen === screen) {
                    screen.onClose()
                }
            }
            pendingTicks = 1
            return true
        }

        return false
    }

    /** Clicks whichever arrow icon reads "Next Page"/"Previous Page" (Hypixel's own pagination button). */
    private fun tryPageTurn(handler: AbstractContainerMenu, containerSize: Int, screen: AbstractContainerScreen<*>, mapping: KeyMapping?, matches: Predicate<KeyMapping>, label: String): Boolean {
        if (mapping == null || mapping.isUnbound || !matches.test(mapping)) return false

        val target = findByName(handler, containerSize, label) ?: return false

        val containerId = handler.containerId
        val slotId = target.index
        pendingClick = Runnable {
            val mc = Minecraft.getInstance()
            val mcPlayer = mc.player
            if (mcPlayer == null || mc.gameMode == null) return@Runnable
            mc.gameMode!!.handleContainerInput(containerId, slotId, 0, ContainerInput.PICKUP, mcPlayer)
        }
        pendingTicks = 1
        return true
    }

    /** Scans the container region for an item whose display name contains `label` (case-insensitive). */
    private fun findByName(handler: AbstractContainerMenu, containerSize: Int, label: String): Slot? {
        for (i in 0 until containerSize) {
            val slot = handler.slots[i]
            val stack = slot.item
            if (stack.isEmpty) continue
            val name = stack.hoverName.string.replace(COLOR, "").trim()
            if (name.lowercase().contains(label)) return slot
        }
        return null
    }

    private fun resolveTarget(handler: AbstractContainerMenu, containerSize: Int, isWardrobe: Boolean, hotkeyIndex: Int): Slot? {
        if (isWardrobe) {
            return findSelectSlot(handler, containerSize, hotkeyIndex)
        }
        if (hotkeyIndex >= LOADOUT_SLOTS.size) return null
        val slotIndex = LOADOUT_SLOTS[hotkeyIndex]
        return if (slotIndex < handler.slots.size) handler.slots[slotIndex] else null
    }

    /** Scans column `column` (of a 9-wide grid) for Hypixel's wool/dye/barrier "select" icon. */
    private fun findSelectSlot(handler: AbstractContainerMenu, containerSize: Int, column: Int): Slot? {
        if (column >= 9) return null
        var i = column
        while (i < containerSize) {
            val slot = handler.slots[i]
            val stack: ItemStack = slot.item
            if (!stack.isEmpty) {
                val id = vanillaId(stack)
                if (id != null &&
                    (id.endsWith("_wool") || id.endsWith("_dye") || id.endsWith("_stained_glass_pane") || id == "minecraft:barrier")
                ) {
                    return slot
                }
            }
            i += 9
        }
        return null
    }

    private fun vanillaId(stack: ItemStack): String? {
        return try {
            BuiltInRegistries.ITEM.getKey(stack.item).toString()
        } catch (e: Exception) {
            null
        }
    }
}
