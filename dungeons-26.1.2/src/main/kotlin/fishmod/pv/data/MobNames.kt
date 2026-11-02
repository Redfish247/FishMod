/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

/**
 * SkyCrypt-style labels for `player_stats.kills` / `deaths` keys.
 * Strips trailing level suffixes (`kuudra_follower_500`) then maps known ids.
 */
object MobNames {
    private val NAMES = mapOf(
        "pond_squid" to "Squid",
        "unburried_zombie" to "Crypt Ghoul",
        "unburied_zombie" to "Crypt Ghoul",
        "zealot_enderman" to "Zealot",
        "invisible_creeper" to "Sneaky Creeper",
        "generator_ghast" to "Minion Ghast",
        "generator_magma_cube" to "Minion Magma Cube",
        "generator_slime" to "Minion Slime",
        "brood_mother_spider" to "Brood Mother",
        "obsidian_wither" to "Obsidian Defender",
        "sadan_statue" to "Terracotta",
        "diamond_guy" to "Angry Archaeologist",
        "tentaclees" to "Fels",
        "master_diamond_guy" to "Master Angry Archaeologist",
        "master_sadan_statue" to "Master Terracotta",
        "master_tentaclees" to "Master Fels",
        "maxor" to "Necron",
        "pig_rider" to "Taurus",
    )
    private val COLOR_CODE = Regex("§[0-9a-fk-or]")
    private val LEVEL_SUFFIX = Regex("_\\d+$")
    private val UNDERSCORES = Regex("_+")

    @JvmStatic
    fun pretty(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val key = raw.trim().lowercase().replace('-', '_').replace(' ', '_')
            .replace(COLOR_CODE, "")
            .replace(LEVEL_SUFFIX, "")
        return NAMES[key] ?: titleCase(key)
    }

    private fun titleCase(key: String): String =
        key.split(UNDERSCORES).filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
}
