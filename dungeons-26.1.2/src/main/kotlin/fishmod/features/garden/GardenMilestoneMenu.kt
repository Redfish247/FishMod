package fishmod.features.garden

import fishmod.features.FishModScreen.ButtonSetting
import fishmod.features.FishModScreen.ColorPickerSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.garden.GardenMilestoneSettings as S

// Farming-tab entries for milestone / level / hoe / misc cards
object GardenMilestoneMenu {

    fun addTo(col: Column) {
        val mh = Feature("Crop Milestone HUD", S::gardenMilestoneHud)
        mh.sub.add(ToggleSetting("ETA", "Time to the next milestone from recent progress", S::gardenMilestoneEta))
        mh.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::gardenMilestoneColor))
        mh.sub.add(ButtonSetting("Copy Milestone Data", "Copies known crop tiers to the clipboard", "Copy") { GardenMilestones.copy() })
        col.features.add(mh)

        val icons = Feature("Garden Menu Numbers", S::gardenIconNumbers)
        icons.sub.add(ColorPickerSetting("Number Colour", "", S::gardenIconNumberColor))
        col.features.add(icons)

        col.features.add(Feature("Milestone Tooltip Totals", S::gardenMilestoneTooltip))

        val gl = Feature("Garden Level Overflow", S::gardenLevelHud)
        gl.sub.add(ToggleSetting("Chat Message", "Message when you reach a new overflow level", S::gardenLevelChat))
        gl.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::gardenLevelColor))
        col.features.add(gl)

        val hoe = Feature("Hoe Level Overflow", S::gardenHoeHud)
        hoe.sub.add(ToggleSetting("Mute Hoe Sounds", "Silences hoe tilling sounds while holding a hoe", S::gardenHoeMute))
        hoe.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::gardenHoeColor))
        col.features.add(hoe)

        val lb = Feature("Log Book Stats", S::gardenLogBookHud)
        lb.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::gardenLogBookColor))
        col.features.add(lb)

        val ca = Feature("Help Carrolyn", S::gardenCarrolyn)
        ca.sub.add(ColorPickerSetting("Title Colour", "Nearest chat colour is used", S::gardenCarrolynColor))
        ca.sub.add(ButtonSetting("Reset", "", "Reset") { GardenMisc.resetCarrolyn() })
        col.features.add(ca)
    }

    fun describe(name: String): String? = when (name) {
        "Crop Milestone HUD" -> "Progress, next milestone and ETA for the crop you farm"
        "Garden Menu Numbers" -> "Levels drawn on milestone, crop upgrade and composter upgrade icons"
        "Milestone Tooltip Totals" -> "Total and average milestone levels on milestone tooltips"
        "Garden Level Overflow" -> "Garden level past max in a HUD and chat"
        "Hoe Level Overflow" -> "Hoe level overflow HUD, optional muted hoe sounds"
        "Log Book Stats" -> "Visitor Log Book stats HUD"
        "Help Carrolyn" -> "Checklist of Carrolyn's requested items"
        else -> null
    }
}
