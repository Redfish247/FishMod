package fishmod.utils.config

import config.practical.manager.ConfigManager
import fishmod.features.dungeon.FishEstTotal
import fishmod.features.dungeon.FishPuzzleDisplay
import fishmod.features.dungeon.f7.F7Huds
import fishmod.utils.config.values.Buttons
import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.DungeonMapSettings
import fishmod.utils.config.values.FishSettings
import fishmod.utils.config.values.Floor7
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Section
import fishmod.utils.dungeon.Split

/**
 * Separate config manager for FishMod-specific settings.
 * Stored in config/fishmod-settings.json, independent of blade-addons config.
 */
object FishConfig {

    @JvmField
    val manager: ConfigManager = ConfigManager(
        "config/fishmod-settings.json",
        listOf(
            FishSettings::class.java, FishPuzzleDisplay::class.java, FishEstTotal::class.java,
            Phase::class.java,
            Section::class.java,
            Split::class.java,
            Dungeons::class.java,
            Floor7::class.java,
            Buttons::class.java,
            F7Huds::class.java,
            DungeonMapSettings::class.java
        )
    )
}
