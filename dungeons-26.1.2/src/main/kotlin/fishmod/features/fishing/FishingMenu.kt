package fishmod.features.fishing

import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.SliderIntSetting
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.fishing.FishingSettings as S

object FishingMenu {

    fun column(): Column {
        val col = Column("Fishing")

        val core = Feature("Fishing", S::class.java, "fishing")
        core.sub.add(ToggleSetting("Any Area", "Detect catches in every world, not just fishing worlds", S::class.java, "fishingAnyArea"))
        core.sub.add(ToggleSetting("Debug Catch Log", "Print each detected catch or drop to chat", S::class.java, "fishingDebug"))
        col.features.add(core)
        col.features.add(Feature("Current Bait HUD", FishingWorldSettings::class.java, "baitHud"))
        col.features.add(Feature("Instant Underwater Fog Clear", FishingWorldSettings::class.java, "instantWaterFog"))
        val shards = Feature("Shard Tracker", FishingWorldSettings::class.java, "shardTracker")
        shards.sub.add(ToggleSetting("Reset On World Change", "Clear the counts when you change servers", FishingWorldSettings::class.java, "shardTrackerResetOnSwap"))
        shards.sub.add(SliderIntSetting("Rows", "How many shard types to list", { FishingWorldSettings.shardTrackerRows }, { FishingWorldSettings.shardTrackerRows = it }, 1, 20))
        col.features.add(shards)

        FishingAlerts.addMenu(col)
        FishingTrackers.addMenu(col)
        FishingWorld.addMenu(col)
        return col
    }

    fun describe(name: String): String? = FishingAlerts.describe(name) ?: FishingTrackers.describe(name) ?: FishingWorld.describe(name) ?: when (name) {
        "Current Bait HUD" -> "Bait in your last hotbar slot and how much is left"
        "Instant Underwater Fog Clear" -> "Skip the slow fade when your view clears underwater"
        "Shard Tracker" -> "HUD counting shards caught or found this session"
        "Fishing" -> "Master switch for all fishing features (all off by default)"
        else -> null
    }
}
