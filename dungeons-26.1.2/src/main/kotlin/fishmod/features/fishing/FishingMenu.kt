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
        col.features.add(Feature("Golden Fish Timer", FishingWorldSettings::class.java, "goldenFishTimer"))
        val totem = Feature("Totem of Corruption Overlay", FishingWorldSettings::class.java, "totemOverlay")
        totem.sub.add(ToggleSetting("Range Ring", "Draw the 16-block effect radius", FishingWorldSettings::class.java, "totemRing"))
        totem.sub.add(SliderIntSetting("Max Distance", "Only track totems this close", { FishingWorldSettings.totemDistance }, { FishingWorldSettings.totemDistance = it }, 5, 64))
        totem.sub.add(fishmod.features.FishModScreen.ColorPickerSetting("Ring Color", "", FishingWorldSettings::class.java, "totemColor"))
        col.features.add(totem)
        val gb = Feature("Geyser Box", FishingWorldSettings::class.java, "geyserBox")
        gb.sub.add(fishmod.features.FishModScreen.ColorPickerSetting("Color", "", FishingWorldSettings::class.java, "geyserColor"))
        col.features.add(gb)
        val sk = Feature("Sulphur Skitter Box", FishingWorldSettings::class.java, "skitterBox")
        sk.sub.add(ToggleSetting("Filled", "Fill the box instead of outline only", FishingWorldSettings::class.java, "skitterFilled"))
        sk.sub.add(fishmod.features.FishModScreen.ColorPickerSetting("Color", "", FishingWorldSettings::class.java, "skitterColor"))
        col.features.add(sk)
        col.features.add(Feature("Sea Creature Kill Timer", FishingWorldSettings::class.java, "scKillTimer"))
        val cap = Feature("Sea Creature Cap Warning", FishingWorldSettings::class.java, "scCap")
        cap.sub.add(SliderIntSetting("Cap", "Alive creatures that trigger the warning", { FishingWorldSettings.scCapMax }, { FishingWorldSettings.scCapMax = it }, 1, 60))
        cap.sub.add(ToggleSetting("Count HUD", "Show your alive creature count", FishingWorldSettings::class.java, "scCapHud"))
        cap.sub.add(fishmod.features.FishModScreen.SoundSearchSetting("Sound", "", { FishingWorldSettings.scCapSound }, { v -> FishingWorldSettings.scCapSound = v }, { 100 }))
        col.features.add(cap)
        col.features.add(Feature("Trophy Fish HUD", FishingWorldSettings::class.java, "trophyHud"))

        FishingAlerts.addMenu(col)
        FishingTrackers.addMenu(col)
        FishingWorld.addMenu(col)
        return col
    }

    fun huntingColumn(): Column {
        val col = Column("Hunting & Foraging")
        val shards = Feature("Shard Tracker", FishingWorldSettings::class.java, "shardTracker")
        shards.sub.add(ToggleSetting("Reset On World Change", "Clear the counts when you change servers", FishingWorldSettings::class.java, "shardTrackerResetOnSwap"))
        shards.sub.add(SliderIntSetting("Rows", "How many shard types to list", { FishingWorldSettings.shardTrackerRows }, { FishingWorldSettings.shardTrackerRows = it }, 1, 20))
        col.features.add(shards)
        val fk = Feature("Fusion Keybinds", FishingWorldSettings::class.java, "fusionKeys")
        fk.sub.add(fishmod.features.FishModScreen.KeybindSetting("Repeat Fusion", "Clicks Repeat Previous Fusion in the Fusion Box", { FusionKeys.repeat }))
        fk.sub.add(fishmod.features.FishModScreen.KeybindSetting("Confirm", "Clicks the green confirm button", { FusionKeys.confirm }))
        fk.sub.add(fishmod.features.FishModScreen.KeybindSetting("Cancel", "Clicks the red cancel button", { FusionKeys.cancel }))
        col.features.add(fk)
        col.features.add(Feature("Mute Phantoms", FishingWorldSettings::class.java, "mutePhantoms"))
        val cb = Feature("Cinderbat Highlight", FishingWorldSettings::class.java, "cinderbatHighlight")
        cb.sub.add(fishmod.features.FishModScreen.ColorPickerSetting("Color", "", FishingWorldSettings::class.java, "cinderbatColor"))
        col.features.add(cb)
        val ib = Feature("Invisibug Highlight", FishingWorldSettings::class.java, "invisibugHighlight")
        ib.sub.add(fishmod.features.FishModScreen.ColorPickerSetting("Color", "", FishingWorldSettings::class.java, "invisibugColor"))
        col.features.add(ib)
        val la = Feature("Lasso Reel Alert", FishingWorldSettings::class.java, "lassoAlert")
        la.sub.add(fishmod.features.FishModScreen.SoundSearchSetting("Sound", "", { FishingWorldSettings.lassoSound }, { v -> FishingWorldSettings.lassoSound = v }, { 100 }))
        col.features.add(la)
        col.features.add(Feature("Honeycomb Pot Timer", FishingWorldSettings::class.java, "honeycombTimer"))
        val hive = Feature("Honeyhive Reminder", fishmod.features.mining.MiningSettings::class.java, "foragingHoneyhive")
        hive.sub.add(fishmod.features.FishModScreen.SliderIntSetting("Remind After (min)", "Minutes after you loot a honeyhive", fishmod.features.mining.MiningSettings::class.java, "foragingHoneyhiveMin", 5, 180))
        hive.sub.add(ToggleSetting("Queen Bee Title", "Title when a Queen Bee refills the hive", fishmod.features.mining.MiningSettings::class.java, "foragingQueenBee"))
        col.features.add(hive)
        return col
    }

    fun describe(name: String): String? = FishingAlerts.describe(name) ?: FishingTrackers.describe(name) ?: FishingWorld.describe(name) ?: when (name) {
        "Current Bait HUD" -> "Bait in your last hotbar slot and how much is left"
        "Instant Underwater Fog Clear" -> "Skip the slow fade when your view clears underwater"
        "Shard Tracker" -> "HUD counting shards caught or found this session"
        "Totem of Corruption Overlay" -> "HUD with the nearest totem's time left and owner, plus its range ring"
        "Geyser Box" -> "Blazing Volcano: box the active geyser area for Sulphur trophy fish"
        "Sulphur Skitter Box" -> "Crimson Isle: box the 4-block range around the nearest sponge"
        "Sea Creature Kill Timer" -> "Chat line with how long each of your sea creatures took to die"
        "Sea Creature Cap Warning" -> "Title and sound when your alive sea creatures reach the cap"
        "Trophy Fish HUD" -> "Session trophy fish caught per fish and tier"
        "Fusion Keybinds" -> "One key = one click on Repeat, Confirm or Cancel in the fusion menu you opened"
        "Mute Phantoms" -> "Silence phantom sounds on Galatea and the Safari"
        "Cinderbat Highlight" -> "Box the Cinderbats on the Crimson Isle"
        "Invisibug Highlight" -> "Box Invisibugs on Moonglade Marsh from their particle trail"
        "Lasso Reel Alert" -> "Title and sound when your lassoed mob shows REEL"
        "Honeycomb Pot Timer" -> "Galatea: timer over each Pot of Honeycomb tree and a chat line when ready (this session)"
        "Golden Fish Timer" -> "Lava fishing HUD: spawn window, hits and despawn countdown for the Golden Fish"
        "Fishing" -> "Master switch for all fishing features (all off by default)"
        else -> null
    }
}
