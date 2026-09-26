package fishmod.cosmetic.badge

import fishmod.utils.HypixelApi
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents

object BadgeRegistry {

    @Volatile
    private var defs: Map<String, BadgeDef> = emptyMap()

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> refresh() }
    }

    @JvmStatic
    fun refresh() {
        HypixelApi.fetchBadgeDefs { list ->
            val map = LinkedHashMap<String, BadgeDef>()
            for (d in list) map[d.id] = d
            defs = map
        }
    }

    @JvmStatic
    fun resolveOrdered(ids: List<String>): List<BadgeDef> =
        ids.mapNotNull { defs[it] }.sortedBy { it.order }
}
