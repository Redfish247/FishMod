package fishmod.features.mining

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.chat.Component

object MiningProfitTracker {
    private var ticks = 0

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!FishSettings.miningProfitEnabled) return@register
            if (client.player == null || !Location.inSkyblock()) return@register

            ticks++
            if (ticks % 200 == 0) {
                Misc.addChatMessage(Component.literal("§3 [Mining] §falive for ${ticks / 20}s"))
            }
        }
    }
}