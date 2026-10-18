package fishmod.features.kuudra

import fishmod.features.kuudra.Kuudra.Supply

// Crate priority table (Odin NoPre)
object KuudraPriority {

    fun of(missing: Supply, pre: Supply, adv: Boolean): String {
        val pull = if (adv) "Pull Square and X Cannon. Next: collect Shop" else "Pull Square. Next: collect Shop"
        return when (missing) {
            Supply.Shop -> when (pre) { Supply.Triangle, Supply.X -> "Go X Cannon"; Supply.Equals, Supply.Slash -> "Go Square, place on Shop"; else -> "" }
            Supply.Triangle -> when (pre) { Supply.Triangle -> pull; Supply.X -> "Go X Cannon"; Supply.Equals -> if (adv) "Go Shop" else "Go X Cannon"; Supply.Slash -> "Go Square, place on Triangle"; else -> "" }
            Supply.Equals -> when (pre) { Supply.Triangle -> if (adv) "Go Shop" else "Go X Cannon"; Supply.X -> "Go X Cannon"; Supply.Equals -> pull; Supply.Slash -> "Go Square, place on Equals"; else -> "" }
            Supply.Slash -> when (pre) { Supply.Triangle -> "Go Square, place on Slash"; Supply.X -> "Go X Cannon"; Supply.Equals -> if (adv) "Go Shop" else "Go X Cannon"; Supply.Slash -> pull; else -> "" }
            Supply.Square -> when (pre) { Supply.Triangle, Supply.Equals -> "Go Shop"; Supply.X, Supply.Slash -> "Go X Cannon"; else -> "" }
            Supply.xCannon -> when (pre) { Supply.Triangle, Supply.Equals -> "Go Shop"; Supply.Slash, Supply.X -> "Go Square, place on X Cannon"; else -> "" }
            Supply.X -> when (pre) { Supply.Triangle -> "Go X Cannon"; Supply.X -> pull; Supply.Equals -> if (adv) "Go Shop" else "Go X Cannon"; Supply.Slash -> "Go Square, place on X"; else -> "" }
            Supply.None -> ""
        }
    }
}
