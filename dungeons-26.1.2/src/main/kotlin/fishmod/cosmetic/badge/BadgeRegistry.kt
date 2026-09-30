package fishmod.cosmetic.badge

import fishmod.utils.debug.FishDiag
import fishmod.utils.HypixelApi
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents

object BadgeRegistry {

    @Volatile
    private var defs: Map<String, BadgeDef> = emptyMap()

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> FishDiag.guard("BadgeRegistry.1", "badge defs refresh failed") { refresh() } }
    }

    @JvmStatic
    fun refresh() {
        HypixelApi.fetchBadgeDefs { list ->
            FishDiag.check(list.isNotEmpty(), "BadgeRegistry.2") { "badge defs fetch returned nothing" }
            val map = LinkedHashMap<String, BadgeDef>()
            for (d in list) map[d.id] = d
            defs = map
        }
    }

    @JvmStatic
    fun resolveOrdered(ids: List<String>): List<BadgeDef> =
        ids.mapNotNull { id ->
            val d = defs[id]
            if (d == null && defs.isNotEmpty()) FishDiag.fail("BadgeRegistry.3", "unknown badge id '$id'")
            d
        }.sortedBy { it.order }
}
