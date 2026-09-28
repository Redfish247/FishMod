package fishmod.features.diana

import fishmod.shaded.practicalconfig.manager.ConfigValue

object DianaSettings {

    // Guessing / burrows
    @ConfigValue @JvmField var dianaSpadeGuess: Boolean = true
    @ConfigValue @JvmField var dianaArrowGuess: Boolean = true
    @ConfigValue @JvmField var dianaBurrowDetection: Boolean = true
    @ConfigValue @JvmField var dianaSubGuesses: Boolean = true
    @ConfigValue @JvmField var dianaSubGuessText: Boolean = false
    @ConfigValue @JvmField var dianaOrderLines: Boolean = true
    @ConfigValue @JvmField var dianaGuessLine: Boolean = true
    @ConfigValue @JvmField var dianaBeaconBeam: Boolean = false
    @ConfigValue @JvmField var dianaBeaconDistance: Int = 8
    @ConfigValue @JvmField var dianaChainEndTitle: Boolean = false

    // Warp
    @ConfigValue @JvmField var dianaWarpCastle: Boolean = true
    @ConfigValue @JvmField var dianaWarpWizard: Boolean = true
    @ConfigValue @JvmField var dianaWarpCrypt: Boolean = true
    @ConfigValue @JvmField var dianaWarpStonks: Boolean = true
    @ConfigValue @JvmField var dianaWarpDa: Boolean = true
    @ConfigValue @JvmField var dianaWarpTaylor: Boolean = true
    @ConfigValue @JvmField var dianaWarpMuseum: Boolean = true
    @ConfigValue @JvmField var dianaDontWarpNearBurrow: Boolean = true
    @ConfigValue @JvmField var dianaWarpBlockDiff: Int = 22
    @ConfigValue @JvmField var dianaBadWarpDistance: Int = 80
    @ConfigValue @JvmField var dianaWarpTitle: Boolean = false
    @ConfigValue @JvmField var dianaWarpTitleSubtitle: Boolean = false

    // Rare mobs
    @ConfigValue @JvmField var dianaScanRareMobs: Boolean = true
    @ConfigValue @JvmField var dianaShareRareMob: Boolean = true
    @ConfigValue @JvmField var dianaReceiveRareMob: Boolean = true
    @ConfigValue @JvmField var dianaHighlightRareMobs: Boolean = false
    @ConfigValue @JvmField var dianaRareMobLine: Boolean = true
    @ConfigValue @JvmField var dianaCocoonTitle: Boolean = true
    @ConfigValue @JvmField var dianaCocoonParty: Boolean = true
    @ConfigValue @JvmField var dianaMythosHp: Boolean = true
    @ConfigValue @JvmField var dianaNoShuriken: Boolean = false
    @ConfigValue @JvmField var dianaInqSpawnText: String = ""
    @ConfigValue @JvmField var dianaMantiSpawnText: String = ""
    @ConfigValue @JvmField var dianaSphinxSpawnText: String = ""
    @ConfigValue @JvmField var dianaKingSpawnText: String = ""
    @ConfigValue @JvmField var dianaTitleFadeIn: Int = 0
    @ConfigValue @JvmField var dianaTitleStay: Int = 60
    @ConfigValue @JvmField var dianaTitleFadeOut: Int = 0
    @ConfigValue @JvmField var dianaRareMobSound: String = "Ping"
    @ConfigValue @JvmField var dianaRareMobVolume: Int = 100

    // Tracker
    @ConfigValue @JvmField var dianaLootTracker: String = "Event"
    @ConfigValue @JvmField var dianaMobTracker: String = "Off"
    @ConfigValue @JvmField var dianaStatsTracker: Boolean = true
    @ConfigValue @JvmField var dianaMfTracker: Boolean = false
    @ConfigValue @JvmField var dianaHideUnobtained: Boolean = true
    @ConfigValue @JvmField var dianaAfkTimeout: Int = 30
    @ConfigValue @JvmField var dianaPriceMode: String = "Sell Offer"
    @ConfigValue @JvmField var dianaStatsMessage: Boolean = true

    // Announcers
    @ConfigValue @JvmField var dianaRareDropChat: Boolean = true
    @ConfigValue @JvmField var dianaHiltMessage: Boolean = true
    @ConfigValue @JvmField var dianaLootScreen: Boolean = true
    @ConfigValue @JvmField var dianaLootParty: Boolean = true
    @ConfigValue @JvmField var dianaMsgChimera: String = ""
    @ConfigValue @JvmField var dianaMsgCore: String = ""
    @ConfigValue @JvmField var dianaMsgStinger: String = ""
    @ConfigValue @JvmField var dianaMsgFood: String = ""
    @ConfigValue @JvmField var dianaMsgWool: String = ""

    // Chat
    @ConfigValue @JvmField var dianaPartyCommands: Boolean = true
    @ConfigValue @JvmField var dianaMessageHider: Boolean = true
    @ConfigValue @JvmField var dianaSphinxSolver: Boolean = true

    // Waypoint look
    @ConfigValue @JvmField var dianaColorClosestGuess: Int = 0xFF9933CC.toInt()
    @ConfigValue @JvmField var dianaColorOtherGuess: Int = 0xFF00F6FF.toInt()
    @ConfigValue @JvmField var dianaColorSubGuess: Int = 0xFF8C8C8C.toInt()
    @ConfigValue @JvmField var dianaColorOrderLine: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var dianaColorStart: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var dianaColorMob: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var dianaColorTreasure: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var dianaColorRareMob: Int = 0xFFFFD600.toInt()
    @ConfigValue @JvmField var dianaColorOther: Int = 0xFF0033FF.toInt()
    @ConfigValue @JvmField var dianaGlowKing: Int = 0xFFFF8C00.toInt()
    @ConfigValue @JvmField var dianaGlowInq: Int = 0xFFFF59BF.toInt()
    @ConfigValue @JvmField var dianaGlowManti: Int = 0xFF007300.toInt()
    @ConfigValue @JvmField var dianaGlowSphinx: Int = 0xFF59CCFF.toInt()
    @ConfigValue @JvmField var dianaDynamicOpacity: Boolean = false
    @ConfigValue @JvmField var dianaOpacity: Int = 75
    @ConfigValue @JvmField var dianaTextOpacity: Int = 100
    @ConfigValue @JvmField var dianaTextShadow: Boolean = false
    @ConfigValue @JvmField var dianaTextScale: Double = 1.0
    @ConfigValue @JvmField var dianaDistanceCutoff: Int = 50
    @ConfigValue @JvmField var dianaShowTimesDug: Boolean = true
    @ConfigValue @JvmField var dianaLineWidth: Int = 3

    // HUD positions
    @ConfigValue @JvmField var dianaLootPosX: Int = 5
    @ConfigValue @JvmField var dianaLootPosY: Int = 60
    @ConfigValue @JvmField var dianaLootPosScale: Double = 1.0
    @ConfigValue @JvmField var dianaMobPosX: Int = 5
    @ConfigValue @JvmField var dianaMobPosY: Int = 150
    @ConfigValue @JvmField var dianaMobPosScale: Double = 1.0
    @ConfigValue @JvmField var dianaStatsPosX: Int = 150
    @ConfigValue @JvmField var dianaStatsPosY: Int = 60
    @ConfigValue @JvmField var dianaStatsPosScale: Double = 1.0
    @ConfigValue @JvmField var dianaMfPosX: Int = 150
    @ConfigValue @JvmField var dianaMfPosY: Int = 200
    @ConfigValue @JvmField var dianaMfPosScale: Double = 1.0
    @ConfigValue @JvmField var dianaHpHudX: Int = 300
    @ConfigValue @JvmField var dianaHpHudY: Int = 40
    @ConfigValue @JvmField var dianaHpHudScale: Double = 1.0
    @ConfigValue @JvmField var dianaShurikenHudX: Int = 300
    @ConfigValue @JvmField var dianaShurikenHudY: Int = 120
    @ConfigValue @JvmField var dianaShurikenHudScale: Double = 1.0
}
