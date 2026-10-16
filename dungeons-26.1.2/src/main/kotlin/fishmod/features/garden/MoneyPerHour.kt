package fishmod.features.garden

import fishmod.features.mining.Mining
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import fishmod.features.garden.GardenSettings as S

// Money per hour HUD: current crop, crop drops (seeds merged), Mooshroom, Bountiful coins and armor/rare drops
object MoneyPerHour {

    private val PURSE = Regex("""^(?:Purse|Piggy): ([\d,]+)""")
    private var lastPurse = -1L
    private var bountiful = 0L
    private var lastTimeMs = 0L
    private var crop: GardenCrops.Crop? = null
    private var tick = 0

    class Row(val label: String, val n: Long, val value: Double)

    // Rows from the profit tracker session: crops + rare/armor drops, seeds merged, mushrooms from Mooshroom when not farming mushroom
    fun rows(items: Map<String, Long>, mooshroom: Boolean): List<Row> {
        val merged = LinkedHashMap<String, LongArray>()
        val vals = HashMap<String, Double>()
        for ((k, n) in items) {
            val cat = k.substringBefore('|')
            if (cat != "CROPS" && cat != "RARE") continue
            val name = k.substringAfter('|')
            var label = name
            if (name.contains("Seeds", true)) label = "Seeds"
            else if (mooshroom && cat == "CROPS" && name.contains("Mushroom", true)) label = "Mooshroom Mushrooms"
            else if (cat == "RARE") label = "Armor/Rare: $name"
            merged.getOrPut(label) { LongArray(1) }[0] += n
            vals[label] = (vals[label] ?: 0.0) + FarmingProfitTracker.priceOf(name) * n
        }
        return merged.map { (l, n) -> Row(l, n[0], vals[l] ?: 0.0) }.sortedByDescending { it.value }
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || !S.gardenMoneyHour || !Garden.inGarden()) return@register
            if (tick++ % 10 != 0) return@register
            crop = GardenCrops.heldCrop() ?: crop
            val t = FarmingProfitTracker.sessionTimeMs()
            if (t < lastTimeMs) bountiful = 0L  // session was reset
            lastTimeMs = t
            // Bountiful pays coins straight to the purse: small positive jumps while holding a farming tool (guess, unverified)
            val purse = Mining.sidebar.firstNotNullOfOrNull { PURSE.find(it)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() }
            if (purse != null) {
                if (lastPurse in 0 until purse && purse - lastPurse < 5000 && mc.screen == null && GardenCrops.holdingFarmingTool()) bountiful += purse - lastPurse
                lastPurse = purse
            }
        }
        Garden.hud("Money Per Hour", "garden_money_hour", 170, 80,
            { S.gardenMoneyHourX }, { S.gardenMoneyHourX = it }, { S.gardenMoneyHourY }, { S.gardenMoneyHourY = it },
            { S.gardenMoneyHourScale }, { S.gardenMoneyHourScale = it },
            { S.gardenMoneyHour && Garden.inGarden() }) { lines() }
    }

    private fun lines(): List<String> {
        val c = crop
        val moosh = c != GardenCrops.Crop.MUSHROOM
        val rows = rows(FarmingProfitTracker.sessionItems(), moosh)
        val t = FarmingProfitTracker.sessionTimeMs()
        val total = rows.sumOf { it.value } + bountiful
        val out = ArrayList<String>()
        out += "${Garden.legacy(S.gardenMoneyHourColor)}§lMoney/h §7(${c?.let { it.color + it.display } ?: "§8no crop"}§7)"
        for (r in rows.take(8)) out += "§6${Garden.short(r.value)}\t§7| §f${r.label} §7x${"%,d".format(r.n)}"
        if (bountiful > 0) out += "§6${Garden.short(bountiful.toDouble())}\t§7| §eBountiful coins"
        val ph = if (t < 60_000) 0.0 else total * 3_600_000.0 / t
        out += "§eTotal §6${Garden.short(total)} §7(§6${Garden.short(ph)}§7/h)"
        return out
    }
}
