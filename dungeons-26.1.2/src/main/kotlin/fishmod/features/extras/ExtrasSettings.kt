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
}
