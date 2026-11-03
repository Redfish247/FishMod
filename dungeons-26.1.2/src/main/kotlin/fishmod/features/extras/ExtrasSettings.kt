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

    // Enchant colours (item tooltips + PV)
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
}
