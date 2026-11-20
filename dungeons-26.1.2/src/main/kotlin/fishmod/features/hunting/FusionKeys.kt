package fishmod.features.hunting

import fishmod.utils.FishKeys
import fishmod.utils.ScreenTitle
import fishmod.utils.TextStrip
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ContainerInput
import fishmod.compat.CI
import fishmod.features.fishing.FishingWorldSettings as W

// Fusion menu keys: one press = one click in the Fusion Box / Confirm Fusion menu you opened (idea from SkyHanni/NoFrills)
object FusionKeys {
    lateinit var repeat: KeyMapping
    lateinit var confirm: KeyMapping
    lateinit var cancel: KeyMapping

    fun init() {
        repeat = reg("Fusion: Repeat"); confirm = reg("Fusion: Confirm"); cancel = reg("Fusion: Cancel")
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            ScreenKeyboardEvents.allowKeyPress(screen).register { s, key -> !(W.fusionKeys && press(s as AbstractContainerScreen<*>, key)) }
        }
    }

    private fun reg(id: String): KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(id, fishmod.compat.GLFW.KEYSYM, -1, FishKeys.category()))

    private fun press(screen: AbstractContainerScreen<*>, key: net.minecraft.client.input.KeyEvent): Boolean {
        // from NoFrills: menu titles and button names
        val title = ScreenTitle.plain(screen).trim()
        val box = title.contains("Fusion Box")
        if (!box && title != "Confirm Fusion") return false
        val menu = screen.menu
        for (slot in menu.slots) {
            val st = slot.item
            if (st.isEmpty) continue
            val hit = if (box) repeat.matches(key) && TextStrip.plain(st.hoverName.string) == "Repeat Previous Fusion"
                else (confirm.matches(key) && st.`is`(CI.LIME_TERRACOTTA)) || (cancel.matches(key) && st.`is`(CI.RED_TERRACOTTA))
            if (!hit) continue
            val mc = Minecraft.getInstance()
            val pl = mc.player ?: return true
            mc.gameMode?.handleContainerInput(menu.containerId, slot.index, 2, ContainerInput.CLONE, pl)
            return true
        }
        return false
    }
}
