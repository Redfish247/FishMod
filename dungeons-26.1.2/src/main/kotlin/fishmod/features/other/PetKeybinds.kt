package fishmod.features.other

import fishmod.utils.Keybinds
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import java.util.function.Predicate

object PetKeybinds {

    @JvmField val PETS = arrayOf("Golden Dragon", "Black Cat", "Ender Dragon")
    @JvmField val COUNT = PETS.size
    private const val PLAYER_INV_SLOTS = 36

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val LEVEL = Regex("""\[Lvl\s*(\d+)]\s*""")
    private val PETS_TITLE = Regex("""^(\(\d+/\d+\)\s*)?Pets.*""")

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
        select(Minecraft.getInstance(), screen, PETS[i])
        return true
    }

    private fun select(mc: Minecraft, screen: AbstractContainerScreen<*>, t: String) {
        val menu = screen.menu
        val size = menu.slots.size - PLAYER_INV_SLOTS
        if (size <= 0) return

        val slot = (0 until size).map { menu.slots[it] }
            .filter { petName(it.item)?.contains(t, true) == true }
            .maxByOrNull { petLevel(it.item) }
        if (slot == null) {
            msg("§cYour §f$t§c isn't on this page.")
            return
        }
        if (ItemUtil.containsLore(slot.item, "Click to despawn") && FishSettings.petKeybindsNoDespawn) {
            msg("§e$t§7 is already summoned.")
            return
        }
        click(mc, menu.containerId, slot.index)
        if (FishSettings.petKeybindsAutoClose && mc.screen === screen) screen.onClose()
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
        PETS_TITLE.matches(fishmod.utils.ScreenTitle.plain(screen).trim())

    private fun click(mc: Minecraft, containerId: Int, slotId: Int) {
        val player = mc.player ?: return
        mc.gameMode?.handleContainerInput(containerId, slotId, 0, ContainerInput.PICKUP, player)
    }

    private fun msg(s: String) = Misc.addChatMessage(Component.literal(s))
}
