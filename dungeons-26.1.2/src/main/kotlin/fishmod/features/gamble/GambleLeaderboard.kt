package fishmod.features.gamble

import com.google.gson.JsonObject

// GET /leaderboard; the #1 entry also feeds the crown cache
object GambleLeaderboard {

    class Entry(val name: String, val uuid: String, val balance: Long)

    @Volatile var entries: List<Entry> = emptyList(); private set

    fun fetch(cb: (List<Entry>?) -> Unit) {
        GambleApi.call("GET", "/leaderboard?limit=10", null) { res ->
            val o = res?.json
            if (res == null || o == null || res.status != 200) { cb(null); return@call }
            val list = parse(o)
            entries = list
            list.firstOrNull()?.let { CrownBadge.setHolder(it.name, it.uuid) }
            cb(list)
        }
    }

    // Accepts entries/leaderboard/top arrays with balance or points
    private fun parse(o: JsonObject): List<Entry> {
        val arr = listOf("entries", "leaderboard", "top").firstNotNullOfOrNull { o.getAsJsonArray(it) } ?: return emptyList()
        return arr.mapNotNull {
            val e = it.takeIf { x -> x.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = e.get("name")?.asString ?: return@mapNotNull null
            val bal = (e.get("balance") ?: e.get("points"))?.let { v -> runCatching { v.asLong }.getOrNull() } ?: 0L
            Entry(name, e.get("uuid")?.asString ?: "", bal)
        }
    }
}
