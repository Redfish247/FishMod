package fishmod.utils.config.values

import fishmod.shaded.practicalconfig.manager.ConfigValue

object Visual {

    @ConfigValue @JvmField var hideFireInf5: Boolean = false

    @ConfigValue @JvmField var hideStuckArrows: Boolean = false

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
    @ConfigValue @JvmField var roHideInventoryLabels: Boolean = true

    @ConfigValue @JvmField var hidePlayersInRange: Boolean = false
    @ConfigValue @JvmField var hidePlayerRange: Double = 3.0

    @ConfigValue @JvmField var itemRarityBackground: Boolean = false
    @ConfigValue @JvmField var itemRarityOpacity: Int = 35
    @ConfigValue @JvmField var itemRarityHypixelColors: Boolean = true

    @ConfigValue @JvmField var hideStatusOverLay: Boolean = false

    @ConfigValue @JvmField var disableGlowing: Boolean = false

    @ConfigValue @JvmField var hideCooldown: Boolean = false

    @ConfigValue @JvmField var hideEntityFire: Boolean = false

    @ConfigValue @JvmField var oldPlayerHead: Boolean = false

    @ConfigValue @JvmField var fixWitherEssence: Boolean = false

    @ConfigValue @JvmField var stopShovelFlattening: Boolean = false

    @ConfigValue @JvmField var noSwingAnimation: Boolean = false
    @ConfigValue @JvmField var noSwingTerminatorOnly: Boolean = false

    @ConfigValue @JvmField var circularRarityBackground: Boolean = false

    @ConfigValue @JvmField var darkModeEnabled: Boolean = false
    @ConfigValue @JvmField var darkModeOpacity: Int = 25
    @ConfigValue @JvmField var darkModeTintHud: Boolean = false

}
