package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/**
 * No Cursor Reset: for a short window (ms) around any screen being open, a container screen
 * re-opening keeps the cursor where it was instead of snapping it to centre.
 * [fishmod.mixin.MouseMixin] does the actual capture (grabMouse) / restore (releaseMouse).
 * Window length is [FishSettings.noCursorResetMs]; measured against wall-clock time.
 */
object NoCursorReset {

    private var clock = System.currentTimeMillis()
    private var wasOpen = false

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.screen != null) {
                wasOpen = true
                clock = System.currentTimeMillis()
            } else if (wasOpen) {
                wasOpen = false
                clock = System.currentTimeMillis()
            }
        }
    }

    /** @return true while [fishmod.mixin.MouseMixin] should restore the cursor instead of centring it. */
    @JvmStatic
    fun shouldHook(): Boolean =
        FishSettings.noCursorReset &&
            System.currentTimeMillis() - clock < FishSettings.noCursorResetMs
}
