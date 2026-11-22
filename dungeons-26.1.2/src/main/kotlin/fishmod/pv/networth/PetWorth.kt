/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth

import com.google.gson.JsonObject
import fishmod.pv.price.ItemPricer

object PetWorth {
    data class LevelInfo(
        @get:JvmName("level") val level: Int,
        @get:JvmName("maxLevel") val maxLevel: Int,
        @get:JvmName("exp") val exp: Double,
        @get:JvmName("xpMax") val xpMax: Double,
        @get:JvmName("xpIntoLevel") val xpIntoLevel: Double,
        @get:JvmName("xpToNext") val xpToNext: Double,
        @get:JvmName("progressToNext") val progressToNext: Float,
    )

    @JvmStatic
    fun level(pet: JsonObject?): Int = levelInfo(pet).level

    @JvmStatic
    fun levelInfo(pet: JsonObject?): LevelInfo {
        if (pet == null || !hasCore(pet)) return LevelInfo(1, 100, 0.0, 0.0, 0.0, 0.0, 0f)
        val type = pet.get("type").asString
        val tier = pet.get("tier").asString
        val exp = pet.get("exp").asDouble
        return getPetLevel(type, boostedTier(tier, prim(pet, "heldItem")), exp)
    }

    private fun hasCore(pet: JsonObject): Boolean =
        prim(pet, "type") != null && prim(pet, "tier") != null && prim(pet, "exp") != null

    private fun prim(o: JsonObject, key: String): String? {
        val value = o.get(key)
        return if (value != null && value.isJsonPrimitive) value.asString else null
    }

    private fun boostedTier(tier: String, heldItem: String?): String {
        val tiers = NetworthData.tiers()
        if (tiers.isEmpty()) return tier
        val tierIndex = maxOf(0, tiers.indexOf(tier))
        val tierBoost = heldItem == "PET_ITEM_TIER_BOOST"
        return tiers[minOf(tiers.size - 1, tierIndex + if (tierBoost) 1 else 0)]
    }

    @JvmStatic
    fun isSoulbound(pet: JsonObject?): Boolean {
        if (pet == null) return false
        val soulbound = pet.get("soulbound")
        if (soulbound != null && soulbound.isJsonPrimitive && soulbound.asBoolean) return true
        val type = pet.get("type")
        if (type == null || !type.isJsonPrimitive) return false
        return NetworthData.soulboundPets().contains(type.asString)
    }

    @JvmStatic
    @JvmOverloads
    fun value(pet: JsonObject?, includeCosmetics: Boolean = true): Double {
        if (pet == null || !hasCore(pet)) return 0.0
        val type = pet.get("type").asString
        val tier = pet.get("tier").asString
        val exp = pet.get("exp").asDouble
        val skin = prim(pet, "skin")
        val heldItem = prim(pet, "heldItem")
        val candyUsedEl = pet.get("candyUsed")
        val candyUsed = if (candyUsedEl != null && candyUsedEl.isJsonPrimitive) candyUsedEl.asInt else 0
        val boostedTier = boostedTier(tier, heldItem)

        val basePetId = "${tier}_$type"
        val useSkin = includeCosmetics && !skin.isNullOrBlank()
        val petId = basePetId + if (useSkin) "_SKINNED_$skin" else ""

        val level = getPetLevel(type, boostedTier, exp)
        val lvl1 = maxPrice("LVL_1_$basePetId", if (useSkin) ItemPricer.price("LVL_1_$petId") else 0.0)
        val lvl100 = maxPrice("LVL_100_$basePetId", if (useSkin) ItemPricer.price("LVL_100_$petId") else 0.0)
        val lvl200 = maxPrice("LVL_200_$basePetId", if (useSkin) ItemPricer.price("LVL_200_$petId") else 0.0)

        var basePrice = if (lvl200 > 0) lvl200 else lvl100
        if (level.level < 100 && level.xpMax > 0) {
            val formula = (lvl100 - lvl1) / level.xpMax
            if (formula != 0.0) basePrice = formula * level.exp + lvl1
        }
        if (level.level > 100 && level.level < 200) {
            val over = level.level - 100
            if (over != 1) {
                val formula = (lvl200 - lvl100) / 100.0
                if (formula != 0.0) basePrice = formula * over + lvl100
            }
        }

        var mods = 0.0
        if (!heldItem.isNullOrBlank()) mods += ItemPricer.price(heldItem) * NetworthData.worth("petItem", 1.0)
        val soulbound = isSoulbound(pet)
        if (useSkin) {
            val skinItem = ItemPricer.price("PET_SKIN_$skin")
            val hasSkinnedMarket = ItemPricer.price("LVL_100_$petId") > 0 || ItemPricer.price("LVL_200_$petId") > 0
            if (skinItem > 0) {
                if (soulbound) {
                    mods += skinItem * NetworthData.worth("soulboundPetSkins", 0.8)
                } else if (!hasSkinnedMarket) {
                    mods += skinItem
                }
            }
        }
        if (candyUsed > 0 && !NetworthData.blockedCandyPets().contains(type)) {
            val maxPetCandyXp = candyUsed * 1_000_000.0
            val xpLess = exp - maxPetCandyXp
            if (xpLess < level.xpMax) {
                val reduce = basePrice * (1.0 - NetworthData.worth("petCandy", 0.65))
                val maxReduction = if (level.level == 100) 5_000_000.0 else 2_500_000.0
                mods -= minOf(reduce, maxReduction)
            }
        }
        return maxOf(0.0, basePrice + mods)
    }

    private fun maxPrice(baseKey: String, skinned: Double): Double = maxOf(ItemPricer.price(baseKey), skinned)

    private fun getPetLevel(type: String, tierName: String, exp: Double): LevelInfo {
        val maxPetLevel = NetworthData.specialLevels()[type] ?: 100
        val offsetTier = if (type == "BINGO") "COMMON" else tierName
        val offset = NetworthData.rarityOffset()[offsetTier] ?: 0
        val all = NetworthData.petLevels()
        val end = minOf(all.size, offset + maxPetLevel - 1)
        val from = minOf(offset, all.size)
        val petLevels = all.subList(from, maxOf(from, end))

        var level = 1
        var totalExp = 0.0
        var xpIntoLevel = exp
        var nextCost = petLevels.firstOrNull() ?: 0
        for (i in 0 until minOf(maxPetLevel, petLevels.size)) {
            val cost = petLevels[i]
            if (totalExp + cost > exp) {
                xpIntoLevel = exp - totalExp
                nextCost = cost
                break
            }
            totalExp += cost
            level++
            xpIntoLevel = 0.0
            nextCost = petLevels.getOrNull(i + 1) ?: 0
        }
        val xpMax = petLevels.sumOf { it.toDouble() }
        val capped = minOf(level, maxPetLevel)
        val progress = if (capped >= maxPetLevel || nextCost <= 0) 1f else (xpIntoLevel / nextCost).coerceIn(0.0, 1.0).toFloat()
        return LevelInfo(capped, maxPetLevel, exp, xpMax, xpIntoLevel, nextCost.toDouble(), progress)
    }
}
