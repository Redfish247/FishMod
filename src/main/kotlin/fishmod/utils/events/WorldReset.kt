package fishmod.utils.events

import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.multiplayer.ClientLevel
import java.lang.ref.WeakReference

object WorldReset {
    private var last: WeakReference<ClientLevel>? = null

    init {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> last = null; fire() }
    }

    @JvmStatic
    fun onSetLevel(level: ClientLevel?) {
        if (level === last?.get()) return
        last = level?.let { WeakReference(it) }
        fire()
    }

    private fun fire() {
        try {
            FishEvents.worldChange.invoke { it.onWorldSwap() }
        } catch (t: Throwable) {
            FishDiag.fail("WorldReset.1", "world change event failed", t)
        }
    }
}
