package fishmod.features.fishing

import fishmod.shaded.practicalconfig.manager.ConfigValue

// Phase 3 (trackers) owns this file; already registered in FishConfig.
object FishingTrackerSettings {
    @ConfigValue @JvmField var fishingTrackers: Boolean = false
}
