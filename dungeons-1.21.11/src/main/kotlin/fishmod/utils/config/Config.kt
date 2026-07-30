package fishmod.utils.config

import config.practical.manager.ConfigManager
import fishmod.features.dungeon.PuzzleDisplay
import fishmod.utils.config.components.Components
import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.ExtraOptions
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Section
import fishmod.utils.dungeon.Split

object Config {

    @JvmField
    val manager: ConfigManager = ConfigManager(
        FolderUtility.OLD_PATH + FolderUtility.ADDONS_NAME,
        listOf(
            Phase::class.java, Section::class.java, Split::class.java, ExtraOptions::class.java,
            Components::class.java, Dungeons::class.java, PuzzleDisplay::class.java
        )
    )
}
