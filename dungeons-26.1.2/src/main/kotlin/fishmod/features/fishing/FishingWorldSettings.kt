package fishmod.features.fishing

import fishmod.shaded.practicalconfig.manager.ConfigValue

// Phase 4 (world/items) owns this file; already registered in FishConfig.
object FishingWorldSettings {
    @ConfigValue @JvmField var fishingWorld: Boolean = false
}
