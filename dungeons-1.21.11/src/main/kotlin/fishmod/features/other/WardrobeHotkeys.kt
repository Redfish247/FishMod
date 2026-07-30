package fishmod.features.other

import fishmod.utils.Keybinds
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.input.KeyInput
import net.minecraft.client.option.KeyBinding
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
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
    fun keyPressed(input: KeyInput, screen: HandledScreen<*>): Boolean {
        return tryActivate(screen) { mapping -> mapping.matchesKey(input) }
    }

    @JvmStatic
    fun mouseClicked(click: Click, screen: HandledScreen<*>): Boolean {
        return tryActivate(screen) { mapping -> mapping.matchesMouse(click) }
    }

    private fun tryActivate(screen: HandledScreen<*>, matches: Predicate<KeyBinding>): Boolean {
        if (!FishSettings.wardrobeHotkeysEnabled) return false
        val slots = Keybinds.wardrobeSlots ?: return false

        val title = screen.title.string.replace(Regex("§."), "").trim()
        val isWardrobe = title.contains("Armor Sets") || title == "Wardrobe"
        val isLoadout = title.contains("Loadouts")
        if (!isWardrobe && !isLoadout) return false

        val handler: ScreenHandler = screen.screenHandler
        val containerSize = handler.slots.size - PLAYER_INV_SLOTS
        // Sanity check: this must actually be a chest-style GUI, not some other screen
        // that happens to share a title substring.
        if (containerSize < 27 || containerSize % 9 != 0) return false

        for (i in slots.indices) {
            val mapping = slots[i]
            if (mapping == null || mapping.isUnbound || !matches.test(mapping)) continue

            val target = resolveTarget(handler, containerSize, isWardrobe, i) ?: return false

            val syncId = handler.syncId
            val slotId = target.id
            pendingClick = Runnable {
                val mc = MinecraftClient.getInstance()
                if (mc.player == null || mc.interactionManager == null) return@Runnable
                mc.interactionManager!!.clickSlot(syncId, slotId, 0, SlotActionType.PICKUP, mc.player)
                // screen.close() (not player.closeHandledScreen()) — matches what pressing Escape
                // does: sends the close packet AND actually dismisses the on-screen GUI. Calling
                // just closeHandledScreen() left the GUI widget on screen out of sync with the
                // now-reset player.currentScreenHandler, which showed up as a close/reopen/close
                // flicker once the server's own state caught up.
                if (FishSettings.wardrobeHotkeysAutoClose && mc.currentScreen === screen) {
                    screen.close()
                }
            }
            pendingTicks = 1
            return true
        }

        return false
    }

    private fun resolveTarget(handler: ScreenHandler, containerSize: Int, isWardrobe: Boolean, hotkeyIndex: Int): Slot? {
        if (isWardrobe) {
            return findSelectSlot(handler, containerSize, hotkeyIndex)
        }
        if (hotkeyIndex >= LOADOUT_SLOTS.size) return null
        val slotIndex = LOADOUT_SLOTS[hotkeyIndex]
        return if (slotIndex < handler.slots.size) handler.slots[slotIndex] else null
    }

    /** Scans column `column` (of a 9-wide grid) for Hypixel's wool/dye/barrier "select" icon. */
    private fun findSelectSlot(handler: ScreenHandler, containerSize: Int, column: Int): Slot? {
        if (column >= 9) return null
        var i = column
        while (i < containerSize) {
            val slot = handler.slots[i]
            val stack: ItemStack = slot.stack
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
            Registries.ITEM.getId(stack.item).toString()
        } catch (e: Exception) {
            null
        }
    }
}
