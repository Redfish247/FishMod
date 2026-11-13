package fishmod.features.mining

import fishmod.features.FishModScreen.ButtonSetting
import fishmod.features.FishModScreen.ColorPickerSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.DropdownSetting
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.InputSetting
import fishmod.features.FishModScreen.SliderIntSetting
import fishmod.features.FishModScreen.SubcategoryHeader
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.mining.MiningSettings as S

object MiningMenu {

    fun column(): Column {
        val col = Column("Mining")

        val nuc = Feature("Crystal Nucleus Highlights", S::class.java, "miningNucleusBoxes")
        nuc.sub.add(ToggleSetting("Only In Nucleus", "Hide unless you're in the Crystal Nucleus area", S::class.java, "miningNucleusOnlyInside"))
        nuc.sub.add(ToggleSetting("Filled", "Fill the boxes, not just outline", S::class.java, "miningNucleusFilled"))
        nuc.sub.add(SliderIntSetting("Fill Opacity %", "", S::class.java, "miningNucleusOpacity", 5, 100).gatedBy { S.miningNucleusFilled })
        nuc.sub.add(ColorPickerSetting("Amber", "", S::class.java, "miningNucleusAmber"))
        nuc.sub.add(ColorPickerSetting("Amethyst", "", S::class.java, "miningNucleusAmethyst"))
        nuc.sub.add(ColorPickerSetting("Topaz", "", S::class.java, "miningNucleusTopaz"))
        nuc.sub.add(ColorPickerSetting("Jade", "", S::class.java, "miningNucleusJade"))
        nuc.sub.add(ColorPickerSetting("Sapphire", "", S::class.java, "miningNucleusSapphire"))
        col.features.add(nuc)

        val pity = Feature("Mineshaft Pity Display", S::class.java, "miningPity")
        pity.sub.add(SliderIntSetting("Grace After Shaft (s)", "Don't count blocks this long after entering a shaft", S::class.java, "miningPityGraceSec", 0, 120, 5))
        pity.sub.add(ToggleSetting("Show Chance", "", S::class.java, "miningPityShowChance"))
        pity.sub.add(ToggleSetting("Blocks Left Breakdown", "Blocks of each type until pity", S::class.java, "miningPityBreakdown"))
        pity.sub.add(ButtonSetting("Reset Counter", "", "Reset") { S.miningPityPoints = 0; S.miningPityBlocks = 0 })
        col.features.add(pity)

        val gem = Feature("Commission Gemstone Lines", S::class.java, "miningGemLines")
        gem.sub.add(ToggleSetting("Walking Paths", "Lines along the tunnels to the nearest spot for each gem (SkyHanni map)", S::class.java, "miningGemPaths"))
        gem.sub.add(ToggleSetting("Boxes", "Box the gemstone blocks too", S::class.java, "miningGemBoxes"))
        gem.sub.add(SliderIntSetting("Scan Radius", "", S::class.java, "miningGemRadius", 8, 48).gatedBy { S.miningGemBoxes })
        gem.sub.add(SliderIntSetting("Boxes Per Gem", "", S::class.java, "miningGemMax", 1, 10).gatedBy { S.miningGemBoxes })
        gem.sub.add(SliderIntSetting("Line Width", "", S::class.java, "miningGemLineWidth", 1, 10))
        gem.sub.add(SliderIntSetting("Box Opacity %", "", S::class.java, "miningGemOpacity", 5, 100).gatedBy { S.miningGemBoxes })
        col.features.add(gem)

        val fossil = Feature("Fossil Excavator Solver", S::class.java, "miningFossilSolver")
        fossil.sub.add(ColorPickerSetting("Highlight Color", "", S::class.java, "miningFossilColor"))
        fossil.sub.add(SliderIntSetting("Highlight Opacity %", "", S::class.java, "miningFossilOpacity", 10, 100))
        fossil.sub.add(ToggleSetting("Show Chance", "Fossil chance on the slot", S::class.java, "miningFossilPercent"))
        col.features.add(fossil)

        val profit = Feature("Mining Profit Tracker", S::class.java, "miningProfit")
        profit.sub.add(DropdownSetting("Category", "", MiningProfitTracker.CATEGORIES, { S.miningProfitCategory }, { v -> S.miningProfitCategory = v }))
        profit.sub.add(DropdownSetting("Sort", "", MiningProfitTracker.SORTS, { S.miningProfitSort }, { v -> S.miningProfitSort = v }))
        profit.sub.add(DropdownSetting("Tracker", "", arrayOf("Session", "Total"), { S.miningProfitMode }, { v -> S.miningProfitMode = v }))
        profit.sub.add(SliderIntSetting("Visible Lines", "Scroll over the HUD in your inventory for the rest", S::class.java, "miningProfitLines", 5, 25))
        profit.sub.add(ToggleSetting("Show Powder", "", S::class.java, "miningProfitShowPowder"))
        profit.sub.add(ToggleSetting("Show Sources", "Per-source counts after merged items", S::class.java, "miningProfitShowSources"))
        profit.sub.add(DropdownSetting("Bazaar Price", "", arrayOf("Sell Offer", "Insta Sell"), { S.miningProfitPriceMode }, { v -> S.miningProfitPriceMode = v }))
        profit.sub.add(SliderIntSetting("AFK Timeout (s)", "", S::class.java, "miningProfitAfkSec", 10, 900, 5))
        profit.sub.add(ButtonSetting("Reset Session", "", "Reset") { MiningProfitTracker.resetSession() })
        profit.sub.add(ButtonSetting("Reset Total", "", "Reset") { MiningProfitTracker.resetTotal() })
        col.features.add(profit)

        val ab = Feature("Mining Abilities", S::class.java, "miningAbilities")
        ab.sub.add(SubcategoryHeader("Ability Ready Title"))
        ab.sub.add(ToggleSetting("Ready Title", "Title when a pickaxe ability is off cooldown", S::class.java, "miningAbilityTitle"))
        ab.sub.add(InputSetting("Title Text", "{ability} = ability name", S::class.java, "miningAbilityTitleText").gatedBy { S.miningAbilityTitle })
        ab.sub.add(SliderIntSetting("Title Time (ms)", "", S::class.java, "miningAbilityTitleMs", 500, 5000, 100).gatedBy { S.miningAbilityTitle })
        ab.sub.add(ToggleSetting("Sound", "", S::class.java, "miningAbilitySound").gatedBy { S.miningAbilityTitle })
        ab.sub.add(SubcategoryHeader("Pickobulus"))
        ab.sub.add(ToggleSetting("Pickobulus Overlay", "Outline the blocks Pickobulus will break", S::class.java, "miningPickobulus"))
        ab.sub.add(ToggleSetting("Pickobulus HUD", "Block count by type", S::class.java, "miningPickobulusHud").gatedBy { S.miningPickobulus })
        ab.sub.add(ColorPickerSetting("Overlay Color", "", S::class.java, "miningPickobulusColor").gatedBy { S.miningPickobulus })
        ab.sub.add(SliderIntSetting("Fill Opacity %", "0 = outline only", S::class.java, "miningPickobulusOpacity", 0, 100).gatedBy { S.miningPickobulus })
        ab.sub.add(SubcategoryHeader("Other"))
        ab.sub.add(ToggleSetting("Maniac Miner HUD", "Blocks broken during Maniac Miner", S::class.java, "miningManiac"))
        ab.sub.add(ToggleSetting("SkyMall HUD", "Current SkyMall perk (from chat)", S::class.java, "miningSkyMall"))
        col.features.add(ab)

        val comm = Feature("Commissions", S::class.java, "miningCommissions")
        comm.sub.add(ToggleSetting("Commission HUD", "Current commissions from the tab list", S::class.java, "miningCommHud"))
        comm.sub.add(ToggleSetting("Completed Title", "", S::class.java, "miningCommTitle"))
        comm.sub.add(InputSetting("Title Text", "", S::class.java, "miningCommTitleText").gatedBy { S.miningCommTitle })
        comm.sub.add(ToggleSetting("Highlight Completed", "In the Commissions menu", S::class.java, "miningCommHighlight"))
        comm.sub.add(ColorPickerSetting("Highlight Color", "", S::class.java, "miningCommHighlightColor").gatedBy { S.miningCommHighlight })
        comm.sub.add(SliderIntSetting("Highlight Opacity %", "", S::class.java, "miningCommHighlightOpacity", 10, 100).gatedBy { S.miningCommHighlight })
        col.features.add(comm)

        val chest = Feature("Powder Chest Overlay", S::class.java, "miningChests")
        chest.sub.add(ToggleSetting("Line", "Line to each chest", S::class.java, "miningChestLine"))
        chest.sub.add(ColorPickerSetting("Color", "", S::class.java, "miningChestColor"))
        chest.sub.add(SliderIntSetting("Opacity %", "", S::class.java, "miningChestOpacity", 5, 100))
        chest.sub.add(SliderIntSetting("Line Width", "", S::class.java, "miningChestLineWidth", 1, 10).gatedBy { S.miningChestLine })
        col.features.add(chest)

        val corpse = Feature("Corpse Announcer", S::class.java, "miningCorpseAnnounce")
        corpse.sub.add(InputSetting("Message", "{x} {y} {z} {type}", S::class.java, "miningCorpseFormat"))
        corpse.sub.add(ToggleSetting("Lapis", "", S::class.java, "miningCorpseLapis"))
        corpse.sub.add(ToggleSetting("Umber", "", S::class.java, "miningCorpseUmber"))
        corpse.sub.add(ToggleSetting("Tungsten", "", S::class.java, "miningCorpseTungsten"))
        corpse.sub.add(ToggleSetting("Vanguard", "", S::class.java, "miningCorpseVanguard"))
        col.features.add(corpse)

        val shaft = Feature("Mineshaft Announce", S::class.java, "miningShaftTitle")
        shaft.sub.add(ToggleSetting("Party Message", "Tell party the shaft type and corpses", S::class.java, "miningShaftParty"))
        shaft.sub.add(InputSetting("Format", "{type} {corpses}, e.g. Ruby 2 | 2L 1U", S::class.java, "miningShaftFormat"))
        shaft.sub.add(SliderIntSetting("Title Time (ms)", "", S::class.java, "miningShaftTitleMs", 500, 8000, 100))
        col.features.add(shaft)

        return col
    }

    fun describe(name: String): String? = when (name) {
        "Crystal Nucleus Highlights" -> "Boxes where each crystal is placed in the Nucleus"
        "Mineshaft Pity Display" -> "Pity counter HUD in the Glacite Tunnels"
        "Commission Gemstone Lines" -> "Lines to gemstones your commissions want"
        "Fossil Excavator Solver" -> "Highlights the best slot to excavate"
        "Mining Profit Tracker" -> "Corpses, mining, fossils, nucleus and chest profit"
        "Mining Abilities" -> "Ready title, Pickobulus overlay, Maniac Miner, SkyMall"
        "Commissions" -> "Commission HUD, completed title and menu highlight"
        "Powder Chest Overlay" -> "Boxes and lines to Crystal Hollows treasure chests"
        "Corpse Announcer" -> "Party chat once per corpse you see in a mineshaft"
        "Mineshaft Announce" -> "Title (and party message) with shaft type and corpses"
        else -> null
    }
}
