package fishmod.features
import fishmod.compat.curScreen

import com.mojang.blaze3d.platform.InputConstants
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.Minecraft

// Holds the sprint key like vanilla toggle sprint, so sprint starts in the same tick its movement is sent.
object AutoSprint {

    private var holding = false

    @JvmStatic
    fun init() {
        ClientTickEvents.START_CLIENT_TICK.register { mc ->
            val key = mc.options.keySprint
            if (active(mc)) {
                if (!key.isDown) key.setDown(true)
                holding = true
            } else if (holding) {
                holding = false
                key.setDown(physicallyDown(mc))
            }
        }
    }

    private fun active(mc: Minecraft): Boolean {
        if (!FishSettings.autoSprintEnabled) return false
        if (mc.player == null || mc.curScreen != null) return false
        if (!Location.inSkyblock()) return false
        return !(FishSettings.autoSprintDungeonOnly && !Location.inDungeon())
    }

    private fun physicallyDown(mc: Minecraft): Boolean {
        val bound = KeyMappingHelper.getBoundKeyOf(mc.options.keySprint)
        return bound.type == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(mc.window, bound.value)
    }
}
