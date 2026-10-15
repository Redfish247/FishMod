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

    // Wyld Woods / mobs
    @ConfigValue @JvmField var riftCrux: Boolean = false
    @ConfigValue @JvmField var riftShyTitle: Boolean = true
    @ConfigValue @JvmField var riftCruxRange: Int = 20
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
    @ConfigValue @JvmField var riftBacteAnnounce: Boolean = false
    @ConfigValue @JvmField var riftBacteColor: Int = 0xFF55FF55.toInt()

    // Living Cave
    @ConfigValue @JvmField var riftSnake: Boolean = false
    @ConfigValue @JvmField var riftSnakeColor: Int = 0xFF5555FF.toInt()
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
}
