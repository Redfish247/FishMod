package fishmod.features.rift

import fishmod.shaded.practicalconfig.manager.ConfigValue

object RiftSettings {

    // Nametags
    @ConfigValue @JvmField var riftNametag: Boolean = false
    @ConfigValue @JvmField var riftNametagVampire: Boolean = true
    @ConfigValue @JvmField var riftNametagSouls: Boolean = true
    @ConfigValue @JvmField var riftPunchcard: Boolean = false
    @ConfigValue @JvmField var riftPunchcardColor: Int = 0xFF55FF55.toInt()

    // Dreadfarm
    @ConfigValue @JvmField var riftAgaricus: Boolean = false
    @ConfigValue @JvmField var riftAgaricusSeconds: Int = 5
    @ConfigValue @JvmField var riftBerberis: Boolean = false
    @ConfigValue @JvmField var riftBerberisColor: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var riftStem: Boolean = false
    @ConfigValue @JvmField var riftStemColor: Int = 0xFFFF55FF.toInt()

    // Wyld Woods / mobs
    @ConfigValue @JvmField var riftCrux: Boolean = false
    @ConfigValue @JvmField var riftShyTitle: Boolean = true
    @ConfigValue @JvmField var riftCruxRange: Int = 20
    @ConfigValue @JvmField var riftCruxTextPct: Int = 80
    @ConfigValue @JvmField var riftCruxWalls: Boolean = false
    @ConfigValue @JvmField var riftCruxShy: Boolean = true
    @ConfigValue @JvmField var riftCruxShadow: Boolean = true
    @ConfigValue @JvmField var riftCruxVolt: Boolean = true
    @ConfigValue @JvmField var riftCruxScribe: Boolean = true
    @ConfigValue @JvmField var riftCruxFrozille: Boolean = true
    @ConfigValue @JvmField var riftCruxSplatter: Boolean = true
    @ConfigValue @JvmField var riftFleespook: Boolean = false
    @ConfigValue @JvmField var riftFleespookColor: Int = 0xFFAA55FF.toInt()

    // Colosseum
    @ConfigValue @JvmField var riftBacte: Boolean = false
    @ConfigValue @JvmField var riftBacteLine: Boolean = true
    @ConfigValue @JvmField var riftBacteTitle: Boolean = true
    @ConfigValue @JvmField var riftBacteSlimeBoxes: Boolean = true
    @ConfigValue @JvmField var riftBacteSizeMin: Int = 3
    @ConfigValue @JvmField var riftBacteSizeMax: Int = 3
    @ConfigValue @JvmField var riftBacteHideSlimes: Boolean = true
    @ConfigValue @JvmField var riftBacteColor: Int = 0xFF55FF55.toInt()

    // Living Cave
    @ConfigValue @JvmField var riftSnake: Boolean = false
    @ConfigValue @JvmField var riftSnakeColor: Int = 0xFF5555FF.toInt()
    @ConfigValue @JvmField var riftSnakeTailColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var riftSnakeHeadColor: Int = 0xFFFF5555.toInt()

    // Mirrorverse / Stillgore / Colosseum
    @ConfigValue @JvmField var riftParkour: Boolean = false
    @ConfigValue @JvmField var riftParkourAhead: Int = 3
    @ConfigValue @JvmField var riftParkourColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var riftEffigies: Boolean = false
    @ConfigValue @JvmField var riftEffigyColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var riftEffigyInvert: Boolean = false
    @ConfigValue @JvmField var riftKillZone: Boolean = false

    // Vampire slayer
    @ConfigValue @JvmField var riftVampire: Boolean = false
    @ConfigValue @JvmField var riftVampireSteakTitle: Boolean = true
    @ConfigValue @JvmField var riftVampireTwinclaws: Boolean = true
    @ConfigValue @JvmField var riftVampireHighlight: Boolean = true
    @ConfigValue @JvmField var riftVampireColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var riftVampireSteakColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var riftVampireLine: Boolean = false
    @ConfigValue @JvmField var riftVampireLineColor: Int = 0xFFFFFFFF.toInt()

    // Leech Supreme
    @ConfigValue @JvmField var riftLeech: Boolean = false
    @ConfigValue @JvmField var riftLeechTitle: Boolean = true
    @ConfigValue @JvmField var riftLeechColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var riftVampireIchor: Boolean = true
    @ConfigValue @JvmField var riftVampireIchorColor: Int = 0xFFAA0000.toInt()
    @ConfigValue @JvmField var riftVampireSpring: Boolean = true
    @ConfigValue @JvmField var riftVampireSpringColor: Int = 0xFFFF55FF.toInt()

    // Burger warning (-1 = McGrubber stacks not seen yet)
    @ConfigValue @JvmField var riftBurgerWarn: Boolean = false
    @ConfigValue @JvmField var riftBurgerTitle: Boolean = true
    @ConfigValue @JvmField var riftGrubberStacks: Int = -1

    // Half-Eaten Cave
    @ConfigValue @JvmField var riftHay: Boolean = false
    @ConfigValue @JvmField var riftHayTitle: Boolean = true
    @ConfigValue @JvmField var riftHayText: String = "§c§lHAY WILL CRUSH YOU!"
    @ConfigValue @JvmField var riftHaySound: Boolean = true
    @ConfigValue @JvmField var riftHayBoxes: Boolean = true
    @ConfigValue @JvmField var riftHayColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var riftHayDelay: Int = 40
    @ConfigValue @JvmField var riftHayLead: Int = 2

    // Rift Guide overlay
    @ConfigValue @JvmField var riftGuide: Boolean = false
    @ConfigValue @JvmField var riftGuideMaxLines: Int = 15
    @ConfigValue @JvmField var riftGuideColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var riftGuideX: Int = 5
    @ConfigValue @JvmField var riftGuideY: Int = 80
    @ConfigValue @JvmField var riftGuideScale: Double = 1.0
}
