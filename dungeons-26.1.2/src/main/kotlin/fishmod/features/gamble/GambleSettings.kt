package fishmod.features.gamble

import fishmod.shaded.practicalconfig.manager.ConfigValue

object GambleSettings {
    @ConfigValue @JvmField var gambleEnabled: Boolean = true
    // Fixed server address, not a setting.
    const val API_BASE = "https://fishmod.dev/api/gamble"
    @ConfigValue @JvmField var gamblePlaytime: Boolean = true
    @ConfigValue @JvmField var gambleShowPoints: Boolean = true
    @ConfigValue @JvmField var gambleLeaderboard: Boolean = true
    @ConfigValue @JvmField var gambleCrown: Boolean = true
    @ConfigValue @JvmField var gambleCrownChat: Boolean = true
}
