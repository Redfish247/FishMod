package fishmod.features.dungeon

import fishmod.shaded.practicalconfig.manager.ConfigValue

object DungeonExtraSettings {

    @ConfigValue @JvmField var deathmiteHighlight: Boolean = false
    @ConfigValue @JvmField var deathmiteColor: Int = 0xFFAA0000.toInt()

    @ConfigValue @JvmField var skullHighlight: Boolean = false
    @ConfigValue @JvmField var skullHide: Boolean = false
    @ConfigValue @JvmField var skullColor: Int = 0xFFFFFFFF.toInt()

    @ConfigValue @JvmField var lowHpAlert: Boolean = false
    @ConfigValue @JvmField var lowHpCooldownSec: Int = 5

    @ConfigValue @JvmField var partyHpHud: Boolean = false
    @ConfigValue @JvmField var partyHpHudX: Int = 5
    @ConfigValue @JvmField var partyHpHudY: Int = 150
    @ConfigValue @JvmField var partyHpHudScale: Double = 1.0

    @ConfigValue @JvmField var classTabColor: Boolean = false

    @ConfigValue @JvmField var kismetCount: Boolean = false
}
