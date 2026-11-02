package fishmod.utils.config.values

import fishmod.shaded.practicalconfig.manager.ConfigValue

object DungeonSettings {

    @ConfigValue @JvmField var partyWarpTimer: Boolean = false

    @ConfigValue @JvmField var invincibilityTimerHud: Boolean = false

    @ConfigValue @JvmField var leapAnnounce: Boolean = false

    @ConfigValue @JvmField var autoRequeueEnabled: Boolean = false

    @ConfigValue @JvmField var keyPickupAlert: Boolean = false

    @ConfigValue @JvmField var hideBlazeTags: Boolean = false

    @ConfigValue @JvmField var dupeClassAlert: Boolean = false

    @ConfigValue @JvmField var dupeClassSkipMage: Boolean = false

    @ConfigValue @JvmField var dupeClassPartyChat: Boolean = false

    @ConfigValue @JvmField var roleColors: Boolean = false

    @ConfigValue @JvmField var classColoredGlow: Boolean = false

    @ConfigValue @JvmField var roleColorArcher: Int = 0xffffaa00.toInt()

    @ConfigValue @JvmField var roleColorBerserk: Int = 0xffaa0000.toInt()

    @ConfigValue @JvmField var roleColorHealer: Int = 0xffff55ff.toInt()

    @ConfigValue @JvmField var roleColorTank: Int = 0xff00aa00.toInt()

    @ConfigValue @JvmField var roleColorMage: Int = 0xff55ffff.toInt()

    @ConfigValue @JvmField var invincibilityHud: Boolean = false

    @ConfigValue @JvmField var invincibilityStatusColor: Boolean = false

    @ConfigValue @JvmField var roleNameTags: Boolean = false

    @ConfigValue @JvmField var bossHpNumbers: Boolean = false

}
