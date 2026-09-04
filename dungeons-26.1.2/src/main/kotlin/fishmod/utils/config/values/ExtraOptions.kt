package fishmod.utils.config.values

import config.practical.manager.ConfigValue
import fishmod.utils.Constants

object ExtraOptions {
    @ConfigValue @JvmField var disableAbilityCooldownSound: Boolean = true

    @ConfigValue @JvmField var textPrefix: String = ""

    @ConfigValue @JvmField var disableScrollHotbar: Boolean = false

    @ConfigValue @JvmField var disableRecipeBook: Boolean = false

    @ConfigValue @JvmField var copyChat: Boolean = false

    @ConfigValue @JvmField var removeColorCodes: Boolean = false

    @ConfigValue @JvmField var replaceColorChars: Boolean = false

    @ConfigValue @JvmField var copyLineOnly: Boolean = false

    @ConfigValue @JvmField var timerPrefixColor: Int = Constants.DARK_PURPLE

    @ConfigValue @JvmField var toggleableSearchBar: Boolean = false
}
