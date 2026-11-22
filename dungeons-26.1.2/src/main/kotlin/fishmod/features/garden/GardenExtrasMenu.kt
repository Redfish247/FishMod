package fishmod.features.garden

import fishmod.features.FishModScreen.ColorPickerSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.InputSetting
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.garden.GardenExtraSettings as X

object GardenExtrasMenu {

    fun add(col: Column) {
        val jeff = Feature("Jeff Buy Order", X::class.java, "gardenJeff")
        jeff.sub.add(InputSetting("Order", "Comma-separated chip names, first not-maxed is highlighted", X::class.java, "gardenJeffOrder"))
        jeff.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", X::class.java, "gardenJeffColor"))
        col.features.add(jeff)

        val ch = Feature("Charmed Visitor HUD", X::class.java, "gardenCharmed")
        ch.sub.add(InputSetting("Tab Header", "Text of the tab-list widget line to show", X::class.java, "gardenCharmedHeader"))
        ch.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", X::class.java, "gardenCharmedColor"))
        col.features.add(ch)

        val cu = Feature("Composter Upgrade Prices", X::class.java, "gardenCompUpgrades")
        cu.sub.add(ColorPickerSetting("Cheapest Highlight", "", X::class.java, "gardenCompUpgradesColor"))
        col.features.add(cu)

        val st = Feature("Stereo Harmony", X::class.java, "gardenStereo")
        st.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", X::class.java, "gardenStereoColor"))
        col.features.add(st)

        val gh = Feature("Greenhouse Status", X::class.java, "gardenGreenhouse")
        gh.sub.add(ColorPickerSetting("Good Colour", "Harvestable / watered", X::class.java, "gardenGreenhouseGoodColor"))
        gh.sub.add(ColorPickerSetting("Bad Colour", "Low water", X::class.java, "gardenGreenhouseBadColor"))
        col.features.add(gh)

        val mut = Feature("Mutations Link", X::class.java, "gardenMutationsLinkOn")
        mut.sub.add(InputSetting("URL", "/fmmutations prints it as a clickable link and copies it", X::class.java, "gardenMutationsUrl"))
        col.features.add(mut)

        val an = Feature("Anita Shop Extras", X::class.java, "gardenAnita")
        an.sub.add(ColorPickerSetting("Best Medal Highlight", "", X::class.java, "gardenAnitaColor"))
        col.features.add(an)

        val pl = Feature("Plot Icons", X::class.java, "gardenPlotIcons")
        pl.sub.add(ColorPickerSetting("Number Colour", "", X::class.java, "gardenPlotIconColor"))
        pl.sub.add(ToggleSetting("Plot Prices", "Cost on plot tooltips", X::class.java, "gardenPlotPrices"))
        pl.sub.add(ToggleSetting("/gp Shortcut", "/gp <plot> runs /plottp <plot> when you type it", X::class.java, "gardenPlotCommands"))
        col.features.add(pl)

        col.features.add(Feature("Farming Toolkit Icons", X::class.java, "gardenToolkitIcons"))

        val ang = Feature("Yaw / Pitch Display", X::class.java, "gardenAngles")
        ang.sub.add(ColorPickerSetting("Label Colour", "Nearest chat colour is used", X::class.java, "gardenAnglesColor"))
        col.features.add(ang)

        val bps = Feature("BPS Tracker", X::class.java, "gardenBps")
        bps.sub.add(ToggleSetting("Crop Speed Meter", "Crops broken per minute by crop type", X::class.java, "gardenCropSpeed"))
        bps.sub.add(ColorPickerSetting("Label Colour", "Nearest chat colour is used", X::class.java, "gardenBpsColor"))
        col.features.add(bps)

        val man = Feature("Mantid Bonus HUD", X::class.java, "gardenMantid")
        man.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", X::class.java, "gardenMantidColor"))
        col.features.add(man)

        val spr = Feature("Spray Expiry Alert", X::class.java, "gardenSprayAlert")
        spr.sub.add(fishmod.features.FishModScreen.SliderIntSetting("Warn Seconds Before", "", X::class.java, "gardenSprayAlertSeconds", 5, 300))
        spr.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", X::class.java, "gardenSprayAlertColor"))
        col.features.add(spr)

        col.features.add(Feature("Phantomleaf Solver", X::class.java, "gardenPhantomleaf"))

        val dna = Feature("DNA Analyzer Solver", X::class.java, "gardenDna")
        dna.sub.add(ColorPickerSetting("Swap Colour", "", X::class.java, "gardenDnaColor"))
        col.features.add(dna)

        val sm = Feature("SkyMart Copper Value", X::class.java, "gardenSkyMart")
        sm.sub.add(ColorPickerSetting("Label Colour", "Nearest chat colour is used", X::class.java, "gardenSkyMartColor"))
        col.features.add(sm)

        col.features.add(Feature("Watering Helper", X::class.java, "gardenWatering"))
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
        "Yaw / Pitch Display" -> "Your yaw and pitch as a HUD while in the Garden"
        "BPS Tracker" -> "Crop blocks you break per second (last 5s), optional crops/min per type"
        "Mantid Bonus HUD" -> "Mantid reforge pest-kill stacks (max 20) and next expiry"
        "Spray Expiry Alert" -> "Title when the current plot's spray is about to run out or expires"
        "Phantomleaf Solver" -> "Close / Found title from the hint sound after a hit"
        "DNA Analyzer Solver" -> "Highlights the next two cells to swap in the DNA Analyzer"
        "SkyMart Copper Value" -> "Coins per copper on SkyMart item tooltips"
        "Watering Helper" -> "Bigger, through-wall greenhouse water level bars"
        else -> null
    }
}
