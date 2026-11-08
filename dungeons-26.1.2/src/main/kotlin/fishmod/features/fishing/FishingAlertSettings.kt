package fishmod.features.fishing

import fishmod.shaded.practicalconfig.manager.ConfigValue

// Phase 2 (alerts/share) owns this file; already registered in FishConfig.
object FishingAlertSettings {
    @ConfigValue @JvmField var fishingAlerts: Boolean = false
}
