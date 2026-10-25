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

        val stem = Feature("Caducous Stem Highlighter", S::riftStem)
        stem.sub.add(ColorPickerSetting("Colour", "", S::riftStemColor))
        col.features.add(stem)

        val crux = Feature("Crux Helper", S::riftCrux)
        crux.sub.add(ToggleSetting("Shy Look Away Title", "Title when a Shy Crux is shouting near you", S::riftShyTitle))
        crux.sub.add(SliderIntSetting("Hint Range", "Only label Crux mobs within this many blocks", S::riftCruxRange, 5, 40))
        crux.sub.add(SliderIntSetting("Text Size", "Hint size in % (80 is the default)", S::riftCruxTextPct, 30, 200))
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
        bacte.sub.add(ToggleSetting("Slime Path Line", "Flat line of a hit slime's flight; only while a tentacle or Blobercyst is alive", S::riftBacteLine))
        bacte.sub.add(ToggleSetting("Spawn Title", "", S::riftBacteTitle))
        bacte.sub.add(ToggleSetting("Slime Boxes", "Green through-wall boxes on slimes", S::riftBacteSlimeBoxes))
        bacte.sub.add(SliderIntSetting("Slime Size Min", "Smallest slime size to box (size 3 is the common one)", S::riftBacteSizeMin, 1, 16))
        bacte.sub.add(SliderIntSetting("Slime Size Max", "Largest slime size to box", S::riftBacteSizeMax, 1, 16))
        bacte.sub.add(ToggleSetting("Hide Slimes", "Stop drawing the slimes themselves (needs Slime Boxes)", S::riftBacteHideSlimes))
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
        snake.sub.add(ColorPickerSetting("Tail Colour", "The oldest block: hit this one first", S::riftSnakeTailColor))
        col.features.add(snake)

        val vamp = Feature("Vampire Slayer Helper", S::riftVampire)
        vamp.sub.add(ToggleSetting("Highlight Boss", "", S::riftVampireHighlight))
        vamp.sub.add(ToggleSetting("Steak Title", "STEAK! at 20% HP", S::riftVampireSteakTitle))
        vamp.sub.add(ToggleSetting("Twinclaws Warning", "", S::riftVampireTwinclaws))
        vamp.sub.add(ColorPickerSetting("Boss Colour", "", S::riftVampireColor))
        vamp.sub.add(ColorPickerSetting("Steakable Colour", "", S::riftVampireSteakColor))
        vamp.sub.add(ToggleSetting("Line to Boss", "", S::riftVampireLine))
        vamp.sub.add(ColorPickerSetting("Line Colour", "", S::riftVampireLineColor))
        vamp.sub.add(ToggleSetting("Highlight Blood Ichor", "", S::riftVampireIchor))
        vamp.sub.add(ColorPickerSetting("Ichor Colour", "", S::riftVampireIchorColor))
        vamp.sub.add(ToggleSetting("Highlight Killer Spring", "", S::riftVampireSpring))
        vamp.sub.add(ColorPickerSetting("Spring Colour", "", S::riftVampireSpringColor))
        col.features.add(vamp)

        val hay = Feature("Half-Eaten Cave Hay Warning", S::riftHay)
        hay.sub.add(ToggleSetting("Title", "Warn when you stand where hay was cleared (it regrows and crushes you)", S::riftHayTitle))
        hay.sub.add(fishmod.features.FishModScreen.InputSetting("Title Text", "& colour codes work", S::riftHayText))
        hay.sub.add(ToggleSetting("Sound", "", S::riftHaySound))
        hay.sub.add(ToggleSetting("Cleared Hay Boxes", "Box the cells where hay was cleared", S::riftHayBoxes))
        hay.sub.add(ColorPickerSetting("Colour", "", S::riftHayColor))
        hay.sub.add(SliderIntSetting("Regrow Seconds", "Seconds until cleared hay starts to crush (about 40 on Hypixel)", S::riftHayDelay, 2, 60))
        hay.sub.add(SliderIntSetting("Warn Seconds Before", "Warn only this long before the hay refills your cell", S::riftHayLead, 1, 10))
        col.features.add(hay)

        val leech = Feature("Leech Supreme Helper", S::riftLeech)
        leech.sub.add(ToggleSetting("Go There Title", "", S::riftLeechTitle))
        leech.sub.add(ColorPickerSetting("Spot Colour", "", S::riftLeechColor))
        col.features.add(leech)

        val burger = Feature("Burger Warning", S::riftBurgerWarn)
        burger.sub.add(ToggleSetting("Title", "Big title as well as the chat warning", S::riftBurgerTitle))
        col.features.add(burger)

        val guide = Feature("Rift Guide Overlay", S::riftGuide)
        guide.sub.add(SliderIntSetting("Max Lines", "", S::riftGuideMaxLines, 3, 40))
        guide.sub.add(ColorPickerSetting("Task Colour", "", S::riftGuideColor))
        col.features.add(guide)

        return col
    }

    fun describe(name: String): String? = when (name) {
        "Rift Nametag Stats" -> "Vampire XP + Enigma Souls in nametags (needs Nametag Stats on)"
        "Punchcard Marker" -> "Mark players you already punched"
        "Agaricus Cap Timer" -> "Countdown on the cap, CLICK when it turns red"
        "Berberis Highlighter" -> "Box the Wilted Berberis from its particles (hold the Farming Wand)"
        "Caducous Stem Highlighter" -> "Box the top Rose Bush of each stem (hold the Farming Wand, harvest top to bottom)"
        "Crux Helper" -> "What to do for each Crux"
        "Fleespook Tracker" -> "Line to the Fleespook"
        "Bacte Helper" -> "Bacte spawn alert, line, optional all-chat announce"
        "Mirrorverse Parkour" -> "Lava Maze, Upside Down and Tubulator platforms (SkyHanni data)"
        "Blood Effigy Spots" -> "Boxes at the six Stillgore Chateau effigy spots"
        "Colosseum Kill Zone Warning" -> "Title when told to get back in the arena"
        "Snake Helper" -> "Outline Living Cave snakes, head highlighted"
        "Rift Guide Overlay" -> "Unfinished Rift Guide tasks on screen; open each guide page once to load it (move it in Edit HUD)"
        "Vampire Slayer Helper" -> "Boss highlight, STEAK alert, Twinclaws warning"
        "Burger Warning" -> "Warn when the Motes Grubber menu opens with Highlite in your inventory and under 5/5 McGrubber burgers"
        "Leech Supreme Helper" -> "Laser phase: box + line to the safe spot and a go-there title"
        else -> null
    }
}
