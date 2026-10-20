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
    @ConfigValue @JvmField var gardenPestMarkerColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenPestLineColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenComposterColor: Int = 0xFF00AA00.toInt()
    @ConfigValue @JvmField var gardenComposterLabelColor: Int = 0xFFAAAAAA.toInt()
    @ConfigValue @JvmField var gardenComposterOrganicColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var gardenComposterFuelColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var gardenComposterTimeColor: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var gardenComposterCompostColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var gardenComposterWarnColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenJacobColor: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var gardenRareCropColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var gardenVisitorStatsColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var gardenShoppingColor: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var gardenShoppingNameColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var gardenShoppingAmountColor: Int = 0xFF555555.toInt()
    @ConfigValue @JvmField var gardenShoppingPriceColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var gardenShoppingTotalColor: Int = 0xFFAAAAAA.toInt()
    @ConfigValue @JvmField var gardenShoppingHintColor: Int = 0xFF555555.toInt()
    @ConfigValue @JvmField var gardenVisitorFullColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenAlert: Boolean = true
    @ConfigValue @JvmField var gardenTitleX: Int = 300
    @ConfigValue @JvmField var gardenTitleY: Int = 60
    @ConfigValue @JvmField var gardenTitleScale: Double = 1.6
    @ConfigValue @JvmField var gardenAlertSeconds: Int = 4
    @ConfigValue @JvmField var gardenAlertX: Int = 300
    @ConfigValue @JvmField var gardenAlertY: Int = 90
    @ConfigValue @JvmField var gardenAlertScale: Double = 1.6
    @ConfigValue @JvmField var gardenPestFinderColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenPestFinderOpacity: Int = 20
    @ConfigValue @JvmField var gardenPestHud: Boolean = false
    @ConfigValue @JvmField var gardenPestHudX: Int = 200
    @ConfigValue @JvmField var gardenPestHudY: Int = 60
    @ConfigValue @JvmField var gardenPestHudScale: Double = 1.0

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

    // Farming profit tracker
    @ConfigValue @JvmField var gardenProfit: Boolean = false
    @ConfigValue @JvmField var gardenProfitMode: String = "Session"
    @ConfigValue @JvmField var gardenProfitCategory: String = "Combined"
    @ConfigValue @JvmField var gardenProfitSort: String = "Most Profit"
    @ConfigValue @JvmField var gardenProfitLines: Int = 25
    @ConfigValue @JvmField var gardenProfitPriceMode: String = "Sell Offer"
    @ConfigValue @JvmField var gardenProfitShowSources: Boolean = true
    @ConfigValue @JvmField var gardenProfitAfkSec: Int = 60
    @ConfigValue @JvmField var gardenProfitColor: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var gardenProfitHudX: Int = 5
    @ConfigValue @JvmField var gardenProfitHudY: Int = 160
    @ConfigValue @JvmField var gardenProfitHudScale: Double = 1.0

    // Crop Fever tracker
    @ConfigValue @JvmField var gardenFever: Boolean = false
    @ConfigValue @JvmField var gardenFeverColor: Int = 0xFFFF55FF.toInt()
    @ConfigValue @JvmField var gardenFeverX: Int = 350
    @ConfigValue @JvmField var gardenFeverY: Int = 220
    @ConfigValue @JvmField var gardenFeverScale: Double = 1.0

    // Farming fortune bonus message
    @ConfigValue @JvmField var gardenFortuneMsg: Boolean = false
    @ConfigValue @JvmField var gardenFortuneMsgText: String = "Bonus Farming Fortune: §f{bonus}"
    @ConfigValue @JvmField var gardenFortuneMsgColor: Int = 0xFFFFAA00.toInt()

    // Garden keybinds
    @ConfigValue @JvmField var gardenKeys: Boolean = false
    @ConfigValue @JvmField var gardenKeysHoe: Boolean = true
    @ConfigValue @JvmField var gardenKeysRod: Boolean = true
    @ConfigValue @JvmField var gardenKeysMousemat: Boolean = true
    @ConfigValue @JvmField var gardenKeysGrasp: Boolean = true

    // Mouse sensitivity reducer
    @ConfigValue @JvmField var gardenSens: Boolean = false
    @ConfigValue @JvmField var gardenSensPercent: Int = 50
    @ConfigValue @JvmField var gardenSensItemsOnly: Boolean = false
    @ConfigValue @JvmField var gardenSensHoe: Boolean = true
    @ConfigValue @JvmField var gardenSensRod: Boolean = true
    @ConfigValue @JvmField var gardenSensVacuum: Boolean = true
    @ConfigValue @JvmField var gardenSensSprayonator: Boolean = true
    @ConfigValue @JvmField var gardenSensMousemat: Boolean = true
    @ConfigValue @JvmField var gardenSensMsg: Boolean = true
    @ConfigValue @JvmField var gardenSensHud: Boolean = true
    @ConfigValue @JvmField var gardenSensColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var gardenSensOriginal: Double = -1.0
    @ConfigValue @JvmField var gardenSensX: Int = 5
    @ConfigValue @JvmField var gardenSensY: Int = 300
    @ConfigValue @JvmField var gardenSensScale: Double = 1.0

    // Speed sign helper
    @ConfigValue @JvmField var gardenSpeedSign: Boolean = false
    @ConfigValue @JvmField var gardenSpeedSignColor: Int = 0xFF55FF55.toInt()
}
