package fishmod.features.gamble

import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

// Gamble Points entry point: command, playtime windows, crown cache
object GambleEngine {

    @JvmStatic
    fun init() {
        GambleCommand.init()
        CrownBadge.init()
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            FishDiag.guard("Gamble.1", "gamble tick") {
                PlaytimeTracker.onTick(mc)
                CrownBadge.onTick(mc)
            }
        }
    }
}
