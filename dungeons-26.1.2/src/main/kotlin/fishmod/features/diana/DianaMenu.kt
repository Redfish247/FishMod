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

object DianaMenu {

    private val TRACKER_MODES = arrayOf("Off", "Event", "Session", "Total")

    fun column(): Column {
        val col = Column("Diana")

        val guess = Feature("Burrow Guessing", S::class.java, "dianaGuessing")
        guess.sub.add(ToggleSetting("Spade Guess", "Guess the burrow from the spade's lava trail", S::class.java, "dianaSpadeGuess"))
        guess.sub.add(ToggleSetting("Arrow Guess", "Guess from the dust arrow shown after digging a burrow", S::class.java, "dianaArrowGuess"))
        guess.sub.add(ToggleSetting("Close Burrow Detection", "Mark Start/Mob/Treasure burrows from their particles", S::class.java, "dianaBurrowDetection"))
        guess.sub.add(ToggleSetting("Show Sub Guesses", "Show the other possible arrow guess spots", S::class.java, "dianaSubGuesses"))
        guess.sub.add(ToggleSetting("\"Possible\" Text", "Label sub guesses with text", S::class.java, "dianaSubGuessText").gatedBy { S.dianaSubGuesses })
        guess.sub.add(ToggleSetting("Guess & Burrow Line", "Line to the closest guess or burrow", S::class.java, "dianaGuessLine"))
        guess.sub.add(ToggleSetting("Optimal Order Lines", "Lines through the next burrows, closest first", S::class.java, "dianaOrderLines"))
        guess.sub.add(ToggleSetting("Beacon Beam", "Beam above guesses and burrows", S::class.java, "dianaBeaconBeam"))
        guess.sub.add(SliderIntSetting("Beam Hide Distance", "Hide the beam when this close", S::class.java, "dianaBeaconDistance", 0, 50).gatedBy { S.dianaBeaconBeam })
        guess.sub.add(ToggleSetting("Chain End Title", "\"Use Spade!\" when a chain ends with nothing nearby", S::class.java, "dianaChainEndTitle"))
        guess.sub.add(ToggleSetting("Burrow Dug Sound", "Play a sound each time you dig a burrow", S::class.java, "dianaBurrowDugSound"))
        guess.sub.add(SoundSearchSetting("Dug Sound", "Type to search every game sound",
            { S.dianaBurrowDugSoundName }, { v -> S.dianaBurrowDugSoundName = v }, { S.dianaBurrowDugVolume }).gatedBy { S.dianaBurrowDugSound })
        guess.sub.add(SliderIntSetting("Dug Volume %", "", S::class.java, "dianaBurrowDugVolume", 0, 500, 5).gatedBy { S.dianaBurrowDugSound })
        guess.sub.add(ToggleSetting("Mute Hypixel Dig Ding", "Mute the sound Hypixel plays when you dig a burrow", S::class.java, "dianaMuteHypixelDug"))
        guess.sub.add(ToggleSetting("Mute Burrow Sounds", "Mute the digging noise and burrow pings", S::class.java, "dianaMuteBurrowSounds"))
        guess.sub.add(ToggleSetting("Mute Spade Sounds", "Mute the note-block harp echo when you use your spade", S::class.java, "dianaMuteSpadeSounds"))
        guess.sub.add(ButtonSetting("Clear Waypoints", "Also /fm diana clear", "Clear") { DianaWaypoints.clearAll() })
        col.features.add(guess)

        val warp = Feature("Diana Warp", S::class.java, "dianaWarp")
        warp.sub.add(KeybindSetting("Guess Warp Key", "Warp to the hub warp closest to your guess", { DianaWarp.guessKey }))
        warp.sub.add(KeybindSetting("Rare Mob Warp Key", "Warp to the warp closest to the newest rare mob", { DianaWarp.rareMobKey }))
        warp.sub.add(SubcategoryHeader("Unlocked Warps"))
        warp.sub.add(ToggleSetting("Castle", "", S::class.java, "dianaWarpCastle"))
        warp.sub.add(ToggleSetting("Wizard", "", S::class.java, "dianaWarpWizard"))
        warp.sub.add(ToggleSetting("Crypt", "", S::class.java, "dianaWarpCrypt"))
        warp.sub.add(ToggleSetting("Stonks", "", S::class.java, "dianaWarpStonks"))
        warp.sub.add(ToggleSetting("Dark Auction", "", S::class.java, "dianaWarpDa"))
        warp.sub.add(ToggleSetting("Taylor", "", S::class.java, "dianaWarpTaylor"))
        warp.sub.add(ToggleSetting("Museum", "", S::class.java, "dianaWarpMuseum"))
        warp.sub.add(SubcategoryHeader("Rules"))
        warp.sub.add(ToggleSetting("Don't Warp Near Burrow", "Skip warping when within 60 blocks", S::class.java, "dianaDontWarpNearBurrow"))
        warp.sub.add(SliderIntSetting("Warp Block Difference", "Warp must save at least this many blocks", S::class.java, "dianaWarpBlockDiff", 0, 100))
        warp.sub.add(InputIntSetting("Bad Warp Distance", "Prefer Castle over Crypt within this many blocks (0 = off)", S::class.java, "dianaBadWarpDistance"))
        warp.sub.add(ToggleSetting("Warp Title", "Title when a warp is worth taking", S::class.java, "dianaWarpTitle"))
        warp.sub.add(ToggleSetting("Warp Title As Subtitle", "Smaller subtitle instead", S::class.java, "dianaWarpTitleSubtitle").gatedBy { S.dianaWarpTitle })
        col.features.add(warp)

        val rare = Feature("Rare Mobs", S::class.java, "dianaRareMobs")
        rare.sub.add(ToggleSetting("Scan World", "Waypoint rare mobs you can see", S::class.java, "dianaScanRareMobs"))
        rare.sub.add(ToggleSetting("Rare Mob Line", "Line to the newest rare mob", S::class.java, "dianaRareMobLine"))
        rare.sub.add(ToggleSetting("Highlight Rare Mobs", "Glow outline in the mob's colour", S::class.java, "dianaHighlightRareMobs"))
        rare.sub.add(ToggleSetting("Cocoon Title", "Title when you cocoon a rare mob", S::class.java, "dianaCocoonTitle"))
        rare.sub.add(ToggleSetting("Cocoon Party Message", "Tell party what you cocooned", S::class.java, "dianaCocoonParty"))
        rare.sub.add(SoundSearchSetting("Alert Sound", "Type to search every game sound",
            { S.dianaRareMobSound }, { v -> S.dianaRareMobSound = v }, { S.dianaRareMobVolume }))
        rare.sub.add(SliderIntSetting("Alert Volume %", "", S::class.java, "dianaRareMobVolume", 0, 500, 5))
        rare.sub.add(SubcategoryHeader("Title Timing (ticks)"))
        rare.sub.add(SliderIntSetting("Fade In", "", S::class.java, "dianaTitleFadeIn", 0, 40))
        rare.sub.add(SliderIntSetting("Stay", "", S::class.java, "dianaTitleStay", 0, 200))
        rare.sub.add(SliderIntSetting("Fade Out", "", S::class.java, "dianaTitleFadeOut", 0, 40))
        rare.sub.add(ButtonSetting("Party Text On Spawn", "Edit in a popup", "Edit") { DianaMessagesScreen.open() })
        col.features.add(rare)

        val share = Feature("Share Rare Mobs", S::class.java, "dianaShareRareMob")
        share.sub.add(ToggleSetting("Inquisitor", "Send coords to party when you dig one", S::class.java, "dianaShareInq"))
        share.sub.add(ToggleSetting("King Minos", "", S::class.java, "dianaShareKing"))
        share.sub.add(ToggleSetting("Manticore", "", S::class.java, "dianaShareManti"))
        share.sub.add(ToggleSetting("Sphinx", "", S::class.java, "dianaShareSphinx"))
        col.features.add(share)

        val receive = Feature("Receive Rare Mobs", S::class.java, "dianaReceiveRareMob")
        receive.sub.add(ToggleSetting("Inquisitor", "Waypoint coords others send (FishMod/SBO/SkyHanni)", S::class.java, "dianaReceiveInq"))
        receive.sub.add(ToggleSetting("King Minos", "", S::class.java, "dianaReceiveKing"))
        receive.sub.add(ToggleSetting("Manticore", "", S::class.java, "dianaReceiveManti"))
        receive.sub.add(ToggleSetting("Sphinx", "", S::class.java, "dianaReceiveSphinx"))
        col.features.add(receive)

        col.features.add(Feature("Mythos Mob HP", S::class.java, "dianaMythosHp"))
        col.features.add(Feature("No Shuriken Overlay", S::class.java, "dianaNoShuriken"))

        val tracker = Feature("Diana Tracker", S::class.java, "dianaTracker")
        tracker.sub.add(DropdownSetting("Loot Tracker", "", TRACKER_MODES, { S.dianaLootTracker }, { v -> S.dianaLootTracker = v }))
        tracker.sub.add(DropdownSetting("Mob Tracker", "", TRACKER_MODES, { S.dianaMobTracker }, { v -> S.dianaMobTracker = v }))
        tracker.sub.add(ToggleSetting("Stats Tracker", "Mobs since Inquisitor, Inquisitors since Chimera, ...", S::class.java, "dianaStatsTracker"))
        tracker.sub.add(ToggleSetting("Magic Find Tracker", "Highest MF you've dropped each rare with", S::class.java, "dianaMfTracker"))
        tracker.sub.add(ToggleSetting("Hide Unobtained", "Hide lines still at 0", S::class.java, "dianaHideUnobtained"))
        tracker.sub.add(SliderIntSetting("AFK Timeout (s)", "Pause playtime after this long idle and take the idle time back", S::class.java, "dianaAfkTimeout", 15, 900, 5))
        tracker.sub.add(DropdownSetting("Bazaar Price", "", arrayOf("Sell Offer", "Insta Sell"), { S.dianaPriceMode }, { v -> S.dianaPriceMode = v }))
        tracker.sub.add(ToggleSetting("Stats Message", "\"Took 120 Mobs to get an Inquis!\"", S::class.java, "dianaStatsMessage"))
        tracker.sub.add(ButtonSetting("Past Events", "Also /fm dianaloot", "Open") { DianaTracker.openPastEvents() })
        tracker.sub.add(ButtonSetting("Reset Session", "Also /fm diana resetsession", "Reset") { DianaTracker.resetSession() })
        tracker.sub.add(ButtonSetting("Import From SBO", "Adds SBO's Total and past years onto FishMod's (also /fm diana importsbo)", "Import") { DianaTracker.importSbo() })
        col.features.add(tracker)

        val ann = Feature("Diana Announcers", S::class.java, "dianaAnnouncers")
        ann.sub.add(ToggleSetting("Rare Drop Chat", "Chat line with MF, count and price", S::class.java, "dianaRareDropChat"))
        ann.sub.add(ToggleSetting("Hilt Drop Message", "Hypixel sends none for Hilt of Revelations", S::class.java, "dianaHiltMessage"))
        ann.sub.add(ToggleSetting("Loot Screen Title", "Title for Chimera/Stick/Relic...", S::class.java, "dianaLootScreen"))
        ann.sub.add(ToggleSetting("Loot Party Message", "Tell party about rare drops", S::class.java, "dianaLootParty"))
        ann.sub.add(ButtonSetting("Drop Messages", "Edit rare drop messages in a popup", "Edit") { DianaMessagesScreen.open() })
        col.features.add(ann)

        col.features.add(Feature("Diana Party Commands", S::class.java, "dianaPartyCommands"))
        run {
            val f = Feature("Diana Message Hider", S::class.java, "dianaMessageHider")
            f.sub.add(ToggleSetting("Follow the Arrows", "", S::class.java, "dianaHideArrows"))
            f.sub.add(ToggleSetting("Warping...", "", S::class.java, "dianaHideWarping"))
            f.sub.add(ToggleSetting("Blocks in the Way", "", S::class.java, "dianaHideBlocked"))
            f.sub.add(ToggleSetting("Ability Cooldown", "", S::class.java, "dianaHideCooldown"))
            f.sub.add(ToggleSetting("Griffin Burrow Lines", "", S::class.java, "dianaHideBurrow"))
            f.sub.add(ToggleSetting("Dug Mob Lines", "", S::class.java, "dianaHideDugMob"))
            col.features.add(f)
        }
        col.features.add(Feature("Sphinx Solver", S::class.java, "dianaSphinxSolver"))
        col.features.add(Feature("Crown of Avarice Counter", S::class.java, "dianaCrownCounter"))
        col.features.add(Feature("Crown of Avarice Milestones", S::class.java, "dianaCrownMilestones"))
        run {
            val f = Feature("Crown of Avarice Tracker", S::class.java, "dianaCrownHud")
            f.sub.add(ToggleSetting("Full Number", "Show the exact coin count instead of 7.92B", S::class.java, "dianaCrownHudFullNumber"))
            col.features.add(f)
        }

        val style = Feature("Diana Waypoint Style", null, null)
        style.sub.add(SubcategoryHeader("Colors"))
        style.sub.add(ColorPickerSetting("Closest Guess", "", S::class.java, "dianaColorClosestGuess"))
        style.sub.add(ColorPickerSetting("Other Guess", "", S::class.java, "dianaColorOtherGuess"))
        style.sub.add(ColorPickerSetting("Sub Guess", "", S::class.java, "dianaColorSubGuess"))
        style.sub.add(ColorPickerSetting("Order Line", "", S::class.java, "dianaColorOrderLine"))
        style.sub.add(ColorPickerSetting("Start Burrow", "", S::class.java, "dianaColorStart"))
        style.sub.add(ColorPickerSetting("Mob Burrow", "", S::class.java, "dianaColorMob"))
        style.sub.add(ColorPickerSetting("Treasure Burrow", "", S::class.java, "dianaColorTreasure"))
        style.sub.add(ColorPickerSetting("Rare Mob", "", S::class.java, "dianaColorRareMob"))
        style.sub.add(ColorPickerSetting("Other Waypoint", "Plain coords from chat", S::class.java, "dianaColorOther"))
        style.sub.add(SubcategoryHeader("Glow Colors"))
        style.sub.add(ColorPickerSetting("King Minos", "", S::class.java, "dianaGlowKing"))
        style.sub.add(ColorPickerSetting("Minos Inquisitor", "", S::class.java, "dianaGlowInq"))
        style.sub.add(ColorPickerSetting("Manticore", "", S::class.java, "dianaGlowManti"))
        style.sub.add(ColorPickerSetting("Sphinx", "", S::class.java, "dianaGlowSphinx"))
        style.sub.add(SubcategoryHeader("Look"))
        style.sub.add(ToggleSetting("Dynamic Opacity", "Fade waypoints as you get close", S::class.java, "dianaDynamicOpacity"))
        style.sub.add(SliderIntSetting("Opacity %", "", S::class.java, "dianaOpacity", 5, 100).gatedBy { !S.dianaDynamicOpacity })
        style.sub.add(SliderIntSetting("Text Opacity %", "", S::class.java, "dianaTextOpacity", 0, 100))
        style.sub.add(ToggleSetting("Text Shadow", "", S::class.java, "dianaTextShadow"))
        style.sub.add(SliderDoubleSetting("Text Scale", "", S::class.java, "dianaTextScale", 0.5, 3.0))
        style.sub.add(SliderIntSetting("Distance Cutoff", "Show distance beyond this, times dug within (0 = both)", S::class.java, "dianaDistanceCutoff", 0, 200))
        style.sub.add(ToggleSetting("Show Times Dug", "", S::class.java, "dianaShowTimesDug"))
        style.sub.add(SliderIntSetting("Line Width", "", S::class.java, "dianaLineWidth", 1, 10))
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
        "Crown of Avarice Counter" -> "Keep counting coins past 1B (/fm crown set <amount>)"
        "Crown of Avarice Tracker" -> "HUD with crown coins (past 1B), coins/hour and time to max"
        "Crown of Avarice Milestones" -> "Chat message every 100M coins your crown collects"
        "Diana Waypoint Style" -> "Colors, opacity and text for Diana waypoints"
        else -> null
    }
}
