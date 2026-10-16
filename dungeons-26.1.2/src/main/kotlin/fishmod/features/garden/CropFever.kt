package fishmod.features.garden

import fishmod.features.mining.Mining
import fishmod.utils.TabListCache
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import fishmod.features.garden.GardenSettings as S

// Crop Fever tracker: only while a farming tool is held during fever; drops and profit/h from the farming profit session
object CropFever {

    private var active = false
    private var lastSeenMs = 0L
    private var startMs = 0L
    private var activeMs = 0L
    private var lastTickMs = 0L
    private var base: Map<String, Long> = emptyMap()
    private var tick = 0

    // Fever has no confirmed chat/tab format; match the name anywhere in sidebar, tab or chat (guess, unverified)
    private fun feverNow(): Boolean =
        Mining.sidebar.any { it.contains("Crop Fever", true) } || TabListCache.entries.any { it.stripped.contains("Crop Fever", true) }

    fun init() {
        Events.ON_GAME_MESSAGE.register { t ->
            if (S.gardenFever && Garden.inGarden() && Garden.strip(t.string).contains("Crop Fever", true)) lastSeenMs = System.currentTimeMillis()
            false
        }
        Events.ON_WORLD_CHANGE.register { active = false; false }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || !S.gardenFever || !Garden.inGarden()) { active = false; return@register }
            val now = System.currentTimeMillis()
            if (tick++ % 10 == 0 && feverNow()) lastSeenMs = now
            val fever = now - lastSeenMs < 3000
            if (fever && !active) { active = true; startMs = now; activeMs = 0L; base = FarmingProfitTracker.sessionItems() }
            else if (!fever && active) active = false
            // Time only counts while farming
            if (active && lastTickMs > 0 && GardenCrops.holdingFarmingTool()) activeMs += (now - lastTickMs).coerceIn(0, 2000)
            lastTickMs = now
        }
        Garden.hud("Crop Fever", "garden_crop_fever", 170, 60,
            { S.gardenFeverX }, { S.gardenFeverX = it }, { S.gardenFeverY }, { S.gardenFeverY = it },
            { S.gardenFeverScale }, { S.gardenFeverScale = it },
            { S.gardenFever && active && GardenCrops.holdingFarmingTool() }) { lines() }
    }

    private fun lines(): List<String> {
        val cur = FarmingProfitTracker.sessionItems()
        // Session reset mid-fever: counts restart from zero
        val delta = cur.mapValues { (k, v) -> (v - (base[k] ?: 0L).coerceAtMost(v)) }.filterValues { it > 0 }
        val rows = MoneyPerHour.rows(delta, GardenCrops.heldCrop() != GardenCrops.Crop.MUSHROOM)
        val total = rows.sumOf { it.value }
        val out = ArrayList<String>()
        out += "${Garden.legacy(S.gardenFeverColor)}§lCrop Fever §7${fishmod.features.diana.DianaTracker.fmtTime(activeMs)}"
        for (r in rows.take(6)) out += "§6${Garden.short(r.value)}\t§7| §f${r.label} §7x${"%,d".format(r.n)}"
        val ph = if (activeMs < 10_000) 0.0 else total * 3_600_000.0 / activeMs
        out += "§eProfit §6${Garden.short(total)} §7(§6${Garden.short(ph)}§7/h)"
        return out
    }
}
