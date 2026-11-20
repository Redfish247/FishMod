package fishmod.features.extras

import fishmod.features.FishModScreen.ButtonSetting
import fishmod.features.FishModScreen.ColorPickerSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.InputSetting
import fishmod.features.FishModScreen.SliderDoubleSetting
import fishmod.features.FishModScreen.SliderIntSetting
import fishmod.features.FishModScreen.SoundSearchSetting
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.extras.ExtrasSettings as S

object ExtrasMenu {
    fun column(): Column {
        val col = Column("Extras")

        val arrow = Feature("Arrow HUD", S::class.java, "arrowHud")
        arrow.sub.add(ToggleSetting("Only While Holding Bow", "Hide the HUD unless a bow is in hand", S::class.java, "arrowHudOnlyBow"))
        arrow.sub.add(ToggleSetting("Show Extra Arrows", "Loose arrows in your inventory besides the selected stack", S::class.java, "arrowHudExtra"))
        arrow.sub.add(ToggleSetting("Rarity Colour", "Colour the arrow name by its rarity", S::class.java, "arrowHudRarity"))
        arrow.sub.add(ColorPickerSetting("Arrow Name Color", "Used when Rarity Colour is off or the rarity is unknown", S::class.java, "arrowHudColor"))
        arrow.sub.add(ToggleSetting("Low Arrows Warning", "Title + sound when the quiver runs low", S::class.java, "arrowLowWarn"))
        arrow.sub.add(SliderIntSetting("Low Threshold", "", S::class.java, "arrowLowThreshold", 16, 2048, 16).gatedBy { S.arrowLowWarn })
        arrow.sub.add(ToggleSetting("Out Of Arrows Warning", "", S::class.java, "arrowOutWarn"))
        arrow.sub.add(SoundSearchSetting("Warning Sound", "Type to search every game sound", { S.arrowWarnSound }, { S.arrowWarnSound = it }))
        col.features.add(arrow)

        val quiver = Feature("Quiver Warning", S::class.java, "quiverWarning")
        quiver.sub.add(SliderIntSetting("Low Threshold", "Shared with Arrow HUD", S::class.java, "arrowLowThreshold", 16, 2048, 16))
        quiver.sub.add(ToggleSetting("Low Arrows Warning", "", S::class.java, "arrowLowWarn"))
        quiver.sub.add(ToggleSetting("Out Of Arrows Warning", "", S::class.java, "arrowOutWarn"))
        col.features.add(quiver)

        val cal = Feature("Calendar Real Times", S::class.java, "calendarRealTimes")
        cal.sub.add(ColorPickerSetting("Time Color", "", S::class.java, "calendarTimeColor"))
        col.features.add(cal)


        val limbo = Feature("Limbo Alert", S::class.java, "limboAlert")
        limbo.sub.add(InputSetting("Title", "& colour codes work", S::class.java, "limboTitle"))
        limbo.sub.add(SoundSearchSetting("Sound", "Type to search every game sound", { S.limboSound }, { S.limboSound = it }, { S.limboVolume }))
        limbo.sub.add(SliderIntSetting("Volume %", "", S::class.java, "limboVolume", 0, 500, 5))
        col.features.add(limbo)

        val restart = Feature("Server Restart Alert", S::class.java, "restartAlert")
        restart.sub.add(InputSetting("Title", "& colour codes work", S::class.java, "restartTitle"))
        restart.sub.add(SoundSearchSetting("Sound", "Type to search every game sound", { S.restartSound }, { S.restartSound = it }, { S.restartVolume }))
        restart.sub.add(SliderIntSetting("Volume %", "", S::class.java, "restartVolume", 0, 500, 5))
        col.features.add(restart)

        val party = Feature("Party Join Sound", S::class.java, "partyJoinSound")
        party.sub.add(SoundSearchSetting("Sound", "Type to search every game sound", { S.partyJoinSoundName }, { S.partyJoinSoundName = it }, { S.partyJoinVolume }))
        party.sub.add(SliderIntSetting("Volume %", "", S::class.java, "partyJoinVolume", 0, 500, 5))
        party.sub.add(ToggleSetting("Show Subtitle", "\"<name> joined the party\" on screen", S::class.java, "partyJoinTitle"))
        col.features.add(party)

        val wp = Feature("Chat Coord Waypoints", S::class.java, "patcherWaypoints")
        wp.sub.add(ColorPickerSetting("Color", "", S::class.java, "patcherWaypointColor"))
        wp.sub.add(SliderIntSetting("Expire After (s)", "", S::class.java, "patcherWaypointSeconds", 5, 600, 5))
        wp.sub.add(ToggleSetting("Beacon Beam", "", S::class.java, "patcherWaypointBeam"))
        wp.sub.add(ToggleSetting("Remove When Reached", "Clears it once you're within 3 blocks", S::class.java, "patcherWaypointRemoveNear"))
        wp.sub.add(SliderDoubleSetting("Text Scale", "", S::class.java, "patcherWaypointTextScale", 0.5, 3.0))
        wp.sub.add(ButtonSetting("Clear Waypoints", "", "Clear") { PatcherWaypoints.clear() })
        col.features.add(wp)

        val fs = Feature("Fairy Soul Waypoints", S::class.java, "fairySoulWaypoints")
        fs.sub.add(ColorPickerSetting("Color", "", S::class.java, "fairySoulColor"))
        fs.sub.add(ToggleSetting("Beacon Beam", "", S::class.java, "fairySoulBeam"))
        fs.sub.add(ToggleSetting("Distance Text", "", S::class.java, "fairySoulText"))
        fs.sub.add(ButtonSetting("Reset Island", "Show every soul on this island again", "Reset") { FairySouls.resetIsland() })
        col.features.add(fs)

        val lvl = Feature("Item Level Overlay", S::class.java, "overlayItemLevels")
        lvl.sub.add(ToggleSetting("Book Level", "Single-enchant books", S::class.java, "overlayBookLevel"))
        lvl.sub.add(ToggleSetting("Pet Level", "", S::class.java, "overlayPetLevel"))
        lvl.sub.add(ToggleSetting("Hide Max Pet Level", "Skip the overlay on max-level pets", S::class.java, "overlayPetHideMax"))
        lvl.sub.add(ToggleSetting("Minion Tier", "", S::class.java, "overlayMinionTier"))
        lvl.sub.add(ToggleSetting("Item Stars", "Dungeon star count on starred items", S::class.java, "overlayItemStars"))
        lvl.sub.add(ToggleSetting("Cake Year", "New Year Cakes", S::class.java, "overlayCakeYear"))
        lvl.sub.add(ColorPickerSetting("Text Color", "", S::class.java, "overlayTextColor"))
        col.features.add(lvl)

        col.features.add(Feature("Booster Cookie Warning", S::class.java, "cookieWarning"))
        col.features.add(Feature("Kick Cooldown Timer", S::class.java, "kickTimer"))

        val date = Feature("SkyBlock Date HUD", S::class.java, "sbDateHud")
        date.sub.add(ToggleSetting("Real Time", "Your local clock after the SkyBlock date", S::class.java, "sbDateHudRealTime"))
        col.features.add(date)

        col.features.add(Feature("Vanquisher Party Share", S::class.java, "vanquisherShare"))
        col.features.add(Feature("Fix Double Clicks", S::class.java, "fixDoubleClicks"))
        col.features.add(Feature("Middle Click Fix", S::class.java, "middleClickFix"))
        col.features.add(Feature("Command Tab Complete", S::class.java, "tabComplete"))
        col.features.add(Feature("Wiki Lookup", S::class.java, "wikiCommand"))
        val ufps = Feature("Unfocused FPS Saver", S::class.java, "unfocusedFps")
        ufps.sub.add(SliderIntSetting("FPS Cap", "Max FPS while the window is not focused", S::class.java, "unfocusedFpsLimit", 1, 60, 1))
        col.features.add(ufps)
        val todo = Feature("Custom Todos", S::class.java, "todoHud")
        todo.sub.add(ColorPickerSetting("Title Color", "", S::class.java, "todoHudTitleColor"))
        todo.sub.add(ColorPickerSetting("Text Color", "", S::class.java, "todoHudColor"))
        col.features.add(todo)
        val bp = Feature("Backpack Preview", S::class.java, "backpackPreview")
        bp.sub.add(ColorPickerSetting("Background", "", S::class.java, "backpackPreviewBg"))
        col.features.add(bp)
        val skill = Feature("Skill Progress HUD", S::class.java, "skillHud")
        skill.sub.add(SliderIntSetting("Hide After (s)", "Hide when no skill XP for this long", S::class.java, "skillHudHideSeconds", 10, 600, 10))
        skill.sub.add(ColorPickerSetting("Text Color", "", S::class.java, "skillHudColor"))
        col.features.add(skill)
        val pick = Feature("Item Pickup Log", S::class.java, "pickupLog")
        pick.sub.add(SliderIntSetting("Show For (s)", "", S::class.java, "pickupLogSeconds", 1, 30, 1))
        pick.sub.add(ColorPickerSetting("Gain Color", "", S::class.java, "pickupLogGainColor"))
        pick.sub.add(ColorPickerSetting("Loss Color", "", S::class.java, "pickupLogLossColor"))
        col.features.add(pick)
        col.features.add(Feature("Command Tooltip in Chat", S::class.java, "commandTooltip"))
        col.features.add(Feature("Compact Bestiary", S::class.java, "compactBestiary"))
        val ofa = Feature("Order Filled Alert", S::class.java, "orderFilledAlert")
        ofa.sub.add(SoundSearchSetting("Sound", "Type to search every game sound", { S.orderFilledSound }, { S.orderFilledSound = it }))
        col.features.add(ofa)
        col.features.add(Feature("SkyBlock XP in Chat", S::class.java, "sbXpChat"))
        col.features.add(Feature("Hoppity Hunt Summary", S::class.java, "hoppitySummary"))
        val tt = Feature("Trick or Treat Chest Alert", S::class.java, "trickTreatAlert")
        tt.sub.add(InputSetting("Title", "& colour codes work", S::class.java, "trickTreatTitle"))
        col.features.add(tt)

        return col
    }

    fun describe(name: String): String? = when (name) {
        "Arrow HUD" -> "Selected arrow + quiver count, low/out warnings"
        "Quiver Warning" -> "Title + sound when the quiver runs low, without the Arrow HUD"
        "Calendar Real Times" -> "Real date + time next to every countdown in the /calendar menu"
        "Status Bars" -> "Defense + health/mana bars above the hotbar (replaces vanilla hearts); move in the HUD editor"
        "Limbo Alert" -> "Title and sound when you get sent to Limbo"
        "Server Restart Alert" -> "Title and sound when Hypixel says your server will restart soon"
        "Party Join Sound" -> "Plays a sound when someone joins your party"
        "Chat Coord Waypoints" -> "Waypoints from Patcher-style x: y: z: coords in chat"
        "Booster Cookie Warning" -> "Chat warning on joining SkyBlock when your Cookie Buff is not active"
        "Command Tab Complete" -> "Tab suggests /warp names, lobby players for /p kick|transfer|promote, /msg etc., and /viewrecipe item ids"
        "Unfocused FPS Saver" -> "Caps FPS while Minecraft is not the focused window"
        "Custom Todos" -> "Todo list HUD; /fmtodo add <text>, /fmtodo done <n>, /fmtodo clear"
        "Backpack Preview" -> "Storage menu: hover an ender chest page or backpack to see its contents (open each once; only when Storage Overlay is off)"
        "Skill Progress HUD" -> "Last skill XP popup: progress to next level, XP/h and ETA; move in the HUD editor"
        "Item Pickup Log" -> "HUD of items gained or lost in your inventory over the last few seconds"
        "Command Tooltip in Chat" -> "Hovering clickable chat shows the command it runs or suggests"
        "Compact Bestiary" -> "Folds the multi-line BESTIARY level-up message into one line"
        "Order Filled Alert" -> "Title + sound when a Bazaar buy order or sell offer is filled"
        "SkyBlock XP in Chat" -> "Copies the action bar SkyBlock XP popup (amount, reason, n/100) into chat"
        "Wiki Lookup" -> "/wiki [item name] posts a clickable Hypixel wiki link; no name = held item"
        "Kick Cooldown Timer" -> "Title one minute after a kick/limbo, when you can rejoin SkyBlock"
        "SkyBlock Date HUD" -> "Current SkyBlock date and hour, optionally with real time; move in the HUD editor"
        "Vanquisher Party Share" -> "Sends your coords to party chat when a Vanquisher spawns nearby"
        "Fix Double Clicks" -> "SkyBlock menus: a double click counts as a normal click, not collect-all"
        "Middle Click Fix" -> "SkyBlock menus: middle click on a menu item acts as a left click"
        "Item Level Overlay" -> "Book level, pet level, minion tier and cake year in the slot corner"
        else -> null
    }
}
