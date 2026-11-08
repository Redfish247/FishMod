package fishmod.features.fishing

import fishmod.shaded.practicalconfig.manager.ConfigValue

// Phase 4 (world/items) owns this file; already registered in FishConfig. Everything ships OFF.
object FishingWorldSettings {
    @ConfigValue @JvmField var fishingWorld: Boolean = false

    // Hook timer + bite indicator
    @ConfigValue @JvmField var hookTimer: Boolean = false
    @ConfigValue @JvmField var hookTimerHideStand: Boolean = true
    @ConfigValue @JvmField var hookTimerScale: Double = 1.5
    @ConfigValue @JvmField var hookBiteText: String = "§c§l!!!"

    // Rare sea creatures
    @ConfigValue @JvmField var rareHighlight: Boolean = false
    @ConfigValue @JvmField var rareHighlightColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var rareHp: Boolean = false
    @ConfigValue @JvmField var rareImmunity: Boolean = false

    // Hiding
    @ConfigValue @JvmField var hideOtherHooks: Boolean = false
    @ConfigValue @JvmField var hidePlayersNearBobber: Boolean = false
    @ConfigValue @JvmField var hidePlayersNearBobberRadius: Double = 8.0

    // Wormhole finder (Froggles arrows -> waypoint); own toggle, off by default
    @ConfigValue @JvmField var wormholeFinder: Boolean = false
    @ConfigValue @JvmField var wormholeDeparture: Boolean = true
    @ConfigValue @JvmField var wormholeDebug: Boolean = false
    @ConfigValue @JvmField var wormholeColor: Int = 0xFFFF55FF.toInt()

    // Hotspot finder (waypoints to loaded hotspot stands + Hotspot Radar flame-trail guess); own toggle, off by default
    @ConfigValue @JvmField var hotspotFinder: Boolean = false
    @ConfigValue @JvmField var hotspotRadar: Boolean = true
    @ConfigValue @JvmField var hotspotDebug: Boolean = false
    @ConfigValue @JvmField var hotspotColor: Int = 0xFFFFAA00.toInt()

    // Item overlays
    @ConfigValue @JvmField var fishingItems: Boolean = false
    @ConfigValue @JvmField var trashBooks: Boolean = false
    @ConfigValue @JvmField var trashBooksList: String = ""
    @ConfigValue @JvmField var trashBooksColor: Int = 0x90FF0000.toInt()
    @ConfigValue @JvmField var rodParts: Boolean = false
    @ConfigValue @JvmField var thunderBottle: Boolean = false
}
