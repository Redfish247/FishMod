package fishmod.utils.config

import fishmod.shaded.practicalconfig.manager.ConfigManager
import fishmod.features.dungeon.FishEstTotal
import fishmod.features.dungeon.FishPuzzleDisplay
import fishmod.features.dungeon.f7.F7Huds
import fishmod.utils.config.values.InvButtonSettings
import fishmod.utils.config.values.DungeonSettings
import fishmod.utils.config.values.DungeonMapSettings
import fishmod.utils.config.values.FishSettings
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.config.values.VisualSettings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.dungeon.GoldorSection
import fishmod.utils.dungeon.RunSplit

object FishConfig {

    private val classes: List<Class<*>> = listOf(
            FishSettings::class.java, FishPuzzleDisplay::class.java, FishEstTotal::class.java,
            BossPhase::class.java,
            GoldorSection::class.java,
            fishmod.features.dungeon.f7.TerminalTracker::class.java,
            fishmod.features.other.Stopwatch::class.java,
            RunSplit::class.java,
            DungeonSettings::class.java,
            Floor7Settings::class.java,
            VisualSettings::class.java,
            InvButtonSettings::class.java,
            F7Huds::class.java,
            DungeonMapSettings::class.java,
            fishmod.features.extras.ExtrasSettings::class.java,
            fishmod.features.diana.DianaSettings::class.java,
            fishmod.features.fishing.FishingSettings::class.java,
            fishmod.features.fishing.FishingAlertSettings::class.java,
            fishmod.features.fishing.FishingTrackerSettings::class.java,
            fishmod.features.fishing.FishingWorldSettings::class.java,
            fishmod.features.mining.MiningSettings::class.java,
            fishmod.features.kuudra.KuudraSettings::class.java,
            fishmod.features.rift.RiftSettings::class.java,
            fishmod.features.garden.GardenSettings::class.java,
            fishmod.features.garden.GardenExtraSettings::class.java,
            fishmod.features.garden.GardenMilestoneSettings::class.java,
            fishmod.features.achievements.AchSettings::class.java,
            fishmod.features.gamble.GambleSettings::class.java
        )

    private const val PATH = "config/fishmod-settings.json"

    init {
        ColorDefaults.snapshot(classes)
        ConfigBackup.run(PATH)
        ConfigKeyMigration.run(PATH)
    }

    @JvmField
    val manager: ConfigManager = ConfigManager(PATH, classes)
}
