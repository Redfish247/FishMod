package fishmod.features.slayer

import fishmod.shaded.practicalconfig.manager.ConfigValue

object EndAlertSettings {

    @ConfigValue @JvmField var superiorDragonAlert: Boolean = false
    @ConfigValue @JvmField var endstoneProtectorAlert: Boolean = false
    @ConfigValue @JvmField var endAlertDurationMs: Int = 2500

    @ConfigValue @JvmField var slayerMiniHighlight: Boolean = false
    @ConfigValue @JvmField var slayerMiniHighlightColor: Int = 0xFFFF5555.toInt()

    @ConfigValue @JvmField var slayerBossBox: Boolean = false
    @ConfigValue @JvmField var slayerBossBoxSpawnMark: Boolean = true
    @ConfigValue @JvmField var slayerBossBoxColor: Int = 0xFFFF3333.toInt()

    @ConfigValue @JvmField var cocoonTimer: Boolean = false
    @ConfigValue @JvmField var protectorHud: Boolean = false
    @ConfigValue @JvmField var protectorHudX: Int = 5
    @ConfigValue @JvmField var protectorHudY: Int = 90
    @ConfigValue @JvmField var protectorHudScale: Double = 1.0
    @ConfigValue @JvmField var bossHud: Boolean = false
    @ConfigValue @JvmField var bossHudX: Int = 5
    @ConfigValue @JvmField var bossHudY: Int = 60
    @ConfigValue @JvmField var bossHudScale: Double = 1.0
    @ConfigValue @JvmField var attuneHighlight: Boolean = false
    @ConfigValue @JvmField var summonsRecallAlert: Boolean = false
    @ConfigValue @JvmField var summonsHud: Boolean = false
    @ConfigValue @JvmField var summonsHudX: Int = 5
    @ConfigValue @JvmField var summonsHudY: Int = 245
    @ConfigValue @JvmField var summonsHudScale: Double = 1.0

    @ConfigValue @JvmField var dragonTracker: Boolean = false
    @ConfigValue @JvmField var dragonTrackerX: Int = 5
    @ConfigValue @JvmField var dragonTrackerY: Int = 120
    @ConfigValue @JvmField var dragonTrackerScale: Double = 1.0

    @ConfigValue @JvmField var phaseEggHits: Boolean = true
    @ConfigValue @JvmField var phaseFirePillar: Boolean = true

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
