package fishmod.features.dungeon

import fishmod.shaded.practicalconfig.manager.ConfigValue

object DungeonMilestoneSettings {

    @ConfigValue @JvmField var milestoneHud: Boolean = false
    @ConfigValue @JvmField var milestoneColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var milestoneHudX: Int = 5
    @ConfigValue @JvmField var milestoneHudY: Int = 120
    @ConfigValue @JvmField var milestoneHudScale: Double = 1.0
}
