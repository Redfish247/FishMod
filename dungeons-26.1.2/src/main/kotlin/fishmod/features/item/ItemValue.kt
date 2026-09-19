package fishmod.features.item

import fishmod.features.croesus.CroesusPrices
import fishmod.utils.data.ItemUtil
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

/**
 * What an item is actually worth. Shared by [ItemPriceTooltip] and [AuctionPriceAutofill] so both
 * agree on the number. Three tiers, cheapest-accurate first:
 *
 * 1. [CroesusPrices.dynamicBinPrice] — for items with a dungeon quality roll, the live lowest BIN
 *    among auctions matching this item's exact quality/stars/recomb/enchants. Class-specific
 *    dungeon gear (Skeleton Master armor, Necron's pieces, ...) swings 10-100x across star count
 *    alone, so this is the only tier that isn't wildly off for those.
 * 2. [CroesusPrices.qualityBinPrice] — live lowest BIN at just the quality roll (ignores
 *    stars/enchants), used while tier 1 is still loading (both are async + cached).
 * 3. bulk bazaar/LBin (base) + [ModifierValue] mods, scaled by a rough quality-bracket multiplier —
 *    used before anything live has loaded, or for items with no quality roll at all.
 */
object ItemValue {

    @JvmStatic
    fun estimate(stack: ItemStack): Double {
        if (stack.isEmpty) return 0.0
        val id = ItemUtil.getId(stack) ?: return 0.0
        val mods = ModifierValue.calc(stack)
        val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
        val boost = tag?.getInt("baseStatBoostPercentage")?.orElse(0) ?: 0

        if (boost > 0 && tag != null) {
            val stars = maxOf(tag.getIntOr("upgrade_level", 0), tag.getIntOr("dungeon_item_level", 0))
            val recomb = tag.getIntOr("rarity_upgrades", 0) >= 1

            val enchantments = LinkedHashMap<String, Int>()
            tag.getCompound("enchantments").ifPresent { ench ->
                for (k in ench.keySet()) {
                    val lvl = ench.getIntOr(k, 0)
                    if (lvl > 0) enchantments[k] = lvl
                }
            }

            // item_tier (combined with dungeon_skill_req) is what ItemQualityTooltip's floor label
            // (M7/F7/M3/...) is derived from — the SAME 50% quality roll from a higher master-mode
            // floor carries a much higher item_tier and sells for far more than a low-floor piece at
            // the same quality, so this has to be matched too, not just the quality roll itself.
            val itemTier = tag.getIntOr("item_tier", 0)

            val extra = LinkedHashMap<String, Any>()
            extra["baseStatBoostPercentage"] = boost
            if (stars > 0) { extra["upgrade_level"] = stars; extra["dungeon_item_level"] = stars }
            if (recomb) extra["rarity_upgrades"] = 1
            if (itemTier > 0) extra["item_tier"] = itemTier

            // Reforge turned out NOT to move price for this item class — buyers treat differently
            // reforged pieces at the same quality/tier as interchangeable, so it's deliberately left
            // out of the match (an earlier version filtered by it and overshot real listings badly).
            val cacheKey = buildString {
                append(id).append('|').append(boost).append('|').append(stars).append('|').append(if (recomb) 1 else 0)
                append('|').append(itemTier)
                for ((k, v) in enchantments.toSortedMap()) append('|').append(k).append(':').append(v)
            }

            val dynamic = CroesusPrices.dynamicBinPrice(id, cacheKey, stack.hoverName.string, enchantments, extra)
            if (dynamic > 0.0) return dynamic

            val live = CroesusPrices.qualityBinPrice(id, boost)
            if (live > 0.0) return live + mods
        }

        val base = CroesusPrices.price(id)
        if (base <= 0.0) return 0.0
        // nothing live has loaded yet (async, first lookup after opening the item/menu) — fall back
        // to a rough bracket estimate rather than the misleading unscaled bulk price
        return (base + mods) * qualityFallbackMultiplier(boost)
    }

    /** Same 17/33/49 buckets [ItemQualityTooltip] colors by — a placeholder until live data (above)
     *  finishes loading, not a substitute for it. */
    private fun qualityFallbackMultiplier(boost: Int): Double {
        if (boost <= 0) return 1.0
        return when {
            boost <= 17 -> 0.6
            boost <= 33 -> 0.85
            boost <= 41 -> 1.0
            boost <= 45 -> 1.3
            boost <= 49 -> 1.8
            else -> 2.5 // 50% - max roll
        }
    }
}
