package fishmod.features.fishing

import fishmod.features.FishModScreen.Column

// Phase 4 (world/items) owns this file. init() runs from Fishing.init(), addMenu()/describe() from FishingMenu.
object FishingWorld {
    @JvmStatic fun init() {}

    fun addMenu(col: Column) {}

    fun describe(name: String): String? = null
}
