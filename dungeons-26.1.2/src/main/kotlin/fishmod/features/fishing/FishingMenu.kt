package fishmod.features.fishing

import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.fishing.FishingSettings as S

object FishingMenu {

    fun column(): Column {
        val col = Column("Fishing")

        val core = Feature("Fishing", S::class.java, "fishing")
        core.sub.add(ToggleSetting("Any Area", "Detect catches in every world, not just fishing worlds", S::class.java, "fishingAnyArea"))
        core.sub.add(ToggleSetting("Debug Catch Log", "Print each detected catch or drop to chat", S::class.java, "fishingDebug"))
        col.features.add(core)

        FishingAlerts.addMenu(col)
        FishingTrackers.addMenu(col)
        FishingWorld.addMenu(col)
        return col
    }

    fun describe(name: String): String? = FishingAlerts.describe(name) ?: FishingTrackers.describe(name) ?: FishingWorld.describe(name) ?: when (name) {
        "Fishing" -> "Master switch for all fishing features (all off by default)"
        else -> null
    }
}
