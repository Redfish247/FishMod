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
import fishmod.pv.data.MagicalPowerCalculator;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;

/** Decodes Hypixel inventory base64 → SkyBlock item stacks for networth. */
public final class InventoryDecoder {
	public record Stack(
		String id,
		int count,
		CompoundTag extraAttributes,
		List<String> lore,
		boolean soulbound,
		String displayName,
		Integer dyeColor,
		String skullValue,
		String skullSignature
	) {
		public Stack(String id, int count, CompoundTag extraAttributes, List<String> lore, boolean soulbound) {
			this(id, count, extraAttributes, lore, soulbound, null, null, null, null);
		}
	}

	/** One loadout wardrobe set; {@code pieces} is helmet to boots with nulls for empty slots. */
	public record WardrobePreset(int slot, boolean equipped, List<Stack> pieces) {
	}

	public record RiftInventories(
		InventorySnapshot.Page inventory,
		List<InventorySnapshot.Slot> equipment,
		List<InventorySnapshot.Slot> armor,
		List<InventorySnapshot.Page> enderPages
	) {
	}

	private InventoryDecoder() {
	}

	/**
	 * Runs work with a thread-local NBT decode cache. Nested calls reuse the outer cache.
	 * Decoded lists are treated as immutable by callers.
	 */
	public static <T> T withSharedDecode(Supplier<T> work) {
		return InventoryDecodeCache.withSharedDecode(work);
	}

	public static void withSharedDecode(Runnable work) {
		InventoryDecodeCache.withSharedDecode(work);
	}

	/**
	 * Home-first-paint gear only: armor, equipment, accessories.
	 * Avoids full inventory / backpack / wardrobe NBT work.
	 */
	public static Map<String, List<Stack>> parseHomeGear(JsonObject member) {
		Map<String, List<Stack>> categories = new LinkedHashMap<>();
		if (member == null) {
			return categories;
		}
		JsonObject inventory = InventoryDecodeSupport.obj(member.get("inventory"));
		if (inventory == null) {
			inventory = InventoryDecodeSupport.obj(member.get("inventories"));
		}
		JsonObject bags = inventory == null ? null : InventoryDecodeSupport.obj(inventory.get("bag_contents"));
		InventoryDecodeCache.put(categories, "armor", InventoryDecodeCache.decodeField(inventory, "inv_armor"));
		InventoryDecodeCache.put(categories, "equipment", InventoryDecodeCache.decodeField(inventory, "equipment_contents"));
		InventoryDecodeCache.put(categories, "accessories", bags == null ? List.of() : InventoryDecodeCache.decodeField(bags, "talisman_bag"));
		return categories;
	}

	/**
	 * Accessory bag plus armor/inventory accessories that still grant Magical Power.
	 * Bag slots are always included; other locations need an ACCESSORY lore line.
	 */
	public static List<Stack> accessoryCandidates(JsonObject member) {
		List<Stack> out = new ArrayList<>();
		if (member == null) {
			return out;
		}
		Map<String, List<Stack>> home = parseHomeGear(member);
		out.addAll(home.getOrDefault("accessories", List.of()));
		for (Stack stack : home.getOrDefault("armor", List.of())) {
			if (looksLikeAccessory(stack)) {
				out.add(stack);
			}
		}
		// Inventory accessories don't count: Hypixel's Magical Power only reads the bag.
		return out;
	}

	private static boolean looksLikeAccessory(Stack stack) {
		if (stack == null || stack.id() == null || stack.id().isBlank()) {
			return false;
		}
		List<String> lore = stack.lore();
		if (lore == null || lore.isEmpty()) {
			return false;
		}
		String last = lore.get(lore.size() - 1);
		return last != null && (last.contains("ACCESSORY") || last.contains("HATCESSORY"));
	}

	public static Map<String, List<Stack>> parseCategories(JsonObject member, JsonObject museumMember) {
		Map<String, List<Stack>> categories = new LinkedHashMap<>();
		JsonObject inventory = InventoryDecodeSupport.obj(member.get("inventory"));
		if (inventory == null) {
			inventory = InventoryDecodeSupport.obj(member.get("inventories"));
		}
		JsonObject shared = InventoryDecodeSupport.obj(member.get("shared_inventory"));
		JsonObject bags = inventory == null ? null : InventoryDecodeSupport.obj(inventory.get("bag_contents"));

		InventoryDecodeCache.put(categories, "armor", InventoryDecodeCache.decodeField(inventory, "inv_armor"));
		InventoryDecodeCache.put(categories, "equipment", InventoryDecodeCache.decodeField(inventory, "equipment_contents"));
		InventoryDecodeCache.put(categories, "inventory", InventoryDecodeCache.decodeField(inventory, "inv_contents"));
		InventoryDecodeCache.put(categories, "enderchest", InventoryDecodeCache.decodeField(inventory, "ender_chest_contents"));
		InventoryDecodeCache.put(categories, "accessories", bags == null ? List.of() : InventoryDecodeCache.decodeField(bags, "talisman_bag"));
		InventoryDecodeCache.put(categories, "personal_vault", InventoryDecodeCache.decodeField(inventory, "personal_vault_contents"));
		InventoryDecodeCache.put(categories, "fishing_bag", bags == null ? List.of() : InventoryDecodeCache.decodeField(bags, "fishing_bag"));
		InventoryDecodeCache.put(categories, "potion_bag", bags == null ? List.of() : InventoryDecodeCache.decodeField(bags, "potion_bag"));
		InventoryDecodeCache.put(categories, "sacks_bag", bags == null ? List.of() : InventoryDecodeCache.decodeField(bags, "sacks_bag"));
		InventoryDecodeCache.put(categories, "quiver", bags == null ? List.of() : InventoryDecodeCache.decodeField(bags, "quiver"));
		InventoryDecodeCache.put(categories, "candy_inventory", shared == null ? List.of() : InventoryDecodeCache.decodeField(shared, "candy_inventory_contents"));
		InventoryDecodeCache.put(categories, "carnival_mask_inventory", shared == null ? List.of() : InventoryDecodeCache.decodeField(shared, "carnival_mask_inventory_contents"));

		List<Stack> storage = new ArrayList<>();
		JsonObject backpacks = inventory == null ? null : InventoryDecodeSupport.obj(inventory.get("backpack_contents"));
		if (backpacks != null) {
			for (var entry : backpacks.entrySet()) {
				storage.addAll(InventoryDecodeCache.decodeDataElement(entry.getValue()));
			}
		}
		JsonObject icons = inventory == null ? null : InventoryDecodeSupport.obj(inventory.get("backpack_icons"));
		if (icons != null) {
			for (var entry : icons.entrySet()) {
				storage.addAll(InventoryDecodeCache.decodeDataElement(entry.getValue()));
			}
		}
		categories.put("storage", storage);

		List<Stack> wardrobe = new ArrayList<>();
		JsonObject loadout = InventoryDecodeSupport.obj(member.get("loadout"));
		JsonObject armorLayouts = loadout == null ? null : InventoryDecodeSupport.obj(loadout.get("armor"));
		if (armorLayouts != null) {
			for (var layout : armorLayouts.entrySet()) {
				JsonObject page = InventoryDecodeSupport.obj(layout.getValue());
				if (page == null) {
					continue;
				}
				for (String slot : List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
					wardrobe.addAll(InventoryDecodeCache.decodeDataElement(page.get(slot)));
				}
			}
		}
		// Legacy wardrobe blob
		if (wardrobe.isEmpty()) {
			wardrobe.addAll(InventoryDecodeCache.decodeField(inventory, "wardrobe_contents"));
		}
		categories.put("wardrobe", wardrobe);

		List<Stack> equipmentLayouts = new ArrayList<>(categories.getOrDefault("equipment", List.of()));
		JsonObject equipLayouts = loadout == null ? null : InventoryDecodeSupport.obj(loadout.get("equipment"));
		if (equipLayouts != null) {
			for (var layout : equipLayouts.entrySet()) {
				JsonObject page = InventoryDecodeSupport.obj(layout.getValue());
				if (page == null) {
					continue;
				}
				for (String slot : List.of("EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4")) {
					equipmentLayouts.addAll(InventoryDecodeCache.decodeDataElement(page.get(slot)));
				}
			}
		}
		categories.put("equipment", equipmentLayouts);

		categories.put("museum", MuseumInventoryDecoder.parseMuseum(museumMember));
		categories.put("sacks", SackDecoder.parseSacks(member, inventory));
		categories.put("essence", parseEssence(member));
		categories.put("pets", List.of()); // pets valued separately from JSON
		return categories;
	}

	public static List<Stack> parseMuseum(JsonObject museumMember) {
		return MuseumInventoryDecoder.parseMuseum(museumMember);
	}

	public static Map<String, Stack> parseMuseumById(JsonObject museumMember) {
		return MuseumInventoryDecoder.parseMuseumById(museumMember);
	}

	/** One toolkit slot. {@code inUse} means the tool was taken out, so the copy in the inventory is the live one. */
	public record ToolkitItem(String category, int index, boolean inUse, Stack stack) {
	}

	/**
	 * Toolkit JSON is {@code {IS_UNLOCKED, IN_USE: {CROP: {"0": bool}}, CROP: {"0": {data}}}}. Older saves may use an
	 * array or a single {@code {data}} per crop, so all three are read.
	 */
	public static List<ToolkitItem> parseToolkit(JsonObject toolkit) {
		List<ToolkitItem> out = new ArrayList<>();
		if (toolkit == null) {
			return out;
		}
		JsonObject inUse = toolkit.get("IN_USE") instanceof JsonObject o ? o : null;
		for (Map.Entry<String, JsonElement> e : toolkit.entrySet()) {
			String key = e.getKey();
			if (key == null || "IS_UNLOCKED".equalsIgnoreCase(key) || "IN_USE".equalsIgnoreCase(key)) {
				continue;
			}
			JsonElement val = e.getValue();
			JsonObject used = inUse != null && inUse.get(key) instanceof JsonObject u ? u : null;
			if (val instanceof JsonArray arr) {
				for (int i = 0; i < arr.size(); i++) {
					out.add(toolkitItem(key, i, used, arr.get(i)));
				}
			} else if (val instanceof JsonObject obj) {
				if (obj.has("data")) {
					out.add(toolkitItem(key, 0, used, obj));
					continue;
				}
				List<Integer> indices = new ArrayList<>();
				for (String slot : obj.keySet()) {
					try {
						indices.add(Integer.parseInt(slot));
					} catch (NumberFormatException ignored) {
					}
				}
				indices.sort(null);
				for (int index : indices) {
					out.add(toolkitItem(key, index, used, obj.get(String.valueOf(index))));
				}
			}
		}
		return out;
	}

	private static ToolkitItem toolkitItem(String category, int index, JsonObject used, JsonElement data) {
		List<Stack> decoded = data == null || data.isJsonNull() ? List.of() : InventoryDecodeCache.decodeDataElement(data);
		JsonElement flag = used == null ? null : used.get(String.valueOf(index));
		boolean inUse = flag != null && flag.isJsonPrimitive() && flag.getAsJsonPrimitive().isBoolean() && flag.getAsBoolean();
		return new ToolkitItem(category, index, inUse, decoded.isEmpty() ? null : decoded.get(0));
	}

	public static List<Stack> stacksFromItemBytes(JsonElement itemBytes) {
		return InventoryDecodeCache.decodeDataElement(itemBytes);
	}

	public static InventorySnapshot.Slot slotFromItemBytes(JsonElement itemBytes) {
		List<Stack> decoded = InventoryDecodeCache.decodeDataElement(itemBytes);
		if (decoded.isEmpty()) {
			return null;
		}
		return InventoryDecodeSupport.toUiSlot(decoded.get(0));
	}

	public static InventorySnapshot.Slot slotFromTag(String tag, String displayName) {
		return slotFromTag(tag, displayName, "");
	}

	/** Like {@link #slotFromTag(String, String)} with optional rarity for {@code PET_*} tags. */
	public static InventorySnapshot.Slot slotFromTag(String tag, String displayName, String tier) {
		if (tag == null || tag.isBlank()) {
			return null;
		}
		String upper = tag.toUpperCase(Locale.ROOT);
		if (upper.startsWith("PET_") && !upper.startsWith("PET_ITEM_")) {
			InventorySnapshot.Slot pet = SkyBlockItemFactory.auctionPetSlot(upper, displayName, tier);
			if (pet != null) {
				return pet;
			}
		}
		return new InventorySnapshot.Slot(
			upper,
			1,
			List.of(),
			displayName == null ? "" : displayName,
			null,
			null,
			null
		);
	}

	public static InventorySnapshot parseUi(JsonObject member) {
		if (member == null) {
			return InventorySnapshot.empty();
		}
		JsonObject inventory = InventoryDecodeSupport.obj(member.get("inventory"));
		if (inventory == null) {
			inventory = InventoryDecodeSupport.obj(member.get("inventories"));
		}
		JsonObject bags = inventory == null ? null : InventoryDecodeSupport.obj(inventory.get("bag_contents"));
		JsonObject loadout = InventoryDecodeSupport.obj(member.get("loadout"));

		List<InventorySnapshot.Slot> invSlots = InventoryDecodeSupport.toUiSlots(
			InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "inv_contents", 36)
		);
		List<InventorySnapshot.Slot> armorSlots = InventoryDecodeSupport.toUiSlots(readArmorSlots(member));
		List<InventorySnapshot.Slot> equipSlots = InventoryDecodeSupport.toUiSlots(
			InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "equipment_contents", 4)
		);
		List<InventorySnapshot.Slot> combinedInv = playerSlots(equipSlots, armorSlots, invSlots);

		List<InventorySnapshot.Page> enderPages = InventoryDecodeSupport.chunkPages(
			InventoryDecodeSupport.toUiSlots(InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "ender_chest_contents", 45)),
			45,
			9,
			"Ender Chest"
		);

		List<InventorySnapshot.Page> backpackPages = new ArrayList<>();
		JsonObject backpacks = inventory == null ? null : InventoryDecodeSupport.obj(inventory.get("backpack_contents"));
		if (backpacks != null) {
			List<String> keys = new ArrayList<>(backpacks.keySet());
			keys.sort(InventoryDecodeSupport::compareKeys);
			for (String key : keys) {
				List<InventorySnapshot.Slot> slots = InventoryDecodeSupport.toUiSlots(
					InventoryDecodeCache.decodeDataElementKeepingEmpty(backpacks.get(key), 27)
				);
				Integer num = InventoryDecodeSupport.tryParseInt(key);
				String title = num != null ? "Backpack " + (num + 1) : "Backpack " + key;
				backpackPages.add(new InventorySnapshot.Page(title, slots, 9));
			}
		}
		if (backpackPages.isEmpty()) {
			backpackPages = List.of(InventorySnapshot.emptyPage("Backpacks", 9));
		}

		List<InventorySnapshot.Page> wardrobePages = WardrobeDecoder.parseWardrobePages(inventory, loadout);
		List<InventorySnapshot.Page> equipmentPages = WardrobeDecoder.parseEquipmentWardrobePages(inventory, loadout);
		InventorySnapshot.AccessoryInfo accessoryInfo = parseAccessoryInfo(member);
		List<InventorySnapshot.Loadout> loadouts = LoadoutDecoder.parseNamedLoadouts(member, loadout, accessoryInfo);

		List<InventorySnapshot.Page> sackPages = SackDecoder.parseSackPages(member, inventory);
		JsonObject shared = InventoryDecodeSupport.obj(member.get("shared_inventory"));
		boolean carnivalPresent = hasInventoryField(shared, "carnival_mask_inventory_contents")
			|| hasInventoryField(inventory, "carnival_mask_inventory_contents")
			|| hasInventoryField(member, "carnival_mask_inventory_contents");
		boolean candyPresent = hasInventoryField(shared, "candy_inventory_contents")
			|| hasInventoryField(inventory, "candy_inventory_contents")
			|| hasInventoryField(bags, "candy_inventory_contents")
			|| hasInventoryField(member, "candy_inventory_contents");
		InventorySnapshot.Page carnivalPage = sharedPage(
			shared, inventory, member, "carnival_mask_inventory_contents", "Carnival Masks"
		);
		InventorySnapshot.Page candyPage = sharedPage(
			shared, inventory, member, "candy_inventory_contents", "Candy Bag"
		);
		InventorySnapshot.Page timePocket = timePocketPage(bags);

		return new InventorySnapshot(
			new InventorySnapshot.Page("Inventory", combinedInv, 9),
			enderPages.isEmpty() ? List.of(InventorySnapshot.emptyPage("Ender Chest", 9)) : enderPages,
			backpackPages,
			wardrobePages,
			equipmentPages,
			loadouts,
			sackPages,
			bagPage(bags, "fishing_bag", "Fishing Bag"),
			bagPage(bags, "potion_bag", "Potion Bag"),
			bagPage(bags, "quiver", "Quiver"),
			accessoryBagPages(bags),
			accessoryInfo,
			timePocket == null ? InventorySnapshot.emptyPage("Time Pocket", 9) : timePocket,
			new InventorySnapshot.Page(
				"Personal Vault",
				InventoryDecodeSupport.toUiSlots(InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, "personal_vault_contents", 27)),
				9
			),
			carnivalPage,
			carnivalPresent,
			candyPage,
			candyPresent,
			inventoryApiEnabled(inventory),
			hasInventoryField(inventory, "personal_vault_contents"),
			timePocket != null
		);
	}

	public static boolean hasInventoryApi(JsonObject member) {
		return member != null && inventoryApiEnabled(inventoryObject(member));
	}

	/** Wardrobe sets from {@code loadout.armor}. Empty when the profile only has the legacy wardrobe blob. */
	public static List<WardrobePreset> wardrobePresets(JsonObject member) {
		if (member == null) {
			return List.of();
		}
		return WardrobeDecoder.armorPresets(inventoryObject(member), InventoryDecodeSupport.obj(member.get("loadout")));
	}

	private static JsonObject inventoryObject(JsonObject member) {
		JsonObject inventory = InventoryDecodeSupport.obj(member.get("inventory"));
		return inventory != null ? inventory : InventoryDecodeSupport.obj(member.get("inventories"));
	}

	/** Hypixel omits inv_contents entirely (even for an empty inventory) when the inventory API is off. */
	static boolean inventoryApiEnabled(JsonObject inventory) {
		return inventory != null && inventory.has("inv_contents") && !inventory.get("inv_contents").isJsonNull();
	}

	private static boolean hasInventoryField(JsonObject container, String field) {
		if (container == null || field == null || !container.has(field) || container.get(field).isJsonNull()) {
			return false;
		}
		return !InventoryDecodeCache.extractData(container.get(field)).isBlank();
	}

	private static InventorySnapshot.Page sharedPage(
		JsonObject shared, JsonObject inventory, JsonObject member, String field, String title
	) {
		if (shared != null && shared.has(field)) {
			return new InventorySnapshot.Page(
				title,
				InventoryDecodeSupport.toUiSlots(InventoryDecodeCache.decodeFieldKeepingEmpty(shared, field, 27)),
				9
			);
		}
		if (inventory != null && inventory.has(field)) {
			return new InventorySnapshot.Page(
				title,
				InventoryDecodeSupport.toUiSlots(InventoryDecodeCache.decodeFieldKeepingEmpty(inventory, field, 27)),
				9
			);
		}
		if (member != null && member.has(field)) {
			return new InventorySnapshot.Page(
				title,
				InventoryDecodeSupport.toUiSlots(InventoryDecodeCache.decodeFieldKeepingEmpty(member, field, 27)),
				9
			);
		}
		return InventorySnapshot.emptyPage(title, 9);
	}

	private static InventorySnapshot.Page bagPage(JsonObject bags, String field, String title) {
		return new InventorySnapshot.Page(
			title,
			InventoryDecodeSupport.toUiSlots(InventoryDecodeCache.decodeFieldKeepingEmpty(bags, field, 27)),
			9
		);
	}

	/** Accessory bag is one flat NBT list; in-game UI pages every 45 slots (9×5). */
	private static List<InventorySnapshot.Page> accessoryBagPages(JsonObject bags) {
		List<InventorySnapshot.Slot> all = InventoryDecodeSupport.toUiSlots(
			InventoryDecodeCache.decodeFieldKeepingEmpty(bags, "talisman_bag", 45)
		);
		List<InventorySnapshot.Page> pages = InventoryDecodeSupport.chunkPages(all, 45, 9, "Accessory Bag");
		return pages.isEmpty() ? List.of(InventorySnapshot.emptyPage("Accessory Bag", 9)) : pages;
	}

	/** Null when no Time Pocket field exists; the API key is unconfirmed, so a few candidates are probed. */
	private static InventorySnapshot.Page timePocketPage(JsonObject bags) {
		if (bags == null) {
			return null;
		}
		for (String key : List.of("time_pocket", "time_bag", "timed_items", "timepocket", "time_pocket_contents")) {
			if (bags.has(key)) {
				return new InventorySnapshot.Page(
					"Time Pocket",
					InventoryDecodeSupport.toUiSlots(InventoryDecodeCache.decodeFieldKeepingEmpty(bags, key, 27)),
					9
				);
			}
		}
		// Fallback: any bag key containing "time"
		for (var entry : bags.entrySet()) {
			if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).contains("time")) {
				return new InventorySnapshot.Page(
					"Time Pocket",
					InventoryDecodeSupport.toUiSlots(InventoryDecodeCache.decodeDataElementKeepingEmpty(entry.getValue(), 27)),
					9
				);
			}
		}
		return null;
	}

	public static InventorySnapshot.AccessoryInfo parseAccessoryInfo(JsonObject member) {
		JsonObject storage = InventoryDecodeSupport.obj(member == null ? null : member.get("accessory_bag_storage"));
		int highest = 0;
		String power = "";
		int bagUpgrades = 0;
		List<String> unlockedPowers = new ArrayList<>();
		List<InventorySnapshot.TuningTemplate> tunings = new ArrayList<>();
		if (storage != null) {
			highest = storage.has("highest_magical_power") && storage.get("highest_magical_power").isJsonPrimitive()
				? Math.max(0, storage.get("highest_magical_power").getAsInt())
				: 0;
			power = storage.has("selected_power") && storage.get("selected_power").isJsonPrimitive()
				? storage.get("selected_power").getAsString()
				: "";
			bagUpgrades = storage.has("bag_upgrades_purchased") && storage.get("bag_upgrades_purchased").isJsonPrimitive()
				? Math.max(0, storage.get("bag_upgrades_purchased").getAsInt())
				: 0;
			if (storage.has("unlocked_powers") && storage.get("unlocked_powers").isJsonArray()) {
				for (JsonElement el : storage.getAsJsonArray("unlocked_powers")) {
					if (el != null && el.isJsonPrimitive()) {
						try {
							String id = el.getAsString();
							if (id != null && !id.isBlank()) {
								unlockedPowers.add(id);
							}
						} catch (Exception ignored) {
						}
					}
				}
			}
			JsonObject tuning = InventoryDecodeSupport.obj(storage.get("tuning"));
			if (tuning != null) {
				List<String> keys = new ArrayList<>();
				for (String key : tuning.keySet()) {
					if (key != null && key.startsWith("slot_")) {
						keys.add(key);
					}
				}
				keys.sort(InventoryDecodeSupport::compareKeys);
				for (String key : keys) {
					Integer index = InventoryDecodeSupport.tryParseInt(key.substring("slot_".length()));
					if (index == null) {
						continue;
					}
					JsonObject slot = InventoryDecodeSupport.obj(tuning.get(key));
					List<InventorySnapshot.StatPoint> stats = LoadoutDecoder.readTuningStats(slot);
					if (!stats.isEmpty() || slot != null) {
						tunings.add(new InventorySnapshot.TuningTemplate(index, stats));
					}
				}
			}
		}
		int current = MagicalPowerCalculator.fromMember(member);
		return new InventorySnapshot.AccessoryInfo(current, highest, power, tunings, bagUpgrades, unlockedPowers);
	}

	public static RiftInventories parseRiftUi(JsonObject member) {
		JsonObject rift = InventoryDecodeSupport.obj(member == null ? null : member.get("rift"));
		JsonObject inv = InventoryDecodeSupport.obj(rift == null ? null : rift.get("inventory"));
		List<InventorySnapshot.Slot> invSlots = InventoryDecodeSupport.toUiSlots(
			InventoryDecodeCache.decodeFieldKeepingEmpty(inv, "inv_contents", 36)
		);
		List<InventorySnapshot.Slot> armorSlots = InventoryDecodeSupport.toUiSlots(
			InventoryDecodeCache.decodeFieldKeepingEmpty(inv, "inv_armor", 4)
		);
		List<InventorySnapshot.Slot> equipSlots = InventoryDecodeSupport.toUiSlots(
			InventoryDecodeCache.decodeFieldKeepingEmpty(inv, "equipment_contents", 4)
		);
		InventorySnapshot.Page invPage = new InventorySnapshot.Page(
			"Inventory", playerSlots(equipSlots, armorSlots, invSlots), 9
		);
		// Rift ender chest is a flat list paged by 45 like the main one (page icons in ender_chest_page_icons).
		List<InventorySnapshot.Page> ender = InventoryDecodeSupport.chunkPages(
			InventoryDecodeSupport.toUiSlots(InventoryDecodeCache.decodeFieldKeepingEmpty(inv, "ender_chest_contents", 45)),
			45,
			9,
			"Ender Chest"
		);
		return new RiftInventories(invPage, equipSlots, armorSlots, ender);
	}

	/**
	 * Player layout used by both inventory renderers: [equip x4][helmet..boots][inv_contents x36].
	 * Hypixel inv_armor is boots-first; inv_contents is 0-8 hotbar, 9-35 main.
	 */
	public static List<InventorySnapshot.Slot> playerSlots(
		List<InventorySnapshot.Slot> equipment,
		List<InventorySnapshot.Slot> armor,
		List<InventorySnapshot.Slot> inventory
	) {
		List<InventorySnapshot.Slot> out = new ArrayList<>(44);
		for (int i = 0; i < 4; i++) {
			out.add(equipment != null && i < equipment.size() ? equipment.get(i) : null);
		}
		if (armor != null && armor.size() >= 4) {
			out.add(armor.get(3));
			out.add(armor.get(2));
			out.add(armor.get(1));
			out.add(armor.get(0));
		} else {
			for (int i = 0; i < 4; i++) {
				out.add(armor != null && i < armor.size() ? armor.get(i) : null);
			}
		}
		for (int i = 0; i < 36; i++) {
			out.add(inventory != null && i < inventory.size() ? inventory.get(i) : null);
		}
		return out;
	}

	public static String prettyWords(String raw) {
		return InventoryDecodeSupport.prettyWords(raw);
	}

	private static List<Stack> parseEssence(JsonObject member) {
		JsonObject currencies = InventoryDecodeSupport.obj(member.get("currencies"));
		JsonObject essence = currencies == null ? null : InventoryDecodeSupport.obj(currencies.get("essence"));
		if (essence == null) {
			return List.of();
		}
		List<Stack> out = new ArrayList<>();
		for (var entry : essence.entrySet()) {
			JsonObject data = InventoryDecodeSupport.obj(entry.getValue());
			if (data == null || !data.has("current") || !data.get("current").isJsonPrimitive()) {
				continue;
			}
			int amount = data.get("current").getAsInt();
			if (amount > 0) {
				out.add(new Stack("ESSENCE_" + entry.getKey().toUpperCase(Locale.ROOT), amount, new CompoundTag(), List.of(), false));
			}
		}
		return out;
	}

	/** Hypixel inv_armor order: 0 boots, 1 leggings, 2 chestplate, 3 helmet (empties kept). */
	public static List<Stack> readArmorSlots(JsonObject member) {
		JsonObject inventory = InventoryDecodeSupport.obj(member.get("inventory"));
		if (inventory == null) {
			inventory = InventoryDecodeSupport.obj(member.get("inventories"));
		}
		String data = "";
		if (inventory != null) {
			data = InventoryDecodeCache.extractData(inventory.get("inv_armor"));
			if (data.isBlank()) {
				data = InventoryDecodeCache.extractData(inventory.get("armor"));
			}
		}
		if (data.isBlank()) {
			data = InventoryDecodeCache.extractData(member.get("inv_armor"));
		}
		if (data.isBlank()) {
			return List.of();
		}
		try {
			return InventoryDecodeCache.decodeKeepingEmpty(data, 4);
		} catch (Exception ignored) {
			return List.of();
		}
	}

	/** Flatten Hypixel JSON text components / quoted SNBT into a §-legacy string. */
	public static String cleanJsonText(String raw) {
		return NbtInventoryCodec.cleanJsonText(raw);
	}
}
