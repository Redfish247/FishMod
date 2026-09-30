package fishmod.features.item

import fishmod.features.croesus.CroesusPrices
import fishmod.utils.data.ItemUtil
import fishmod.utils.debug.FishDiag
import net.minecraft.world.item.ItemStack

object ItemValue {

    @JvmStatic
    fun estimate(stack: ItemStack): Double = try {
        estimateInner(stack)
    } catch (e: Exception) {
        FishDiag.fail("ItemValue.1", "value estimate failed for ${stack.hoverName.string}", e)
        0.0
    }

    private fun estimateInner(stack: ItemStack): Double {
        if (stack.isEmpty) return 0.0
        val id = ItemUtil.getId(stack) ?: return 0.0
        val mods = ModifierValue.calc(stack)
        FishDiag.check(!mods.isNaN() && mods >= 0.0, "ItemValue.2") { "modifier value $mods for $id" }
        val tag = stack.fishmodCustomDataTag()
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

            val itemTier = tag.getIntOr("item_tier", 0)

            val extra = LinkedHashMap<String, Any>()
            extra["baseStatBoostPercentage"] = boost
            if (stars > 0) { extra["upgrade_level"] = stars; extra["dungeon_item_level"] = stars }
            if (recomb) extra["rarity_upgrades"] = 1
            if (itemTier > 0) extra["item_tier"] = itemTier

            val cacheKey = buildString {
                append(id).append('|').append(boost).append('|').append(stars).append('|').append(if (recomb) 1 else 0)
                append('|').append(itemTier)
                for ((k, v) in enchantments.toSortedMap()) append('|').append(k).append(':').append(v)
            }

            val dynamic = CroesusPrices.dynamicBinPrice(id, cacheKey, stack.hoverName.string, enchantments, extra)
            FishDiag.check(!dynamic.isNaN(), "ItemValue.3") { "dynamicBinPrice NaN for $cacheKey" }
            if (dynamic > 0.0) return dynamic

            val live = CroesusPrices.qualityBinPrice(id, boost)
            FishDiag.check(!live.isNaN(), "ItemValue.4") { "qualityBinPrice NaN for $id boost=$boost" }
            if (live > 0.0) return live + mods
        }

        val base = CroesusPrices.price(id)
        FishDiag.check(!base.isNaN(), "ItemValue.5") { "CroesusPrices.price NaN for $id" }
        if (base <= 0.0) return 0.0
        return (base + mods) * qualityFallbackMultiplier(boost)
    }

    private fun qualityFallbackMultiplier(boost: Int): Double {
        if (boost <= 0) return 1.0
        return when {
            boost <= 17 -> 0.6
            boost <= 33 -> 0.85
            boost <= 41 -> 1.0
            boost <= 45 -> 1.3
            boost <= 49 -> 1.8
            else -> 2.5
        }
    }
}
