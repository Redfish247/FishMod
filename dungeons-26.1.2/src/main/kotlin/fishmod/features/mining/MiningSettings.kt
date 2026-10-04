package fishmod.features.mining

import fishmod.shaded.practicalconfig.manager.ConfigValue

object MiningSettings {

    // Crystal Nucleus
    @ConfigValue @JvmField var miningNucleusBoxes: Boolean = true
    @ConfigValue @JvmField var miningNucleusFilled: Boolean = true
    @ConfigValue @JvmField var miningNucleusOpacity: Int = 35
    @ConfigValue @JvmField var miningNucleusOnlyInside: Boolean = true
    @ConfigValue @JvmField var miningNucleusAmber: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var miningNucleusAmethyst: Int = 0xFFAA00AA.toInt()
    @ConfigValue @JvmField var miningNucleusTopaz: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var miningNucleusJade: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var miningNucleusSapphire: Int = 0xFF55FFFF.toInt()

    // Mineshaft pity
    @ConfigValue @JvmField var miningPity: Boolean = true
    @ConfigValue @JvmField var miningPityBreakdown: Boolean = false
    @ConfigValue @JvmField var miningPityShowChance: Boolean = true
    @ConfigValue @JvmField var miningPityGraceSec: Int = 30
    @ConfigValue @JvmField var miningPityPoints: Int = 0
    @ConfigValue @JvmField var miningPityBlocks: Int = 0
    @ConfigValue @JvmField var miningPityHudX: Int = 5
    @ConfigValue @JvmField var miningPityHudY: Int = 120
    @ConfigValue @JvmField var miningPityHudScale: Double = 1.0

    // Gemstone locator
    @ConfigValue @JvmField var miningGemLines: Boolean = true
    @ConfigValue @JvmField var miningGemBoxes: Boolean = true
    @ConfigValue @JvmField var miningGemRadius: Int = 24
    @ConfigValue @JvmField var miningGemMax: Int = 3
    @ConfigValue @JvmField var miningGemLineWidth: Int = 3
    @ConfigValue @JvmField var miningGemOpacity: Int = 60

    // Fossil solver
    @ConfigValue @JvmField var miningFossilSolver: Boolean = true
    @ConfigValue @JvmField var miningFossilColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var miningFossilOpacity: Int = 55
    @ConfigValue @JvmField var miningFossilPercent: Boolean = true
    @ConfigValue @JvmField var miningFossilHudX: Int = 5
    @ConfigValue @JvmField var miningFossilHudY: Int = 40
    @ConfigValue @JvmField var miningFossilHudScale: Double = 1.0

    // Profit tracker
    @ConfigValue @JvmField var miningProfit: Boolean = true
    @ConfigValue @JvmField var miningProfitMode: String = "Session"
    @ConfigValue @JvmField var miningProfitCategory: String = "Combined"
    @ConfigValue @JvmField var miningProfitSort: String = "Most Profit"
    @ConfigValue @JvmField var miningProfitLines: Int = 25
    @ConfigValue @JvmField var miningProfitPriceMode: String = "Sell Offer"
    @ConfigValue @JvmField var miningProfitShowPowder: Boolean = true
    @ConfigValue @JvmField var miningProfitShowSources: Boolean = true
    @ConfigValue @JvmField var miningProfitAfkSec: Int = 60
    @ConfigValue @JvmField var miningProfitHudX: Int = 5
    @ConfigValue @JvmField var miningProfitHudY: Int = 160
    @ConfigValue @JvmField var miningProfitHudScale: Double = 1.0

    // Abilities
    @ConfigValue @JvmField var miningAbilityTitle: Boolean = true
    @ConfigValue @JvmField var miningAbilityTitleText: String = "§a{ability} Ready!"
    @ConfigValue @JvmField var miningAbilityTitleMs: Int = 1500
    @ConfigValue @JvmField var miningAbilitySound: Boolean = true
    @ConfigValue @JvmField var miningPickobulus: Boolean = true
    @ConfigValue @JvmField var miningPickobulusColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var miningPickobulusOpacity: Int = 25
    @ConfigValue @JvmField var miningPickobulusHud: Boolean = true
    @ConfigValue @JvmField var miningPickobulusHudX: Int = 200
    @ConfigValue @JvmField var miningPickobulusHudY: Int = 40
    @ConfigValue @JvmField var miningPickobulusHudScale: Double = 1.0
    @ConfigValue @JvmField var miningManiac: Boolean = true
    @ConfigValue @JvmField var miningManiacHudX: Int = 200
    @ConfigValue @JvmField var miningManiacHudY: Int = 100
    @ConfigValue @JvmField var miningManiacHudScale: Double = 1.0
    @ConfigValue @JvmField var miningSkyMall: Boolean = true
    @ConfigValue @JvmField var miningSkyMallPerk: String = ""
    @ConfigValue @JvmField var miningSkyMallHudX: Int = 200
    @ConfigValue @JvmField var miningSkyMallHudY: Int = 120
    @ConfigValue @JvmField var miningSkyMallHudScale: Double = 1.0

    // Commissions
    @ConfigValue @JvmField var miningCommHud: Boolean = true
    @ConfigValue @JvmField var miningCommTitle: Boolean = true
    @ConfigValue @JvmField var miningCommTitleText: String = "§a§lCommission Complete!"
    @ConfigValue @JvmField var miningCommHighlight: Boolean = true
    @ConfigValue @JvmField var miningCommHighlightColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var miningCommHighlightOpacity: Int = 50
    @ConfigValue @JvmField var miningCommHudX: Int = 5
    @ConfigValue @JvmField var miningCommHudY: Int = 60
    @ConfigValue @JvmField var miningCommHudScale: Double = 1.0

    // Powder chests
    @ConfigValue @JvmField var miningChests: Boolean = true
    @ConfigValue @JvmField var miningChestLine: Boolean = true
    @ConfigValue @JvmField var miningChestColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var miningChestOpacity: Int = 45
    @ConfigValue @JvmField var miningChestLineWidth: Int = 3

    // Corpses / shafts
    @ConfigValue @JvmField var miningCorpseAnnounce: Boolean = false
    @ConfigValue @JvmField var miningCorpseFormat: String = "x: {x}, y: {y}, z: {z} | {type} Corpse"
    @ConfigValue @JvmField var miningCorpseLapis: Boolean = true
    @ConfigValue @JvmField var miningCorpseUmber: Boolean = true
    @ConfigValue @JvmField var miningCorpseTungsten: Boolean = true
    @ConfigValue @JvmField var miningCorpseVanguard: Boolean = true
    @ConfigValue @JvmField var miningShaftTitle: Boolean = true
    @ConfigValue @JvmField var miningShaftParty: Boolean = false
    @ConfigValue @JvmField var miningShaftFormat: String = "{type} | {corpses}"
    @ConfigValue @JvmField var miningShaftTitleMs: Int = 3000
}
