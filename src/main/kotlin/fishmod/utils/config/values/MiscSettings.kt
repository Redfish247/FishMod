package fishmod.utils.config.values

import fishmod.shaded.practicalconfig.manager.ConfigValue
import fishmod.utils.FishConstants

object MiscSettings {
    @ConfigValue @JvmField var muteAbilityCooldown: Boolean = true

    @ConfigValue @JvmField var chatPrefix: String = ""

    @ConfigValue @JvmField var lockHotbarScroll: Boolean = false

    @ConfigValue @JvmField var hideRecipeBook: Boolean = false

    @ConfigValue @JvmField var chatCopy: Boolean = false

    @ConfigValue @JvmField var chatCopyStripColors: Boolean = false

    @ConfigValue @JvmField var chatCopyAmpersand: Boolean = false

    @ConfigValue @JvmField var chatCopySingleLine: Boolean = false

    @ConfigValue @JvmField var timerLabelColor: Int = FishConstants.DARK_PURPLE
}
