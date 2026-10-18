package fishmod.features.garden

import fishmod.features.mining.Mining
import fishmod.utils.TabListCache
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import fishmod.features.garden.GardenSettings as S

// Crop Fever time: drops already land in the farming profit tracker, so only the time spent farming during fever is added to it
object CropFever {

    private var lastSeenMs = 0L
    private var lastTickMs = 0L
    private var tick = 0

    // Fever has no confirmed chat/tab format; match the name anywhere in sidebar, tab or chat (guess, unverified)
    private fun feverNow(): Boolean =
        Mining.sidebar.any { it.contains("Crop Fever", true) } || TabListCache.entries.any { it.stripped.contains("Crop Fever", true) }

    fun init() {
        Events.ON_GAME_MESSAGE.register { t ->
            if (S.gardenFever && Garden.inGarden() && Garden.strip(t.string).contains("Crop Fever", true)) lastSeenMs = System.currentTimeMillis()
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || !S.gardenFever || !Garden.inGarden()) { lastTickMs = 0L; return@register }
            val now = System.currentTimeMillis()
            if (tick++ % 10 == 0 && feverNow()) lastSeenMs = now
            val fever = now - lastSeenMs < 3000
            // Time only counts while farming
            if (fever && lastTickMs > 0 && GardenCrops.holdingFarmingTool()) FarmingProfitTracker.addFeverTime((now - lastTickMs).coerceIn(0, 2000))
            lastTickMs = now
        }
    }
}
