package fishmod.utils.config

import fishmod.shaded.practicalconfig.manager.ConfigManager
import fishmod.utils.config.values.MiscSettings

object ConfigRoot {

    @JvmField
    val manager: ConfigManager = ConfigManager(
        FishPaths.OLD_PATH + FishPaths.ADDONS_NAME,
        listOf(
            MiscSettings::class.java
        )
    )
}
