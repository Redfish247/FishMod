package fishmod.utils.config

import fishmod.shaded.practicalconfig.manager.ConfigManager
import fishmod.utils.config.values.MiscSettings

object ConfigRoot {

    init { ConfigKeyMigration.run(FishPaths.OLD_PATH + FishPaths.ADDONS_NAME) }

    @JvmField
    val manager: ConfigManager = ConfigManager(
        FishPaths.OLD_PATH + FishPaths.ADDONS_NAME,
        listOf(
            MiscSettings::class.java
        )
    )
}
