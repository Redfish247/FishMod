/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

/** Trophy frog catalog (Lotus Atoll / Ribery order). */
object TrophyFrogData {
    private val DEFS: List<TrophyFishData.Def> = listOf(
        "COMMON_FROG",
        "EXPLODING_FROG",
        "LEAP_FROG",
        "WETLANDS_FROG",
        "REALITY_HOPPER",
        "CAVE_FROG",
        "SEA_FROG",
        "TREE_FROG",
        "HIGHLANDS_FROG",
        "BULLFROG",
        "BLESSED_FROG",
        "PUDDLE_JUMPER",
    ).map { TrophyFishData.Def(it, pretty(it)) }

    @JvmStatic
    fun all(): List<TrophyFishData.Def> = DEFS

    private fun pretty(id: String): String {
        if (id.equals("REALITY_HOPPER", ignoreCase = true)) return "Reality Hopper"
        if (id.equals("BULLFROG", ignoreCase = true)) return "Bullfrog"
        return id.replace('-', '_').split('_').filter { it.isNotEmpty() }
            .joinToString(" ") { it.first().uppercaseChar() + it.substring(1).lowercase() }
    }
}
