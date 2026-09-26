package fishmod.features.item

import fishmod.features.croesus.CroesusPrices
import fishmod.utils.networth.NwConstants
import net.minecraft.world.item.ItemStack

object ModifierValue {

    @JvmStatic
    fun calc(stack: ItemStack): Double {
        val tag = stack.fishmodCustomDataTag() ?: return 0.0
        var v = 0.0

        tag.getCompound("enchantments").ifPresent { ench ->
            for (k in ench.keySet()) {
                val name = k.uppercase()
                val lvl = ench.getIntOr(k, 0)
                if (lvl <= 0) continue
                if (NwConstants.IGNORED_ENCHANTMENTS[name] == lvl) continue
                if (name in NwConstants.STACKING_ENCHANTMENTS) continue
                v += enchPrice(name, lvl) * NwConstants.ENCHANTMENTS
            }
        }

        tag.getList("ability_scroll").ifPresent { scrolls ->
            for (i in scrolls.indices) {
                val s = scrolls.getStringOr(i, "")
                if (s.isNotBlank()) v += CroesusPrices.price(s) * NwConstants.NECRON_BLADE_SCROLL
            }
        }

        val hpb = tag.getIntOr("hot_potato_count", 0)
        if (hpb > 0) {
            v += CroesusPrices.price("HOT_POTATO_BOOK") * minOf(hpb, 10)
            if (hpb > 10) v += CroesusPrices.price("FUMING_POTATO_BOOK") * (hpb - 10) * NwConstants.FUMING_POTATO_BOOK
        }

        if (tag.getIntOr("rarity_upgrades", 0) >= 1) v += CroesusPrices.price("RECOMBOBULATOR_3000") * NwConstants.RECOMBOBULATOR

        val stars = maxOf(tag.getIntOr("upgrade_level", 0), tag.getIntOr("dungeon_item_level", 0))
        for (i in 6..stars) {
            val idx = i - 6
            if (idx < NwConstants.MASTER_STARS.size) v += CroesusPrices.price(NwConstants.MASTER_STARS[idx])
        }

        tag.getCompound("gems").ifPresent { gems ->
            for (k in gems.keySet()) {
                if (k.endsWith("_gem")) continue
                val tier = gems.getStringOr(k, "")
                if (tier.isBlank()) continue
                val type = k.substringBefore("_")
                v += CroesusPrices.price("${tier}_${type}_GEM")
            }
        }

        NwConstants.REFORGES[tag.getStringOr("modifier", "")]?.let { v += CroesusPrices.price(it) }

        if (tag.getIntOr("art_of_war_count", 0) > 0) v += CroesusPrices.price("THE_ART_OF_WAR") * NwConstants.ART_OF_WAR

        return v
    }

    private fun enchPrice(name: String, lvl: Int): Double {
        val exact = CroesusPrices.price("ENCHANTMENT_${name}_$lvl")
        if (exact > 0.0) return exact
        for (l in lvl - 1 downTo 1) {
            val p = CroesusPrices.price("ENCHANTMENT_${name}_$l")
            if (p > 0.0) return if (name == "CHIMERA") p else p * (1 shl (lvl - l))
        }
        return 0.0
    }
}
