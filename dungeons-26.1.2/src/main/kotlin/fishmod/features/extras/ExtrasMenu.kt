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
        lvl.sub.add(ToggleSetting("Cake Year", "New Year Cakes", S::class.java, "overlayCakeYear"))
        lvl.sub.add(ColorPickerSetting("Text Color", "", S::class.java, "overlayTextColor"))
        col.features.add(lvl)

        return col
    }

    fun describe(name: String): String? = when (name) {
        "Arrow HUD" -> "Selected arrow + quiver count, low/out warnings"
        "Quiver Warning" -> "Title + sound when the quiver runs low, without the Arrow HUD"
        "Calendar Real Times" -> "Real date + time next to every countdown in the /calendar menu"
        "Status Bars" -> "Defense + health/mana bars above the hotbar (replaces vanilla hearts); move in the HUD editor"
        "Limbo Alert" -> "Title and sound when you get sent to Limbo"
        "Party Join Sound" -> "Plays a sound when someone joins your party"
        "Chat Coord Waypoints" -> "Waypoints from Patcher-style x: y: z: coords in chat"
        "Item Level Overlay" -> "Book level, pet level, minion tier and cake year in the slot corner"
        else -> null
    }
}
