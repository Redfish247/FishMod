package fishmod.features.garden

import fishmod.utils.events.chatRaw
import fishmod.features.mining.Mining
import fishmod.utils.TabListCache
import fishmod.utils.events.FishEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import fishmod.features.garden.GardenSettings as S

object CropFever {

    private var lastSeenMs = 0L
    private var lastTickMs = 0L
    private var tick = 0

    private fun feverNow(): Boolean =
        Mining.sidebar.any { it.contains("Crop Fever", true) } || TabListCache.entries.any { it.stripped.contains("Crop Fever", true) }

    fun init() {
        FishEvents.chat.register { t ->
            if (S.gardenFever && Garden.inGarden() && Garden.strip(t.chatRaw).contains("Crop Fever", true)) lastSeenMs = System.currentTimeMillis()
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || !S.gardenFever || !Garden.inGarden()) { lastTickMs = 0L; return@register }
            val now = System.currentTimeMillis()
            if (tick++ % 10 == 0 && feverNow()) lastSeenMs = now
            val fever = now - lastSeenMs < 3000
            if (fever && lastTickMs > 0 && GardenCrops.holdingFarmingTool()) FarmingProfitTracker.addFeverTime((now - lastTickMs).coerceIn(0, 2000))
            lastTickMs = now
        }
    }
}
