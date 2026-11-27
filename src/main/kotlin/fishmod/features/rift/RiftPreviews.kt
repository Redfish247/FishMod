package fishmod.features.rift

import fishmod.features.WorldPreview as P
import fishmod.features.rift.RiftSettings as S

object RiftPreviews {
    private var done = false

    fun register() {
        if (done) return
        done = true

        P.register("Rift Nametag Stats") { c, x, y ->
            P.text(c, "§f[MVP§c+§f] Player", x, y - 10, 1.5f)
            P.text(c, "§4Vampire §f6 §7(2400 XP)  §5Souls §f12", x, y + 4, 1.2f)
        }
        P.register("Punchcard Marker") { c, x, y ->
            P.text(c, "§f[MVP§c+§f] Player", x, y - 10, 1.5f)
            P.text(c, "§f✔ Punched", x, y + 4, 1.2f)
            P.box(c, x - 46, y + 18, 92, 3, S.riftPunchcardColor)
        }
        P.register("Agaricus Cap Timer") { c, x, y ->
            P.box(c, x - 14, y - 14, 28, 28, 0xFFAA5555.toInt())
            P.text(c, "§a${S.riftAgaricusSeconds}.0", x, y - 4, 1.5f)
            P.caption(c, x, y, "Turns red: click")
        }
        P.register("Berberis Highlighter") { c, x, y ->
            P.box(c, x - 14, y - 14, 28, 28, S.riftBerberisColor)
        }
        P.register("Caducous Stem Highlighter") { c, x, y ->
            for (i in 0..2) P.box(c, x - 10, y + 20 - i * 22, 20, 20, 0xFF55AA55.toInt())
            P.box(c, x - 10, y - 24, 20, 20, S.riftStemColor)
        }
        P.register("Crux Helper") { c, x, y ->
            P.box(c, x - 14, y - 10, 28, 40, 0xFFAA55FF.toInt())
        }
        P.register("Fleespook Tracker") { c, x, y ->
            P.line(c, x, y + 70, x + 70, y - 20, S.riftFleespookColor)
            P.box(c, x + 62, y - 36, 18, 18, S.riftFleespookColor)
        }
        P.register("Bacte Helper") { c, x, y ->
            P.line(c, x - 70, y + 40, x + 40, y + 10, S.riftBacteColor)
            P.box(c, x + 34, y + 2, 16, 16, 0xFF55FF55.toInt())
        }
        P.register("Mirrorverse Parkour") { c, x, y ->
            for (i in 0 until S.riftParkourAhead.coerceIn(1, 8)) P.box(c, x - 60 + i * 20, y + 20 - i * 8, 16, 6, S.riftParkourColor)
        }
        P.register("Blood Effigy Spots") { c, x, y ->
            for (i in 0..5) {
                val broken = (i % 2 == 0) != S.riftEffigyInvert
                P.box(c, x - 54 + i * 20, y - 6, 16, 16, if (broken) 0xFF55FF55.toInt() else S.riftEffigyColor)
            }
        }
        P.register("Colosseum Kill Zone Warning") { c, x, y -> P.box(c, x - 40, y, 80, 30, 0xFFFF5555.toInt()) }
        P.register("Snake Helper") { c, x, y ->
            val cols = intArrayOf(S.riftSnakeTailColor, S.riftSnakeColor, S.riftSnakeColor, S.riftSnakeColor, S.riftSnakeHeadColor)
            for ((i, col) in cols.withIndex()) P.box(c, x - 50 + i * 20, y - 8 + (i % 2) * 14, 16, 16, col)
        }
        P.register("Vampire Slayer Helper") { c, x, y ->
            P.box(c, x - 14, y - 10, 28, 40, S.riftVampireSteakColor)
            if (S.riftVampireLine) P.line(c, x, y + 80, x, y + 30, S.riftVampireLineColor)
            if (S.riftVampireIchor) P.box(c, x + 40, y + 10, 14, 14, S.riftVampireIchorColor)
            if (S.riftVampireSpring) P.box(c, x - 54, y + 10, 14, 14, S.riftVampireSpringColor)
        }
        P.register("Half-Eaten Cave Hay Warning") { c, x, y ->
            for (i in 0..2) P.box(c, x - 40 + i * 28, y, 24, 24, S.riftHayColor)
        }
        P.register("Leech Supreme Helper") { c, x, y ->
            P.box(c, x - 14, y + 4, 28, 28, S.riftLeechColor)
            P.line(c, x, y + 80, x, y + 32, S.riftLeechColor)
        }
        P.register("Burger Warning") { c, x, y -> P.caption(c, x, y, "Warning title shown at its alert spot") }
    }
}
