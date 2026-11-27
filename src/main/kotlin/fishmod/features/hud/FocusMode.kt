package fishmod.features.hud

import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

object FocusMode {

    private val PRICE_PHASE = Identifier.fromNamespaceAndPath("fishmod", "item_price_tooltip_last")
    private val PHASE = Identifier.fromNamespaceAndPath("fishmod", "focus_mode")

    @JvmStatic
    fun init() {
        ItemTooltipCallback.EVENT.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, PRICE_PHASE)
        ItemTooltipCallback.EVENT.addPhaseOrdering(PRICE_PHASE, PHASE)
        ItemTooltipCallback.EVENT.register(PHASE, ItemTooltipCallback { _, _, _, lines ->
            if (!FishSettings.focusMode || !SkyblockLocation.inSkyblock() || lines.size <= 1) return@ItemTooltipCallback
            try {
                if (!fishmod.compat.GLFW.keyDown(Minecraft.getInstance().window, fishmod.compat.GLFW.GLFW_KEY_LEFT_ALT)) return@ItemTooltipCallback
                val name = lines[0]
                lines.clear()
                lines.add(name)
            } catch (e: Exception) {
                FishDiag.fail("FocusMode.1", "focus mode failed", e)
            }
        })
    }
}
