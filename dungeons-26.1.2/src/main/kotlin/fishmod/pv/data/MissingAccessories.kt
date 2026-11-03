/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonElement
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.price.HypixelItemsCache
import fishmod.pv.price.ItemPricer

/**
 * Accessories missing from the bag, SkyHelper style: only the top missing tier of each chain, priced after
 * selling the tier already owned, cheapest coins per MP first.
 * Upgrade chains come from NEU `talisman_upgrades` plus our `accessory_families.json`.
 */
object MissingAccessories {
    /** `soldId` is the owned lower tier replaced by this one, or null. */
    @JvmRecord
    data class Entry(
        val id: String,
        val name: String,
        val tier: String,
        val price: Double,
        val mpGain: Int,
        val soldId: String?,
        val soldName: String?,
        val soldPrice: Double,
        val soulbound: Boolean,
    ) {
        fun priced(): Boolean = price > 0.0

        fun netCost(): Double = maxOf(0.0, price - maxOf(0.0, soldPrice))

        fun coinsPerMp(): Double = if (mpGain > 0) netCost() / mpGain else Double.MAX_VALUE
    }

    /** `costToMax` is the summed net cost of every listed entry. */
    @JvmRecord
    data class Result(val entries: List<Entry>, val costToMax: Double, val mpToMax: Int, val unpricedTop: Int) {
        companion object {
            @JvmStatic
            fun empty(): Result = Result(emptyList(), 0.0, 0, 0)
        }
    }

    private class Candidate(val id: String, val name: String, val tier: String, val soulbound: Boolean)

    private class OwnedTier(val id: String?, val mp: Int)

    @JvmStatic
    fun compute(ownedIds: Collection<String?>): Result {
        val items = HypixelItemsCache.allItems()
        if (items.isEmpty()) return Result.empty()
        val upgrades = NeuRepoCache.talismanUpgrades()
        val ignored = NeuRepoCache.ignoredTalismans()

        val owned = HashSet<String>()
        var ownsAbicase = false
        for (raw in ownedIds) {
            if (raw.isNullOrBlank()) continue
            val id = raw.trim().uppercase()
            owned.add(id)
            if (id.contains("ABICASE")) ownsAbicase = true
        }

        val missing = ArrayList<Candidate>()
        for (item in items) {
            if (str(item.get("category")) != "ACCESSORY") continue
            val id = str(item.get("id")).uppercase()
            if (id.isBlank() || id in ignored || id in owned) continue
            if (id.contains("ABICASE") && ownsAbicase) continue
            // Rift accessories only work in the Rift unless Hypixel marks them transferable.
            if (str(item.get("origin")) == "RIFT" && !bool(item.get("rift_transferrable"))) continue
            if (ownsHigherTier(id, owned, upgrades)) continue
            val tier = tierOf(str(item.get("tier")))
            val name = str(item.get("name"))
            val soulbound = str(item.get("soulbound")).isNotBlank()
            missing.add(Candidate(id, name.ifBlank { id }, tier, soulbound))
        }

        val missingIds = missing.mapTo(HashSet()) { it.id }

        // Keyed by family so ids that share one (abicases) collapse to a single suggestion.
        val byFamily = LinkedHashMap<String, Entry>()
        for (candidate in missing) {
            val higher = upgrades[candidate.id] ?: emptyList()
            if (higher.any { it in missingIds } || hasMissingHigherFamilyMember(candidate.id, missingIds)) continue
            val sold = bestOwnedInChain(candidate.id, owned, upgrades)
            val mpGain = maxOf(0, MagicalPowerCalculator.magicalPower(candidate.id, candidate.tier) - sold.mp)
            // Same-rarity upgrade of something already owned (e.g. Campfire 13 -> 20) adds nothing.
            if (sold.id != null && mpGain == 0) continue
            val price = ItemPricer.price(candidate.id)
            val soldPrice = if (sold.id == null) 0.0 else ItemPricer.price(sold.id)
            val entry = Entry(
                candidate.id, candidate.name, candidate.tier, price, mpGain,
                sold.id, sold.id?.let { nameOf(it) }, soldPrice, candidate.soulbound,
            )
            byFamily.merge(MagicalPowerCalculator.accessoryFamily(candidate.id), entry, ::better)
        }

        val entries = ArrayList(byFamily.values)
        var costToMax = 0.0
        var mpToMax = 0
        var unpricedTop = 0
        for (entry in entries) {
            mpToMax += entry.mpGain
            if (entry.priced()) costToMax += entry.netCost() else unpricedTop++
        }

        entries.sortWith(
            compareBy<Entry> { !it.priced() }
                .thenBy { if (it.priced()) it.coinsPerMp() else 0.0 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
        return Result(entries.toList(), costToMax, mpToMax, unpricedTop)
    }

    private fun better(a: Entry, b: Entry): Entry {
        if (a.mpGain != b.mpGain) return if (a.mpGain > b.mpGain) a else b
        if (a.priced() != b.priced()) return if (a.priced()) a else b
        return if (a.netCost() <= b.netCost()) a else b
    }

    private fun ownsHigherTier(id: String, owned: Set<String>, upgrades: Map<String, List<String>>): Boolean {
        if ((upgrades[id] ?: emptyList()).any { it in owned }) return true
        val family = MagicalPowerCalculator.accessoryFamily(id)
        val rank = MagicalPowerCalculator.accessoryFamilyRank(id)
        return owned.any {
            family == MagicalPowerCalculator.accessoryFamily(it) && MagicalPowerCalculator.accessoryFamilyRank(it) >= rank
        }
    }

    private fun hasMissingHigherFamilyMember(id: String, missingIds: Set<String>): Boolean {
        val family = MagicalPowerCalculator.accessoryFamily(id)
        val rank = MagicalPowerCalculator.accessoryFamilyRank(id)
        return missingIds.any {
            it != id &&
                family == MagicalPowerCalculator.accessoryFamily(it) &&
                MagicalPowerCalculator.accessoryFamilyRank(it) > rank
        }
    }

    /** Best owned lower tier in the same chain; its MP is what the upgrade replaces. */
    private fun bestOwnedInChain(id: String, owned: Set<String>, upgrades: Map<String, List<String>>): OwnedTier {
        val family = MagicalPowerCalculator.accessoryFamily(id)
        var bestId: String? = null
        var best = 0
        for (ownedId in owned) {
            // Owned ids in the same chain are always lower tiers, otherwise this id would not be missing.
            val sameChain = (upgrades[ownedId] ?: emptyList()).contains(id) ||
                family == MagicalPowerCalculator.accessoryFamily(ownedId)
            if (!sameChain) continue
            val def = HypixelItemsCache.get(ownedId)
            val tier = tierOf(if (def == null) "" else str(def.get("tier")))
            val mp = MagicalPowerCalculator.magicalPower(ownedId, tier)
            if (bestId == null || mp > best) {
                bestId = ownedId
                best = mp
            }
        }
        return OwnedTier(bestId, best)
    }

    private fun nameOf(id: String): String {
        val def = HypixelItemsCache.get(id)
        val name = if (def == null) "" else str(def.get("name"))
        return name.ifBlank { id }
    }

    /** Hypixel omits `tier` for common items. */
    private fun tierOf(tier: String?): String = if (tier.isNullOrBlank()) "COMMON" else tier.trim().uppercase()

    private fun str(element: JsonElement?): String =
        if (element != null && element.isJsonPrimitive) element.asString else ""

    private fun bool(element: JsonElement?): Boolean =
        element != null && element.isJsonPrimitive && "true".equals(element.asString, ignoreCase = true)
}
