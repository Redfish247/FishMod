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

        val tag = Feature("Rift Nametag Stats", S::class.java, "riftNametag")
        tag.sub.add(ToggleSetting("Vampire Slayer", "Vampire level + XP", S::class.java, "riftNametagVampire"))
        tag.sub.add(ToggleSetting("Enigma Souls", "Souls found", S::class.java, "riftNametagSouls"))
        col.features.add(tag)

        val punch = Feature("Punchcard Marker", S::class.java, "riftPunchcard")
        punch.sub.add(ColorPickerSetting("Colour", "", S::class.java, "riftPunchcardColor"))
        col.features.add(punch)

        val cap = Feature("Agaricus Cap Timer", S::class.java, "riftAgaricus")
        cap.sub.add(SliderIntSetting("Countdown Seconds", "", S::class.java, "riftAgaricusSeconds", 1, 10))
        col.features.add(cap)

        val ber = Feature("Berberis Highlighter", S::class.java, "riftBerberis")
        ber.sub.add(ColorPickerSetting("Colour", "", S::class.java, "riftBerberisColor"))
        col.features.add(ber)

        val stem = Feature("Caducous Stem Highlighter", S::class.java, "riftStem")
        stem.sub.add(ColorPickerSetting("Colour", "", S::class.java, "riftStemColor"))
        col.features.add(stem)

        val crux = Feature("Crux Helper", S::class.java, "riftCrux")
        crux.sub.add(ToggleSetting("Shy Look Away Title", "Title when a Shy Crux is shouting near you", S::class.java, "riftShyTitle"))
        crux.sub.add(SliderIntSetting("Hint Range", "Only label Crux mobs within this many blocks", S::class.java, "riftCruxRange", 5, 40))
        crux.sub.add(SliderIntSetting("Text Size", "Hint size in % (80 is the default)", S::class.java, "riftCruxTextPct", 30, 200))
        crux.sub.add(ToggleSetting("Through Walls", "", S::class.java, "riftCruxWalls"))
        crux.sub.add(ToggleSetting("Shy Hint", "", S::class.java, "riftCruxShy"))
        crux.sub.add(ToggleSetting("Shadow Hint", "", S::class.java, "riftCruxShadow"))
        crux.sub.add(ToggleSetting("Volt Hint", "", S::class.java, "riftCruxVolt"))
        crux.sub.add(ToggleSetting("Scribe Hint", "", S::class.java, "riftCruxScribe"))
        crux.sub.add(ToggleSetting("Frozille Hint", "", S::class.java, "riftCruxFrozille"))
        crux.sub.add(ToggleSetting("Splatter Hint", "", S::class.java, "riftCruxSplatter"))
        col.features.add(crux)

        val flee = Feature("Fleespook Tracker", S::class.java, "riftFleespook")
        flee.sub.add(ColorPickerSetting("Line Colour", "", S::class.java, "riftFleespookColor"))
        col.features.add(flee)

        val bacte = Feature("Bacte Helper", S::class.java, "riftBacte")
        bacte.sub.add(ToggleSetting("Slime Path Line", "Flat line of a hit slime's flight; only while a tentacle or Blobercyst is alive", S::class.java, "riftBacteLine"))
        bacte.sub.add(ToggleSetting("Spawn Title", "", S::class.java, "riftBacteTitle"))
        bacte.sub.add(ToggleSetting("Slime Boxes", "Green through-wall boxes on slimes", S::class.java, "riftBacteSlimeBoxes"))
        bacte.sub.add(SliderIntSetting("Slime Size Min", "Smallest slime size to box (size 3 is the common one)", S::class.java, "riftBacteSizeMin", 1, 16))
        bacte.sub.add(SliderIntSetting("Slime Size Max", "Largest slime size to box", S::class.java, "riftBacteSizeMax", 1, 16))
        bacte.sub.add(ToggleSetting("Hide Slimes", "Stop drawing the slimes themselves (needs Slime Boxes)", S::class.java, "riftBacteHideSlimes"))
        bacte.sub.add(ColorPickerSetting("Line Colour", "", S::class.java, "riftBacteColor"))
        col.features.add(bacte)

        val park = Feature("Mirrorverse Parkour", S::class.java, "riftParkour")
        park.sub.add(SliderIntSetting("Platforms Ahead", "", S::class.java, "riftParkourAhead", 1, 8))
        park.sub.add(ColorPickerSetting("Colour", "", S::class.java, "riftParkourColor"))
        col.features.add(park)

        val eff = Feature("Blood Effigy Spots", S::class.java, "riftEffigies")
        eff.sub.add(ColorPickerSetting("Colour", "", S::class.java, "riftEffigyColor"))
        eff.sub.add(ToggleSetting("Invert Colours", "Swap which square colour means not broken", S::class.java, "riftEffigyInvert"))
        col.features.add(eff)
        col.features.add(Feature("Colosseum Kill Zone Warning", S::class.java, "riftKillZone"))

        val snake = Feature("Snake Helper", S::class.java, "riftSnake")
        snake.sub.add(ColorPickerSetting("Body Colour", "", S::class.java, "riftSnakeColor"))
        snake.sub.add(ColorPickerSetting("Head Colour", "", S::class.java, "riftSnakeHeadColor"))
        snake.sub.add(ColorPickerSetting("Tail Colour", "The oldest block: hit this one first", S::class.java, "riftSnakeTailColor"))
        col.features.add(snake)

        val vamp = Feature("Vampire Slayer Helper", S::class.java, "riftVampire")
        vamp.sub.add(ToggleSetting("Highlight Boss", "", S::class.java, "riftVampireHighlight"))
        vamp.sub.add(ToggleSetting("Steak Title", "STEAK! at 20% HP", S::class.java, "riftVampireSteakTitle"))
        vamp.sub.add(ToggleSetting("Twinclaws Warning", "", S::class.java, "riftVampireTwinclaws"))
        vamp.sub.add(ColorPickerSetting("Boss Colour", "", S::class.java, "riftVampireColor"))
        vamp.sub.add(ColorPickerSetting("Steakable Colour", "", S::class.java, "riftVampireSteakColor"))
        vamp.sub.add(ToggleSetting("Line to Boss", "", S::class.java, "riftVampireLine"))
        vamp.sub.add(ColorPickerSetting("Line Colour", "", S::class.java, "riftVampireLineColor"))
        vamp.sub.add(ToggleSetting("Highlight Blood Ichor", "", S::class.java, "riftVampireIchor"))
        vamp.sub.add(ColorPickerSetting("Ichor Colour", "", S::class.java, "riftVampireIchorColor"))
        vamp.sub.add(ToggleSetting("Highlight Killer Spring", "", S::class.java, "riftVampireSpring"))
        vamp.sub.add(ColorPickerSetting("Spring Colour", "", S::class.java, "riftVampireSpringColor"))
        col.features.add(vamp)

        val hay = Feature("Half-Eaten Cave Hay Warning", S::class.java, "riftHay")
        hay.sub.add(ToggleSetting("Title", "Warn when you stand where hay was cleared (it regrows and crushes you)", S::class.java, "riftHayTitle"))
        hay.sub.add(fishmod.features.FishModScreen.InputSetting("Title Text", "& colour codes work", S::class.java, "riftHayText"))
        hay.sub.add(ToggleSetting("Sound", "", S::class.java, "riftHaySound"))
        hay.sub.add(ToggleSetting("Cleared Hay Boxes", "Box the cells where hay was cleared", S::class.java, "riftHayBoxes"))
        hay.sub.add(ColorPickerSetting("Colour", "", S::class.java, "riftHayColor"))
        hay.sub.add(SliderIntSetting("Regrow Seconds", "Seconds until cleared hay starts to crush (about 40 on Hypixel)", S::class.java, "riftHayDelay", 2, 60))
        hay.sub.add(SliderIntSetting("Warn Seconds Before", "Warn only this long before the hay refills your cell", S::class.java, "riftHayLead", 1, 10))
        col.features.add(hay)

        val leech = Feature("Leech Supreme Helper", S::class.java, "riftLeech")
        leech.sub.add(ToggleSetting("Go There Title", "", S::class.java, "riftLeechTitle"))
        leech.sub.add(ColorPickerSetting("Spot Colour", "", S::class.java, "riftLeechColor"))
        col.features.add(leech)

        val burger = Feature("Burger Warning", S::class.java, "riftBurgerWarn")
        burger.sub.add(ToggleSetting("Title", "Big title as well as the chat warning", S::class.java, "riftBurgerTitle"))
        col.features.add(burger)

        val guide = Feature("Rift Guide Overlay", S::class.java, "riftGuide")
        guide.sub.add(SliderIntSetting("Max Lines", "", S::class.java, "riftGuideMaxLines", 3, 40))
        guide.sub.add(ColorPickerSetting("Task Colour", "", S::class.java, "riftGuideColor"))
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
