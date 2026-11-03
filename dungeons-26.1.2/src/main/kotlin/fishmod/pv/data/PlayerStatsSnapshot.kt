/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import java.util.OptionalDouble

/** Computed overview combat/fortune stats from profile + equipped gear lore. */
class PlayerStatsSnapshot private constructor(
    entries: List<Entry>?,
    /** Null until full inventories have been parsed. */
    @get:JvmName("farmingFortune") val farmingFortune: FarmingFortuneEstimate?,
) {
    class Entry(id: String?, label: String?, value: OptionalDouble?) {
        @get:JvmName("id") val id: String = id ?: ""
        @get:JvmName("label") val label: String = label ?: ""
        @get:JvmName("value") val value: OptionalDouble = value ?: OptionalDouble.empty()

        fun present(): Boolean = value.isPresent
    }

    @get:JvmName("entries")
    val entries: List<Entry> = if (entries.isNullOrEmpty()) emptyList() else entries.toList()

    constructor(entries: List<Entry>?) : this(entries, null)

    fun withFarmingFortune(estimate: FarmingFortuneEstimate?): PlayerStatsSnapshot = PlayerStatsSnapshot(entries, estimate)

    fun isEmpty(): Boolean = entries.none { it.present() }

    companion object {
        @JvmStatic
        fun empty(): PlayerStatsSnapshot = PlayerStatsSnapshot(emptyList())
    }
}
