/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.data.InventorySnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Named loadout / pet preset decoding. */
final class LoadoutDecoder {
	static final int LOADOUT_SLOT_COUNT = 27;

	private LoadoutDecoder() {
	}

	/**
	 * Named presets under {@code loadout.loadouts}, resolving armor/equipment set ids and pet uniqueId.
	 * Equipped wardrobe sets store pieces in inv_armor / equipment_contents (layout entry is empty).
	 */
	static List<InventorySnapshot.Loadout> parseNamedLoadouts(
		JsonObject member,
		JsonObject loadout,
		InventorySnapshot.AccessoryInfo accessoryInfo
	) {
		Map<Integer, InventorySnapshot.Loadout> byIndex = new LinkedHashMap<>();
		if (loadout == null) {
			return padLoadouts(byIndex);
		}
		JsonObject inventory = InventoryDecodeSupport.obj(member.get("inventory"));
		if (inventory == null) {
			inventory = InventoryDecodeSupport.obj(member.get("inventories"));
		}
		JsonObject named = InventoryDecodeSupport.obj(loadout.get("loadouts"));
		JsonObject armorLayouts = InventoryDecodeSupport.obj(loadout.get("armor"));
		JsonObject equipLayouts = InventoryDecodeSupport.obj(loadout.get("equipment"));
		Map<String, JsonObject> petsByUuid = indexPets(member);

		if (named != null && !named.entrySet().isEmpty()) {
			for (String key : named.keySet()) {
				JsonObject entry = InventoryDecodeSupport.obj(named.get(key));
				if (entry == null) {
					continue;
				}
				Integer index = InventoryDecodeSupport.tryParseInt(key);
				if (index == null) {
					index = byIndex.size();
				}
				String name = entry.has("name") && entry.get("name").isJsonPrimitive()
					? entry.get("name").getAsString()
					: "Loadout " + index;
				Integer armorId = InventoryDecodeSupport.jsonInt(entry, "armor_set_id");
				Integer equipId = InventoryDecodeSupport.jsonInt(entry, "equipment_set_id");
				List<InventorySnapshot.Slot> armor = WardrobeDecoder.readArmorSet(inventory, armorLayouts, armorId, null);
				List<InventorySnapshot.Slot> equip = WardrobeDecoder.readEquipSet(inventory, equipLayouts, equipId, null);
				String power = entry.has("power_stone") && entry.get("power_stone").isJsonPrimitive()
					? entry.get("power_stone").getAsString()
					: "";
				Integer tuneSlot = InventoryDecodeSupport.jsonInt(entry, "tuning_points_slot");
				List<InventorySnapshot.StatPoint> tuning = resolveTuning(accessoryInfo, tuneSlot);
				String petUuid = entry.has("pet") && entry.get("pet").isJsonPrimitive()
					? entry.get("pet").getAsString()
					: "";
				JsonObject petJson = petUuid.isBlank()
					? null
					: petsByUuid.get(InventoryDecodeSupport.normalizeUuid(petUuid));
				InventorySnapshot.Slot petSlot = petSlot(petJson);
				String petLabel = petLabel(petJson);
				byIndex.put(index, new InventorySnapshot.Loadout(
					name, equip, armor, power, tuneSlot, tuning, petSlot, petLabel,
					presetSlot(entry, "mining_core_selected_slot"),
					presetSlot(entry, "foraging_core_selected_slot")
				));
			}
		}

		// Fallback: wardrobe set keys if named presets are empty.
		if (byIndex.isEmpty()) {
			LinkedHashMap<String, Boolean> keys = new LinkedHashMap<>();
			if (armorLayouts != null) {
				for (String key : armorLayouts.keySet()) {
					if (!"equipped_set".equals(key)) {
						keys.put(key, true);
					}
				}
			}
			if (equipLayouts != null) {
				for (String key : equipLayouts.keySet()) {
					if (!"equipped_set".equals(key)) {
						keys.put(key, true);
					}
				}
			}
			for (String key : keys.keySet()) {
				Integer id = InventoryDecodeSupport.tryParseInt(key);
				if (id == null) {
					continue;
				}
				byIndex.put(id, new InventorySnapshot.Loadout(
					"Loadout " + id,
					WardrobeDecoder.readEquipSet(inventory, equipLayouts, id, key),
					WardrobeDecoder.readArmorSet(inventory, armorLayouts, id, key),
					"",
					null,
					List.of(),
					null,
					"",
					null,
					null
				));
			}
		}
		return padLoadouts(byIndex);
	}

	/** Loadouts with gear, pet, power, tuning or a tree preset, in key order (API keys are 1-based). */
	static List<InventorySnapshot.Loadout> padLoadouts(Map<Integer, InventorySnapshot.Loadout> byIndex) {
		List<InventorySnapshot.Loadout> out = new ArrayList<>();
		if (byIndex == null || byIndex.isEmpty()) {
			return out;
		}
		List<Integer> keys = new ArrayList<>(byIndex.keySet());
		keys.sort(null);
		for (Integer key : keys) {
			InventorySnapshot.Loadout loadout = byIndex.get(key);
			if (loadout != null && loadoutHasContent(loadout) && out.size() < LOADOUT_SLOT_COUNT) {
				out.add(loadout);
			}
		}
		return out;
	}

	static Integer presetSlot(JsonObject entry, String key) {
		Integer slot = InventoryDecodeSupport.jsonInt(entry, key);
		return slot != null && slot >= 1 && slot <= 5 ? slot : null;
	}

	static boolean loadoutHasContent(InventorySnapshot.Loadout loadout) {
		if (loadout == null) {
			return false;
		}
		if (!loadout.powerStone().isBlank() || !loadout.petLabel().isBlank()) {
			return true;
		}
		if (loadout.hotmPreset() != null || loadout.hotfPreset() != null) {
			return true;
		}
		if (loadout.pet() != null && !loadout.pet().isEmpty()) {
			return true;
		}
		if (loadout.tuning() != null && !loadout.tuning().isEmpty()) {
			return true;
		}
		for (InventorySnapshot.Slot slot : loadout.armor()) {
			if (slot != null && !slot.isEmpty()) {
				return true;
			}
		}
		for (InventorySnapshot.Slot slot : loadout.equipment()) {
			if (slot != null && !slot.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	static InventorySnapshot.Loadout emptyLoadout(int index) {
		List<InventorySnapshot.Slot> emptyEquip = new ArrayList<>(4);
		List<InventorySnapshot.Slot> emptyArmor = new ArrayList<>(4);
		for (int i = 0; i < 4; i++) {
			emptyEquip.add(null);
			emptyArmor.add(null);
		}
		return new InventorySnapshot.Loadout(
			"Loadout " + (index + 1),
			emptyEquip,
			emptyArmor,
			"",
			null,
			List.of(),
			null,
			"",
			null,
			null
		);
	}

	static List<InventorySnapshot.StatPoint> resolveTuning(InventorySnapshot.AccessoryInfo info, Integer slot) {
		if (info == null || slot == null) {
			return List.of();
		}
		for (InventorySnapshot.TuningTemplate template : info.tunings()) {
			if (template.slot() == slot) {
				return template.stats();
			}
		}
		return List.of();
	}

	static List<InventorySnapshot.StatPoint> readTuningStats(JsonObject slot) {
		if (slot == null) {
			return List.of();
		}
		List<InventorySnapshot.StatPoint> stats = new ArrayList<>();
		for (var entry : List.of(
			Map.entry("health", "HP"),
			Map.entry("defense", "Def"),
			Map.entry("walk_speed", "Spd"),
			Map.entry("strength", "Str"),
			Map.entry("critical_damage", "CD"),
			Map.entry("critical_chance", "CC"),
			Map.entry("attack_speed", "AS"),
			Map.entry("intelligence", "Int")
		)) {
			if (!slot.has(entry.getKey()) || !slot.get(entry.getKey()).isJsonPrimitive()) {
				continue;
			}
			int value = slot.get(entry.getKey()).getAsInt();
			if (value != 0) {
				stats.add(new InventorySnapshot.StatPoint(entry.getKey(), entry.getValue(), value));
			}
		}
		return stats;
	}

	static Map<String, JsonObject> indexPets(JsonObject member) {
		Map<String, JsonObject> map = new LinkedHashMap<>();
		JsonObject petsData = InventoryDecodeSupport.obj(member.get("pets_data"));
		JsonArray pets = null;
		if (petsData != null && petsData.has("pets") && petsData.get("pets").isJsonArray()) {
			pets = petsData.getAsJsonArray("pets");
		} else if (member.has("pets") && member.get("pets").isJsonArray()) {
			pets = member.getAsJsonArray("pets");
		}
		if (pets == null) {
			return map;
		}
		for (JsonElement element : pets) {
			JsonObject pet = InventoryDecodeSupport.obj(element);
			if (pet == null) {
				continue;
			}
			// Loadouts reference uniqueId; some payloads also expose uuid.
			if (pet.has("uniqueId") && pet.get("uniqueId").isJsonPrimitive()) {
				map.put(InventoryDecodeSupport.normalizeUuid(pet.get("uniqueId").getAsString()), pet);
			}
			if (pet.has("uuid") && pet.get("uuid").isJsonPrimitive()) {
				map.put(InventoryDecodeSupport.normalizeUuid(pet.get("uuid").getAsString()), pet);
			}
		}
		return map;
	}

	static InventorySnapshot.Slot petSlot(JsonObject pet) {
		if (pet == null || !pet.has("type")) {
			return null;
		}
		String type = pet.get("type").getAsString();
		String tier = pet.has("tier") && pet.get("tier").isJsonPrimitive() ? pet.get("tier").getAsString() : "COMMON";
		String label = petLabel(pet);
		List<String> lore = new ArrayList<>();
		if (!tier.isBlank()) {
			lore.add(InventoryDecodeSupport.prettyWords(tier));
		}
		if (pet.has("heldItem") && pet.get("heldItem").isJsonPrimitive()) {
			lore.add(pet.get("heldItem").getAsString());
		}
		// NEU pet items are TYPE;tierIndex (COMMON=0 … MYTHIC=5).
		String neuId = type.toUpperCase(Locale.ROOT) + ";" + petTierIndex(tier);
		return new InventorySnapshot.Slot(neuId, 1, lore, label, null, null, null);
	}

	static String petLabel(JsonObject pet) {
		if (pet == null || !pet.has("type")) {
			return "";
		}
		return InventoryDecodeSupport.prettyWords(pet.get("type").getAsString());
	}

	static int petTierIndex(String tier) {
		if (tier == null) {
			return 0;
		}
		return switch (tier.toUpperCase(Locale.ROOT)) {
			case "COMMON" -> 0;
			case "UNCOMMON" -> 1;
			case "RARE" -> 2;
			case "EPIC" -> 3;
			case "LEGENDARY" -> 4;
			case "MYTHIC" -> 5;
			default -> 4;
		};
	}
}
