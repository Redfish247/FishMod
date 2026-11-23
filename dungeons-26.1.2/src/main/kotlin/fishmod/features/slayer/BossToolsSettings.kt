package fishmod.features.slayer

import fishmod.shaded.practicalconfig.manager.ConfigValue

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

    @ConfigValue @JvmField var sunGecko: Boolean = false
    @ConfigValue @JvmField var sunGeckoModifiers: Boolean = true
    @ConfigValue @JvmField var sunGeckoX: Int = 5
    @ConfigValue @JvmField var sunGeckoY: Int = 230
    @ConfigValue @JvmField var sunGeckoScale: Double = 1.0
}
