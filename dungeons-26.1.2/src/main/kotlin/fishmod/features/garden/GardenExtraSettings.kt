package fishmod.features.garden

import fishmod.shaded.practicalconfig.manager.ConfigValue

// Settings for the Working-On garden cards (Jeff, perks, menus, plots)
object GardenExtraSettings {

    // Jeff chip buy order
    @ConfigValue @JvmField var gardenJeff: Boolean = false
    @ConfigValue @JvmField var gardenJeffOrder: String = "Rarefinder, Vermin Vaporizer, Hypercharge, Cropshot, Overdrive, Evergreen, Mechamind, Quickdraw"
    @ConfigValue @JvmField var gardenJeffColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var gardenJeffHudX: Int = 5
    @ConfigValue @JvmField var gardenJeffHudY: Int = 100
    @ConfigValue @JvmField var gardenJeffHudScale: Double = 1.0

    // Charmed visitor + mushroom pet perk HUDs (tab-list widgets)
    @ConfigValue @JvmField var gardenCharmed: Boolean = false
    @ConfigValue @JvmField var gardenCharmedHeader: String = "Charmed"
    @ConfigValue @JvmField var gardenCharmedColor: Int = 0xFFFF55FF.toInt()
    @ConfigValue @JvmField var gardenCharmedHudX: Int = 5
    @ConfigValue @JvmField var gardenCharmedHudY: Int = 130
    @ConfigValue @JvmField var gardenCharmedHudScale: Double = 1.0

    // Composter upgrade prices
    @ConfigValue @JvmField var gardenCompUpgrades: Boolean = false
    @ConfigValue @JvmField var gardenCompUpgradesColor: Int = 0xFF55FF55.toInt()

    // Stereo Harmony
    @ConfigValue @JvmField var gardenStereo: Boolean = false
    @ConfigValue @JvmField var gardenStereoActive: String = ""
    @ConfigValue @JvmField var gardenStereoColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var gardenStereoHudX: Int = 200
    @ConfigValue @JvmField var gardenStereoHudY: Int = 120
    @ConfigValue @JvmField var gardenStereoHudScale: Double = 1.0

    // Greenhouse status
    @ConfigValue @JvmField var gardenGreenhouse: Boolean = false
    @ConfigValue @JvmField var gardenGreenhouseGoodColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var gardenGreenhouseBadColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var gardenGreenhouseHudX: Int = 200
    @ConfigValue @JvmField var gardenGreenhouseHudY: Int = 150
    @ConfigValue @JvmField var gardenGreenhouseHudScale: Double = 1.0

    // Mutations link (/fmmutations)
    @ConfigValue @JvmField var gardenMutationsLinkOn: Boolean = true
    @ConfigValue @JvmField var gardenMutationsUrl: String = "https://elitebot.dev/mutations"

    // Anita shop
    @ConfigValue @JvmField var gardenAnita: Boolean = false
    @ConfigValue @JvmField var gardenAnitaColor: Int = 0xFFFFAA00.toInt()

    // Plots: icons, prices, /gp shortcut
    @ConfigValue @JvmField var gardenPlotIcons: Boolean = false
    @ConfigValue @JvmField var gardenPlotIconColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var gardenPlotPrices: Boolean = false
    @ConfigValue @JvmField var gardenPlotCommands: Boolean = false

    // Farming toolkit menu icons
    @ConfigValue @JvmField var gardenToolkitIcons: Boolean = false
}
