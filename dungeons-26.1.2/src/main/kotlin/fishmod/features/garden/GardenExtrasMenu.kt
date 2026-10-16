package fishmod.features.garden

import fishmod.features.FishModScreen.ColorPickerSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.InputSetting
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.garden.GardenExtraSettings as X

// Farming tab entries for the Working-On garden cards
object GardenExtrasMenu {

    fun add(col: Column) {
        val jeff = Feature("Jeff Buy Order", X::gardenJeff)
        jeff.sub.add(InputSetting("Order", "Comma-separated chip names, first not-maxed is highlighted", X::gardenJeffOrder))
        jeff.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", X::gardenJeffColor))
        col.features.add(jeff)

        val ch = Feature("Charmed Visitor HUD", X::gardenCharmed)
        ch.sub.add(InputSetting("Tab Header", "Text of the tab-list widget line to show", X::gardenCharmedHeader))
        ch.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", X::gardenCharmedColor))
        col.features.add(ch)

        val cu = Feature("Composter Upgrade Prices", X::gardenCompUpgrades)
        cu.sub.add(ColorPickerSetting("Cheapest Highlight", "", X::gardenCompUpgradesColor))
        col.features.add(cu)

        val st = Feature("Stereo Harmony", X::gardenStereo)
        st.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", X::gardenStereoColor))
        col.features.add(st)

        val gh = Feature("Greenhouse Status", X::gardenGreenhouse)
        gh.sub.add(ColorPickerSetting("Good Colour", "Harvestable / watered", X::gardenGreenhouseGoodColor))
        gh.sub.add(ColorPickerSetting("Bad Colour", "Low water", X::gardenGreenhouseBadColor))
        col.features.add(gh)

        val mut = Feature("Mutations Link", X::gardenMutationsLinkOn)
        mut.sub.add(InputSetting("URL", "/fmmutations prints it as a clickable link and copies it", X::gardenMutationsUrl))
        col.features.add(mut)

        val an = Feature("Anita Shop Extras", X::gardenAnita)
        an.sub.add(ColorPickerSetting("Best Medal Highlight", "", X::gardenAnitaColor))
        col.features.add(an)

        val pl = Feature("Plot Icons", X::gardenPlotIcons)
        pl.sub.add(ColorPickerSetting("Number Colour", "", X::gardenPlotIconColor))
        pl.sub.add(ToggleSetting("Plot Prices", "Cost on plot tooltips", X::gardenPlotPrices))
        pl.sub.add(ToggleSetting("/gp Shortcut", "/gp <plot> runs /plottp <plot> when you type it", X::gardenPlotCommands))
        col.features.add(pl)

        col.features.add(Feature("Farming Toolkit Icons", X::gardenToolkitIcons))
    }

    fun describe(name: String): String? = when (name) {
        "Jeff Buy Order" -> "Chip buy order HUD inside Jeff's shop"
        "Charmed Visitor HUD" -> "Charmed visitor perk progress from the tab list"
        "Composter Upgrade Prices" -> "Upgrade costs rounded down, cheapest highlighted"
        "Stereo Harmony" -> "Active vinyl with its pest and crop; crop icons in the menu"
        "Greenhouse Status" -> "Harvestable and water status from the tab list"
        "Mutations Link" -> "/fmmutations: clickable, copied mutations website link"
        "Anita Shop Extras" -> "Fortune per medal and coins per medal on Anita's shop"
        "Plot Icons" -> "Plot numbers, prices and /gp shortcut"
        "Farming Toolkit Icons" -> "Crop icons in the farming toolkit menu"
        else -> null
    }
}
