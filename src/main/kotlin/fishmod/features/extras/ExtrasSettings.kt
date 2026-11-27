package fishmod.features.extras

import fishmod.shaded.practicalconfig.manager.ConfigValue

object ExtrasSettings {
    @ConfigValue @JvmField var arrowHud: Boolean = false
    @ConfigValue @JvmField var arrowHudOnlyBow: Boolean = true
    @ConfigValue @JvmField var arrowHudExtra: Boolean = true
    @ConfigValue @JvmField var arrowHudRarity: Boolean = true
    @ConfigValue @JvmField var arrowHudX: Int = 10
    @ConfigValue @JvmField var arrowHudY: Int = 90
    @ConfigValue @JvmField var arrowHudScale: Double = 1.0
    @ConfigValue @JvmField var arrowHudColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var arrowLowWarn: Boolean = true
    @ConfigValue @JvmField var arrowLowThreshold: Int = 128
    @ConfigValue @JvmField var arrowOutWarn: Boolean = true
    @ConfigValue @JvmField var arrowWarnSound: String = "Note: Bass"
    @ConfigValue @JvmField var quiverWarning: Boolean = false
    @ConfigValue @JvmField var calendarRealTimes: Boolean = false
    @ConfigValue @JvmField var calendarTimeColor: Int = 0xFF55FFFF.toInt()

    @ConfigValue @JvmField var limboAlert: Boolean = false
    @ConfigValue @JvmField var limboTitle: String = "§cIN LIMBO"
    @ConfigValue @JvmField var limboSound: String = "Anvil Land"
    @ConfigValue @JvmField var limboVolume: Int = 100
    @ConfigValue @JvmField var restartAlert: Boolean = false
    @ConfigValue @JvmField var restartTitle: String = "§cSERVER RESTARTING"
    @ConfigValue @JvmField var restartSound: String = "Note: Pling"
    @ConfigValue @JvmField var restartVolume: Int = 100

    @ConfigValue @JvmField var partyJoinSound: Boolean = false
    @ConfigValue @JvmField var partyJoinSoundName: String = "Note: Pling"
    @ConfigValue @JvmField var partyJoinVolume: Int = 100
    @ConfigValue @JvmField var partyJoinTitle: Boolean = false

    @ConfigValue @JvmField var patcherWaypoints: Boolean = false
    @ConfigValue @JvmField var patcherWaypointColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var patcherWaypointSeconds: Int = 60
    @ConfigValue @JvmField var patcherWaypointBeam: Boolean = true
    @ConfigValue @JvmField var patcherWaypointRemoveNear: Boolean = true
    @ConfigValue @JvmField var patcherWaypointTextScale: Double = 1.0

    @ConfigValue @JvmField var fairySoulWaypoints: Boolean = false
    @ConfigValue @JvmField var fairySoulColor: Int = 0xFFFF55FF.toInt()
    @ConfigValue @JvmField var fairySoulBeam: Boolean = true
    @ConfigValue @JvmField var fairySoulText: Boolean = true

    @ConfigValue @JvmField var overlayItemLevels: Boolean = false
    @ConfigValue @JvmField var overlayBookLevel: Boolean = true
    @ConfigValue @JvmField var overlayPetLevel: Boolean = true
    @ConfigValue @JvmField var overlayPetHideMax: Boolean = false
    @ConfigValue @JvmField var overlayMinionTier: Boolean = true
    @ConfigValue @JvmField var overlayItemStars: Boolean = true
    @ConfigValue @JvmField var overlayCakeYear: Boolean = true
    @ConfigValue @JvmField var overlayTextColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var obtainedDateTooltip: Boolean = false
    @ConfigValue @JvmField var suggestionClickAhs: Boolean = true

    @ConfigValue @JvmField var pvMaxedColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var pvMaxedRainbow: Boolean = false

    @ConfigValue @JvmField var enchantColors: Boolean = false
    @ConfigValue @JvmField var enchantMaxColor: Int = 0xFFFF55FF.toInt()
    @ConfigValue @JvmField var enchantMaxChroma: Boolean = true
    @ConfigValue @JvmField var enchantHighColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var enchantHighChroma: Boolean = false
    @ConfigValue @JvmField var enchantUltColor: Int = 0xFFFF55FF.toInt()
    @ConfigValue @JvmField var enchantUltChroma: Boolean = false
    @ConfigValue @JvmField var enchantNormalColor: Int = 0xFF5555FF.toInt()
    @ConfigValue @JvmField var enchantNormalChroma: Boolean = false
    @ConfigValue @JvmField var enchantBold: Boolean = true

    @ConfigValue @JvmField var cookieWarning: Boolean = false
    @ConfigValue @JvmField var bitsWarning: Boolean = false
    @ConfigValue @JvmField var ahPriceCheck: Boolean = false
    @ConfigValue @JvmField var ahPriceCheckWarnPct: Int = 20
    @ConfigValue @JvmField var kickTimer: Boolean = false
    @ConfigValue @JvmField var sbDateHud: Boolean = false
    @ConfigValue @JvmField var sbDateHudX: Int = 10
    @ConfigValue @JvmField var sbDateHudY: Int = 110
    @ConfigValue @JvmField var sbDateHudScale: Double = 1.0
    @ConfigValue @JvmField var sbDateHudRealTime: Boolean = true
    @ConfigValue @JvmField var cfJacobSpam: Boolean = false
    @ConfigValue @JvmField var vanquisherShare: Boolean = false
    @ConfigValue @JvmField var fixDoubleClicks: Boolean = false
    @ConfigValue @JvmField var middleClickFix: Boolean = false
    @ConfigValue @JvmField var tabComplete: Boolean = false
    @ConfigValue @JvmField var wikiCommand: Boolean = false
    @ConfigValue @JvmField var unfocusedFps: Boolean = false
    @ConfigValue @JvmField var unfocusedFpsLimit: Int = 15
    @ConfigValue @JvmField var todoHud: Boolean = false
    @ConfigValue @JvmField var todoItems: String = ""
    @ConfigValue @JvmField var todoHudX: Int = 10
    @ConfigValue @JvmField var todoHudY: Int = 140
    @ConfigValue @JvmField var todoHudScale: Double = 1.0
    @ConfigValue @JvmField var todoHudTitleColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var todoHudColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var backpackPreview: Boolean = false
    @ConfigValue @JvmField var skillHud: Boolean = false
    @ConfigValue @JvmField var pickupLog: Boolean = false
    @ConfigValue @JvmField var commandTooltip: Boolean = false
    @ConfigValue @JvmField var compactBestiary: Boolean = false
    @ConfigValue @JvmField var sbXpChat: Boolean = false
    @ConfigValue @JvmField var orderFilledAlert: Boolean = false
    @ConfigValue @JvmField var orderFilledSound: String = "Note: Pling"
    @ConfigValue @JvmField var pickupLogX: Int = 10
    @ConfigValue @JvmField var pickupLogY: Int = 200
    @ConfigValue @JvmField var pickupLogScale: Double = 1.0
    @ConfigValue @JvmField var pickupLogSeconds: Int = 5
    @ConfigValue @JvmField var pickupLogGainColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var pickupLogLossColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var skillHudX: Int = 10
    @ConfigValue @JvmField var skillHudY: Int = 170
    @ConfigValue @JvmField var skillHudScale: Double = 1.0
    @ConfigValue @JvmField var skillHudHideSeconds: Int = 60
    @ConfigValue @JvmField var skillHudColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var backpackPreviewTint: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var hoppitySummary: Boolean = false
    @ConfigValue @JvmField var trickTreatAlert: Boolean = false
    @ConfigValue @JvmField var trickTreatTitle: String = "§6TRICK OR TREAT CHEST"
    @ConfigValue @JvmField var hideBuffMessages: Boolean = false
    @ConfigValue @JvmField var outbidAlert: Boolean = false
    @ConfigValue @JvmField var outbidSound: String = "Note: Bass"
    @ConfigValue @JvmField var trevorHelper: Boolean = false
    @ConfigValue @JvmField var trevorGlowColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var giftTracker: Boolean = false
    @ConfigValue @JvmField var teleportPadNames: Boolean = false
    @ConfigValue @JvmField var hoppityEggWaypoints: Boolean = false
    @ConfigValue @JvmField var stackingEnchantTooltip: Boolean = false
    @ConfigValue @JvmField var jerryHighlight: Boolean = false
    @ConfigValue @JvmField var reindrakeAlert: Boolean = false
    @ConfigValue @JvmField var carnivalShootout: Boolean = false
    @ConfigValue @JvmField var strayRabbitAlert: Boolean = false
    @ConfigValue @JvmField var strayRabbitSound: String = "Note: Pling"
    @ConfigValue @JvmField var hoppityEggColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var giftHudX: Int = 10
    @ConfigValue @JvmField var giftHudY: Int = 230
    @ConfigValue @JvmField var giftHudScale: Double = 1.0
    @ConfigValue @JvmField var giftHudColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var hotxHelper: Boolean = false
    @ConfigValue @JvmField var hotxLevelColor: Int = 0x6055FF55
    @ConfigValue @JvmField var hotxMaxedColor: Int = 0x60FFAA00
    @ConfigValue @JvmField var beaconSolver: Boolean = false
    @ConfigValue @JvmField var beaconSolverColor: Int = 0xC855FF55.toInt()
}
