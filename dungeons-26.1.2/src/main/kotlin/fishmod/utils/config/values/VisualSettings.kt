package fishmod.utils.config.values

import fishmod.shaded.practicalconfig.manager.ConfigValue

object VisualSettings {

    @ConfigValue @JvmField var hideBurningInf5: Boolean = false

    @ConfigValue @JvmField var hideArrowsInBody: Boolean = false

    @ConfigValue @JvmField var renderOptimizer: Boolean = false

    @ConfigValue @JvmField var hideDeadMobs: Boolean = false

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
    @ConfigValue @JvmField var roHideObjective: Boolean = false
    @ConfigValue @JvmField var roRemoveDamageIndicator: Boolean = false
    @ConfigValue @JvmField var roFormatDamageIndicator: Boolean = false

    @ConfigValue @JvmField var hideNearbyPlayers: Boolean = false
    @ConfigValue @JvmField var hidePlayersRadius: Double = 3.0

    @ConfigValue @JvmField var rarityBackground: Boolean = false
    @ConfigValue @JvmField var itemRarityOpacity: Int = 35
    @ConfigValue @JvmField var itemRarityHypixelColors: Boolean = true

    @ConfigValue @JvmField var hideEffectHud: Boolean = false

    @ConfigValue @JvmField var noEntityGlow: Boolean = false

    @ConfigValue @JvmField var hideItemCooldown: Boolean = false

    @ConfigValue @JvmField var hideMobFire: Boolean = false

    @ConfigValue @JvmField var nametagsOnTop: Boolean = true

    @ConfigValue @JvmField var lateWorldText: Boolean = true

    @ConfigValue @JvmField var gizmoOverlays: Boolean = true

    @ConfigValue @JvmField var legacyPlayerHead: Boolean = false

    @ConfigValue @JvmField var witherEssenceSkin: Boolean = false

    @ConfigValue @JvmField var noShovelPath: Boolean = false

    @ConfigValue @JvmField var noSwingAnimation: Boolean = false
    @ConfigValue @JvmField var noSwingTerminatorOnly: Boolean = false

    @ConfigValue @JvmField var rarityBgCircle: Boolean = false

    @ConfigValue @JvmField var darkModeEnabled: Boolean = false
    @ConfigValue @JvmField var darkModeOpacity: Int = 25
    @ConfigValue @JvmField var darkModeTintHud: Boolean = false

}
