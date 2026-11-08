package fishmod.features.fishing

import fishmod.pv.price.ItemPricer
import fishmod.utils.SkyblockLocation
import fishmod.utils.SkyblockLocation.*

// Pure data and parsers for the trackers. Strings adapted from Feesh (Sleepy-Panda, Apache-2.0); ALL UNVERIFIED IN GAME.
object FishingTrackerMath {
    @JvmField var priceFn: (String) -> Double = { ItemPricer.price(it) }

    const val FESTIVAL_ENDED = "FISHING FESTIVAL The festival has concluded! Time to dry off and repair your rods!"
    const val BARN_FULL = "There is not enough space for another Sea Creature! Kill some to make space for new ones!"
    private val TREASURE_RE = Regex("""^\S? ?(GOOD JUNK|GREAT JUNK|OUTSTANDING JUNK|GOOD|GREAT|OUTSTANDING|MUSICAL) CATCH!""")
    private val DICE_RE = Regex("""^Your (High Class )?Archfiend Dice rolled a (\d+)!""")

    // Idle gaps longer than this do not count as fishing time
    const val ACTIVE_GAP_MS = 300_000L

    @JvmStatic fun activeDelta(lastAt: Long, now: Long): Long = if (lastAt <= 0L) 0L else (now - lastAt).coerceIn(0L, ACTIVE_GAP_MS)

    // Rates need a minute of data first, else 0
    @JvmStatic fun perHour(v: Double, ms: Long): Double = if (ms < 60_000L) 0.0 else v * 3_600_000.0 / ms

    @JvmStatic fun pct(n: Long, total: Long): String = if (total <= 0L) "0%" else "%.1f%%".format(n * 100.0 / total)

    @JvmStatic fun short(v: Double): String {
        val a = Math.abs(v)
        return when {
            a >= 1e9 -> "%.2fb".format(v / 1e9)
            a >= 1e6 -> "%.2fm".format(v / 1e6)
            a >= 1e3 -> "%.1fk".format(v / 1e3)
            else -> "%.0f".format(v)
        }
    }

    @JvmStatic fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    // "GOOD CATCH" style label, or null
    @JvmStatic fun treasure(plain: String): String? {
        if (!plain.contains("CATCH!")) return null
        val label = TREASURE_RE.find(plain)?.groupValues?.get(1) ?: return null
        return if (label == "MUSICAL") null else "$label CATCH"
    }

    class Dice(val highClass: Boolean, val roll: Int)

    @JvmStatic fun dice(plain: String): Dice? {
        if (!plain.contains("Archfiend Dice rolled a ")) return null
        val m = DICE_RE.find(plain) ?: return null
        val roll = m.groupValues[2].toIntOrNull() ?: return null
        return if (roll in 1..7) Dice(m.groupValues[1].isNotEmpty(), roll) else null
    }

    // Net coins of one roll: cost, plus win on 6/7 minus the lost dice (Feesh constants, unverified)
    @JvmStatic fun diceCoins(d: Dice): Double {
        val dicePrice = priceFn(if (d.highClass) "HIGH_CLASS_ARCHFIEND_DICE" else "ARCHFIEND_DICE")
        var c = -(if (d.highClass) 6_600_000.0 else 666_000.0)
        when (d.roll) {
            6 -> c += (if (d.highClass) 100_000_000.0 else 15_000_000.0) - dicePrice
            7 -> c += priceFn("DYE_ARCHFIEND") - dicePrice
        }
        return c
    }
}

// One parsed per-area chat line: counter key plus coins (can be negative)
class AreaLine(val key: String, val coins: Double)

// Generic data-driven per-area tracker definition.
class AreaDef(
    val id: String,
    val title: String,
    // empty = every world
    val locs: Set<SkyblockLocation>,
    val sc: (SeaCreature) -> Boolean,
    val drops: Set<String>,
    // "N <pitySc> since last <pityDrop>"
    val pitySc: String? = null,
    val pityDrop: String? = null,
    val line: ((String) -> AreaLine?)? = null,
    // true = shown only after a line in the last 10 min (dice)
    val recentOnly: Boolean = false,
) {
    fun inArea(loc: SkyblockLocation) = locs.isEmpty() || loc in locs
}

object FishingTrackerAreas {
    private fun names(vararg n: String): (SeaCreature) -> Boolean { val s = n.toSet(); return { it.name in s } }
    private val NONE: (SeaCreature) -> Boolean = { false }

    const val TREASURE = "treasure"
    const val DICE = "dice"

    @JvmField val ALL: List<AreaDef> = listOf(
        AreaDef("crimson", "Crimson Isle", setOf(CRIMSON_ISLE), names("Thunder", "Lord Jawbus", "Fiery Scuttler", "Ragnarok", "Plhlegblast"), setOf("Radioactive Vial")),
        AreaDef("bayou", "Backwater Bayou", setOf(BACKWATER_BAYOU), names("Titanoboa"), setOf("Titanoboa Shed", "Snake Eyes"), "Titanoboa", "Titanoboa Shed"),
        AreaDef("lotus", "Lotus Atoll", setOf(LOTUS_ATOLL), names("Frog Prince", "Puddle Jumper"), setOf("Prince's Crown Jewel"), "Frog Prince", "Prince's Crown Jewel"),
        AreaDef("torrhus", "Torrhus Canyon", setOf(TORRHUS_CANYON), names("Silkbreeze", "Giant Isopod"), emptySet()),
        AreaDef("moonglade", "Moonglade Marsh", setOf(MOONGLADE_MARSH), names("The Loch Emperor", "Nessie"), emptySet()),
        AreaDef("jerry", "Jerry's Workshop", setOf(JERRYS_WORKSHOP), names("Yeti", "Reindrake"), emptySet()),
        // Feesh also requires a hotspot nearby; that check belongs to the world phase and is not done here
        AreaDef("hotspot", "Water Hotspots", setOf(BACKWATER_BAYOU, SPIDERS_DEN, HUB, JERRYS_WORKSHOP, LOTUS_ATOLL, THE_PARK, TORRHUS_CANYON), names("Wiki Tiki"), setOf("Tiki Mask")),
        AreaDef("magma", "Magma Core", setOf(CRYSTAL_HOLLOWS), { it.magmaFields }, setOf("Magma Core")),
        AreaDef(TREASURE, "Treasure", emptySet(), NONE, setOf("Treasure Dye"), line = { p -> FishingTrackerMath.treasure(p)?.let { AreaLine(it, 0.0) } }),
        AreaDef(DICE, "Archfiend Dice", emptySet(), NONE, emptySet(), line = { p ->
            FishingTrackerMath.dice(p)?.let { AreaLine((if (it.highClass) "High Class " else "") + "Roll ${it.roll}", FishingTrackerMath.diceCoins(it)) }
        }, recentOnly = true),
    )
}
