package fishmod.cosmetic.badge

import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap

object BadgeManager {

    private val byUuid: MutableMap<String, List<String>> = ConcurrentHashMap()
    private val nameToUuid: MutableMap<String, String> = ConcurrentHashMap()

    @JvmStatic
    fun clearAll() {
        byUuid.clear()
    }

    @JvmStatic
    fun replaceNames(names: Map<String, String>) {
        nameToUuid.keys.retainAll(names.keys)
        nameToUuid.putAll(names)
    }

    @JvmStatic
    fun uuidForName(name: String): String? = nameToUuid[name]

    @JvmStatic
    fun acceptBadges(queried: Set<String>, raw: Map<String, String>) {
        for (u in queried) {
            val json = raw[u]
            if (json == null) {
                byUuid.remove(u)
                continue
            }
            try {
                val arr = JsonParser.parseString(json).asJsonArray
                val ids = ArrayList<String>(arr.size())
                for (el in arr) ids.add(el.asString)
                byUuid[u] = ids
            } catch (e: Exception) {
                byUuid.remove(u)
            }
        }
    }

    @JvmStatic
    fun badgesFor(uuidNoDashes: String): List<BadgeDef> {
        val ids = byUuid[uuidNoDashes] ?: return emptyList()
        return BadgeRegistry.resolveOrdered(ids)
    }
}
