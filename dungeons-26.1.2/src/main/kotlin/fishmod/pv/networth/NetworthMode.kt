/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth

enum class NetworthMode(
    @get:JvmName("display") val display: String,
    @get:JvmName("includeCosmetics") val includeCosmetics: Boolean,
    @get:JvmName("soulboundFilter") val soulboundFilter: SoulboundFilter,
) {
    NORMAL("Normal", true, SoulboundFilter.ALL),
    NON_COSMETIC("Non-cosmetic", false, SoulboundFilter.ALL),
    UNSOULBOUND("Unsoulbound", true, SoulboundFilter.ONLY_UNSOULBOUND),
    UNSOULBOUND_NON_COSMETIC("Unsoulbound Non-Cosmetic", false, SoulboundFilter.ONLY_UNSOULBOUND);

    enum class SoulboundFilter {
        ALL,
        ONLY_SOULBOUND,
        ONLY_UNSOULBOUND;

        fun accepts(soulbound: Boolean): Boolean = when (this) {
            ALL -> true
            ONLY_SOULBOUND -> soulbound
            ONLY_UNSOULBOUND -> !soulbound
        }

        /** Purse / bank are liquid coins - only in full and un-soulbound views. */
        fun includesLiquid(): Boolean = this != ONLY_SOULBOUND
    }

    fun next(): NetworthMode = entries[(ordinal + 1) % entries.size]

    fun prev(): NetworthMode = entries[(ordinal + entries.size - 1) % entries.size]
}
