package fishmod.utils.config.values

import config.practical.manager.ConfigValue

object Visual {

    @ConfigValue @JvmField var hideFireInf5: Boolean = false

    @ConfigValue @JvmField var hideStuckArrows: Boolean = false

    /** Master gate for the Render Optimizer group (hide players/dead entities, no-swing, shovel). */
    @ConfigValue @JvmField var renderOptimizer: Boolean = false

    @ConfigValue @JvmField var hideDeadEntities: Boolean = false

    @ConfigValue @JvmField var roHideFallingBlocks: Boolean = false
    @ConfigValue @JvmField var roHideLightning: Boolean = false
    @ConfigValue @JvmField var roHideExperienceOrbs: Boolean = false
    @ConfigValue @JvmField var roHideDeathAnimation: Boolean = false
    @ConfigValue @JvmField var roHideDyingArmorStands: Boolean = false
    @ConfigValue @JvmField var roHideExplosionParticles: Boolean = false
    @ConfigValue @JvmField var roHideArcherPassive: Boolean = false
    @ConfigValue @JvmField var roHideHealerFairy: Boolean = false
    @ConfigValue @JvmField var roHideSoulWeaver: Boolean = false
    @ConfigValue @JvmField var roHideTentacleHead: Boolean = false
    @ConfigValue @JvmField var roHideFireOverlay: Boolean = false

    /** Hide other players within [hidePlayerRange] blocks (declutter crowded hubs / boss fights). */
    @ConfigValue @JvmField var hidePlayersInRange: Boolean = false
    @ConfigValue @JvmField var hidePlayerRange: Double = 3.0

    @ConfigValue @JvmField var itemRarityBackground: Boolean = false
    /** Background tint opacity, 0-100%. */
    @ConfigValue @JvmField var itemRarityOpacity: Int = 35
    /** Use Hypixel's per-rarity colours instead of blade-addons' darker palette. */
    @ConfigValue @JvmField var itemRarityHypixelColors: Boolean = true

    @ConfigValue @JvmField var hideStatusOverLay: Boolean = false

    @ConfigValue @JvmField var disableGlowing: Boolean = false

    @ConfigValue @JvmField var drawStarCount: Boolean = false


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
