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
 * Wardrobe/Loadouts quick-swap: pressing FishMod's slot-N hotkey (keyboard or mouse button,
 * whatever it's bound to in Controls) clicks the matching slot in the currently open Wardrobe
 * (Armor Sets) or Loadouts GUI.
 *
 * Loadouts uses a fixed slot layout (verified in-game): 3 columns x 4 rows of "select this
 * loadout" icons at raw slot indices 14/15/16, 23/24/25, 32/33/34, 41/42/43 — hardcoded below.
 *
 * Wardrobe's clickable "select this set" icon moves depending on how many sets are on the page,
 * so instead of a fixed index it's found each time by scanning the hotkey's column for
 * Hypixel's wool/dye/barrier icon.
 *
 * The actual click is deferred by one client tick after the key/click event, since firing it
 * synchronously in the same tick as the input event was causing visual glitches in Hypixel's GUI.
 */
object WardrobeHotkeys {

    private const val PLAYER_INV_SLOTS = 36

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

        val title = screen.title.string.replace(Regex("§."), "").trim()
        val isWardrobe = title.contains("Armor Sets") || title == "Wardrobe"
        val isLoadout = title.contains("Loadouts")
        if (!isWardrobe && !isLoadout) return false

        val handler: AbstractContainerMenu = screen.menu
        val containerSize = handler.slots.size - PLAYER_INV_SLOTS
        // Sanity check: this must actually be a chest-style GUI, not some other screen
        // that happens to share a title substring.
        if (containerSize < 27 || containerSize % 9 != 0) return false

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
                // handleContainerInput(containerId, slotId, button, ContainerInput, player) — button
                // 0 = left click, matching the old clickSlot(syncId, slotId, button, actionType, player).
                mc.gameMode!!.handleContainerInput(containerId, slotId, 0, ContainerInput.PICKUP, mcPlayer)
                // screen.onClose() (not player.closeContainer()) — matches what pressing Escape
                // does: sends the close packet AND actually dismisses the on-screen GUI. Calling
                // just closeContainer() left the GUI widget on screen out of sync with the
                // now-reset player.containerMenu, which showed up as a close/reopen/close
                // flicker once the server's own state caught up.
                if (FishSettings.wardrobeHotkeysAutoClose && mc.screen === screen) {
                    screen.onClose()
                }
            }
            pendingTicks = 1
            return true
        }

        return false
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
