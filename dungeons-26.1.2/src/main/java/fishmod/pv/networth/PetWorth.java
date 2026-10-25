/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth;

import com.google.gson.JsonObject;
import fishmod.pv.price.ItemPricer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PetWorth {
	private PetWorth() {
	}

	public static int level(JsonObject pet) {
		return levelInfo(pet).level();
	}

	public static LevelInfo levelInfo(JsonObject pet) {
		if (pet == null || !pet.has("type") || !pet.has("tier") || !pet.has("exp")) {
			return new LevelInfo(1, 100, 0, 0, 0, 0, 0f);
		}
		String type = pet.get("type").getAsString();
		String tier = pet.get("tier").getAsString();
		double exp = pet.get("exp").getAsDouble();
		String heldItem = pet.has("heldItem") && !pet.get("heldItem").isJsonNull() ? pet.get("heldItem").getAsString() : null;
		List<String> tiers = NetworthData.tiers();
		int tierIndex = Math.max(0, tiers.indexOf(tier));
		boolean tierBoost = "PET_ITEM_TIER_BOOST".equals(heldItem);
		int boostedIndex = Math.min(tiers.size() - 1, tierIndex + (tierBoost ? 1 : 0));
		return getPetLevel(type, tiers.get(boostedIndex), exp);
	}

	public record LevelInfo(
		int level,
		int maxLevel,
		double exp,
		double xpMax,
		double xpIntoLevel,
		double xpToNext,
		float progressToNext
	) {
	}

	public static boolean isSoulbound(JsonObject pet) {
		if (pet == null) {
			return false;
		}
		if (pet.has("soulbound") && pet.get("soulbound").isJsonPrimitive() && pet.get("soulbound").getAsBoolean()) {
			return true;
		}
		if (!pet.has("type") || pet.get("type").isJsonNull()) {
			return false;
		}
		return NetworthData.soulboundPets().contains(pet.get("type").getAsString());
	}

	public static double value(JsonObject pet) {
		return value(pet, true);
	}

	public static double value(JsonObject pet, boolean includeCosmetics) {
		if (pet == null || !pet.has("type") || !pet.has("tier") || !pet.has("exp")) {
			return 0;
		}
		String type = pet.get("type").getAsString();
		String tier = pet.get("tier").getAsString();
		double exp = pet.get("exp").getAsDouble();
		String skin = pet.has("skin") && !pet.get("skin").isJsonNull() ? pet.get("skin").getAsString() : null;
		String heldItem = pet.has("heldItem") && !pet.get("heldItem").isJsonNull() ? pet.get("heldItem").getAsString() : null;
		int candyUsed = pet.has("candyUsed") ? pet.get("candyUsed").getAsInt() : 0;

		List<String> tiers = NetworthData.tiers();
		int tierIndex = Math.max(0, tiers.indexOf(tier));
		boolean tierBoost = "PET_ITEM_TIER_BOOST".equals(heldItem);
		int boostedIndex = Math.min(tiers.size() - 1, tierIndex + (tierBoost ? 1 : 0));
		String boostedTier = tiers.get(boostedIndex);

		// Price keys use the pet's actual tier (SkyHelper); XP table uses boosted tier.
		String basePetId = tier + "_" + type;
		boolean useSkin = includeCosmetics && skin != null && !skin.isBlank();
		String petId = basePetId + (useSkin ? "_SKINNED_" + skin : "");

		LevelInfo level = getPetLevel(type, boostedTier, exp);
		double lvl1 = maxPrice("LVL_1_" + basePetId, useSkin ? ItemPricer.price("LVL_1_" + petId) : 0);
		double lvl100 = maxPrice("LVL_100_" + basePetId, useSkin ? ItemPricer.price("LVL_100_" + petId) : 0);
		double lvl200 = maxPrice("LVL_200_" + basePetId, useSkin ? ItemPricer.price("LVL_200_" + petId) : 0);

		double basePrice = lvl200 > 0 ? lvl200 : lvl100;
		if (level.level() < 100 && level.xpMax() > 0) {
			double formula = (lvl100 - lvl1) / level.xpMax();
			if (formula != 0) {
				basePrice = formula * level.exp() + lvl1;
			}
		}
		if (level.level() > 100 && level.level() < 200) {
			int over = level.level() - 100;
			if (over != 1) {
				double formula = (lvl200 - lvl100) / 100.0;
				if (formula != 0) {
					basePrice = formula * over + lvl100;
				}
			}
		}

		double mods = 0;
		if (heldItem != null && !heldItem.isBlank()) {
			mods += ItemPricer.price(heldItem) * NetworthData.worth("petItem", 1);
		}
		boolean soulbound = isSoulbound(pet);
		if (useSkin) {
			double skinItem = ItemPricer.price("PET_SKIN_" + skin);
			boolean hasSkinnedMarket = ItemPricer.price("LVL_100_" + petId) > 0
				|| ItemPricer.price("LVL_200_" + petId) > 0;
			if (skinItem > 0) {
				if (soulbound) {
					mods += skinItem * NetworthData.worth("soulboundPetSkins", 0.8);
				} else if (!hasSkinnedMarket) {
					mods += skinItem;
				}
			}
		}
		if (candyUsed > 0 && !NetworthData.blockedCandyPets().contains(type)) {
			double maxPetCandyXp = candyUsed * 1_000_000.0;
			double xpLess = exp - maxPetCandyXp;
			if (xpLess < level.xpMax()) {
				double reduce = basePrice * (1.0 - NetworthData.worth("petCandy", 0.65));
				double maxReduction = level.level() == 100 ? 5_000_000 : 2_500_000;
				mods -= Math.min(reduce, maxReduction);
			}
		}
		return Math.max(0, basePrice + mods);
	}

	private static double maxPrice(String baseKey, double skinned) {
		return Math.max(ItemPricer.price(baseKey), skinned);
	}

	private static LevelInfo getPetLevel(String type, String tierName, double exp) {
		Map<String, Integer> special = NetworthData.specialLevels();
		int maxPetLevel = special.getOrDefault(type, 100);
		Map<String, Integer> offsets = NetworthData.rarityOffset();
		String offsetTier = "BINGO".equals(type) ? "COMMON" : tierName;
		int offset = offsets.getOrDefault(offsetTier, 0);
		List<Integer> all = NetworthData.petLevels();
		int end = Math.min(all.size(), offset + maxPetLevel - 1);
		List<Integer> petLevels = all.subList(Math.min(offset, all.size()), Math.max(Math.min(offset, all.size()), end));

		int level = 1;
		double totalExp = 0;
		double xpIntoLevel = exp;
		int nextCost = petLevels.isEmpty() ? 0 : petLevels.get(0);
		for (int i = 0; i < maxPetLevel && i < petLevels.size(); i++) {
			int cost = petLevels.get(i);
			if (totalExp + cost > exp) {
				xpIntoLevel = exp - totalExp;
				nextCost = cost;
				break;
			}
			totalExp += cost;
			level++;
			xpIntoLevel = 0;
			nextCost = i + 1 < petLevels.size() ? petLevels.get(i + 1) : 0;
		}
		double xpMax = 0;
		for (int v : petLevels) {
			xpMax += v;
		}
		int capped = Math.min(level, maxPetLevel);
		float progress = capped >= maxPetLevel || nextCost <= 0
			? 1f
			: (float) Math.max(0, Math.min(1, xpIntoLevel / nextCost));
		return new LevelInfo(capped, maxPetLevel, exp, xpMax, xpIntoLevel, nextCost, progress);
	}
}
