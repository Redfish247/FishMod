/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.data.InventorySnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Wardrobe / equipment-set page decoding. */
final class WardrobeDecoder {
	/** Rank max (18) + Community Center wardrobe/loadout upgrades (9). */
	static final int WARDROBE_SLOT_COUNT = 27;
	/** Hypixel wardrobe UI shows 9 set columns per page. */
	static final int WARDROBE_SETS_PER_PAGE = 9;

	private WardrobeDecoder() {
	}

	record WardrobeSet(List<InventorySnapshot.Slot> slots, boolean equipped) {
	}

	static List<InventorySnapshot.Page> parseWardrobePages(JsonObject inventory, JsonObject loadout) {
		Map<Integer, WardrobeSet> byId = new LinkedHashMap<>();
		JsonObject armorLayouts = loadout == null ? null : InventoryDecodeSupport.obj(loadout.get("armor"));
		Integer equippedId = armorLayouts == null ? null : InventoryDecodeSupport.jsonInt(armorLayouts, "equipped_set");
		if (armorLayouts != null && !armorLayouts.entrySet().isEmpty()) {
			for (String key : armorLayouts.keySet()) {
				if ("equipped_set".equals(key)) {
					continue;
				}
				Integer setId = layoutSetId(armorLayouts.get(key), key);
				if (setId == null) {
					continue;
				}
				List<InventorySnapshot.Slot> slots = readArmorSet(inventory, armorLayouts, setId, key);
				boolean equipped = equippedId != null && equippedId.equals(setId);
				byId.put(setId, new WardrobeSet(slots, equipped));
			}
		}
		if (byId.isEmpty()) {
			List<InventorySnapshot.Slot> flat = InventoryDecodeSupport.toUiSlots(
				InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "wardrobe_contents", 72)
			);
			for (int i = 0, setId = 0; i + 3 < flat.size(); i += 4, setId++) {
				List<InventorySnapshot.Slot> set = new ArrayList<>(4);
				set.add(flat.get(i));
				set.add(flat.get(i + 1));
				set.add(flat.get(i + 2));
				set.add(flat.get(i + 3));
				byId.put(setId, new WardrobeSet(set, false));
			}
		}
		return packWardrobeSetPages(padWardrobeSets(byId), "Wardrobe");
	}

	static List<InventoryDecoder.WardrobePreset> armorPresets(JsonObject inventory, JsonObject loadout) {
		JsonObject armorLayouts = loadout == null ? null : InventoryDecodeSupport.obj(loadout.get("armor"));
		if (armorLayouts == null) {
			return List.of();
		}
		Integer equippedId = InventoryDecodeSupport.jsonInt(armorLayouts, "equipped_set");
		List<InventoryDecoder.WardrobePreset> out = new ArrayList<>();
		for (String key : armorLayouts.keySet()) {
			if ("equipped_set".equals(key)) {
				continue;
			}
			Integer setId = layoutSetId(armorLayouts.get(key), key);
			if (setId == null) {
				continue;
			}
			JsonObject page = findLayout(armorLayouts, setId, key);
			List<InventoryDecoder.Stack> pieces = new ArrayList<>(4);
			for (String slot : List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
				List<InventoryDecoder.Stack> decoded = page == null
					? List.of()
					: InventoryDecodeCache.decodeDataElement(page.get(slot));
				pieces.add(decoded.isEmpty() ? null : decoded.get(0));
			}
			boolean equipped = equippedId != null && equippedId.equals(setId);
			if (equipped && pieces.stream().allMatch(java.util.Objects::isNull)) {
				List<InventoryDecoder.Stack> live = InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "inv_armor", 4);
				pieces = new ArrayList<>(4);
				for (int i = 3; i >= 0; i--) {
					pieces.add(i < live.size() ? live.get(i) : null);
				}
			}
			out.add(new InventoryDecoder.WardrobePreset(setId, equipped, pieces));
		}
		out.sort(java.util.Comparator.comparingInt(InventoryDecoder.WardrobePreset::slot));
		return out;
	}

	static List<InventorySnapshot.Page> parseEquipmentWardrobePages(JsonObject inventory, JsonObject loadout) {
		Map<Integer, WardrobeSet> byId = new LinkedHashMap<>();
		JsonObject equipLayouts = loadout == null ? null : InventoryDecodeSupport.obj(loadout.get("equipment"));
		Integer equippedId = equipLayouts == null ? null : InventoryDecodeSupport.jsonInt(equipLayouts, "equipped_set");
		if (equipLayouts != null) {
			for (String key : equipLayouts.keySet()) {
				if ("equipped_set".equals(key)) {
					continue;
				}
				Integer setId = layoutSetId(equipLayouts.get(key), key);
				if (setId == null) {
					continue;
				}
				List<InventorySnapshot.Slot> slots = readEquipSet(inventory, equipLayouts, setId, key);
				boolean equipped = equippedId != null && equippedId.equals(setId);
				byId.put(setId, new WardrobeSet(slots, equipped));
			}
		}
		if (byId.isEmpty()) {
			List<InventorySnapshot.Slot> equipped = InventoryDecodeSupport.toUiSlots(
				InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "equipment_contents", 4)
			);
			while (equipped.size() < 4) {
				equipped.add(null);
			}
			// Hypixel equipped_set is 1-based when loadout layouts exist; live-only
			// fallback has no layouts so treat missing id as slot 1.
			int idx = equippedId == null
				? 1
				: Math.max(1, Math.min(WARDROBE_SLOT_COUNT, equippedId));
			byId.put(idx, new WardrobeSet(new ArrayList<>(equipped.subList(0, 4)), true));
		}
		return packWardrobeSetPages(padWardrobeSets(byId), "Equipment");
	}

	/** Armor/equipment sets per page, each set a vertical column. */
	static List<InventorySnapshot.Page> packWardrobeSetPages(List<WardrobeSet> sets, String titlePrefix) {
		if (sets.isEmpty()) {
			sets = padWardrobeSets(Map.of());
		}
		List<InventorySnapshot.Page> pages = new ArrayList<>();
		final int perPage = WARDROBE_SETS_PER_PAGE;
		for (int start = 0; start < sets.size(); start += perPage) {
			int end = Math.min(sets.size(), start + perPage);
			int count = end - start;
			List<InventorySnapshot.Slot> slots = new ArrayList<>(4 * count);
			int equippedColumn = -1;
			// Row-major by piece so columns=count draws piece rows with sets as columns.
			for (int piece = 0; piece < 4; piece++) {
				for (int col = 0; col < count; col++) {
					WardrobeSet set = sets.get(start + col);
					slots.add(piece < set.slots().size() ? set.slots().get(piece) : null);
					if (piece == 0 && set.equipped()) {
						equippedColumn = col;
					}
				}
			}
			int pageIndex = start / perPage + 1;
			int totalPages = (sets.size() + perPage - 1) / perPage;
			String title = totalPages <= 1 ? titlePrefix : titlePrefix + " " + pageIndex;
			pages.add(new InventorySnapshot.Page(title, slots, count, equippedColumn));
		}
		return pages;
	}

	/**
	 * Fill every wardrobe slot (including empty / locked) so page totals stay fixed.
	 * Hypixel loadout set ids are 1-based (1..27). Padding 0..26 left a permanent empty
	 * first column. Legacy flat {@code wardrobe_contents} still uses 0-based ids.
	 */
	static List<WardrobeSet> padWardrobeSets(Map<Integer, WardrobeSet> byId) {
		List<WardrobeSet> out = new ArrayList<>(WARDROBE_SLOT_COUNT);
		boolean zeroBased = byId != null && byId.containsKey(0);
		if (zeroBased) {
			for (int i = 0; i < WARDROBE_SLOT_COUNT; i++) {
				WardrobeSet set = byId.get(i);
				out.add(set != null ? set : emptyWardrobeSet());
			}
		} else {
			for (int i = 1; i <= WARDROBE_SLOT_COUNT; i++) {
				WardrobeSet set = byId == null ? null : byId.get(i);
				out.add(set != null ? set : emptyWardrobeSet());
			}
		}
		return out;
	}

	private static WardrobeSet emptyWardrobeSet() {
		List<InventorySnapshot.Slot> empty = new ArrayList<>(4);
		empty.add(null);
		empty.add(null);
		empty.add(null);
		empty.add(null);
		return new WardrobeSet(empty, false);
	}

	static Integer layoutSetId(JsonElement element, String key) {
		JsonObject page = InventoryDecodeSupport.obj(element);
		if (page != null && page.has("id") && page.get("id").isJsonPrimitive()) {
			try {
				return page.get("id").getAsInt();
			} catch (Exception ignored) {
			}
		}
		return InventoryDecodeSupport.tryParseInt(key);
	}

	static List<InventorySnapshot.Slot> readArmorSet(
		JsonObject inventory,
		JsonObject layouts,
		Integer id,
		String keyHint
	) {
		JsonObject page = findLayout(layouts, id, keyHint);
		List<InventorySnapshot.Slot> slots = new ArrayList<>(4);
		for (String slot : List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
			List<InventoryDecoder.Stack> decoded = page == null
				? List.of()
				: InventoryDecodeCache.decodeDataElement(page.get(slot));
			slots.add(decoded.isEmpty() ? null : InventoryDecodeSupport.toUiSlot(decoded.get(0)));
		}
		Integer equippedId = layouts == null ? null : InventoryDecodeSupport.jsonInt(layouts, "equipped_set");
		boolean equipped = id != null && equippedId != null && id.equals(equippedId);
		if (equipped && slots.stream().allMatch(s -> s == null || s.isEmpty())) {
			return liveArmorHelmetToBoots(inventory);
		}
		return slots;
	}

	static List<InventorySnapshot.Slot> readEquipSet(
		JsonObject inventory,
		JsonObject layouts,
		Integer id,
		String keyHint
	) {
		JsonObject page = findLayout(layouts, id, keyHint);
		List<InventorySnapshot.Slot> slots = new ArrayList<>(4);
		for (String slot : List.of("EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4")) {
			List<InventoryDecoder.Stack> decoded = page == null
				? List.of()
				: InventoryDecodeCache.decodeDataElement(page.get(slot));
			slots.add(decoded.isEmpty() ? null : InventoryDecodeSupport.toUiSlot(decoded.get(0)));
		}
		Integer equippedId = layouts == null ? null : InventoryDecodeSupport.jsonInt(layouts, "equipped_set");
		boolean equipped = id != null && equippedId != null && id.equals(equippedId);
		if (equipped && slots.stream().allMatch(s -> s == null || s.isEmpty())) {
			List<InventorySnapshot.Slot> live = InventoryDecodeSupport.toUiSlots(
				InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "equipment_contents", 4)
			);
			while (live.size() < 4) {
				live.add(null);
			}
			return new ArrayList<>(live.subList(0, 4));
		}
		return slots;
	}

	/** inv_armor is boots→helmet; loadout/wardrobe columns are helmet→boots. */
	static List<InventorySnapshot.Slot> liveArmorHelmetToBoots(JsonObject inventory) {
		List<InventorySnapshot.Slot> raw = InventoryDecodeSupport.toUiSlots(
			InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "inv_armor", 4)
		);
		while (raw.size() < 4) {
			raw.add(null);
		}
		List<InventorySnapshot.Slot> out = new ArrayList<>(4);
		out.add(raw.get(3));
		out.add(raw.get(2));
		out.add(raw.get(1));
		out.add(raw.get(0));
		return out;
	}

	static JsonObject findLayout(JsonObject layouts, Integer id, String keyHint) {
		if (layouts == null) {
			return null;
		}
		if (keyHint != null && layouts.has(keyHint)) {
			return InventoryDecodeSupport.obj(layouts.get(keyHint));
		}
		if (id != null) {
			if (layouts.has(String.valueOf(id))) {
				return InventoryDecodeSupport.obj(layouts.get(String.valueOf(id)));
			}
			for (var entry : layouts.entrySet()) {
				JsonObject page = InventoryDecodeSupport.obj(entry.getValue());
				if (page != null && page.has("id") && page.get("id").isJsonPrimitive() && page.get("id").getAsInt() == id) {
					return page;
				}
			}
		}
		return null;
	}
}
