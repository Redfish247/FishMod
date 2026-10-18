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

// The Extras tab in /fm
object ExtrasMenu {
    fun column(): Column {
        val col = Column("Extras")

        val arrow = Feature("Arrow HUD", S::arrowHud)
        arrow.sub.add(ToggleSetting("Only While Holding Bow", "Hide the HUD unless a bow is in hand", S::arrowHudOnlyBow))
        arrow.sub.add(ToggleSetting("Show Extra Arrows", "Loose arrows in your inventory besides the selected stack", S::arrowHudExtra))
        arrow.sub.add(ToggleSetting("Rarity Colour", "Colour the arrow name by its rarity", S::arrowHudRarity))
        arrow.sub.add(ColorPickerSetting("Arrow Name Color", "Used when Rarity Colour is off or the rarity is unknown", S::arrowHudColor))
        arrow.sub.add(ToggleSetting("Low Arrows Warning", "Title + sound when the quiver runs low", S::arrowLowWarn))
        arrow.sub.add(SliderIntSetting("Low Threshold", "", S::arrowLowThreshold, 16, 2048, 16).gatedBy { S.arrowLowWarn })
        arrow.sub.add(ToggleSetting("Out Of Arrows Warning", "", S::arrowOutWarn))
        arrow.sub.add(SoundSearchSetting("Warning Sound", "Type to search every game sound", { S.arrowWarnSound }, { S.arrowWarnSound = it }))
        col.features.add(arrow)

        val limbo = Feature("Limbo Alert", S::limboAlert)
        limbo.sub.add(InputSetting("Title", "& colour codes work", S::limboTitle))
        limbo.sub.add(SoundSearchSetting("Sound", "Type to search every game sound", { S.limboSound }, { S.limboSound = it }, { S.limboVolume }))
        limbo.sub.add(SliderIntSetting("Volume %", "", S::limboVolume, 0, 500, 5))
        col.features.add(limbo)

        val party = Feature("Party Join Sound", S::partyJoinSound)
        party.sub.add(SoundSearchSetting("Sound", "Type to search every game sound", { S.partyJoinSoundName }, { S.partyJoinSoundName = it }, { S.partyJoinVolume }))
        party.sub.add(SliderIntSetting("Volume %", "", S::partyJoinVolume, 0, 500, 5))
        party.sub.add(ToggleSetting("Show Subtitle", "\"<name> joined the party\" on screen", S::partyJoinTitle))
        col.features.add(party)

        val wp = Feature("Chat Coord Waypoints", S::patcherWaypoints)
        wp.sub.add(ColorPickerSetting("Color", "", S::patcherWaypointColor))
        wp.sub.add(SliderIntSetting("Expire After (s)", "", S::patcherWaypointSeconds, 5, 600, 5))
        wp.sub.add(ToggleSetting("Beacon Beam", "", S::patcherWaypointBeam))
        wp.sub.add(ToggleSetting("Remove When Reached", "Clears it once you're within 3 blocks", S::patcherWaypointRemoveNear))
        wp.sub.add(SliderDoubleSetting("Text Scale", "", S::patcherWaypointTextScale, 0.5, 3.0))
        wp.sub.add(ButtonSetting("Clear Waypoints", "", "Clear") { PatcherWaypoints.clear() })
        col.features.add(wp)

        return col
    }

    fun describe(name: String): String? = when (name) {
        "Arrow HUD" -> "Selected arrow + quiver count, low/out warnings"
        "Limbo Alert" -> "Title and sound when you get sent to Limbo"
        "Party Join Sound" -> "Plays a sound when someone joins your party"
        "Chat Coord Waypoints" -> "Waypoints from Patcher-style x: y: z: coords in chat"
        else -> null
    }
}
