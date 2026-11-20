package fishmod.features.slayers

import fishmod.shaded.practicalconfig.manager.ConfigValue

object EndAlertSettings {

    @ConfigValue @JvmField var superiorDragonAlert: Boolean = false
    @ConfigValue @JvmField var endstoneProtectorAlert: Boolean = false
    @ConfigValue @JvmField var endAlertDurationMs: Int = 2500

    @ConfigValue @JvmField var slayerMiniHighlight: Boolean = false
    @ConfigValue @JvmField var slayerMiniHighlightColor: Int = 0xFFFF5555.toInt()

    @ConfigValue @JvmField var groundItemNames: Boolean = false
    @ConfigValue @JvmField var groundItemRange: Int = 16
    @ConfigValue @JvmField var groundItemScale: Double = 1.0
}
