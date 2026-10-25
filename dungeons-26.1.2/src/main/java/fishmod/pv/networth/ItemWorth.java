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
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.price.HypixelItemsCache;
import fishmod.pv.price.ItemPricer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class ItemWorth {
	private static final Map<String, UpgradeEnchant> ENCHANT_UPGRADES = Map.of(
		"SCAVENGER", new UpgradeEnchant("GOLDEN_BOUNTY", 6),
		"PESTERMINATOR", new UpgradeEnchant("PESTHUNTING_GUIDE", 6),
		"LUCK_OF_THE_SEA", new UpgradeEnchant("GOLD_BOTTLE_CAP", 7),
		"PISCARY", new UpgradeEnchant("TROUBLED_BUBBLE", 7),
		"FRAIL", new UpgradeEnchant("SEVERED_PINCER", 7),
		"SPIKED_HOOK", new UpgradeEnchant("OCTOPUS_TENDRIL", 7),
		"CHARM", new UpgradeEnchant("CHAIN_END_TIMES", 6),
		"VENOMOUS", new UpgradeEnchant("FATEFUL_STINGER", 7)
	);

	private ItemWorth() {
	}

	public static double value(InventoryDecoder.Stack stack) {
		return value(stack, true);
	}

	public static double value(InventoryDecoder.Stack stack, boolean includeCosmetics) {
		return breakdown(stack, includeCosmetics).total();
	}

	/**
	 * Labeled coin breakdown for one item (base + priced modifiers).
	 * Uses the same math as {@link #value}; amounts of 0 are omitted.
	 */
	public static Breakdown breakdown(InventoryDecoder.Stack stack) {
		return breakdown(stack, true);
	}

	public static Breakdown breakdown(InventoryDecoder.Stack stack, boolean includeCosmetics) {
		if (stack == null || stack.id() == null) {
			return Breakdown.empty();
		}
		if (!includeCosmetics && isCosmeticItem(stack)) {
			return Breakdown.empty();
		}

		List<Part> parts = new ArrayList<>();
		CompoundTag ea = stack.extraAttributes();
		if (ea == null || ea.isEmpty()) {
			double material = ItemPricer.materialPrice(stack.id()) * Math.max(0L, stack.count());
			if (material > 0) {
				parts.add(Part.item(baseItemLabel(stack), material, itemNameColor(stack)));
			}
			return new Breakdown(Math.max(0, material), List.copyOf(parts));
		}

		JsonObject skyblockItem = HypixelItemsCache.get(stack.id());
		String itemId = resolveItemId(stack, skyblockItem, includeCosmetics);
		double base = ItemPricer.price(itemId) * stack.count();
		double total = base;
		if (base > 0) {
			parts.add(Part.item(baseItemLabel(stack), base, itemNameColor(stack)));
		}

		total += addPotatoParts(parts, ea);
		double recombVal = recomb(stack, skyblockItem, ea);
		if (recombVal > 0) {
			parts.add(Part.upgrade("Recombobulator", "", recombVal));
			total += recombVal;
		}

		total += addCounted(parts, ea, "art_of_war_count", "Art of War", "THE_ART_OF_WAR",
			NetworthData.worth("artOfWar", 0.6));
		total += addCounted(parts, ea, "artOfPeaceApplied", "Art of Peace", "THE_ART_OF_PEACE",
			NetworthData.worth("artOfPeace", 0.8));
		total += addStarParts(parts, skyblockItem, ea);

		double reforgeVal = reforge(stack, skyblockItem, ea);
		if (reforgeVal > 0) {
			String modifier = NbtAttrs.string(ea, "modifier");
			String stone = NetworthData.reforgeItem(modifier);
			String stoneName = stone == null || stone.isBlank()
				? InventoryDecoder.prettyWords(modifier)
				: SkyBlockItemFactory.plainDisplayName(stone);
			parts.add(Part.upgrade("Reforge", stoneName, reforgeVal));
			total += reforgeVal;
		}

		total += addFlag(parts, ea, "ethermerge", "Etherwarp", "ETHERWARP_CONDUIT",
			NetworthData.worth("etherwarp", 1));
		total += addCounted(parts, ea, "tuned_transmission", "Transmission Tuner", "TRANSMISSION_TUNER",
			NetworthData.worth("tunedTransmission", 0.7));
		total += addCounted(parts, ea, "wood_singularity_count", "Wood Singularity", "WOOD_SINGULARITY",
			NetworthData.worth("woodSingularity", 0.5));
		total += addCounted(parts, ea, "divan_powder_coating", "Divan Powder Coating", "DIVAN_POWDER_COATING",
			NetworthData.worth("divanPowderCoating", 0.8));
		total += addCounted(parts, ea, "farming_for_dummies_count", "Farming for Dummies", "FARMING_FOR_DUMMIES",
			NetworthData.worth("farmingForDummies", 0.5));
		total += addCounted(parts, ea, "jalapeno_count", "Jalapeno Book", "JALAPENO_BOOK",
			NetworthData.worth("jalapenoBook", 0.8));
		total += addCounted(parts, ea, "mana_disintegrator", "Mana Disintegrator", "MANA_DISINTEGRATOR",
			NetworthData.worth("manaDisintegrator", 0.8));
		total += addCounted(parts, ea, "polarvoid", "Polarvoid Book", "POLARVOID_BOOK",
			NetworthData.worth("polarvoidBook", 1));

		double enrichVal = enrichment(ea);
		if (enrichVal > 0) {
			String enrich = NbtAttrs.string(ea, "talisman_enrichment");
			parts.add(Part.other("Enrichment", InventoryDecoder.prettyWords(enrich), enrichVal));
			total += enrichVal;
		}

		total += addGemParts(parts, stack, ea);
		total += addScrollParts(parts, ea);
		total += addDrillParts(parts, ea);
		total += addRodParts(parts, ea);
		total += addEnchantParts(parts, stack, ea);
		total += addBookParts(parts, stack, ea);

		if (includeCosmetics) {
			double dyeVal = dye(ea);
			if (dyeVal > 0) {
				String dyeId = NbtAttrs.string(ea, "dye_item");
				parts.add(Part.other("Dye", SkyBlockItemFactory.plainDisplayName(dyeId), dyeVal));
				total += dyeVal;
			}
			double runeVal = runeOnItem(stack, ea);
			if (runeVal > 0) {
				CompoundTag runes = NbtAttrs.compound(ea, "runes");
				String type = runes == null || runes.isEmpty() ? "Rune" : runes.keySet().iterator().next();
				int tier = runes == null ? 1 : NbtAttrs.intValue(runes, type, 1);
				parts.add(Part.other("Rune", InventoryDecoder.prettyWords(type) + " " + tier, runeVal));
				total += runeVal;
			}
			double skinVal = soulboundSkin(stack, ea);
			if (skinVal > 0) {
				String skin = NbtAttrs.string(ea, "skin");
				parts.add(Part.other("Skin", SkyBlockItemFactory.plainDisplayName(skin), skinVal));
				total += skinVal;
			}
		}

		return new Breakdown(Math.max(0, total), List.copyOf(parts));
	}

	public record Breakdown(double total, List<Part> parts) {
		public Breakdown {
			parts = parts == null ? List.of() : List.copyOf(parts);
		}

		public static Breakdown empty() {
			return new Breakdown(0, List.of());
		}
	}

	public enum Section {
		BASE("Base item"),
		UPGRADES("Upgrades"),
		GEMSTONES("Gemstones"),
		ENCHANTMENTS("Enchantments"),
		CONTENTS("Contents"),
		OTHER("Other");

		private final String title;

		Section(String title) {
			this.title = title;
		}

		public String title() {
			return this.title;
		}
	}

	public enum Role {
		/** Catalog base item name. */
		ITEM,
		/** Summary upgrade line (HPB's 10/10, Stars 5/5). */
		UPGRADE,
		/** Nested contributor under a section. */
		DETAIL,
		/** Collapsed remainder note. */
		NOTE
	}

	/** One priced row in an item breakdown. */
	public record Part(Section section, Role role, String label, String detail, double amount, int accentColor) {
		public Part {
			section = section == null ? Section.OTHER : section;
			role = role == null ? Role.DETAIL : role;
			label = label == null ? "" : label;
			detail = detail == null ? "" : detail;
		}

		public static Part item(String name, double amount, int rarityColor) {
			return new Part(Section.BASE, Role.ITEM, name, "", amount, rarityColor);
		}

		public static Part upgrade(String label, String detail, double amount) {
			return new Part(Section.UPGRADES, Role.UPGRADE, label, detail, amount, 0);
		}

		public static Part detail(Section section, String label, double amount) {
			return new Part(section, Role.DETAIL, label, "", amount, 0);
		}

		public static Part other(String label, String detail, double amount) {
			return new Part(Section.OTHER, Role.DETAIL, label, detail == null ? "" : detail, amount, 0);
		}

		public static Part note(Section section, String text) {
			return new Part(section, Role.NOTE, text == null ? "" : text, "", 0, 0);
		}
	}

	private static String displayName(InventoryDecoder.Stack stack) {
		if (stack.displayName() != null && !stack.displayName().isBlank()) {
			return stripCodes(stack.displayName());
		}
		return SkyBlockItemFactory.plainDisplayName(stack.id());
	}

	/** Catalog name without reforge prefix (e.g. Dark Claymore, not Withered Dark Claymore). */
	private static String baseItemLabel(InventoryDecoder.Stack stack) {
		String plain = SkyBlockItemFactory.plainDisplayName(stack.id());
		if (plain != null && !plain.isBlank()) {
			return plain;
		}
		return displayName(stack);
	}

	private static String stripCodes(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replaceAll("§.", "").trim();
	}

	private static int itemNameColor(InventoryDecoder.Stack stack) {
		String last = stack.lore().isEmpty() ? "" : stack.lore().get(stack.lore().size() - 1);
		String upper = last.toUpperCase(Locale.ROOT);
		for (String tier : List.of(
			"MYTHIC", "LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON",
			"DIVINE", "VERY SPECIAL", "SPECIAL", "SUPREME", "ULTIMATE"
		)) {
			if (upper.contains(tier)) {
				return SkyBlockItemFactory.tierArgb(tier.replace(' ', '_'));
			}
		}
		return SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(stack.id()));
	}

	private static double addPotatoParts(List<Part> parts, CompoundTag ea) {
		int count = NbtAttrs.intValue(ea, "hot_potato_count", 0);
		if (count <= 0) {
			return 0;
		}
		double total = 0;
		int hot = Math.min(count, 10);
		double hotVal = ItemPricer.price("HOT_POTATO_BOOK") * hot * NetworthData.worth("hotPotatoBook", 1);
		if (hotVal > 0) {
			parts.add(Part.upgrade("HPB's", hot + "/10", hotVal));
			total += hotVal;
		}
		if (count > 10) {
			int fuming = count - 10;
			double fumVal = ItemPricer.price("FUMING_POTATO_BOOK") * fuming * NetworthData.worth("fumingPotatoBook", 0.6);
			if (fumVal > 0) {
				parts.add(Part.upgrade("Fuming", fuming + "/5", fumVal));
				total += fumVal;
			}
		}
		return total;
	}

	private static final int MAX_ENCHANT_DETAILS = 7;

	private static double addEnchantParts(List<Part> parts, InventoryDecoder.Stack stack, CompoundTag ea) {
		if ("ENCHANTED_BOOK".equals(stack.id())) {
			return 0;
		}
		Map<String, Integer> enchants = NbtAttrs.intMap(ea, "enchantments");
		if (enchants.isEmpty()) {
			return 0;
		}
		double total = 0;
		List<Part> children = new ArrayList<>();
		Map<String, List<String>> blocked = NetworthData.blockedEnchants();
		Map<String, Integer> ignored = NetworthData.ignoredEnchants();
		List<String> stacking = NetworthData.stackingEnchants();
		List<String> ignoreSilex = NetworthData.ignoreSilex();
		for (var entry : enchants.entrySet()) {
			String name = entry.getKey().toUpperCase(Locale.ROOT);
			int value = entry.getValue();
			List<String> blockedForItem = blocked.get(stack.id());
			if (blockedForItem != null && blockedForItem.contains(name)) {
				continue;
			}
			if (ignored.getOrDefault(name, Integer.MIN_VALUE) == value) {
				continue;
			}
			int displayLevel = stacking.contains(name) ? 1 : value;
			double line = 0;
			if ("EFFICIENCY".equals(name) && value >= 6 && !ignoreSilex.contains(stack.id())) {
				int efficiencyLevel = value - ("STONK_PICKAXE".equals(stack.id()) ? 6 : 5);
				if (efficiencyLevel > 0) {
					line += ItemPricer.price("SIL_EX") * efficiencyLevel * NetworthData.worth("silex", 0.75);
				}
			}
			UpgradeEnchant upgrade = ENCHANT_UPGRADES.get(name);
			if (upgrade != null && value >= upgrade.tier()) {
				line += ItemPricer.price(upgrade.item()) * NetworthData.worth("enchantmentUpgrades", 0.8);
			}
			line += ItemPricer.price("ENCHANTMENT_" + name + "_" + value);
			if (line > 0) {
				children.add(Part.detail(Section.ENCHANTMENTS, enchantLabel(name, displayLevel), line));
				total += line;
			}
		}
		if (total > 0) {
			children.sort((a, b) -> Double.compare(b.amount(), a.amount()));
			int shown = Math.min(MAX_ENCHANT_DETAILS, children.size());
			parts.addAll(children.subList(0, shown));
			int hidden = children.size() - shown;
			if (hidden > 0) {
				parts.add(Part.note(Section.ENCHANTMENTS, "+" + hidden + " more enchant" + (hidden == 1 ? "" : "s")));
			}
		}
		return total;
	}

	private static double addBookParts(List<Part> parts, InventoryDecoder.Stack stack, CompoundTag ea) {
		if (!"ENCHANTED_BOOK".equals(stack.id())) {
			return 0;
		}
		Map<String, Integer> enchants = NbtAttrs.intMap(ea, "enchantments");
		if (enchants.isEmpty()) {
			return 0;
		}
		double total = 0;
		for (var entry : enchants.entrySet()) {
			String name = entry.getKey().toUpperCase(Locale.ROOT);
			int value = entry.getValue();
			double line = ItemPricer.price("ENCHANTMENT_" + name + "_" + value);
			if (line > 0) {
				parts.add(Part.detail(Section.ENCHANTMENTS, enchantLabel(name, value), line));
				total += line;
			}
		}
		return total;
	}

	private static double addStarParts(List<Part> parts, JsonObject skyblockItem, CompoundTag ea) {
		double master = masterStars(skyblockItem, ea);
		double essence = essenceStars(skyblockItem, ea);
		if (essence <= 0 && master <= 0) {
			return 0;
		}
		int level = upgradeLevel(ea);
		double total = 0;
		if (essence > 0) {
			int starShow = Math.min(level, 5);
			parts.add(Part.upgrade("Stars", starShow + "/5", essence));
			total += essence;
		}
		if (master > 0) {
			int masters = Math.min(Math.max(0, level - 5), 5);
			parts.add(Part.upgrade("Master Stars", masters + "/5", master));
			total += master;
		}
		return total;
	}

	private static double addGemParts(List<Part> parts, InventoryDecoder.Stack stack, CompoundTag ea) {
		CompoundTag gems = NbtAttrs.compound(ea, "gems");
		if (gems == null || gems.isEmpty()) {
			return 0;
		}
		double total = 0;
		Map<String, GemBucket> buckets = new LinkedHashMap<>();
		for (String key : gems.keySet()) {
			if ("unlocked_slots".equals(key) || key.endsWith("_gem")) {
				continue;
			}
			String tier;
			Tag value = gems.get(key);
			if (value instanceof CompoundTag compound) {
				tier = NbtAttrs.string(compound, "quality");
			} else {
				tier = NbtAttrs.string(gems, key);
			}
			if (tier == null || tier.isBlank()) {
				continue;
			}
			String type = NbtAttrs.string(gems, key + "_gem");
			if (type == null || type.isBlank()) {
				int underscore = key.indexOf('_');
				type = underscore > 0 ? key.substring(0, underscore) : key;
			}
			String id = (tier + "_" + type + "_GEM").toUpperCase(Locale.ROOT);
			double line = ItemPricer.price(id) * NetworthData.worth("gemstone", 1);
			if (line > 0) {
				String label = InventoryDecoder.prettyWords(tier) + " " + InventoryDecoder.prettyWords(type);
				buckets.merge(id, new GemBucket(label, line, 1), GemBucket::plus);
				total += line;
			}
		}
		if (total > 0) {
			for (GemBucket bucket : buckets.values()) {
				String label = bucket.count > 1 ? bucket.label + " (x" + bucket.count + ")" : bucket.label;
				parts.add(Part.detail(Section.GEMSTONES, label, bucket.value));
			}
		}
		return total;
	}

	private record GemBucket(String label, double value, int count) {
		GemBucket plus(GemBucket other) {
			return new GemBucket(this.label, this.value + other.value, this.count + other.count);
		}
	}

	private static double addScrollParts(List<Part> parts, CompoundTag ea) {
		List<String> scrolls = NbtAttrs.stringList(ea, "ability_scroll");
		if (scrolls.isEmpty()) {
			return 0;
		}
		double total = 0;
		for (String id : scrolls) {
			double line = ItemPricer.price(id.toUpperCase(Locale.ROOT)) * NetworthData.worth("necronBladeScroll", 1);
			if (line > 0) {
				parts.add(Part.other("Scroll", SkyBlockItemFactory.plainDisplayName(id), line));
				total += line;
			}
		}
		return total;
	}

	private static double addCounted(
		List<Part> parts,
		CompoundTag ea,
		String key,
		String label,
		String itemId,
		double mult
	) {
		int count = NbtAttrs.intValue(ea, key, 0);
		if (count <= 0) {
			return 0;
		}
		double val = ItemPricer.price(itemId) * count * mult;
		if (val > 0) {
			parts.add(Part.upgrade(label, count > 1 ? "x" + count : "", val));
		}
		return val;
	}

	private static double addFlag(
		List<Part> parts,
		CompoundTag ea,
		String key,
		String label,
		String itemId,
		double mult
	) {
		if (!NbtAttrs.has(ea, key)) {
			return 0;
		}
		double val = ItemPricer.price(itemId) * mult;
		if (val > 0) {
			parts.add(Part.upgrade(label, "", val));
		}
		return val;
	}

	private static double addDrillParts(List<Part> parts, CompoundTag ea) {
		double total = 0;
		for (String key : List.of("drill_part_upgrade_module", "drill_part_fuel_tank", "drill_part_engine")) {
			String part = NbtAttrs.string(ea, key);
			if (part == null || part.isBlank()) {
				continue;
			}
			double line = ItemPricer.price(part.toUpperCase(Locale.ROOT)) * NetworthData.worth("drillPart", 1);
			if (line > 0) {
				parts.add(Part.other("Drill Part", SkyBlockItemFactory.plainDisplayName(part), line));
				total += line;
			}
		}
		return total;
	}

	private static double addRodParts(List<Part> parts, CompoundTag ea) {
		double total = 0;
		for (String key : List.of("line", "hook", "sinker")) {
			CompoundTag part = NbtAttrs.compound(ea, key);
			if (part == null) {
				continue;
			}
			String id = NbtAttrs.string(part, "part");
			if (id == null || id.isBlank()) {
				continue;
			}
			double line = ItemPricer.price(id.toUpperCase(Locale.ROOT)) * NetworthData.worth("rodPart", 1);
			if (line > 0) {
				parts.add(Part.other(InventoryDecoder.prettyWords(key), SkyBlockItemFactory.plainDisplayName(id), line));
				total += line;
			}
		}
		return total;
	}

	private static String enchantLabel(String name, int level) {
		return InventoryDecoder.prettyWords(name) + " " + roman(level);
	}

	private static String roman(int value) {
		if (value <= 0) {
			return String.valueOf(value);
		}
		int[] nums = {10, 9, 5, 4, 1};
		String[] glyphs = {"X", "IX", "V", "IV", "I"};
		StringBuilder sb = new StringBuilder();
		int n = Math.min(value, 20);
		for (int i = 0; i < nums.length; i++) {
			while (n >= nums[i]) {
				sb.append(glyphs[i]);
				n -= nums[i];
			}
		}
		return sb.toString();
	}

	/** Standalone cosmetic items (skins, dyes, furniture, etc.) - zeroed when cosmetics off. */
	private static boolean isCosmeticItem(InventoryDecoder.Stack stack) {
		String id = stack.id().toUpperCase(Locale.ROOT);
		if (id.contains("_SKIN") || id.startsWith("PET_SKIN_") || id.endsWith("_DYE") || id.contains("DYE_")) {
			return true;
		}
		if ("RUNE".equals(id) || "UNIQUE_RUNE".equals(id) || id.startsWith("RUNE_")) {
			return true;
		}
		JsonObject hypixel = HypixelItemsCache.get(id);
		if (hypixel != null && hypixel.has("category") && hypixel.get("category").isJsonPrimitive()) {
			String cat = hypixel.get("category").getAsString();
			if ("COSMETIC".equalsIgnoreCase(cat)) {
				return true;
			}
		}
		return false;
	}

	private static String resolveItemId(InventoryDecoder.Stack stack, JsonObject skyblockItem, boolean includeCosmetics) {
		String itemId = stack.id();
		CompoundTag ea = stack.extraAttributes();
		String skin = NbtAttrs.string(ea, "skin");
		if (includeCosmetics && skin != null && !skin.isBlank()) {
			String skinned = itemId + "_SKINNED_" + skin;
			if (ItemPricer.price(skinned) > ItemPricer.price(itemId)) {
				return skinned;
			}
		}
		if ("RUNE".equals(itemId) || "UNIQUE_RUNE".equals(itemId)) {
			CompoundTag runes = NbtAttrs.compound(ea, "runes");
			if (runes != null && !runes.isEmpty()) {
				String type = runes.keySet().iterator().next();
				int tier = NbtAttrs.intValue(runes, type, 1);
				return ("RUNE_" + type + "_" + tier).toUpperCase(Locale.ROOT);
			}
		}
		if ("NEW_YEAR_CAKE".equals(itemId)) {
			return "NEW_YEAR_CAKE_" + NbtAttrs.intValue(ea, "new_years_cake", 0);
		}
		if (includeCosmetics && NbtAttrs.has(ea, "is_shiny") && ItemPricer.price(itemId + "_SHINY") > 0) {
			return itemId + "_SHINY";
		}
		if (itemId.startsWith("STARRED_") && ItemPricer.price(itemId) <= 0) {
			String unstarred = itemId.substring("STARRED_".length());
			if (ItemPricer.price(unstarred) > 0) {
				return unstarred;
			}
		}
		return itemId;
	}

	private static double recomb(InventoryDecoder.Stack stack, JsonObject skyblockItem, CompoundTag ea) {
		int upgrades = NbtAttrs.intValue(ea, "rarity_upgrades", 0);
		if (upgrades <= 0 || NbtAttrs.has(ea, "item_tier")) {
			return 0;
		}
		boolean hasEnchants = !NbtAttrs.intMap(ea, "enchantments").isEmpty();
		String category = skyblockItem != null && skyblockItem.has("category") && skyblockItem.get("category").isJsonPrimitive()
			? skyblockItem.get("category").getAsString()
			: "";
		boolean allows = NetworthData.allowedRecombCategories().contains(category)
			|| NetworthData.allowedRecombIds().contains(stack.id());
		String lastLore = stack.lore().isEmpty() ? "" : stack.lore().get(stack.lore().size() - 1);
		boolean accessory = lastLore.contains("ACCESSORY") || lastLore.contains("HATCESSORY");
		if (!(hasEnchants || allows || accessory)) {
			return 0;
		}
		return ItemPricer.price("RECOMBOBULATOR_3000") * NetworthData.worth("recombobulator", 0.8);
	}

	private static int upgradeLevel(CompoundTag ea) {
		String dungeon = String.valueOf(NbtAttrs.intValue(ea, "dungeon_item_level", 0));
		String upgrade = String.valueOf(NbtAttrs.intValue(ea, "upgrade_level", 0));
		int d = Integer.parseInt(dungeon.replaceAll("\\D", "").isEmpty() ? "0" : dungeon.replaceAll("\\D", ""));
		int u = Integer.parseInt(upgrade.replaceAll("\\D", "").isEmpty() ? "0" : upgrade.replaceAll("\\D", ""));
		return Math.max(d, u);
	}

	private static double masterStars(JsonObject skyblockItem, CompoundTag ea) {
		int level = upgradeLevel(ea);
		if (level <= 5) {
			return 0;
		}
		JsonArray costs = skyblockItem != null && skyblockItem.get("upgrade_costs") instanceof JsonArray
			? skyblockItem.getAsJsonArray("upgrade_costs")
			: null;
		if (costs != null && costs.size() > 5) {
			return 0;
		}
		int starsUsed = Math.min(level - 5, 5);
		List<String> stars = NetworthData.masterStars();
		double total = 0;
		for (int i = 0; i < starsUsed && i < stars.size(); i++) {
			total += ItemPricer.price(stars.get(i)) * NetworthData.worth("masterStar", 1);
		}
		return total;
	}

	private static double essenceStars(JsonObject skyblockItem, CompoundTag ea) {
		if (skyblockItem == null || !(skyblockItem.get("upgrade_costs") instanceof JsonArray)) {
			return 0;
		}
		int level = upgradeLevel(ea);
		if (level <= 0) {
			return 0;
		}
		JsonArray costs = skyblockItem.getAsJsonArray("upgrade_costs");
		double total = 0;
		for (int i = 0; i < Math.min(level, costs.size()); i++) {
			JsonElement step = costs.get(i);
			if (step.isJsonArray()) {
				for (JsonElement cost : step.getAsJsonArray()) {
					if (cost.isJsonObject()) {
						total += starCost(cost.getAsJsonObject());
					}
				}
			} else if (step.isJsonObject()) {
				total += starCost(step.getAsJsonObject());
			}
		}
		return total;
	}

	private static double starCost(JsonObject upgrade) {
		if (upgrade == null) {
			return 0;
		}
		int amount = upgrade.has("amount") && upgrade.get("amount").isJsonPrimitive() ? upgrade.get("amount").getAsInt() : 0;
		if (upgrade.has("essence_type") && upgrade.get("essence_type").isJsonPrimitive()) {
			String type = upgrade.get("essence_type").getAsString().toUpperCase(Locale.ROOT);
			return ItemPricer.price("ESSENCE_" + type) * amount * NetworthData.worth("essence", 0.75);
		}
		if (upgrade.has("item_id") && upgrade.get("item_id").isJsonPrimitive()) {
			return ItemPricer.price(upgrade.get("item_id").getAsString()) * amount;
		}
		return 0;
	}

	private static double enrichment(CompoundTag ea) {
		String enrichment = NbtAttrs.string(ea, "talisman_enrichment");
		if (enrichment == null || enrichment.isBlank()) {
			return 0;
		}
		double min = Double.POSITIVE_INFINITY;
		for (String id : NetworthData.enrichments()) {
			double price = ItemPricer.price(id);
			if (price > 0) {
				min = Math.min(min, price);
			}
		}
		if (!Double.isFinite(min)) {
			return 0;
		}
		return min * NetworthData.worth("enrichment", 0.5);
	}

	private static double reforge(InventoryDecoder.Stack stack, JsonObject skyblockItem, CompoundTag ea) {
		String modifier = NbtAttrs.string(ea, "modifier");
		if (modifier == null || modifier.isBlank()) {
			return 0;
		}
		String category = skyblockItem != null && skyblockItem.has("category") && skyblockItem.get("category").isJsonPrimitive()
			? skyblockItem.get("category").getAsString()
			: "";
		if ("ACCESSORY".equals(category)) {
			return 0;
		}
		String item = NetworthData.reforgeItem(modifier);
		if (item == null) {
			return 0;
		}
		return ItemPricer.price(item) * NetworthData.worth("reforge", 1);
	}

	private static double dye(CompoundTag ea) {
		String dye = NbtAttrs.string(ea, "dye_item");
		if (dye == null || dye.isBlank()) {
			return 0;
		}
		return ItemPricer.price(dye) * NetworthData.worth("dye", 0.9);
	}

	private static double runeOnItem(InventoryDecoder.Stack stack, CompoundTag ea) {
		if (stack.id().startsWith("RUNE")) {
			return 0;
		}
		CompoundTag runes = NbtAttrs.compound(ea, "runes");
		if (runes == null || runes.isEmpty()) {
			return 0;
		}
		String type = runes.keySet().iterator().next();
		int tier = NbtAttrs.intValue(runes, type, 1);
		String id = ("RUNE_" + type + "_" + tier).toUpperCase(Locale.ROOT);
		return ItemPricer.price(id) * NetworthData.worth("runes", 0.6);
	}

	private static double soulboundSkin(InventoryDecoder.Stack stack, CompoundTag ea) {
		String skin = NbtAttrs.string(ea, "skin");
		if (skin == null || skin.isBlank() || !stack.soulbound()) {
			return 0;
		}
		if (stack.id().contains(skin)) {
			return 0;
		}
		return ItemPricer.price(skin) * NetworthData.worth("soulboundSkins", 0.8);
	}

	private record UpgradeEnchant(String item, int tier) {
	}
}
