package fishmod.features.fishing

import fishmod.shaded.practicalconfig.manager.ConfigValue

object FishingSettings {
    @ConfigValue @JvmField var fishing: Boolean = false
    @ConfigValue @JvmField var fishingAnyArea: Boolean = false
    @ConfigValue @JvmField var fishingDebug: Boolean = false
}
