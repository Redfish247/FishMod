package fishmod.utils

import fishmod.utils.debug.FishDebug
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.PracticeMode
import fishmod.utils.events.FishEvents
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
import net.minecraft.network.chat.Component

// SkyBlock island from the Hypixel mod API location packet; label is the packet's map name.
enum class SkyblockLocation(val label: String) {
    UNKNOWN("Unknown"),
    DUNGEON("Dungeon"),
    DUNGEON_HUB("Dungeon Hub"),
    PRIVATE_ISLAND("Private Island"),
    HUB("Hub"),
    GALATEA("Galatea"),
    // own packet map names since the foraging/safari update (seen in error reports)
    MOONGLADE_MARSH("Moonglade Marsh"),
    TORRHUS_CANYON("Torrhus Canyon"),
    LOTUS_ATOLL("Lotus Atoll"),
    THE_PARK("The Park"),
    THE_FARMING_ISLANDS("The Farming Islands"),
    GOLD_MINE("Gold Mine"),
    DEEP_CAVERNS("Deep Caverns"),
    DWARVEN_MINES("Dwarven Mines"),
    CRYSTAL_HOLLOWS("Crystal Hollows"),
    SPIDERS_DEN("Spider's Den"),
    THE_END("The End"),
    CRIMSON_ISLE("Crimson Isle"),
    GARDEN("Garden"),
    THE_RIFT("The Rift"),
    BACKWATER_BAYOU("Backwater Bayou"),
    JERRYS_WORKSHOP("Jerry's Workshop"),
    MINESHAFT("Mineshaft"),
    DARK_AUCTION("Dark Auction"),
    KUUDRA("Kuudra");

    override fun toString(): String = "$label (${(System.currentTimeMillis() - since) / 1000.0}s ago)"

    companion object {
        // the dungeon run map is called "Catacombs"
        private val BY_MAP: Map<String, SkyblockLocation> = entries.associateBy { it.label.lowercase() } + ("catacombs" to DUNGEON)

        private var here: SkyblockLocation = UNKNOWN
        private var onSkyblock = false
        private var since = System.currentTimeMillis()

        // raw Hypixel server mode, e.g. "hub" or "combat_1"
        @JvmStatic var currentMode: String = ""
            private set

        @JvmStatic
        fun init() {
            val api = HypixelModAPI.getInstance()
            api.createHandler(ClientboundLocationPacket::class.java) { packet ->
                FishDiag.guard("SkyblockLocation.2", "location packet handling failed ($packet)") {
                    currentMode = packet.mode.orElse("")
                    val map = packet.map.orElse(null) ?: return@guard
                    packet.serverType.ifPresent { onSkyblock = it.name == "SkyBlock" }
                    changeLocation(getLocation(map))
                }
            }
            api.subscribeToEventPacket(ClientboundLocationPacket::class.java)
        }

        @JvmStatic
        fun getLocation(locationName: String): SkyblockLocation {
            BY_MAP[locationName.lowercase()]?.let { return it }
            if (onSkyblock) FishDiag.fail("SkyblockLocation.1", "unknown SkyBlock location name '$locationName'")
            return UNKNOWN
        }

        @JvmStatic
        fun changeLocation(location: SkyblockLocation) {
            here = location
            since = System.currentTimeMillis()
            FishDebug.sendDebugMessage(Component.literal("Island: $here"))
            FishDiag.guard("SkyblockLocation.3", "location change listener threw for ${location.label}") {
                FishEvents.locationChange.invoke { it.onLocationChange(here) }
            }
        }

        // practice mode fakes a dungeon in singleplayer
        @JvmStatic
        fun `in`(location: SkyblockLocation): Boolean =
            (location == DUNGEON && PracticeMode.active) || (onSkyblock && here == location)

        @JvmStatic
        fun inDungeon(): Boolean = `in`(DUNGEON)

        @JvmStatic
        fun inDungeonHub(): Boolean = onSkyblock && here == DUNGEON_HUB

        // FishDebug-only: lets singleplayer test worlds pretend to be on SkyBlock (/fm debug skyblock on).
        @JvmStatic
        fun devSetSkyblock(on: Boolean) { onSkyblock = on }

        @JvmStatic
        fun getCurrentLocation(): SkyblockLocation = here

        @JvmStatic
        fun inSkyblock(): Boolean = onSkyblock || PracticeMode.active
    }
}
