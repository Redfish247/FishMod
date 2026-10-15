package fishmod.features.rift

import fishmod.features.FishModScreen.ColorPickerSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.SliderIntSetting
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.rift.RiftSettings as S

// The Rift tab in /fm
object RiftMenu {

    fun column(): Column {
        val col = Column("Rift")

        val tag = Feature("Rift Nametag Stats", S::riftNametag)
        tag.sub.add(ToggleSetting("Vampire Slayer", "Vampire level + XP", S::riftNametagVampire))
        tag.sub.add(ToggleSetting("Enigma Souls", "Souls found", S::riftNametagSouls))
        col.features.add(tag)

        val punch = Feature("Punchcard Marker", S::riftPunchcard)
        punch.sub.add(ColorPickerSetting("Colour", "", S::riftPunchcardColor))
        col.features.add(punch)

        val cap = Feature("Agaricus Cap Timer", S::riftAgaricus)
        cap.sub.add(SliderIntSetting("Countdown Seconds", "", S::riftAgaricusSeconds, 1, 10))
        col.features.add(cap)

        val ber = Feature("Berberis Highlighter", S::riftBerberis)
        ber.sub.add(ColorPickerSetting("Colour", "", S::riftBerberisColor))
        col.features.add(ber)

        val crux = Feature("Crux Helper", S::riftCrux)
        crux.sub.add(ToggleSetting("Shy Look Away Title", "Title when a Shy Crux is shouting near you", S::riftShyTitle))
        crux.sub.add(SliderIntSetting("Hint Range", "Only label Crux mobs within this many blocks", S::riftCruxRange, 5, 40))
        crux.sub.add(ToggleSetting("Through Walls", "", S::riftCruxWalls))
        crux.sub.add(ToggleSetting("Shy Hint", "", S::riftCruxShy))
        crux.sub.add(ToggleSetting("Shadow Hint", "", S::riftCruxShadow))
        crux.sub.add(ToggleSetting("Volt Hint", "", S::riftCruxVolt))
        crux.sub.add(ToggleSetting("Scribe Hint", "", S::riftCruxScribe))
        crux.sub.add(ToggleSetting("Frozille Hint", "", S::riftCruxFrozille))
        crux.sub.add(ToggleSetting("Splatter Hint", "", S::riftCruxSplatter))
        col.features.add(crux)

        val flee = Feature("Fleespook Tracker", S::riftFleespook)
        flee.sub.add(ColorPickerSetting("Line Colour", "", S::riftFleespookColor))
        col.features.add(flee)

        val bacte = Feature("Bacte Helper", S::riftBacte)
        bacte.sub.add(ToggleSetting("Line To Bacte", "", S::riftBacteLine))
        bacte.sub.add(ToggleSetting("Spawn Title", "", S::riftBacteTitle))
        bacte.sub.add(ColorPickerSetting("Line Colour", "", S::riftBacteColor))
        col.features.add(bacte)

        val park = Feature("Mirrorverse Parkour", S::riftParkour)
        park.sub.add(SliderIntSetting("Platforms Ahead", "", S::riftParkourAhead, 1, 8))
        park.sub.add(ColorPickerSetting("Colour", "", S::riftParkourColor))
        col.features.add(park)

        val eff = Feature("Blood Effigy Spots", S::riftEffigies)
        eff.sub.add(ColorPickerSetting("Colour", "", S::riftEffigyColor))
        eff.sub.add(ToggleSetting("Invert Colours", "Swap which square colour means not broken", S::riftEffigyInvert))
        col.features.add(eff)
        col.features.add(Feature("Colosseum Kill Zone Warning", S::riftKillZone))

        val snake = Feature("Snake Helper", S::riftSnake)
        snake.sub.add(ColorPickerSetting("Body Colour", "", S::riftSnakeColor))
        snake.sub.add(ColorPickerSetting("Head Colour", "", S::riftSnakeHeadColor))
        col.features.add(snake)

        val vamp = Feature("Vampire Slayer Helper", S::riftVampire)
        vamp.sub.add(ToggleSetting("Highlight Boss", "", S::riftVampireHighlight))
        vamp.sub.add(ToggleSetting("Steak Title", "STEAK! at 20% HP", S::riftVampireSteakTitle))
        vamp.sub.add(ToggleSetting("Twinclaws Warning", "", S::riftVampireTwinclaws))
        vamp.sub.add(ColorPickerSetting("Boss Colour", "", S::riftVampireColor))
        vamp.sub.add(ColorPickerSetting("Steakable Colour", "", S::riftVampireSteakColor))
        col.features.add(vamp)

        return col
    }

    fun describe(name: String): String? = when (name) {
        "Rift Nametag Stats" -> "Vampire XP + Enigma Souls in nametags (needs Nametag Stats on)"
        "Punchcard Marker" -> "Mark players you already punched"
        "Agaricus Cap Timer" -> "Countdown on the cap, CLICK when it turns red"
        "Berberis Highlighter" -> "Box the Wilted Berberis from its particles (hold the Farming Wand)"
        "Crux Helper" -> "What to do for each Crux"
        "Fleespook Tracker" -> "Line to the Fleespook"
        "Bacte Helper" -> "Bacte spawn alert, line, optional all-chat announce"
        "Mirrorverse Parkour" -> "Lava Maze, Upside Down and Tubulator platforms (SkyHanni data)"
        "Blood Effigy Spots" -> "Boxes at the six Stillgore Chateau effigy spots"
        "Colosseum Kill Zone Warning" -> "Title when told to get back in the arena"
        "Snake Helper" -> "Outline Living Cave snakes, head highlighted"
        "Vampire Slayer Helper" -> "Boss highlight, STEAK alert, Twinclaws warning"
        else -> null
    }
}
