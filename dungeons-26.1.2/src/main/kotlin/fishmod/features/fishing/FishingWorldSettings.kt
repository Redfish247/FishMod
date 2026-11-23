package fishmod.features.fishing

import fishmod.shaded.practicalconfig.manager.ConfigValue

object FishingWorldSettings {
    @ConfigValue @JvmField var fishingWorld: Boolean = false

    @ConfigValue @JvmField var baitHud: Boolean = false
    @ConfigValue @JvmField var rodTimer: Boolean = false
    @ConfigValue @JvmField var baitHudX: Int = 5
    @ConfigValue @JvmField var baitHudY: Int = 100
    @ConfigValue @JvmField var baitHudScale: Double = 1.0


    @ConfigValue @JvmField var shardTracker: Boolean = false
    @ConfigValue @JvmField var shardTrackerResetOnSwap: Boolean = false
    @ConfigValue @JvmField var shardTrackerRows: Int = 8
    @ConfigValue @JvmField var shardTrackerX: Int = 5
    @ConfigValue @JvmField var shardTrackerY: Int = 115
    @ConfigValue @JvmField var shardTrackerScale: Double = 1.0
    @ConfigValue @JvmField var shardTrackerProfit: Boolean = true

    @ConfigValue @JvmField var foragingTracker: Boolean = false
    @ConfigValue @JvmField var foragingTrackerResetOnSwap: Boolean = false
    @ConfigValue @JvmField var foragingTrackerRows: Int = 6
    @ConfigValue @JvmField var foragingTrackerX: Int = 5
    @ConfigValue @JvmField var foragingTrackerY: Int = 130
    @ConfigValue @JvmField var foragingTrackerScale: Double = 1.0
    @ConfigValue @JvmField var treecapTimer: Boolean = false
    @ConfigValue @JvmField var treecapCooldownMs: Int = 1000
    @ConfigValue @JvmField var treecapX: Int = 5
    @ConfigValue @JvmField var treecapY: Int = 260
    @ConfigValue @JvmField var treecapScale: Double = 1.0

    @ConfigValue @JvmField var hookTimer: Boolean = false
    @ConfigValue @JvmField var hookTimerHideStand: Boolean = true
    @ConfigValue @JvmField var hookTimerScale: Double = 1.5
    @ConfigValue @JvmField var hookBiteText: String = "§c§l!!!"
    @ConfigValue @JvmField var hookBiteSound: Boolean = false
    @ConfigValue @JvmField var hookBiteSoundName: String = "pling"

    @ConfigValue @JvmField var cinderbatHighlight: Boolean = false
    @ConfigValue @JvmField var cinderbatColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var invisibugHighlight: Boolean = false
    @ConfigValue @JvmField var invisibugColor: Int = 0x80FF5555.toInt()
    @ConfigValue @JvmField var lassoAlert: Boolean = false
    @ConfigValue @JvmField var lassoSound: String = "pling"
    @ConfigValue @JvmField var lassoPopup: Boolean = true

    @ConfigValue @JvmField var geyserBox: Boolean = false
    @ConfigValue @JvmField var geyserColor: Int = 0x60FF5555.toInt()
    @ConfigValue @JvmField var skitterBox: Boolean = false
    @ConfigValue @JvmField var skitterFilled: Boolean = false
    @ConfigValue @JvmField var skitterColor: Int = 0x40FFFF55.toInt()

    @ConfigValue @JvmField var scKillTimer: Boolean = false
    @ConfigValue @JvmField var scCap: Boolean = false
    @ConfigValue @JvmField var scCapMax: Int = 10
    @ConfigValue @JvmField var scCapSound: String = "pling"
    @ConfigValue @JvmField var scCapHud: Boolean = true
    @ConfigValue @JvmField var scCapX: Int = 5
    @ConfigValue @JvmField var scCapY: Int = 200
    @ConfigValue @JvmField var scCapScale: Double = 1.0

    @ConfigValue @JvmField var trophyHud: Boolean = false
    @ConfigValue @JvmField var trophyHudX: Int = 5
    @ConfigValue @JvmField var trophyHudY: Int = 215
    @ConfigValue @JvmField var trophyHudScale: Double = 1.0

    @ConfigValue @JvmField var fusionKeys: Boolean = false
    @ConfigValue @JvmField var mutePhantoms: Boolean = false

    @ConfigValue @JvmField var totemOverlay: Boolean = false
    @ConfigValue @JvmField var totemRing: Boolean = true
    @ConfigValue @JvmField var totemDistance: Int = 20
    @ConfigValue @JvmField var totemColor: Int = 0xFFAA00AA.toInt()
    @ConfigValue @JvmField var totemX: Int = 5
    @ConfigValue @JvmField var totemY: Int = 240
    @ConfigValue @JvmField var totemScale: Double = 1.0

    @ConfigValue @JvmField var goldenFishTimer: Boolean = false
    @ConfigValue @JvmField var goldenFishX: Int = 5
    @ConfigValue @JvmField var goldenFishY: Int = 170
    @ConfigValue @JvmField var goldenFishScale: Double = 1.0

    @ConfigValue @JvmField var rareHighlight: Boolean = false
    @ConfigValue @JvmField var rareHighlightColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var rareHp: Boolean = false
    @ConfigValue @JvmField var rareImmunity: Boolean = false

    @ConfigValue @JvmField var hideOtherHooks: Boolean = false
    @ConfigValue @JvmField var hidePlayersNearBobber: Boolean = false
    @ConfigValue @JvmField var hidePlayersNearBobberRadius: Double = 8.0
    @ConfigValue @JvmField var hideCommonCreatures: Boolean = false

    @ConfigValue @JvmField var wormholeFinder: Boolean = false
    @ConfigValue @JvmField var wormholeDeparture: Boolean = true
    @ConfigValue @JvmField var wormholeDebug: Boolean = false
    @ConfigValue @JvmField var wormholeColor: Int = 0xFFFF55FF.toInt()

    @ConfigValue @JvmField var hotspotFinder: Boolean = false
    @ConfigValue @JvmField var hotspotRadar: Boolean = true
    @ConfigValue @JvmField var hotspotDebug: Boolean = false
    @ConfigValue @JvmField var hotspotColor: Int = 0xFFFFAA00.toInt()

    @ConfigValue @JvmField var fishingItems: Boolean = false
    @ConfigValue @JvmField var trashBooks: Boolean = false
    @ConfigValue @JvmField var trashBooksList: String = ""
    @ConfigValue @JvmField var trashBooksColor: Int = 0x90FF0000.toInt()
    @ConfigValue @JvmField var rodParts: Boolean = false
    @ConfigValue @JvmField var thunderBottle: Boolean = false
}
