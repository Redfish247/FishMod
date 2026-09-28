package fishmod.features.diana

import fishmod.features.FishModScreen.ButtonSetting
import fishmod.features.FishModScreen.ColorPickerSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.DropdownSetting
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.InputIntSetting
import fishmod.features.FishModScreen.InputSetting
import fishmod.features.FishModScreen.KeybindSetting
import fishmod.features.FishModScreen.SliderDoubleSetting
import fishmod.features.FishModScreen.SliderIntSetting
import fishmod.features.FishModScreen.SoundSearchSetting
import fishmod.features.FishModScreen.SubcategoryHeader
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.diana.DianaSettings as S

// The Diana tab in /fm
object DianaMenu {

    private val TRACKER_MODES = arrayOf("Off", "Event", "Session", "Total")

    fun column(): Column {
        val col = Column("Diana")

        val guess = Feature("Burrow Guessing", null, null)
        guess.sub.add(ToggleSetting("Spade Guess", "Guess the burrow from the spade's lava trail", S::dianaSpadeGuess))
        guess.sub.add(ToggleSetting("Arrow Guess", "Guess from the dust arrow shown after digging a burrow", S::dianaArrowGuess))
        guess.sub.add(ToggleSetting("Close Burrow Detection", "Mark Start/Mob/Treasure burrows from their particles", S::dianaBurrowDetection))
        guess.sub.add(ToggleSetting("Show Sub Guesses", "Show the other possible arrow guess spots", S::dianaSubGuesses))
        guess.sub.add(ToggleSetting("\"Possible\" Text", "Label sub guesses with text", S::dianaSubGuessText).gatedBy { S.dianaSubGuesses })
        guess.sub.add(ToggleSetting("Guess & Burrow Line", "Line to the closest guess or burrow", S::dianaGuessLine))
        guess.sub.add(ToggleSetting("Optimal Order Lines", "Lines through the next burrows, closest first", S::dianaOrderLines))
        guess.sub.add(ToggleSetting("Beacon Beam", "Beam above guesses and burrows", S::dianaBeaconBeam))
        guess.sub.add(SliderIntSetting("Beam Hide Distance", "Hide the beam when this close", S::dianaBeaconDistance, 0, 50).gatedBy { S.dianaBeaconBeam })
        guess.sub.add(ToggleSetting("Chain End Title", "\"Use Spade!\" when a chain ends with nothing nearby", S::dianaChainEndTitle))
        guess.sub.add(ButtonSetting("Clear Waypoints", "Also /fm diana clear", "Clear") { DianaWaypoints.clearAll() })
        col.features.add(guess)

        val warp = Feature("Diana Warp", null, null)
        warp.sub.add(KeybindSetting("Guess Warp Key", "Warp to the hub warp closest to your guess", { DianaWarp.guessKey }))
        warp.sub.add(KeybindSetting("Rare Mob Warp Key", "Warp to the warp closest to the newest rare mob", { DianaWarp.rareMobKey }))
        warp.sub.add(SubcategoryHeader("Unlocked Warps"))
        warp.sub.add(ToggleSetting("Castle", "", S::dianaWarpCastle))
        warp.sub.add(ToggleSetting("Wizard", "", S::dianaWarpWizard))
        warp.sub.add(ToggleSetting("Crypt", "", S::dianaWarpCrypt))
        warp.sub.add(ToggleSetting("Stonks", "", S::dianaWarpStonks))
        warp.sub.add(ToggleSetting("Dark Auction", "", S::dianaWarpDa))
        warp.sub.add(ToggleSetting("Taylor", "", S::dianaWarpTaylor))
        warp.sub.add(ToggleSetting("Museum", "", S::dianaWarpMuseum))
        warp.sub.add(SubcategoryHeader("Rules"))
        warp.sub.add(ToggleSetting("Don't Warp Near Burrow", "Skip warping when within 60 blocks", S::dianaDontWarpNearBurrow))
        warp.sub.add(SliderIntSetting("Warp Block Difference", "Warp must save at least this many blocks", S::dianaWarpBlockDiff, 0, 100))
        warp.sub.add(InputIntSetting("Bad Warp Distance", "Prefer Castle over Crypt within this many blocks (0 = off)", S::dianaBadWarpDistance))
        warp.sub.add(ToggleSetting("Warp Title", "Title when a warp is worth taking", S::dianaWarpTitle))
        warp.sub.add(ToggleSetting("Warp Title As Subtitle", "Smaller subtitle instead", S::dianaWarpTitleSubtitle).gatedBy { S.dianaWarpTitle })
        col.features.add(warp)

        val rare = Feature("Rare Mobs", null, null)
        rare.sub.add(ToggleSetting("Scan World", "Waypoint rare mobs you can see", S::dianaScanRareMobs))
        rare.sub.add(ToggleSetting("Share To Party", "Send coords of rare mobs you dig", S::dianaShareRareMob))
        rare.sub.add(ToggleSetting("Receive From Party", "Waypoint coords others send (FishMod/SBO/SkyHanni)", S::dianaReceiveRareMob))
        rare.sub.add(ToggleSetting("Rare Mob Line", "Line to the newest rare mob", S::dianaRareMobLine))
        rare.sub.add(ToggleSetting("Highlight Rare Mobs", "Glow outline in the mob's colour", S::dianaHighlightRareMobs))
        rare.sub.add(ToggleSetting("Cocoon Title", "Title when you cocoon a rare mob", S::dianaCocoonTitle))
        rare.sub.add(ToggleSetting("Cocoon Party Message", "Tell party what you cocooned", S::dianaCocoonParty))
        rare.sub.add(SoundSearchSetting("Alert Sound", "Type to search every game sound",
            { S.dianaRareMobSound }, { v -> S.dianaRareMobSound = v }, { S.dianaRareMobVolume }))
        rare.sub.add(SliderIntSetting("Alert Volume %", "", S::dianaRareMobVolume, 0, 200, 5))
        rare.sub.add(SubcategoryHeader("Title Timing (ticks)"))
        rare.sub.add(SliderIntSetting("Fade In", "", S::dianaTitleFadeIn, 0, 40))
        rare.sub.add(SliderIntSetting("Stay", "", S::dianaTitleStay, 0, 200))
        rare.sub.add(SliderIntSetting("Fade Out", "", S::dianaTitleFadeOut, 0, 40))
        rare.sub.add(SubcategoryHeader("Party Text On Spawn ({since}, {chance})"))
        rare.sub.add(InputSetting("Inquisitor", "Empty = off", S::dianaInqSpawnText))
        rare.sub.add(InputSetting("Manticore", "Empty = off", S::dianaMantiSpawnText))
        rare.sub.add(InputSetting("Sphinx", "Empty = off", S::dianaSphinxSpawnText))
        rare.sub.add(InputSetting("King Minos", "Empty = off", S::dianaKingSpawnText))
        col.features.add(rare)

        col.features.add(Feature("Mythos Mob HP", S::dianaMythosHp))
        col.features.add(Feature("No Shuriken Overlay", S::dianaNoShuriken))

        val tracker = Feature("Diana Tracker", null, null)
        tracker.sub.add(DropdownSetting("Loot Tracker", "", TRACKER_MODES, { S.dianaLootTracker }, { v -> S.dianaLootTracker = v }))
        tracker.sub.add(DropdownSetting("Mob Tracker", "", TRACKER_MODES, { S.dianaMobTracker }, { v -> S.dianaMobTracker = v }))
        tracker.sub.add(ToggleSetting("Stats Tracker", "Mobs since Inquisitor, Inquisitors since Chimera, ...", S::dianaStatsTracker))
        tracker.sub.add(ToggleSetting("Magic Find Tracker", "Highest MF you've dropped each rare with", S::dianaMfTracker))
        tracker.sub.add(ToggleSetting("Hide Unobtained", "Hide lines still at 0", S::dianaHideUnobtained))
        tracker.sub.add(SliderIntSetting("AFK Timeout (s)", "Pause the timer after this long idle", S::dianaAfkTimeout, 15, 900, 5))
        tracker.sub.add(DropdownSetting("Bazaar Price", "", arrayOf("Sell Offer", "Insta Sell"), { S.dianaPriceMode }, { v -> S.dianaPriceMode = v }))
        tracker.sub.add(ToggleSetting("Stats Message", "\"Took 120 Mobs to get an Inquis!\"", S::dianaStatsMessage))
        tracker.sub.add(ButtonSetting("Past Events", "Also /fm diana pastevents", "Open") { DianaTracker.openPastEvents() })
        tracker.sub.add(ButtonSetting("Reset Session", "Also /fm diana resetsession", "Reset") { DianaTracker.resetSession() })
        col.features.add(tracker)

        val ann = Feature("Diana Announcers", null, null)
        ann.sub.add(ToggleSetting("Rare Drop Chat", "Chat line with MF, count and price", S::dianaRareDropChat))
        ann.sub.add(ToggleSetting("Hilt Drop Message", "Hypixel sends none for Hilt of Revelations", S::dianaHiltMessage))
        ann.sub.add(ToggleSetting("Loot Screen Title", "Title for Chimera/Stick/Relic...", S::dianaLootScreen))
        ann.sub.add(ToggleSetting("Loot Party Message", "Tell party about rare drops", S::dianaLootParty))
        ann.sub.add(SubcategoryHeader("Custom Messages ({mf} {amount} {percentage} {price} {since})"))
        ann.sub.add(InputSetting("Chimera", "Empty = default", S::dianaMsgChimera))
        ann.sub.add(InputSetting("Manti-core", "Empty = default", S::dianaMsgCore))
        ann.sub.add(InputSetting("Fateful Stinger", "Empty = default", S::dianaMsgStinger))
        ann.sub.add(InputSetting("Brain Food", "Empty = default", S::dianaMsgFood))
        ann.sub.add(InputSetting("Shimmering Wool", "Empty = default", S::dianaMsgWool))
        col.features.add(ann)

        col.features.add(Feature("Diana Party Commands", S::dianaPartyCommands))
        col.features.add(Feature("Diana Message Hider", S::dianaMessageHider))
        col.features.add(Feature("Sphinx Solver", S::dianaSphinxSolver))

        val style = Feature("Diana Waypoint Style", null, null)
        style.sub.add(SubcategoryHeader("Colors"))
        style.sub.add(ColorPickerSetting("Closest Guess", "", S::dianaColorClosestGuess))
        style.sub.add(ColorPickerSetting("Other Guess", "", S::dianaColorOtherGuess))
        style.sub.add(ColorPickerSetting("Sub Guess", "", S::dianaColorSubGuess))
        style.sub.add(ColorPickerSetting("Order Line", "", S::dianaColorOrderLine))
        style.sub.add(ColorPickerSetting("Start Burrow", "", S::dianaColorStart))
        style.sub.add(ColorPickerSetting("Mob Burrow", "", S::dianaColorMob))
        style.sub.add(ColorPickerSetting("Treasure Burrow", "", S::dianaColorTreasure))
        style.sub.add(ColorPickerSetting("Rare Mob", "", S::dianaColorRareMob))
        style.sub.add(ColorPickerSetting("Other Waypoint", "Plain coords from chat", S::dianaColorOther))
        style.sub.add(SubcategoryHeader("Glow Colors"))
        style.sub.add(ColorPickerSetting("King Minos", "", S::dianaGlowKing))
        style.sub.add(ColorPickerSetting("Minos Inquisitor", "", S::dianaGlowInq))
        style.sub.add(ColorPickerSetting("Manticore", "", S::dianaGlowManti))
        style.sub.add(ColorPickerSetting("Sphinx", "", S::dianaGlowSphinx))
        style.sub.add(SubcategoryHeader("Look"))
        style.sub.add(ToggleSetting("Dynamic Opacity", "Fade waypoints as you get close", S::dianaDynamicOpacity))
        style.sub.add(SliderIntSetting("Opacity %", "", S::dianaOpacity, 5, 100).gatedBy { !S.dianaDynamicOpacity })
        style.sub.add(SliderIntSetting("Text Opacity %", "", S::dianaTextOpacity, 0, 100))
        style.sub.add(ToggleSetting("Text Shadow", "", S::dianaTextShadow))
        style.sub.add(SliderDoubleSetting("Text Scale", "", S::dianaTextScale, 0.5, 3.0))
        style.sub.add(SliderIntSetting("Distance Cutoff", "Show distance beyond this, times dug within (0 = both)", S::dianaDistanceCutoff, 0, 200))
        style.sub.add(ToggleSetting("Show Times Dug", "", S::dianaShowTimesDug))
        style.sub.add(SliderIntSetting("Line Width", "", S::dianaLineWidth, 1, 10))
        col.features.add(style)

        return col
    }

    fun describe(name: String): String? = when (name) {
        "Burrow Guessing" -> "Spade + arrow guesses, burrow detection, lines"
        "Diana Warp" -> "Keybind warp to the best hub warp"
        "Rare Mobs" -> "Inquisitor/King/Manticore/Sphinx waypoints, sharing, alerts"
        "Mythos Mob HP" -> "HUD with nearby mythological mob health"
        "No Shuriken Overlay" -> "Warn when a rare mob has no Extreme Focus shuriken"
        "Diana Tracker" -> "Loot, mobs, stats and magic find overlays"
        "Diana Announcers" -> "Rare drop chat, titles and party messages"
        "Diana Party Commands" -> "!chim !inq !relic !stick !since !burrow !mob"
        "Diana Message Hider" -> "Hide spammy Diana chat"
        "Sphinx Solver" -> "Click anywhere in chat to answer the Sphinx"
        "Diana Waypoint Style" -> "Colors, opacity and text for Diana waypoints"
        else -> null
    }
}
