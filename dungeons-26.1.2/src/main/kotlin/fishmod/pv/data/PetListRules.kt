/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

object PetListRules {
    @JvmField
    val WISP_LADDER: List<String> = listOf("DROPLET_WISP", "FROST_WISP", "GLACIAL_WISP", "SUBZERO_WISP")
    private val HIDDEN = setOf("BINGO")

    private val WISP_ORDER: Comparator<PetSnapshot.Entry> = compareBy<PetSnapshot.Entry> { wispRank(it.type) }
        .thenBy { it.tierIndex }
        .thenBy { it.exp }
        .thenByDescending { it.uuid }

    @JvmStatic
    fun visibleOwned(pets: List<PetSnapshot.Entry>): List<PetSnapshot.Entry> {
        val out = ArrayList<PetSnapshot.Entry>()
        var bestWisp: PetSnapshot.Entry? = null
        for (pet in pets) {
            val type = key(pet.type)
            if (type in HIDDEN) continue
            if (wispRank(type) >= 0) {
                if (bestWisp == null || WISP_ORDER.compare(pet, bestWisp) > 0) bestWisp = pet
                continue
            }
            out.add(pet)
        }
        bestWisp?.let { out.add(it) }
        return out
    }

    /** Whether a pet type the player doesn't own should appear as a "missing" slot. */
    @JvmStatic
    fun showAsMissing(type: String?, ownedTypes: Set<String>): Boolean {
        val key = key(type)
        if (key in HIDDEN) return false
        val rank = wispRank(key)
        if (rank < 0) return true
        if (WISP_LADDER.any { it in ownedTypes }) return false
        return rank == 0
    }

    @JvmStatic
    fun wispRank(type: String?): Int = WISP_LADDER.indexOf(key(type))

    private fun key(type: String?): String = type?.uppercase() ?: ""
}
