package fishmod.features

import fishmod.utils.networth.ItemsDb

// Shared pieces of the Farming and Mining profit trackers
object ProfitTrackerKit {

    interface Cat { val color: String; val short: String }

    class Row<C>(val cat: C, val name: String, val n: Long, val value: Double, val split: Map<C, Long> = emptyMap())

    private val RARITY = mapOf(
        "COMMON" to "§f", "UNCOMMON" to "§a", "RARE" to "§9", "EPIC" to "§5", "LEGENDARY" to "§6",
        "MYTHIC" to "§d", "DIVINE" to "§b", "SPECIAL" to "§c", "VERY_SPECIAL" to "§c",
    )

    // Name colour from the item's Hypixel tier; null for unknown items
    fun rarityColor(id: String?): String? {
        id ?: return null
        return RARITY[ItemsDb.get(id)?.get("tier")?.asString ?: "COMMON"]
    }

    // Same item from several sources: one row, per-source counts shown after it
    fun <C> mergeSources(rows: List<Row<C>>): List<Row<C>> where C : Enum<C>, C : Cat = rows.groupBy { it.name }.map { (name, rs) ->
        if (rs.size == 1) rs[0]
        else Row(rs.maxBy { it.n }.cat, name, rs.sumOf { it.n }, rs.sumOf { it.value }, rs.sortedBy { it.cat.ordinal }.associate { it.cat to it.n })
    }

    fun <C> sort(rows: List<Row<C>>, mode: String): List<Row<C>> where C : Enum<C>, C : Cat = when (mode) {
        "Least Profit" -> rows.sortedBy { it.value }
        "Category" -> rows.sortedWith(compareBy<Row<C>> { it.cat.ordinal }.thenByDescending { it.value })
        else -> rows.sortedByDescending { it.value }
    }

    fun <C> rowLine(r: Row<C>, value: String, nameColor: String?, showSources: Boolean): String where C : Enum<C>, C : Cat =
        "$value	§7| ${nameColor ?: r.cat.color}${r.name} §f${"%,d".format(r.n)}" +
            (if (r.split.isEmpty() || !showSources) "" else " §8(" + r.split.entries.joinToString(" ") { "${it.key.color}${it.key.short} §f${"%,d".format(it.value)}" } + "§8)")

    fun perHour(v: Double, timeMs: Long) = if (timeMs < 60_000) 0.0 else v * 3_600_000.0 / timeMs

    // HUD lines rebuilt on a data change, else at most once a second (playtime, settings)
    class LineCache {
        private var ver = -1; private var at = 0L; private var lines: List<String> = emptyList()
        fun get(version: Int, build: () -> List<String>): List<String> {
            val now = System.currentTimeMillis()
            if (ver == version && now - at < 1000) return lines
            ver = version; at = now
            lines = build()
            return lines
        }
    }
}
