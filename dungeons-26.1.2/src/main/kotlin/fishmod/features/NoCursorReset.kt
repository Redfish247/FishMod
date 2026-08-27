package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

/**
 * No Cursor Reset (Odin's model): the cursor keeps its position only for a short window around a
 * container-screen close, not forever — so returning to gameplay after being away still recentres.
 */
object NoCursorReset {

    private const val GRACE_MS = 250L
    private var seenAt = 0L

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.screen is AbstractContainerScreen<*>) seenAt = System.currentTimeMillis()
        }
    }

    /** @return true while [MouseMixin] should skip the grabMouse() xpos/ypos recentre. */
    @JvmStatic
    fun shouldHook(): Boolean =
        FishSettings.noCursorReset && System.currentTimeMillis() - seenAt < GRACE_MS
}
