package fishmod.utils.config

import fishmod.shaded.practicalconfig.manager.ConfigManager
import fishmod.features.dungeon.FishEstTotal
import fishmod.features.dungeon.FishPuzzleDisplay
import fishmod.features.dungeon.f7.F7Huds
import fishmod.utils.config.values.Buttons
import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.DungeonMapSettings
import fishmod.utils.config.values.FishSettings
import fishmod.utils.config.values.Floor7
import fishmod.utils.config.values.Visual
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Section
import fishmod.utils.dungeon.Split

object FishConfig {

    private val classes: List<Class<*>> = listOf(
            FishSettings::class.java, FishPuzzleDisplay::class.java, FishEstTotal::class.java,
            Phase::class.java,
            Section::class.java,
            fishmod.features.dungeon.f7.TerminalTracker::class.java,
            Split::class.java,
            Dungeons::class.java,
            Floor7::class.java,
            Visual::class.java,
            Buttons::class.java,
            F7Huds::class.java,
            DungeonMapSettings::class.java,
            fishmod.features.extras.ExtrasSettings::class.java,
            fishmod.features.diana.DianaSettings::class.java,
            fishmod.features.mining.MiningSettings::class.java,
            fishmod.features.kuudra.KuudraSettings::class.java,
            fishmod.features.rift.RiftSettings::class.java,
            fishmod.features.garden.GardenSettings::class.java,
            fishmod.features.garden.GardenExtraSettings::class.java,
            fishmod.features.garden.GardenMilestoneSettings::class.java,
            fishmod.features.achievements.AchSettings::class.java
        )

    init { ColorDefaults.snapshot(classes) }

    @JvmField
    val manager: ConfigManager = ConfigManager("config/fishmod-settings.json", classes)
}
