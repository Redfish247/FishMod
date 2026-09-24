package fishmod.features.other

import fishmod.utils.Keybinds
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import java.util.function.Predicate

// Fixed keybinds for specific pets, matched by name so the slot/page doesn't matter.
object PetKeybinds {

    @JvmField val PETS = arrayOf("Golden Dragon", "Black Cat", "Ender Dragon")
    @JvmField val COUNT = PETS.size
    private const val PLAYER_INV_SLOTS = 36
    private const val TIMEOUT_MS = 4000L

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val LEVEL = Regex("\\[Lvl\\s*(\\d+)]\\s*")
    // "Pets" or "(1/3) Pets"
    private val PETS_TITLE = Regex("^(\\(\\d+/\\d+\\)\\s*)?Pets.*")

    private var target: String? = null
    private var targetStartedAt = 0L
    private var waitTicks = 0
    private var openedByUs = false

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> tick(mc) }
    }

    private fun tick(mc: Minecraft) {
        val binds = Keybinds.petKeybinds ?: return
        if (FishSettings.petKeybindsEnabled && mc.screen == null) {
            for (i in binds.indices) {
                while (binds[i].consumeClick()) startTarget(i, openMenu = true)
            }
        }

        val t = target ?: return
        if (System.currentTimeMillis() - targetStartedAt > TIMEOUT_MS) {
            msg("§cCouldn't find your §f$t§c in the pets menu.")
            target = null
            return
        }
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return
        if (!isPetsMenu(screen)) return
        if (waitTicks > 0) { waitTicks--; return }
        step(mc, screen, t)
    }

    private fun startTarget(i: Int, openMenu: Boolean) {
        target = PETS.getOrNull(i) ?: return
        targetStartedAt = System.currentTimeMillis()
        waitTicks = if (openMenu) 3 else 0
        openedByUs = openMenu
        if (openMenu) Misc.executeCommand("pets")
    }

    private fun step(mc: Minecraft, screen: AbstractContainerScreen<*>, t: String) {
        val menu = screen.menu
        val size = menu.slots.size - PLAYER_INV_SLOTS
        if (size <= 0) return
        // Wait for the server to fill the menu.
        if ((0 until size).none { !menu.slots[it].item.isEmpty }) return

        // Highest-level copy wins if you own more than one.
        val slot = (0 until size).map { menu.slots[it] }
            .filter { petName(it.item)?.contains(t, true) == true }
            .maxByOrNull { petLevel(it.item) }
        if (slot != null) {
            target = null
            if (ItemUtil.containsLore(slot.item, "Click to despawn") && FishSettings.petKeybindsNoDespawn) {
                msg("§e$t§7 is already summoned.")
                if (openedByUs && FishSettings.petKeybindsAutoClose) screen.onClose()
                return
            }
            click(mc, menu.containerId, slot.index)
            if (FishSettings.petKeybindsAutoClose && mc.screen === screen) screen.onClose()
            return
        }

        val next = (0 until size).map { menu.slots[it] }.firstOrNull {
            !it.item.isEmpty && it.item.hoverName.string.replace(COLOR, "").trim().equals("Next Page", true)
        }
        if (next == null) {
            msg("§cCouldn't find your §f$t§c in the pets menu.")
            target = null
            return
        }
        click(mc, menu.containerId, next.index)
        waitTicks = 4
    }

    @JvmStatic
    fun keyPressed(input: KeyEvent, screen: AbstractContainerScreen<*>): Boolean =
        handleInput(screen) { it.matches(input) }

    @JvmStatic
    fun mouseClicked(click: MouseButtonEvent, screen: AbstractContainerScreen<*>): Boolean {
        if (click.button() == 0 || click.button() == 1) return false
        return handleInput(screen) { it.matchesMouse(click) }
    }

    private fun handleInput(screen: AbstractContainerScreen<*>, matches: Predicate<KeyMapping>): Boolean {
        if (!FishSettings.petKeybindsEnabled || !isPetsMenu(screen)) return false
        val binds = Keybinds.petKeybinds ?: return false
        val i = binds.indexOfFirst { !it.isUnbound && matches.test(it) }
        if (i < 0) return false
        startTarget(i, openMenu = false)
        return true
    }

    private fun petName(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val raw = stack.hoverName.string.replace(COLOR, "").trim()
        val m = LEVEL.find(raw) ?: return null
        return raw.substring(m.range.last + 1).trim()
    }

    private fun petLevel(stack: ItemStack): Int {
        val raw = stack.hoverName.string.replace(COLOR, "").trim()
        return LEVEL.find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun isPetsMenu(screen: AbstractContainerScreen<*>): Boolean =
        PETS_TITLE.matches(screen.title.string.replace(COLOR, "").trim())

    private fun click(mc: Minecraft, containerId: Int, slotId: Int) {
        val player = mc.player ?: return
        mc.gameMode?.handleContainerInput(containerId, slotId, 0, ContainerInput.PICKUP, player)
    }

    private fun msg(s: String) = Misc.addChatMessage(Component.literal(s))
}
