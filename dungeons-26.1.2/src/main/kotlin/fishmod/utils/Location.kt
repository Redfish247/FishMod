package fishmod.utils

import fishmod.utils.debug.Debug
import fishmod.utils.events.Events
import net.hypixel.data.type.ServerType
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket
import net.minecraft.network.chat.Component

enum class Location(val name2: String) {
    UNKNOWN("Unknown"),
    DUNGEON("Dungeon"),
    DUNGEON_HUB("Dungeon Hub"),
    PRIVATE_ISLAND("Private Island"),
    HUB("Hub"),
    GALATEA("Galatea"),
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

    override fun toString(): String {
        return "Location: " + name2 + " Last changed: " + (System.currentTimeMillis() - lastChanged) / 1000.0 + "s"
    }

    companion object {
        private var currentLocation: Location = UNKNOWN
        private var inSkyblockFlag: Boolean = false
        private var detectedNewLocation: Boolean = false
        private var lastChanged: Long = System.currentTimeMillis()

        @JvmStatic
        fun init() {
            val instance = HypixelModAPI.getInstance()
            instance.createHandler(ClientboundLocationPacket::class.java) { packet ->
                packet.map.ifPresent { locationName ->
                    if (packet.serverType.isPresent) {
                        val serverType: ServerType = packet.serverType.get()
                        inSkyblockFlag = serverType.name == "SkyBlock"
                    }

                    changeLocation(getLocation(locationName))
                }
            }

            instance.subscribeToEventPacket(ClientboundLocationPacket::class.java)

            Events.ON_WORLD_CHANGE.register {
                detectedNewLocation = false
                false
            }
        }

        @JvmStatic
        fun getLocation(locationName: String): Location {
            var location: Location
            try {
                location = valueOf(locationName.uppercase().replace(" ", "_").replace("'", ""))
            } catch (ignored: IllegalArgumentException) {
                location = UNKNOWN
            }
            return location
        }

        @JvmStatic
        fun changeLocation(location: Location) {
            currentLocation = location
            detectedNewLocation = true
            lastChanged = System.currentTimeMillis()
            Debug.sendDebugMessage(Component.literal("Location: $currentLocation"))

            Events.ON_LOCATION_CHANGE.invoke { locationChangeEvent -> locationChangeEvent.onLocationChange(currentLocation) }
        }

        @JvmStatic
        fun `in`(location: Location): Boolean {
            if (fishmod.utils.dungeon.PracticeMode.active && location == DUNGEON) return true
            if (!inSkyblockFlag) return false
            return currentLocation == location
        }

        @JvmStatic
        fun inDungeon(): Boolean {
            if (fishmod.utils.dungeon.PracticeMode.active) return true
            if (!inSkyblockFlag) return false
            return currentLocation == DUNGEON
        }

        @JvmStatic
        fun inDungeonHub(): Boolean = inSkyblockFlag && currentLocation == DUNGEON_HUB

        @JvmStatic
        fun getCurrentLocation(): Location = currentLocation

        @JvmStatic
        fun inSkyblock(): Boolean = inSkyblockFlag || fishmod.utils.dungeon.PracticeMode.active

        @JvmStatic
        fun hasReceivedLocation(): Boolean = detectedNewLocation
    }
}
