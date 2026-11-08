package fishmod.features.fishing

import fishmod.features.FishModScreen.Column

// Phase 2 (alerts/share) owns this file. init() runs from Fishing.init(), addMenu()/describe() from FishingMenu.
object FishingAlerts {
    @JvmStatic fun init() {}

    fun addMenu(col: Column) {}

    fun describe(name: String): String? = null
}
