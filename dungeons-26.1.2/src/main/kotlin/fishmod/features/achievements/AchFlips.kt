package fishmod.features.achievements

// Flip profit from Bazaar/AH chat: buys set a per-item cost basis, sells realise profit against it
object AchFlips {

    private val BZ_BUY = Regex("^\\[Bazaar] Claimed ([\\d,]+)x (.+?) worth ([\\d,.]+) coins bought at [\\d,.]+ each!")
    private val BZ_SELL = Regex("^\\[Bazaar] Claimed ([\\d,.]+) coins from selling ([\\d,]+)x (.+?) at [\\d,.]+ each!")
    private val AH_BUY = Regex("^You purchased (.+?) for ([\\d,]+) coins!")
    private val AH_SELL = Regex("^You collected ([\\d,]+) coins from selling (.+?) to .+ in an auction!")

    private fun n(s: String) = s.replace(",", "").toDoubleOrNull() ?: 0.0

    fun onChat(s: String) {
        if (!s.startsWith("[Bazaar]") && !s.startsWith("You ")) return
        BZ_BUY.find(s)?.let { buy(it.groupValues[2], n(it.groupValues[1]), n(it.groupValues[3])); return }
        BZ_SELL.find(s)?.let { sell(it.groupValues[3], n(it.groupValues[2]), n(it.groupValues[1])); return }
        AH_BUY.find(s)?.let { buy(it.groupValues[1], 1.0, n(it.groupValues[2])); return }
        AH_SELL.find(s)?.let { sell(it.groupValues[2], 1.0, n(it.groupValues[1])) }
    }

    private fun buy(item: String, qty: Double, cost: Double) {
        if (qty <= 0 || cost <= 0) return
        val b = AchStore.data.basis
        if (b.size > 600 && !b.containsKey(item)) b.remove(b.keys.first())
        val cur = b.getOrPut(item) { DoubleArray(2) }
        cur[0] += qty; cur[1] += cost
        AchStore.changed()
    }

    private fun sell(item: String, qty: Double, revenue: Double) {
        val cur = AchStore.data.basis[item] ?: return
        if (qty <= 0 || cur[0] <= 0) return
        val used = minOf(qty, cur[0])
        val avg = cur[1] / cur[0]
        val profit = revenue * (used / qty) - used * avg
        cur[0] -= used; cur[1] -= used * avg
        if (cur[0] <= 0.0001) AchStore.data.basis.remove(item)
        AchStore.inc("flip.net", profit.toLong())
        AchStore.max("flip.profit", AchStore.get("flip.net"))
    }
}
