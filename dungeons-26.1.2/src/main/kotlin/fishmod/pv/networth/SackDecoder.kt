/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth

import com.google.gson.JsonObject
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.neu.NeuRepoCache
import java.util.Locale
import net.minecraft.nbt.CompoundTag

/** Sack counts and sack UI page decoding. */
internal object SackDecoder {
    /** Page for counted items no NEU sack lists. */
    const val UNSORTED_PAGE = "Unsorted"

    /** NEU lists Rough/Flawed/Fine only - add Flawless/Perfect for each gem type. */
    @JvmField
    val GEMSTONE_TYPES = arrayOf(
        "RUBY", "JADE", "SAPPHIRE", "AMETHYST", "AMBER", "TOPAZ", "JASPER", "OPAL",
        "AQUAMARINE", "CITRINE", "ONYX", "PERIDOT",
    )

    private val GEM_TIERS = arrayOf("ROUGH_", "FLAWED_", "FINE_", "FLAWLESS_", "PERFECT_")
    private val SIZE_PREFIXES = arrayOf("SMALL_", "MEDIUM_", "LARGE_")

    @JvmStatic
    fun parseSacks(member: JsonObject, inventory: JsonObject?): List<InventoryDecoder.Stack> {
        val counts = sackCountsObject(member, inventory) ?: return emptyList()
        val out = ArrayList<InventoryDecoder.Stack>()
        for ((key, value) in counts.entrySet()) {
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) continue
            val amountLong = maxOf(0L, value.asDouble.toLong())
            if (amountLong <= 0L) continue
            val amount = if (amountLong > Int.MAX_VALUE) Int.MAX_VALUE else amountLong.toInt()
            out.add(InventoryDecoder.Stack(key, amount, CompoundTag(), emptyList(), false))
        }
        return out
    }

    @JvmStatic
    fun sackCountsObject(member: JsonObject, inventory: JsonObject?): JsonObject? =
        InventoryDecodeSupport.obj(member.get("sacks_counts"))
            ?: inventory?.let { InventoryDecodeSupport.obj(it.get("sacks_counts")) }

    /**
     * One page per owned sack (from `bag_contents.sacks_bag`) with every holdable item (count may be 0).
     * NEU's Rune sack has an empty contents list - real runes go on `Rune Sack`.
     * Gemstone sack is expanded with Flawless/Perfect (NEU only lists Rough-Fine).
     */
    @JvmStatic
    fun parseSackPages(member: JsonObject, inventory: JsonObject?): List<InventorySnapshot.Page> {
        val counts = LinkedHashMap<String, Int>()
        sackCountsObject(member, inventory)?.entrySet()?.forEach { (key, value) ->
            // Same number check and int clamp as parseSacks; a non-numeric value would throw.
            if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                counts[key.uppercase(Locale.ROOT)] = maxOf(0.0, minOf(Int.MAX_VALUE.toDouble(), value.asDouble)).toInt()
            }
        }
        val defs = NeuRepoCache.sackDefinitions()
        val sackItems = LinkedHashMap<String, String?>()
        for (name in defs.keys) sackItems[name] = NeuRepoCache.sackItemId(name)
        return buildSackPages(defs, sackItems, counts, ownedSackKeys(inventory))
    }

    /**
     * Owned sack keys (size prefix stripped), or null when unknown. With the inventory API off Hypixel
     * still sends `sacks_bag`, but empty, so it can't be trusted then.
     */
    @JvmStatic
    fun ownedSackKeys(inventory: JsonObject?): Set<String>? {
        if (inventory == null || !InventoryDecoder.inventoryApiEnabled(inventory)) return null
        val bags = InventoryDecodeSupport.obj(inventory.get("bag_contents")) ?: return null
        val sacksBag = bags.get("sacks_bag")
        if (sacksBag == null || sacksBag.isJsonNull) return null
        val owned = HashSet<String>()
        for (stack in InventoryDecodeCache.decodeField(bags, "sacks_bag")) {
            if (stack.id.isNotBlank()) owned.add(sackKey(stack.id))
        }
        return owned
    }

    /** Small/Medium/Large variants are one sack type; NEU only names the Large item. */
    @JvmStatic
    fun sackKey(itemId: String?): String {
        if (itemId == null) return ""
        val key = itemId.trim().uppercase(Locale.ROOT)
        val prefix = SIZE_PREFIXES.firstOrNull { key.startsWith(it) } ?: return key
        return key.substring(prefix.length)
    }

    /** [owned] comes from [ownedSackKeys]; null means unknown, so only sacks holding something are shown. */
    @JvmStatic
    fun buildSackPages(
        defs: Map<String, List<String>?>,
        sackItems: Map<String, String?>?,
        counts: Map<String, Int?>,
        owned: Set<String>?,
    ): List<InventorySnapshot.Page> {
        val contentsByName = LinkedHashMap<String, List<String>>()
        for ((sackName, value) in defs) {
            var contents = value ?: emptyList()
            if (sackName.equals("Gemstone", ignoreCase = true)) contents = expandGemstoneContents(contents)
            contentsByName[sackName] = contents
        }
        if (contentsByName.keys.none { it.equals("Gemstone", ignoreCase = true) }) {
            contentsByName["Gemstone"] = expandGemstoneContents(emptyList())
        }
        // NEU's Rune sack has no contents list, so the repo loader drops it; runes would fall into Unsorted.
        if (contentsByName.keys.none { it.equals("Rune", ignoreCase = true) }) {
            contentsByName["Rune"] = emptyList()
        }

        val gemExtras = ArrayList<InventorySnapshot.Slot?>()
        val runes = ArrayList<InventorySnapshot.Slot?>()
        val other = ArrayList<InventorySnapshot.Slot?>()
        val neuItems = HashSet<String>()
        for (contents in contentsByName.values) {
            for (itemId in contents) claimSackId(neuItems, itemId)
        }
        for ((id, amount) in counts) {
            if (isClaimedSackId(neuItems, id) || amount == null || amount <= 0) continue
            val slot = InventoryDecodeSupport.toUiSlot(InventoryDecoder.Stack(id, amount, CompoundTag(), emptyList(), false))
            when {
                isGemstoneSackId(id) -> gemExtras.add(slot)
                isRuneSackId(id) -> runes.add(slot)
                else -> other.add(slot)
            }
        }

        val visible = HashSet<String>()
        val listed = HashSet<String>()
        for ((sackName, contents) in contentsByName) {
            val show = if (owned == null) {
                holdsSomething(sackName, contents, counts, runes, gemExtras, emptySet())
            } else {
                owned.contains(sackKey(sackItemFor(sackItems, sackName)))
            }
            if (show) {
                visible.add(sackName)
                contents.forEach { claimSackId(listed, it) }
            }
        }
        // Ownership comes from sacks_bag, which can miss sacks (e.g. the Rune Sack). A sack still gets its own
        // page when it holds something no visible sack lists, instead of dumping that into a catch-all page.
        if (owned != null) {
            for ((sackName, contents) in contentsByName) {
                if (sackName !in visible && holdsSomething(sackName, contents, counts, runes, gemExtras, listed)) {
                    visible.add(sackName)
                    contents.forEach { claimSackId(listed, it) }
                }
            }
        }
        val shown = contentsByName.keys.filterTo(LinkedHashSet()) { it in visible }

        // Events overlaps Winter/Spooky/Fishing etc. sharing one count, so only list what no other owned sack shows.
        val elsewhere = HashSet<String>()
        for (sackName in shown) {
            if (!sackName.equals("Events", ignoreCase = true)) {
                contentsByName.getValue(sackName).forEach { claimSackId(elsewhere, it) }
            }
        }

        val pages = ArrayList<InventorySnapshot.Page>()
        for (sackName in shown) {
            val slots = ArrayList<InventorySnapshot.Slot?>()
            if (sackName.equals("Rune", ignoreCase = true)) {
                slots.addAll(runes)
                runes.clear()
            } else {
                val events = sackName.equals("Events", ignoreCase = true)
                for (itemId in contentsByName.getValue(sackName)) {
                    if (events && isClaimedSackId(elsewhere, itemId)) continue
                    slots.add(InventoryDecodeSupport.toUiSlot(
                        InventoryDecoder.Stack(itemId, sackAmount(counts, itemId), CompoundTag(), emptyList(), false)
                    ))
                }
                if (sackName.equals("Gemstone", ignoreCase = true)) {
                    slots.addAll(gemExtras)
                    gemExtras.clear()
                }
            }
            pages.add(InventorySnapshot.Page(sackName, slots, 9))
        }

        other.addAll(gemExtras)
        other.addAll(runes)
        if (other.isNotEmpty()) pages.add(InventorySnapshot.Page(UNSORTED_PAGE, other, 9))
        return pages
    }

    private fun holdsSomething(
        sackName: String,
        contents: List<String>,
        counts: Map<String, Int?>,
        runes: List<InventorySnapshot.Slot?>,
        gemExtras: List<InventorySnapshot.Slot?>,
        alreadyListed: Set<String>,
    ): Boolean {
        if (sackName.equals("Rune", ignoreCase = true) && runes.isNotEmpty()) return true
        if (sackName.equals("Gemstone", ignoreCase = true) && gemExtras.isNotEmpty()) return true
        return contents.any { sackAmount(counts, it) > 0 && !isClaimedSackId(alreadyListed, it) }
    }

    private fun sackItemFor(sackItems: Map<String, String?>?, sackName: String): String {
        val item = sackItems?.get(sackName)
        if (!item.isNullOrBlank()) return item
        return sackName.uppercase(Locale.ROOT).replace(' ', '_') + "_SACK"
    }

    @JvmStatic
    fun expandGemstoneContents(contents: List<String?>?): List<String> {
        val seenGems = LinkedHashSet<String>()
        contents?.forEach { itemId ->
            if (itemId.isNullOrBlank()) return@forEach
            val upper = itemId.uppercase(Locale.ROOT)
            val tier = GEM_TIERS.firstOrNull { upper.startsWith(it) && upper.endsWith("_GEM") } ?: return@forEach
            seenGems.add(upper.substring(tier.length, upper.length - "_GEM".length))
        }
        // Always include every known gem (incl. Citrine) so Perfect tier rows stay complete.
        seenGems.addAll(GEMSTONE_TYPES)
        return seenGems.flatMap { gem -> GEM_TIERS.map { tier -> tier + gem + "_GEM" } }
    }

    @JvmStatic
    fun isGemstoneSackId(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        val key = id.uppercase(Locale.ROOT)
        return key.endsWith("_GEM") || key.endsWith("_GEMSTONE")
    }

    @JvmStatic
    fun isRuneSackId(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        val key = id.uppercase(Locale.ROOT)
        return key.startsWith("RUNE_") || key.contains("_RUNE")
    }

    @JvmStatic
    fun sackAmount(counts: Map<String, Int?>, itemId: String?): Int {
        if (itemId == null) return 0
        val key = itemId.uppercase(Locale.ROOT)
        return counts[key] ?: counts[key.replace('-', ':')] ?: counts[key.replace(':', '-')] ?: 0
    }

    @JvmStatic
    fun claimSackId(claimed: MutableSet<String>, id: String?) {
        if (id == null) return
        val key = id.uppercase(Locale.ROOT)
        claimed.add(key)
        claimed.add(key.replace('-', ':'))
        claimed.add(key.replace(':', '-'))
    }

    @JvmStatic
    fun isClaimedSackId(claimed: Set<String>, id: String?): Boolean {
        if (id == null) return false
        val key = id.uppercase(Locale.ROOT)
        return key in claimed || key.replace('-', ':') in claimed || key.replace(':', '-') in claimed
    }
}
