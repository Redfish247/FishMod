package fishmod.utils.config

import config.practical.manager.ConfigManager
import fishmod.utils.config.values.ExtraOptions

object Config {

    @JvmField
    val manager: ConfigManager = ConfigManager(
        FolderUtility.OLD_PATH + FolderUtility.ADDONS_NAME,
        listOf(
            ExtraOptions::class.java
        )
    )
}
