package fishmod.features.garden

import fishmod.features.FishModScreen.ButtonSetting
import fishmod.features.FishModScreen.ColorPickerSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.DropdownSetting
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.InputSetting
import fishmod.features.FishModScreen.KeybindSetting
import fishmod.features.FishModScreen.SliderIntSetting
import fishmod.features.FishModScreen.SubcategoryHeader
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.garden.GardenSettings as S

object GardenMenu {

    fun column(): Column {
        val col = Column("Farming")

        val timer = Feature("Visitor Timer", S::class.java, "gardenVisitorTimer")
        timer.sub.add(ToggleSetting("Queue Full Warning", "Title when the visitor queue is full", S::class.java, "gardenVisitorSixthWarn"))
        timer.sub.add(ColorPickerSetting("Queue Full Colour", "Nearest chat colour is used", S::class.java, "gardenVisitorFullColor"))
        col.features.add(timer)

        val arrive = Feature("Visitor Arrival Alert", S::class.java, "gardenVisitorArrivalTitle")
        arrive.sub.add(InputSetting("Title Text", "{name} = visitor", S::class.java, "gardenVisitorArrivalText"))
        arrive.sub.add(ToggleSetting("Sound", "", S::class.java, "gardenVisitorArrivalSound"))
        arrive.sub.add(ToggleSetting("Hide Arrival Chat", "", S::class.java, "gardenVisitorHideArrival"))
        col.features.add(arrive)

        val shop = Feature("Visitor Shopping List", S::class.java, "gardenShoppingList")
        shop.sub.add(ToggleSetting("Show Prices", "", S::class.java, "gardenShoppingPrices"))
        shop.sub.add(ButtonSetting("Clear List", "", "Clear") { Visitors.clear() })
        shop.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::class.java, "gardenShoppingColor"))
        shop.sub.add(ColorPickerSetting("Item Name Colour", "Nearest chat colour is used", S::class.java, "gardenShoppingNameColor"))
        shop.sub.add(ColorPickerSetting("Amount Colour", "Nearest chat colour is used", S::class.java, "gardenShoppingAmountColor"))
        shop.sub.add(ColorPickerSetting("Price Colour", "Nearest chat colour is used", S::class.java, "gardenShoppingPriceColor"))
        shop.sub.add(ColorPickerSetting("Total Colour", "Nearest chat colour is used", S::class.java, "gardenShoppingTotalColor"))
        shop.sub.add(ColorPickerSetting("Hint Colour", "Nearest chat colour is used", S::class.java, "gardenShoppingHintColor"))
        col.features.add(shop)

        col.features.add(Feature("Visitor Menu Info", S::class.java, "gardenVisitorMenuInfo"))

        val rare = Feature("Visitor Rare Reward Warning", S::class.java, "gardenRareRewardWarn")
        rare.sub.add(InputSetting("Rewards", "Comma-separated names", S::class.java, "gardenRareRewardList"))
        rare.sub.add(ColorPickerSetting("Accept Highlight", "", S::class.java, "gardenRareRewardColor"))
        col.features.add(rare)

        val alert = Feature("Rare Drop Alert", S::class.java, "gardenAlert")
        alert.sub.add(SliderIntSetting("Show For (s)", "Position and size: HUD editor", S::class.java, "gardenAlertSeconds", 1, 15))
        col.features.add(alert)

        val stats = Feature("Visitor Stats", S::class.java, "gardenVisitorStats")
        stats.sub.add(ButtonSetting("Reset", "", "Reset") {
            S.gardenStatAccepted = 0; S.gardenStatCopper = 0; S.gardenStatGardenXp = 0; S.gardenStatRare = 0
        })
        stats.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::class.java, "gardenVisitorStatsColor"))
        col.features.add(stats)

        val pt = Feature("Pest Spawn Title", S::class.java, "gardenPestTitle")
        pt.sub.add(InputSetting("Title Text", "{plot} = plot name", S::class.java, "gardenPestTitleText"))
        pt.sub.add(ToggleSetting("Sound", "", S::class.java, "gardenPestSound"))
        col.features.add(pt)

        val pf = Feature("Pest Finder", S::class.java, "gardenPestFinder")
        pf.sub.add(ToggleSetting("Line", "Line to each infested plot", S::class.java, "gardenPestFinderLine"))
        pf.sub.add(ColorPickerSetting("Plot Colour", "", S::class.java, "gardenPestFinderColor"))
        pf.sub.add(ColorPickerSetting("Pest Marker Colour", "Box on the pest from the vacuum trail", S::class.java, "gardenPestMarkerColor"))
        pf.sub.add(ColorPickerSetting("Line Colour", "", S::class.java, "gardenPestLineColor"))
        pf.sub.add(SliderIntSetting("Opacity %", "", S::class.java, "gardenPestFinderOpacity", 5, 100))
        col.features.add(pf)

        val pth = Feature("Pest Timer HUD", S::class.java, "gardenPestHud")
        pth.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::class.java, "gardenPestHudColor"))
        col.features.add(pth)


        val fp = Feature("Farming Profit Tracker", S::class.java, "gardenProfit")
        fp.sub.add(DropdownSetting("Category", "", FarmingProfitTracker.CATEGORIES, { S.gardenProfitCategory }, { v -> S.gardenProfitCategory = v }))
        fp.sub.add(DropdownSetting("Sort", "", FarmingProfitTracker.SORTS, { S.gardenProfitSort }, { v -> S.gardenProfitSort = v }))
        fp.sub.add(DropdownSetting("Tracker", "", arrayOf("Session", "Total"), { S.gardenProfitMode }, { v -> S.gardenProfitMode = v }))
        fp.sub.add(SliderIntSetting("Visible Lines", "Scroll over the HUD in your inventory for the rest", S::class.java, "gardenProfitLines", 5, 25))
        fp.sub.add(ToggleSetting("Show Sources", "Per-source counts after merged items", S::class.java, "gardenProfitShowSources"))
        fp.sub.add(DropdownSetting("Bazaar Price", "", arrayOf("Sell Offer", "Insta Sell"), { S.gardenProfitPriceMode }, { v -> S.gardenProfitPriceMode = v }))
        fp.sub.add(SliderIntSetting("AFK Timeout (s)", "", S::class.java, "gardenProfitAfkSec", 10, 900, 5))
        fp.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::class.java, "gardenProfitColor"))
        fp.sub.add(ButtonSetting("Reset Session", "", "Reset") { FarmingProfitTracker.resetSession() })
        fp.sub.add(ButtonSetting("Reset Total", "", "Reset") { FarmingProfitTracker.resetTotal() })
        col.features.add(fp)

        val cf = Feature("Crop Fever Time", S::class.java, "gardenFever")
        cf.sub.add(ColorPickerSetting("Line Colour", "Nearest chat colour is used", S::class.java, "gardenFeverColor"))
        col.features.add(cf)

        val ff = Feature("Fortune Bonus Message", S::class.java, "gardenFortuneMsg")
        ff.sub.add(InputSetting("Message", "{bonus} = bonus value", S::class.java, "gardenFortuneMsgText"))
        ff.sub.add(ColorPickerSetting("Colour", "Nearest chat colour is used", S::class.java, "gardenFortuneMsgColor"))
        col.features.add(ff)

        val carr = Feature("Help Carrolyn", S::class.java, "gardenCarrolyn")
        carr.sub.add(SubcategoryHeader("Carrolyn items get a tooltip hint; right-click one for a clickable warp to the Crimson Isle"))
        col.features.add(carr)

        val gk = Feature("Garden Keybinds", S::class.java, "gardenKeys")
        gk.sub.add(SubcategoryHeader("Your own keys act as the vanilla ones while farming in a plot (not the barn)"))
        for (i in GardenKeybinds.NAMES.indices) gk.sub.add(KeybindSetting(GardenKeybinds.NAMES[i], "", { fishmod.utils.FishKeys.gardenKeys?.getOrNull(i) }))
        gk.sub.add(ToggleSetting("Farming Tool", "", S::class.java, "gardenKeysHoe"))
        gk.sub.add(ToggleSetting("Fishing Rod", "", S::class.java, "gardenKeysRod"))
        gk.sub.add(ToggleSetting("Squeaky Mousemat", "", S::class.java, "gardenKeysMousemat"))
        gk.sub.add(ToggleSetting("Sun's Grasp", "", S::class.java, "gardenKeysGrasp"))
        col.features.add(gk)

        val sens = Feature("Mouse Sensitivity Reducer", S::class.java, "gardenSens")
        sens.sub.add(SliderIntSetting("Sensitivity %", "Of your normal setting, on the ground in a plot", S::class.java, "gardenSensPercent", 5, 100))
        sens.sub.add(ToggleSetting("Only With Items", "Only lower while holding one of the items below", S::class.java, "gardenSensItemsOnly"))
        sens.sub.add(ToggleSetting("Farming Tool", "Hoe / axe / dicer / cane knife etc.", S::class.java, "gardenSensHoe"))
        sens.sub.add(ToggleSetting("Fishing Rod", "Any fishing rod", S::class.java, "gardenSensRod"))
        sens.sub.add(ToggleSetting("Pest Vacuum", "", S::class.java, "gardenSensVacuum"))
        sens.sub.add(ToggleSetting("Sprayonator", "", S::class.java, "gardenSensSprayonator"))
        sens.sub.add(ToggleSetting("Squeaky Mousemat", "", S::class.java, "gardenSensMousemat"))
        sens.sub.add(ToggleSetting("Chat Message", "", S::class.java, "gardenSensMsg"))
        sens.sub.add(ToggleSetting("HUD", "Shown while lowered", S::class.java, "gardenSensHud"))
        sens.sub.add(ColorPickerSetting("Colour", "Nearest chat colour is used", S::class.java, "gardenSensColor"))
        col.features.add(sens)

        val ss = Feature("Speed Sign Helper", S::class.java, "gardenSpeedSign")
        ss.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::class.java, "gardenSpeedSignColor"))
        col.features.add(ss)

        val comp = Feature("Composter HUD", S::class.java, "gardenComposterHud")
        comp.sub.add(ToggleSetting("Inactive Warning", "Title when the composter runs dry", S::class.java, "gardenComposterWarn"))
        comp.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::class.java, "gardenComposterColor"))
        comp.sub.add(ColorPickerSetting("Label Colour", "Nearest chat colour is used", S::class.java, "gardenComposterLabelColor"))
        comp.sub.add(ColorPickerSetting("Organic Matter Colour", "Nearest chat colour is used", S::class.java, "gardenComposterOrganicColor"))
        comp.sub.add(ColorPickerSetting("Fuel Colour", "Nearest chat colour is used", S::class.java, "gardenComposterFuelColor"))
        comp.sub.add(ColorPickerSetting("Time Left Colour", "Nearest chat colour is used", S::class.java, "gardenComposterTimeColor"))
        comp.sub.add(ColorPickerSetting("Stored Compost Colour", "Nearest chat colour is used", S::class.java, "gardenComposterCompostColor"))
        comp.sub.add(ColorPickerSetting("Warning Colour", "Nearest chat colour is used", S::class.java, "gardenComposterWarnColor"))
        col.features.add(comp)

        val jac = Feature("Jacob's Contest HUD", S::class.java, "gardenJacobHud")
        jac.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::class.java, "gardenJacobColor"))
        col.features.add(jac)

        val rc = Feature("Rare Crop Tracker", S::class.java, "gardenRareCrop")
        rc.sub.add(ToggleSetting("Title", "", S::class.java, "gardenRareCropTitle"))
        rc.sub.add(ButtonSetting("Reset", "", "Reset") { S.gardenRareCropCount = 0 })
        rc.sub.add(ColorPickerSetting("Colour", "Nearest chat colour is used", S::class.java, "gardenRareCropColor"))
        col.features.add(rc)

        GardenMilestoneMenu.addTo(col)
        GardenExtrasMenu.add(col)

        return col
    }

    fun describe(name: String): String? = when (name) {
        "Visitor Timer" -> "Visitor count and time to the next visitor"
        "Visitor Arrival Alert" -> "Title when a visitor arrives"
        "Visitor Shopping List" -> "Items your visitors want, with prices"
        "Visitor Menu Info" -> "Cost and coins/copper on the Accept Offer tooltip"
        "Visitor Rare Reward Warning" -> "Title and highlight for rare visitor rewards"
        "Rare Drop Alert" -> "On-screen alert for rare crops and rare visitor rewards, coloured by rarity"
        "Visitor Stats" -> "Accepted visitors, copper, garden XP, rare rewards"
        "Pest Spawn Title" -> "Title when a pest spawns"
        "Pest Finder" -> "Waypoints on infested plots"
        "Pest Timer HUD" -> "Pest count, cooldown, plots, spray, bonus"
        "Farming Profit Tracker" -> "Pests, crops, visitors and rare crops: session/total profit per hour"
        "Crop Fever Time" -> "Adds total time spent farming during Crop Fever to the Farming Profit tracker"
        "Fortune Bonus Message" -> "Chat message when your bonus Farming Fortune changes"
        "Mouse Sensitivity Reducer" -> "Lowers mouse sensitivity while on the ground in a plot"
        "Speed Sign Helper" -> "Best speed and yaw/pitch for the held crop on the sign screen"
        "Composter HUD" -> "Composter widget from the tab list"
        "Jacob's Contest HUD" -> "Jacob's Contest widget from the tab list"
        "Rare Crop Tracker" -> "Counts RARE CROP drops"
        else -> GardenMilestoneMenu.describe(name) ?: GardenExtrasMenu.describe(name)
    }
}
