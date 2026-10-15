package fishmod.features.garden

import fishmod.shaded.practicalconfig.manager.ConfigValue

object GardenSettings {

    // Visitors
    @ConfigValue @JvmField var gardenVisitorTimer: Boolean = false
    @ConfigValue @JvmField var gardenVisitorSixthWarn: Boolean = false
    @ConfigValue @JvmField var gardenVisitorTimerHudX: Int = 5
    @ConfigValue @JvmField var gardenVisitorTimerHudY: Int = 60
    @ConfigValue @JvmField var gardenVisitorTimerHudScale: Double = 1.0
    @ConfigValue @JvmField var gardenVisitorArrivalTitle: Boolean = false
    @ConfigValue @JvmField var gardenVisitorArrivalText: String = "§a{name} §ehas arrived!"
    @ConfigValue @JvmField var gardenVisitorArrivalSound: Boolean = true
    @ConfigValue @JvmField var gardenVisitorHideArrival: Boolean = false
    @ConfigValue @JvmField var gardenShoppingList: Boolean = false
    @ConfigValue @JvmField var gardenShoppingPrices: Boolean = true
    @ConfigValue @JvmField var gardenShoppingHudX: Int = 5
    @ConfigValue @JvmField var gardenShoppingHudY: Int = 90
    @ConfigValue @JvmField var gardenShoppingHudScale: Double = 1.0
    @ConfigValue @JvmField var gardenVisitorMenuInfo: Boolean = false
    @ConfigValue @JvmField var gardenRareRewardWarn: Boolean = false
    @ConfigValue @JvmField var gardenRareRewardList: String = "Flowering Bouquet, Overgrown Grass, Green Bandana, Dedication, Music Rune, Space Helmet, Cultivating, Replenish, Delicate, Copper Dye, Jungle Key, Fruit Bowl, Harbinger Potion, Pet Cake"
    @ConfigValue @JvmField var gardenRareRewardColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var gardenVisitorStats: Boolean = false
    @ConfigValue @JvmField var gardenVisitorStatsHudX: Int = 5
    @ConfigValue @JvmField var gardenVisitorStatsHudY: Int = 200
    @ConfigValue @JvmField var gardenVisitorStatsHudScale: Double = 1.0
    @ConfigValue @JvmField var gardenStatAccepted: Int = 0
    @ConfigValue @JvmField var gardenStatCopper: Int = 0
    @ConfigValue @JvmField var gardenStatGardenXp: Int = 0
    @ConfigValue @JvmField var gardenStatRare: Int = 0

    // Pests
    @ConfigValue @JvmField var gardenPestTitle: Boolean = false
    @ConfigValue @JvmField var gardenPestTitleText: String = "§c§lPEST! §e{plot}"
    @ConfigValue @JvmField var gardenPestSound: Boolean = true
    @ConfigValue @JvmField var gardenPestFinder: Boolean = false
    @ConfigValue @JvmField var gardenPestFinderLine: Boolean = true
    @ConfigValue @JvmField var gardenPestHudColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenPestProfitColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenPestMarkerColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenPestLineColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenComposterColor: Int = 0xFF00AA00.toInt()
    @ConfigValue @JvmField var gardenComposterWarnColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenJacobColor: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var gardenRareCropColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var gardenVisitorStatsColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var gardenShoppingColor: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var gardenVisitorFullColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenPestFinderColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenPestFinderOpacity: Int = 20
    @ConfigValue @JvmField var gardenPestHud: Boolean = false
    @ConfigValue @JvmField var gardenPestHudX: Int = 200
    @ConfigValue @JvmField var gardenPestHudY: Int = 60
    @ConfigValue @JvmField var gardenPestHudScale: Double = 1.0
    @ConfigValue @JvmField var gardenPestProfit: Boolean = false
    @ConfigValue @JvmField var gardenPestProfitHudX: Int = 200
    @ConfigValue @JvmField var gardenPestProfitHudY: Int = 120
    @ConfigValue @JvmField var gardenPestProfitHudScale: Double = 1.0

    // Garden widgets
    @ConfigValue @JvmField var gardenComposterHud: Boolean = false
    @ConfigValue @JvmField var gardenComposterWarn: Boolean = false
    @ConfigValue @JvmField var gardenComposterHudX: Int = 200
    @ConfigValue @JvmField var gardenComposterHudY: Int = 180
    @ConfigValue @JvmField var gardenComposterHudScale: Double = 1.0
    @ConfigValue @JvmField var gardenJacobHud: Boolean = false
    @ConfigValue @JvmField var gardenJacobHudX: Int = 350
    @ConfigValue @JvmField var gardenJacobHudY: Int = 60
    @ConfigValue @JvmField var gardenJacobHudScale: Double = 1.0
    @ConfigValue @JvmField var gardenRareCrop: Boolean = false
    @ConfigValue @JvmField var gardenRareCropTitle: Boolean = true
    @ConfigValue @JvmField var gardenRareCropHudX: Int = 350
    @ConfigValue @JvmField var gardenRareCropHudY: Int = 140
    @ConfigValue @JvmField var gardenRareCropHudScale: Double = 1.0
    @ConfigValue @JvmField var gardenRareCropCount: Int = 0
}
