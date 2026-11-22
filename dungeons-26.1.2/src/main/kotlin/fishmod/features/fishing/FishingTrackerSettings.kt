package fishmod.features.fishing

import fishmod.shaded.practicalconfig.manager.ConfigValue

object FishingTrackerSettings {
    @ConfigValue @JvmField var fishingTrackers: Boolean = false

    @ConfigValue @JvmField var fishingTrackerMode: String = "Session"
    @ConfigValue @JvmField var fishingTrackerLines: Int = 10
    @ConfigValue @JvmField var fishingCostPerCatch: Int = 0
    @ConfigValue @JvmField var fishingBarnSeconds: Int = 300

    @ConfigValue @JvmField var fishingShowWhen: String = "Both"

    @ConfigValue @JvmField var fishingScHud: Boolean = false
    @ConfigValue @JvmField var fishingScHudX: Int = 5
    @ConfigValue @JvmField var fishingScHudY: Int = 60
    @ConfigValue @JvmField var fishingScHudScale: Double = 1.0

    @ConfigValue @JvmField var fishingProfitHud: Boolean = false
    @ConfigValue @JvmField var fishingProfitHudX: Int = 5
    @ConfigValue @JvmField var fishingProfitHudY: Int = 160
    @ConfigValue @JvmField var fishingProfitHudScale: Double = 1.0

    @ConfigValue @JvmField var fishingEffHud: Boolean = false
    @ConfigValue @JvmField var fishingEffHudX: Int = 5
    @ConfigValue @JvmField var fishingEffHudY: Int = 240
    @ConfigValue @JvmField var fishingEffHudScale: Double = 1.0

    @ConfigValue @JvmField var fishingBarnHud: Boolean = false
    @ConfigValue @JvmField var fishingBarnHudX: Int = 5
    @ConfigValue @JvmField var fishingBarnHudY: Int = 290
    @ConfigValue @JvmField var fishingBarnHudScale: Double = 1.0

    @ConfigValue @JvmField var fishingFestivalHud: Boolean = false
    @ConfigValue @JvmField var fishingFestivalHudX: Int = 5
    @ConfigValue @JvmField var fishingFestivalHudY: Int = 310
    @ConfigValue @JvmField var fishingFestivalHudScale: Double = 1.0

    @ConfigValue @JvmField var fishingAreaHud: Boolean = false
    @ConfigValue @JvmField var fishingAreaHudX: Int = 200
    @ConfigValue @JvmField var fishingAreaHudY: Int = 60
    @ConfigValue @JvmField var fishingAreaHudScale: Double = 1.0
    @ConfigValue @JvmField var fishingAreaTreasure: Boolean = false
    @ConfigValue @JvmField var fishingAreaDice: Boolean = false
}
