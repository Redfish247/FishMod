package fishmod.features.slayer

import fishmod.shaded.practicalconfig.manager.ConfigValue

object EndAlertSettings {

    @ConfigValue @JvmField var superiorDragonAlert: Boolean = false
    @ConfigValue @JvmField var endstoneProtectorAlert: Boolean = false
    @ConfigValue @JvmField var endAlertDurationMs: Int = 2500

    @ConfigValue @JvmField var slayerMiniHighlight: Boolean = false
    @ConfigValue @JvmField var slayerMiniHighlightColor: Int = 0xFFFF5555.toInt()

    @ConfigValue @JvmField var cocoonTimer: Boolean = false
    @ConfigValue @JvmField var summonsRecallAlert: Boolean = false

    @ConfigValue @JvmField var eggSacHighlight: Boolean = false
    @ConfigValue @JvmField var eggSacColor: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var yangGlyphTracer: Boolean = false
    @ConfigValue @JvmField var yangGlyphColor: Int = 0xFF00FFFF.toInt()
    @ConfigValue @JvmField var broodmotherHud: Boolean = false
    @ConfigValue @JvmField var broodmotherHudX: Int = 5
    @ConfigValue @JvmField var broodmotherHudY: Int = 200
    @ConfigValue @JvmField var broodmotherHudScale: Double = 1.0
    @ConfigValue @JvmField var noQuestWarning: Boolean = false
    @ConfigValue @JvmField var zealotHud: Boolean = false
    @ConfigValue @JvmField var zealotHudX: Int = 5
    @ConfigValue @JvmField var zealotHudY: Int = 215
    @ConfigValue @JvmField var zealotHudScale: Double = 1.0

    @ConfigValue @JvmField var groundItemNames: Boolean = false
    @ConfigValue @JvmField var groundItemRange: Int = 16
    @ConfigValue @JvmField var groundItemScale: Double = 1.0
}
