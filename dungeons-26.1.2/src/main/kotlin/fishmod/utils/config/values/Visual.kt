package fishmod.utils.config.values

import config.practical.manager.ConfigValue

object Visual {

    @ConfigValue @JvmField var hideFireInf5: Boolean = false

    @ConfigValue @JvmField var hideStuckArrows: Boolean = false

    @ConfigValue @JvmField var hideDeadEntities: Boolean = false

    /** Hide other players within [hidePlayerRange] blocks (declutter crowded hubs / boss fights). */
    @ConfigValue @JvmField var hidePlayersInRange: Boolean = false
    @ConfigValue @JvmField var hidePlayerRange: Double = 3.0

    @ConfigValue @JvmField var itemRarityBackground: Boolean = false
    /** Background tint opacity 0-100% (NoammAddons default is ~30). */
    @ConfigValue @JvmField var itemRarityOpacity: Int = 35
    /** Use Hypixel's per-rarity colours instead of blade-addons' darker palette. */
    @ConfigValue @JvmField var itemRarityHypixelColors: Boolean = true

    @ConfigValue @JvmField var hideStatusOverLay: Boolean = false

    @ConfigValue @JvmField var disableGlowing: Boolean = false

    @ConfigValue @JvmField var drawStarCount: Boolean = false

    @ConfigValue @JvmField var highlightProtectedItem: Boolean = false

    @ConfigValue @JvmField var compactHoppityMsgs: Boolean = false

    @ConfigValue @JvmField var hideCooldown: Boolean = false

    @ConfigValue @JvmField var hideEntityFire: Boolean = false

    @ConfigValue @JvmField var oldPlayerHead: Boolean = false

    @ConfigValue @JvmField var fixWitherEssence: Boolean = false

    @ConfigValue @JvmField var oldFishingRod: Boolean = false

    @ConfigValue @JvmField var stopShovelFlattening: Boolean = false

    @ConfigValue @JvmField var stopPearlSwing: Boolean = false

    /** Suppress the first-person hand swing animation (optionally only while holding a Terminator). */
    @ConfigValue @JvmField var noSwingAnimation: Boolean = false
    @ConfigValue @JvmField var noSwingTerminatorOnly: Boolean = false

    @ConfigValue @JvmField var circularRarityBackground: Boolean = false

}
