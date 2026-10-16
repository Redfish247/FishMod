package fishmod.features.garden

// Drop rows from the profit tracker session (used by Crop Fever)
object MoneyPerHour {

    class Row(val label: String, val n: Long, val value: Double)

    // Crops + rare/armor drops, seeds merged, mushrooms from Mooshroom when not farming mushroom
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
}
