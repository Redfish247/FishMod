package fishmod.features

/**
 * Computes "overflow" pet levels — the level a maxed pet (Lvl 100 / 200) keeps climbing to
 * as it gains XP past max. Ported from NopoMod's OverflowPetLevels (XP table + math).
 */
object OverflowPetLevels {

    enum class Rarity(@JvmField val offset: Int) {
        COMMON(0), UNCOMMON(6), RARE(11), EPIC(15), LEGENDARY(20), MYTHIC(20), UNKNOWN(20)
    }

    private val LIST_OF_XP = intArrayOf(
        100, 110, 120, 130, 145, 160, 175, 190, 210, 230, 250, 275, 300, 330, 360, 400, 440, 490, 540, 600,
        660, 730, 800, 880, 960, 1050, 1150, 1260, 1380, 1510, 1650, 1800, 1960, 2130, 2310, 2500, 2700, 2920, 3160, 3420,
        3700, 4000, 4350, 4750, 5200, 5700, 6300, 7000, 7800, 8700, 9700, 10800, 12000, 13300, 14700, 16200, 17800, 19500, 21300, 23200,
        25200, 27400, 29800, 32400, 35200, 38200, 41400, 44800, 48400, 52200, 56200, 60400, 64800, 69400, 74200, 79200, 84700, 90700, 97200, 104200,
        111700, 119700, 128200, 137200, 146700, 156700, 167700, 179700, 192700, 206700, 221700, 237700, 254700, 272700, 291700, 311700, 333700, 357700, 383700, 411700,
        441700, 476700, 516700, 561700, 611700, 666700, 726700, 791700, 861700, 936700, 1016700, 1101700, 1191700, 1286700, 1386700, 1496700, 1616700, 1746700, 1886700, 0,
        5555
    )

    @JvmStatic
    fun getXpForLevel(level: Int, rarity: Rarity): Int {
        val offset = rarity.offset + level
        return if (offset < LIST_OF_XP.size) LIST_OF_XP[offset] else 1886700
    }

    /** Total XP required to reach `level` from 0 for the given rarity. */
    @JvmStatic
    fun getCalculativeXpForLevel(level: Int, rarity: Rarity): Long {
        var xp = 0L
        for (i in 0 until level) xp += getXpForLevel(i, rarity)
        return xp
    }

    /** The overflow level for a given total XP. */
    @JvmStatic
    fun calcLevel(totalXp: Double, rarity: Rarity): Int {
        var exp = totalXp
        var i = 0
        while (exp > 0) {
            exp -= getXpForLevel(i, rarity)
            i++
            if (i > 1000) break // safety
        }
        return maxOf(1, i)
    }

    /** Leftover XP into the current overflow level (progress numerator). */
    @JvmStatic
    fun calcLeftOverXp(totalXp: Double, rarity: Rarity): Double {
        var exp = totalXp
        var i = 0
        while (exp > 0) {
            val xp = getXpForLevel(i, rarity)
            if (exp > xp) exp -= xp
            else return exp
            i++
            if (i > 1000) break
        }
        return -1.0
    }
}
