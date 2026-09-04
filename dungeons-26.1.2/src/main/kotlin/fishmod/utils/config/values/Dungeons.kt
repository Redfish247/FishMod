package fishmod.utils.config.values

import config.practical.manager.ConfigValue

object Dungeons {

    @ConfigValue @JvmField var enableWarpCooldown: Boolean = false

    @ConfigValue @JvmField var displayInvincibilityTimer: Boolean = false

    @ConfigValue @JvmField var enableLeapMessages: Boolean = false

    @ConfigValue @JvmField var enableAutoRequeue: Boolean = false

    @ConfigValue @JvmField var enableKeyNotifier: Boolean = false

    @ConfigValue @JvmField var hideBlazeNameTag: Boolean = false

    @ConfigValue @JvmField var detectDuplicateClass: Boolean = false

    @ConfigValue @JvmField var ignoreDupeMage: Boolean = false

    @ConfigValue @JvmField var dupeClassPartyChat: Boolean = false

    @ConfigValue @JvmField var useClassColors: Boolean = false

    @ConfigValue @JvmField var archerColor: Int = 0xffffaa00.toInt()

    @ConfigValue @JvmField var berserkColor: Int = 0xffaa0000.toInt()

    @ConfigValue @JvmField var healerColor: Int = 0xffff55ff.toInt()

    @ConfigValue @JvmField var tankColor: Int = 0xff00aa00.toInt()

    @ConfigValue @JvmField var mageColor: Int = 0xff55ffff.toInt()

    @ConfigValue @JvmField var InvincibilityDuration: Boolean = false

    @ConfigValue @JvmField var useStatusColorForInvincibility: Boolean = false

    @ConfigValue @JvmField var renderClassName: Boolean = false

    @ConfigValue @JvmField var bossHealthNumbers: Boolean = true

}
