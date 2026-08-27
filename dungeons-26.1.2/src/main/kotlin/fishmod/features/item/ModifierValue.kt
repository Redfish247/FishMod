package fishmod.features.item

import fishmod.features.croesus.CroesusPrices
import fishmod.utils.networth.NwConstants
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

/**
 * Rough "modifier worth" for the Item Tooltip value line — enchants, potato books, recomb, master
 * stars, gemstones, reforge stone and Art of War, priced through [CroesusPrices]. Not a full
 * networth (no pets/prestige/drill-parts), just the common high-value additions on dungeon gear.
 */
object ModifierValue {

    @JvmStatic
    fun calc(stack: ItemStack): Double {
        val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return 0.0
        var v = 0.0

        // enchantments -> enchanted-book prices
        tag.getCompound("enchantments").ifPresent { ench ->
            for (k in ench.keySet()) {
                val name = k.uppercase()
                val lvl = ench.getIntOr(k, 0)
                if (lvl <= 0) continue
                if (NwConstants.IGNORED_ENCHANTMENTS[name] == lvl) continue
                if (name in NwConstants.STACKING_ENCHANTMENTS) continue
                v += CroesusPrices.price("ENCHANTMENT_${name}_$lvl") * NwConstants.ENCHANTMENTS
            }
        }

        // hot potato / fuming books
        val hpb = tag.getIntOr("hot_potato_count", 0)
        if (hpb > 0) {
            v += CroesusPrices.price("HOT_POTATO_BOOK") * minOf(hpb, 10)
            if (hpb > 10) v += CroesusPrices.price("FUMING_POTATO_BOOK") * (hpb - 10) * NwConstants.FUMING_POTATO_BOOK
        }

        // recombobulator
        if (tag.getIntOr("rarity_upgrades", 0) >= 1) v += CroesusPrices.price("RECOMBOBULATOR_3000") * NwConstants.RECOMBOBULATOR

        // master / dungeon stars past 5
        val stars = maxOf(tag.getIntOr("upgrade_level", 0), tag.getIntOr("dungeon_item_level", 0))
        for (i in 6..stars) {
            val idx = i - 6
            if (idx < NwConstants.MASTER_STARS.size) v += CroesusPrices.price(NwConstants.MASTER_STARS[idx])
        }

        // gemstones
        tag.getCompound("gems").ifPresent { gems ->
            for (k in gems.keySet()) {
                if (k.endsWith("_gem")) continue // slot-unlock marker, not a socketed gem
                val tier = gems.getStringOr(k, "")
                if (tier.isBlank()) continue
                val type = k.substringBefore("_")
                v += CroesusPrices.price("${tier}_${type}_GEM")
            }
        }

        // reforge stone
        NwConstants.REFORGES[tag.getStringOr("modifier", "")]?.let { v += CroesusPrices.price(it) }

        // art of war
        if (tag.getIntOr("art_of_war_count", 0) > 0) v += CroesusPrices.price("THE_ART_OF_WAR") * NwConstants.ART_OF_WAR

        return v
    }
}
