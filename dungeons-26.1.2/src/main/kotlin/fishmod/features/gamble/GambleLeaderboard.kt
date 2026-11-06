package fishmod.features.gamble

import com.google.gson.JsonObject

// GET /leaderboard; the #1 entry also feeds the crown cache
object GambleLeaderboard {

    class Entry(val name: String, val uuid: String, val balance: Long)

    @Volatile var entries: List<Entry> = emptyList(); private set

    // Dev-only offline board (/gamble dev); null = live worker
    @Volatile var fake: List<Entry>? = null

    fun fetch(cb: (List<Entry>?) -> Unit) {
        fake?.let { f ->
            entries = f
            f.firstOrNull()?.let { CrownBadge.setHolder(it.name, it.uuid) }
            cb(f)
            return
        }
        GambleApi.call("GET", "/leaderboard?limit=10", null) { res ->
            val a = res?.arr
            if (res == null || a == null || res.status != 200) { cb(null); return@call }
            val list = parse(a)
            entries = list
            list.firstOrNull()?.let { CrownBadge.setHolder(it.name, it.uuid) }
            cb(list)
        }
    }

    // Worker returns a bare array of {rank, uuid, name, points}
    private fun parse(arr: com.google.gson.JsonArray): List<Entry> {
        return arr.mapNotNull {
            val e = it.takeIf { x -> x.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = e.get("name")?.asString ?: return@mapNotNull null
            val bal = e.get("points")?.let { v -> runCatching { v.asLong }.getOrNull() } ?: 0L
            Entry(name, e.get("uuid")?.asString ?: "", bal)
        }
    }
}
