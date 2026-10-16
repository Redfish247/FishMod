package fishmod.features.garden

import fishmod.features.FishModScreen.ButtonSetting
import fishmod.features.FishModScreen.ColorPickerSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.DropdownSetting
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.InputSetting
import fishmod.features.FishModScreen.SliderIntSetting
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.garden.GardenSettings as S

// The Farming tab in /fm
object GardenMenu {

    fun column(): Column {
        val col = Column("Farming")

        val timer = Feature("Visitor Timer", S::gardenVisitorTimer)
        timer.sub.add(ToggleSetting("Queue Full Warning", "Title when the visitor queue is full", S::gardenVisitorSixthWarn))
        timer.sub.add(ColorPickerSetting("Queue Full Colour", "Nearest chat colour is used", S::gardenVisitorFullColor))
        col.features.add(timer)

        val arrive = Feature("Visitor Arrival Alert", S::gardenVisitorArrivalTitle)
        arrive.sub.add(InputSetting("Title Text", "{name} = visitor", S::gardenVisitorArrivalText))
        arrive.sub.add(ToggleSetting("Sound", "", S::gardenVisitorArrivalSound))
        arrive.sub.add(ToggleSetting("Hide Arrival Chat", "", S::gardenVisitorHideArrival))
        col.features.add(arrive)

        val shop = Feature("Visitor Shopping List", S::gardenShoppingList)
        shop.sub.add(ToggleSetting("Show Prices", "", S::gardenShoppingPrices))
        shop.sub.add(ButtonSetting("Clear List", "", "Clear") { Visitors.clear() })
        shop.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::gardenShoppingColor))
        shop.sub.add(ColorPickerSetting("Item Name Colour", "Nearest chat colour is used", S::gardenShoppingNameColor))
        shop.sub.add(ColorPickerSetting("Amount Colour", "Nearest chat colour is used", S::gardenShoppingAmountColor))
        shop.sub.add(ColorPickerSetting("Price Colour", "Nearest chat colour is used", S::gardenShoppingPriceColor))
        shop.sub.add(ColorPickerSetting("Total Colour", "Nearest chat colour is used", S::gardenShoppingTotalColor))
        shop.sub.add(ColorPickerSetting("Hint Colour", "Nearest chat colour is used", S::gardenShoppingHintColor))
        col.features.add(shop)

        col.features.add(Feature("Visitor Menu Info", S::gardenVisitorMenuInfo))

        val rare = Feature("Visitor Rare Reward Warning", S::gardenRareRewardWarn)
        rare.sub.add(InputSetting("Rewards", "Comma-separated names", S::gardenRareRewardList))
        rare.sub.add(ColorPickerSetting("Accept Highlight", "", S::gardenRareRewardColor))
        col.features.add(rare)

        val alert = Feature("Rare Drop Alert", S::gardenAlert)
        alert.sub.add(SliderIntSetting("Show For (s)", "Position and size: HUD editor", S::gardenAlertSeconds, 1, 15))
        col.features.add(alert)

        val stats = Feature("Visitor Stats", S::gardenVisitorStats)
        stats.sub.add(ButtonSetting("Reset", "", "Reset") {
            S.gardenStatAccepted = 0; S.gardenStatCopper = 0; S.gardenStatGardenXp = 0; S.gardenStatRare = 0
        })
        stats.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::gardenVisitorStatsColor))
        col.features.add(stats)

        val pt = Feature("Pest Spawn Title", S::gardenPestTitle)
        pt.sub.add(InputSetting("Title Text", "{plot} = plot name", S::gardenPestTitleText))
        pt.sub.add(ToggleSetting("Sound", "", S::gardenPestSound))
        col.features.add(pt)

        val pf = Feature("Pest Finder", S::gardenPestFinder)
        pf.sub.add(ToggleSetting("Line", "Line to each infested plot", S::gardenPestFinderLine))
        pf.sub.add(ColorPickerSetting("Plot Colour", "", S::gardenPestFinderColor))
        pf.sub.add(ColorPickerSetting("Pest Marker Colour", "Box on the pest from the vacuum trail", S::gardenPestMarkerColor))
        pf.sub.add(ColorPickerSetting("Line Colour", "", S::gardenPestLineColor))
        pf.sub.add(SliderIntSetting("Opacity %", "", S::gardenPestFinderOpacity, 5, 100))
        col.features.add(pf)

        val pth = Feature("Pest Timer HUD", S::gardenPestHud)
        pth.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::gardenPestHudColor))
        col.features.add(pth)


        val fp = Feature("Farming Profit Tracker", S::gardenProfit)
        fp.sub.add(DropdownSetting("Category", "", FarmingProfitTracker.CATEGORIES, { S.gardenProfitCategory }, { v -> S.gardenProfitCategory = v }))
        fp.sub.add(DropdownSetting("Sort", "", FarmingProfitTracker.SORTS, { S.gardenProfitSort }, { v -> S.gardenProfitSort = v }))
        fp.sub.add(DropdownSetting("Tracker", "", arrayOf("Session", "Total"), { S.gardenProfitMode }, { v -> S.gardenProfitMode = v }))
        fp.sub.add(SliderIntSetting("Visible Lines", "Scroll over the HUD in your inventory for the rest", S::gardenProfitLines, 5, 25))
        fp.sub.add(ToggleSetting("Show Sources", "Per-source counts after merged items", S::gardenProfitShowSources))
        fp.sub.add(DropdownSetting("Bazaar Price", "", arrayOf("Sell Offer", "Insta Sell"), { S.gardenProfitPriceMode }, { v -> S.gardenProfitPriceMode = v }))
        fp.sub.add(SliderIntSetting("AFK Timeout (s)", "", S::gardenProfitAfkSec, 10, 900, 5))
        fp.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::gardenProfitColor))
        fp.sub.add(ButtonSetting("Reset Session", "", "Reset") { FarmingProfitTracker.resetSession() })
        fp.sub.add(ButtonSetting("Reset Total", "", "Reset") { FarmingProfitTracker.resetTotal() })
        col.features.add(fp)

        val cf = Feature("Crop Fever Time", S::gardenFever)
        cf.sub.add(ColorPickerSetting("Line Colour", "Nearest chat colour is used", S::gardenFeverColor))
        col.features.add(cf)

        val ff = Feature("Fortune Bonus Message", S::gardenFortuneMsg)
        ff.sub.add(InputSetting("Message", "{bonus} = bonus value", S::gardenFortuneMsgText))
        ff.sub.add(ColorPickerSetting("Colour", "Nearest chat colour is used", S::gardenFortuneMsgColor))
        col.features.add(ff)

        val sens = Feature("Mouse Sensitivity Reducer", S::gardenSens)
        sens.sub.add(SliderIntSetting("Sensitivity %", "Of your normal setting, on the ground in a plot", S::gardenSensPercent, 5, 100))
        sens.sub.add(ToggleSetting("Only With Items", "Only lower while holding one of the items below", S::gardenSensItemsOnly))
        sens.sub.add(ToggleSetting("Farming Tool", "Hoe / axe / dicer / cane knife etc.", S::gardenSensHoe))
        sens.sub.add(ToggleSetting("Pest Vacuum", "", S::gardenSensVacuum))
        sens.sub.add(ToggleSetting("Sprayonator", "", S::gardenSensSprayonator))
        sens.sub.add(ToggleSetting("Squeaky Mousemat", "", S::gardenSensMousemat))
        sens.sub.add(ToggleSetting("Chat Message", "", S::gardenSensMsg))
        sens.sub.add(ToggleSetting("HUD", "Shown while lowered", S::gardenSensHud))
        sens.sub.add(ColorPickerSetting("Colour", "Nearest chat colour is used", S::gardenSensColor))
        col.features.add(sens)

        val ss = Feature("Speed Sign Helper", S::gardenSpeedSign)
        ss.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::gardenSpeedSignColor))
        col.features.add(ss)

        val comp = Feature("Composter HUD", S::gardenComposterHud)
        comp.sub.add(ToggleSetting("Inactive Warning", "Title when the composter runs dry", S::gardenComposterWarn))
        comp.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::gardenComposterColor))
        comp.sub.add(ColorPickerSetting("Label Colour", "Nearest chat colour is used", S::gardenComposterLabelColor))
        comp.sub.add(ColorPickerSetting("Organic Matter Colour", "Nearest chat colour is used", S::gardenComposterOrganicColor))
        comp.sub.add(ColorPickerSetting("Fuel Colour", "Nearest chat colour is used", S::gardenComposterFuelColor))
        comp.sub.add(ColorPickerSetting("Time Left Colour", "Nearest chat colour is used", S::gardenComposterTimeColor))
        comp.sub.add(ColorPickerSetting("Stored Compost Colour", "Nearest chat colour is used", S::gardenComposterCompostColor))
        comp.sub.add(ColorPickerSetting("Warning Colour", "Nearest chat colour is used", S::gardenComposterWarnColor))
        col.features.add(comp)

        val jac = Feature("Jacob's Contest HUD", S::gardenJacobHud)
        jac.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::gardenJacobColor))
        col.features.add(jac)

        val rc = Feature("Rare Crop Tracker", S::gardenRareCrop)
        rc.sub.add(ToggleSetting("Title", "", S::gardenRareCropTitle))
        rc.sub.add(ButtonSetting("Reset", "", "Reset") { S.gardenRareCropCount = 0 })
        rc.sub.add(ColorPickerSetting("Colour", "Nearest chat colour is used", S::gardenRareCropColor))
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
