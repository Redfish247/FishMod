package fishmod.features.fishing

import fishmod.features.FishModScreen.Column

// Phase 3 (trackers) owns this file. init() runs from Fishing.init(), addMenu()/describe() from FishingMenu.
object FishingTrackers {
    @JvmStatic fun init() {}

    fun addMenu(col: Column) {}

    fun describe(name: String): String? = null
}
