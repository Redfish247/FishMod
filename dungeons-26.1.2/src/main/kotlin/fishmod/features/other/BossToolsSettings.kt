package fishmod.features.other

import fishmod.shaded.practicalconfig.manager.ConfigValue

// Settings for Ghost Counter, extra boss damage indicator and Sun Gecko helper
object BossToolsSettings {

    @ConfigValue @JvmField var ghostCounter: Boolean = false
    @ConfigValue @JvmField var ghostX: Int = 5
    @ConfigValue @JvmField var ghostY: Int = 200
    @ConfigValue @JvmField var ghostScale: Double = 1.0

    @ConfigValue @JvmField var bossIndicator: Boolean = false
    @ConfigValue @JvmField var bossIndicatorDungeon: Boolean = true
    @ConfigValue @JvmField var bossIndicatorNether: Boolean = true
    @ConfigValue @JvmField var bossIndicatorOther: Boolean = true
    @ConfigValue @JvmField var bossIndicatorTime: Boolean = true
}
