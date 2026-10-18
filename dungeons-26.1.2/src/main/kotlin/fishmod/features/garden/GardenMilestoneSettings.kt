package fishmod.features.garden

import fishmod.shaded.practicalconfig.manager.ConfigValue

// Settings for milestone, garden level, hoe and misc garden cards
object GardenMilestoneSettings {

    @ConfigValue @JvmField var gardenMilestoneHud: Boolean = false
    @ConfigValue @JvmField var gardenMilestoneEta: Boolean = true
    @ConfigValue @JvmField var gardenMilestoneColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var gardenMilestoneHudX: Int = 5
    @ConfigValue @JvmField var gardenMilestoneHudY: Int = 240
    @ConfigValue @JvmField var gardenMilestoneHudScale: Double = 1.0

    @ConfigValue @JvmField var gardenIconNumbers: Boolean = false
    @ConfigValue @JvmField var gardenIconNumberColor: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var gardenMilestoneTooltip: Boolean = false

    @ConfigValue @JvmField var gardenLevelHud: Boolean = false
    @ConfigValue @JvmField var gardenLevelChat: Boolean = true
    @ConfigValue @JvmField var gardenLevelColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var gardenLevelXp: Long = 0
    @ConfigValue @JvmField var gardenLevelHudX: Int = 5
    @ConfigValue @JvmField var gardenLevelHudY: Int = 280
    @ConfigValue @JvmField var gardenLevelHudScale: Double = 1.0

    @ConfigValue @JvmField var gardenHoeHud: Boolean = false
    @ConfigValue @JvmField var gardenHoeMute: Boolean = false
    @ConfigValue @JvmField var gardenHoeColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var gardenHoeHudX: Int = 5
    @ConfigValue @JvmField var gardenHoeHudY: Int = 310
    @ConfigValue @JvmField var gardenHoeHudScale: Double = 1.0

    @ConfigValue @JvmField var gardenLogBookHud: Boolean = false
    @ConfigValue @JvmField var gardenLogBookColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var gardenLogBookHudX: Int = 200
    @ConfigValue @JvmField var gardenLogBookHudY: Int = 240
    @ConfigValue @JvmField var gardenLogBookHudScale: Double = 1.0

}
