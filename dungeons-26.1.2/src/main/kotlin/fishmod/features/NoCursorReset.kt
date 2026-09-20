package fishmod.features

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

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

    @JvmStatic
    fun shouldHook(): Boolean =
        FishSettings.noCursorReset &&
            System.currentTimeMillis() - clock < FishSettings.noCursorResetMs
}
