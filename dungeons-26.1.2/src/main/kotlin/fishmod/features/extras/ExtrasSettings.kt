package fishmod.features.extras

import fishmod.shaded.practicalconfig.manager.ConfigValue

object ExtrasSettings {
    // Arrow HUD
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

    // Status bars
    @ConfigValue @JvmField var statusBars: Boolean = false
    @ConfigValue @JvmField var statusBarsHideVanilla: Boolean = true
    @ConfigValue @JvmField var statusBarsHealth: Boolean = true
    @ConfigValue @JvmField var statusBarsMana: Boolean = true
    @ConfigValue @JvmField var statusBarsDefense: Boolean = true
    @ConfigValue @JvmField var statusBarsXp: Boolean = true
    @ConfigValue @JvmField var statusBarX: Int = -1
    @ConfigValue @JvmField var statusBarY: Int = -1
    @ConfigValue @JvmField var statusBarsScale: Double = 1.0
    @ConfigValue @JvmField var statusBarsHealthColor: Int = 0xFFFF3B3B.toInt()
    @ConfigValue @JvmField var statusBarsOverflowColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var statusBarsManaColor: Int = 0xFF4FE3F0.toInt()
    @ConfigValue @JvmField var statusBarsDefenseColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var statusBarsXpColor: Int = 0xFF80FF20.toInt()
    @ConfigValue @JvmField var statusBarsTextColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var statusBarsTextShadow: Boolean = true
    @ConfigValue @JvmField var statusBarsBgOpacity: Int = 50

    // Limbo alert
    @ConfigValue @JvmField var limboAlert: Boolean = false
    @ConfigValue @JvmField var limboTitle: String = "§cIN LIMBO"
    @ConfigValue @JvmField var limboSound: String = "Anvil Land"
    @ConfigValue @JvmField var limboVolume: Int = 100

    // Party join sound
    @ConfigValue @JvmField var partyJoinSound: Boolean = false
    @ConfigValue @JvmField var partyJoinSoundName: String = "Note: Pling"
    @ConfigValue @JvmField var partyJoinVolume: Int = 100
    @ConfigValue @JvmField var partyJoinTitle: Boolean = false

    // Patcher waypoints
    @ConfigValue @JvmField var patcherWaypoints: Boolean = false
    @ConfigValue @JvmField var patcherWaypointColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var patcherWaypointSeconds: Int = 60
    @ConfigValue @JvmField var patcherWaypointBeam: Boolean = true
    @ConfigValue @JvmField var patcherWaypointRemoveNear: Boolean = true
    @ConfigValue @JvmField var patcherWaypointTextScale: Double = 1.0

    // Fairy soul waypoints
    @ConfigValue @JvmField var fairySoulWaypoints: Boolean = false
    @ConfigValue @JvmField var fairySoulColor: Int = 0xFFFF55FF.toInt()
    @ConfigValue @JvmField var fairySoulBeam: Boolean = true
    @ConfigValue @JvmField var fairySoulText: Boolean = true

    // Item overlays + tooltip
    @ConfigValue @JvmField var overlayItemLevels: Boolean = false
    @ConfigValue @JvmField var overlayBookLevel: Boolean = true
    @ConfigValue @JvmField var overlayPetLevel: Boolean = true
    @ConfigValue @JvmField var overlayMinionTier: Boolean = true
    @ConfigValue @JvmField var overlayCakeYear: Boolean = true
    @ConfigValue @JvmField var overlayTextColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var obtainedDateTooltip: Boolean = false
    @ConfigValue @JvmField var suggestionClickAhs: Boolean = true

    // PV maxed highlight
    @ConfigValue @JvmField var pvMaxedColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var pvMaxedRainbow: Boolean = false
}
