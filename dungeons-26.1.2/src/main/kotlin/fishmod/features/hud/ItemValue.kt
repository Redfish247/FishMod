package fishmod.features.hud

import fishmod.features.dungeon.trackers.CroesusPrices
import fishmod.utils.data.ItemTools
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.ItemWorth
import fishmod.pv.price.ItemPricer
import fishmod.utils.debug.FishDiag
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

object ItemValue {

    @JvmStatic
    fun estimate(stack: ItemStack): Double = try {
        maxOf(estimateInner(stack), fullWorth(stack))
    } catch (e: Exception) {
        FishDiag.fail("ItemValue.1", "value estimate failed for ${stack.hoverName.string}", e)
        0.0
    }

    private fun petValue(stack: ItemStack): Double {
        val raw = stack.fishmodCustomDataTag()?.getString("petInfo")?.orElse("") ?: return 0.0
        if (raw.isEmpty()) return 0.0
        val o = com.google.gson.JsonParser.parseString(raw).asJsonObject
        return fishmod.pv.networth.PetWorth.value(o, true)
    }

    // Full networth calc as a floor: covers drill parts, gem slots, coatings etc. the quick path misses.
    private fun fullWorth(stack: ItemStack): Double {
        if (stack.isEmpty || !ItemPricer.isReady()) return 0.0
        val id = ItemTools.getId(stack) ?: return 0.0
        if (id == "PET" || id == "ENCHANTED_BOOK") return 0.0
        val tag = stack.fishmodCustomDataTag() ?: return 0.0
        val lore = stack.get(DataComponents.LORE)?.lines()?.map { it.string } ?: emptyList()
        val v = ItemWorth.value(InventoryDecoder.Stack(id, 1, tag, lore, false, stack.hoverName.string))
        FishDiag.check(!v.isNaN() && !v.isInfinite(), "ItemValue.6") { "ItemWorth.value returned $v for $id" }
        return if (v.isNaN() || v.isInfinite()) 0.0 else v
    }

    // Single-enchant books price as ENCHANTMENT_<NAME>_<LVL>; everything else uses its SkyBlock id.
    @JvmStatic
    fun priceId(stack: ItemStack): String? {
        val id = ItemTools.getId(stack) ?: return null
        if (id != "ENCHANTED_BOOK") return id
        val ench = stack.fishmodCustomDataTag()?.getCompound("enchantments")?.orElse(null) ?: return id
        val key = ench.keySet().singleOrNull() ?: return id
        val lvl = ench.getIntOr(key, 0).takeIf { it > 0 } ?: return id
        return "ENCHANTMENT_${key.uppercase()}_$lvl"
    }

    private fun estimateInner(stack: ItemStack): Double {
        if (stack.isEmpty) return 0.0
        val id = priceId(stack) ?: return 0.0
        if (id == "PET") return petValue(stack)
        if (id.startsWith("ENCHANTMENT_")) return CroesusPrices.price(id)
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
            val cap = sanityCap(id, mods, boost)
            if (dynamic > 0.0 && dynamic <= cap) return dynamic

            val live = CroesusPrices.qualityBinPrice(id, boost)
            FishDiag.check(!live.isNaN(), "ItemValue.4") { "qualityBinPrice NaN for $id boost=$boost" }
            if (live > 0.0 && live + mods <= cap) return live + mods
            if (dynamic > cap || live + mods > cap) fishmod.utils.debug.FishDebug.LOGGER.debug("[ItemValue] {} BIN outlier dyn={} live={} cap={}", id, dynamic, live, cap)
        }

        val base = CroesusPrices.price(id)
        FishDiag.check(!base.isNaN(), "ItemValue.5") { "CroesusPrices.price NaN for $id" }
        if (base <= 0.0) return 0.0
        return (base + mods) * qualityFallbackMultiplier(boost)
    }

    // Rare exact-match BINs can be wildly overpriced; reject anything far above clean lowest BIN + upgrades.
    private fun sanityCap(id: String, mods: Double, boost: Int): Double {
        val base = CroesusPrices.cachedPrice(id)
        if (base <= 0.0) return Double.MAX_VALUE
        return (base + mods) * qualityFallbackMultiplier(boost) * 2.0
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
