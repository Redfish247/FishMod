package fishmod.features.fishing

import fishmod.shaded.practicalconfig.manager.ConfigValue

// Core fishing settings; everything ships OFF.
object FishingSettings {
    // Master switch: nothing in features/fishing runs while this is off
    @ConfigValue @JvmField var fishing: Boolean = false
    // Skip the fishing-world check (testing; line strings are unverified)
    @ConfigValue @JvmField var fishingAnyArea: Boolean = false
    // Print every detected catch/drop to chat so strings can be checked in game
    @ConfigValue @JvmField var fishingDebug: Boolean = false
}
