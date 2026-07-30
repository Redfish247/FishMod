package fishmod.utils.config.values

import config.practical.manager.ConfigValue

object Dungeons {

    @ConfigValue @JvmField var highlightItems: Boolean = false

    @ConfigValue @JvmField var enableWarpCooldown: Boolean = false

    @ConfigValue @JvmField var displayInvincibilityTimer: Boolean = false

    @ConfigValue @JvmField var showProcTitle: Boolean = false

    @ConfigValue @JvmField var useSprites: Boolean = false

    @ConfigValue @JvmField var hideAfterLeap: Boolean = false

    @ConfigValue @JvmField var hideOnlyInBoss: Boolean = true

    @ConfigValue @JvmField var hideAtSS: Boolean = false

    @ConfigValue @JvmField var hideBeforeTermsOnly: Boolean = false

    @ConfigValue @JvmField var displayChestCount: Boolean = false

    @ConfigValue @JvmField var onlyAfterRunOver: Boolean = false

    @ConfigValue @JvmField var sendChestWarning: Boolean = false

    @ConfigValue @JvmField var chestWarningCount: Int = 55

    @ConfigValue @JvmField var enableLeapMessages: Boolean = false

    @ConfigValue @JvmField var enableAutoRequeue: Boolean = false

    @ConfigValue @JvmField var calculateCriticalHit: Boolean = false

    @ConfigValue @JvmField var onlyInBoss: Boolean = false

    @ConfigValue @JvmField var enableKeyNotifier: Boolean = false

    @ConfigValue @JvmField var enableSecretSpawnTimer: Boolean = false

    @ConfigValue @JvmField var combineScreenNotifications: Boolean = false

    @ConfigValue @JvmField var dontProtectHeldItem: Boolean = true

    @ConfigValue @JvmField var hideBlazeNameTag: Boolean = false

    @ConfigValue @JvmField var hidePlayersInRange: Boolean = false

    @ConfigValue @JvmField var hidePlayerRange: Double = 2.0

    @ConfigValue @JvmField var disableDropAnimation: Boolean = false

    @ConfigValue @JvmField var maskHighlight: Boolean = false

    @ConfigValue @JvmField var detectDuplicateClass: Boolean = false

    @ConfigValue @JvmField var ignoreDupeMage: Boolean = false

    @ConfigValue @JvmField var dupeClassPartyChat: Boolean = false

    @ConfigValue @JvmField var detectPlayerCount: Boolean = false

    @ConfigValue @JvmField var useClassColors: Boolean = false

    @ConfigValue @JvmField var archerColor: Int = 0xffffaa00.toInt()

    @ConfigValue @JvmField var berserkColor: Int = 0xffaa0000.toInt()

    @ConfigValue @JvmField var healerColor: Int = 0xffff55ff.toInt()

    @ConfigValue @JvmField var tankColor: Int = 0xff00aa00.toInt()

    @ConfigValue @JvmField var mageColor: Int = 0xff55ffff.toInt()

    @ConfigValue @JvmField var InvincibilityDuration: Boolean = false

    @ConfigValue @JvmField var useStatusColorForInvincibility: Boolean = false

    @ConfigValue @JvmField var highlightTeammates: Boolean = false

    @ConfigValue @JvmField var renderClassName: Boolean = false

    @ConfigValue @JvmField var dontHighlightHiddenTeammates: Boolean = false

    @ConfigValue @JvmField var displayKeyForAllClasses: Boolean = false

    @ConfigValue @JvmField var quizTimer: Boolean = false

    @ConfigValue @JvmField var bossHealthNumbers: Boolean = false

    @ConfigValue @JvmField var removeMaskPart: Boolean = true

    @ConfigValue @JvmField var alertBloodSpawns: Boolean = false

    @ConfigValue @JvmField var alertBloodForAllClasses: Boolean = false

    @ConfigValue @JvmField var quizProgress: Boolean = false

}
