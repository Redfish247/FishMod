/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth;

import com.google.gson.JsonObject;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.neu.NeuRepoCache;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/** Sack counts and sack UI page decoding. */
final class SackDecoder {
	/** Page for counted items no NEU sack lists. */
	static final String UNSORTED_PAGE = "Unsorted";

	/** NEU lists Rough/Flawed/Fine only - add Flawless/Perfect for each gem type. */
	static final String[] GEMSTONE_TYPES = {
		"RUBY", "JADE", "SAPPHIRE", "AMETHYST", "AMBER", "TOPAZ", "JASPER", "OPAL",
		"AQUAMARINE", "CITRINE", "ONYX", "PERIDOT"
	};

	private SackDecoder() {
	}

	static List<InventoryDecoder.Stack> parseSacks(JsonObject member, JsonObject inventory) {
		JsonObject counts = sackCountsObject(member, inventory);
		if (counts == null) {
			return List.of();
		}
		List<InventoryDecoder.Stack> out = new ArrayList<>();
		for (var entry : counts.entrySet()) {
			if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) {
				continue;
			}
			long amountLong = Math.max(0L, (long) entry.getValue().getAsDouble());
			if (amountLong <= 0L) {
				continue;
			}
			int amount = amountLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amountLong;
			out.add(new InventoryDecoder.Stack(entry.getKey(), amount, new CompoundTag(), List.of(), false));
		}
		return out;
	}

	static JsonObject sackCountsObject(JsonObject member, JsonObject inventory) {
		JsonObject counts = InventoryDecodeSupport.obj(member.get("sacks_counts"));
		if (counts == null && inventory != null) {
			counts = InventoryDecodeSupport.obj(inventory.get("sacks_counts"));
		}
		return counts;
	}

	/**
	 * One page per owned sack (from {@code bag_contents.sacks_bag}) with every holdable item (count may be 0).
	 * NEU's Rune sack has an empty contents list - real runes go on {@code Rune Sack}.
	 * Gemstone sack is expanded with Flawless/Perfect (NEU only lists Rough-Fine).
	 */
	static List<InventorySnapshot.Page> parseSackPages(JsonObject member, JsonObject inventory) {
		JsonObject countsJson = sackCountsObject(member, inventory);
		Map<String, Integer> counts = new LinkedHashMap<>();
		if (countsJson != null) {
			for (var entry : countsJson.entrySet()) {
				// Same number check and int clamp as parseSacks; a non-numeric value would throw.
				if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
					double raw = entry.getValue().getAsDouble();
					counts.put(entry.getKey().toUpperCase(Locale.ROOT), (int) Math.max(0D, Math.min(Integer.MAX_VALUE, raw)));
				}
			}
		}
		Map<String, List<String>> defs = NeuRepoCache.sackDefinitions();
		Map<String, String> sackItems = new LinkedHashMap<>();
		for (String name : defs.keySet()) {
			sackItems.put(name, NeuRepoCache.sackItemId(name));
		}
		return buildSackPages(defs, sackItems, counts, ownedSackKeys(inventory));
	}

	/**
	 * Owned sack keys (size prefix stripped), or null when unknown. With the inventory API off Hypixel
	 * still sends {@code sacks_bag}, but empty, so it can't be trusted then.
	 */
	static Set<String> ownedSackKeys(JsonObject inventory) {
		if (!InventoryDecoder.inventoryApiEnabled(inventory)) {
			return null;
		}
		JsonObject bags = InventoryDecodeSupport.obj(inventory.get("bag_contents"));
		if (bags == null || !bags.has("sacks_bag") || bags.get("sacks_bag").isJsonNull()) {
			return null;
		}
		Set<String> owned = new HashSet<>();
		for (InventoryDecoder.Stack stack : InventoryDecodeCache.decodeField(bags, "sacks_bag")) {
			if (stack != null && stack.id() != null && !stack.id().isBlank()) {
				owned.add(sackKey(stack.id()));
			}
		}
		return owned;
	}

	/** Small/Medium/Large variants are one sack type; NEU only names the Large item. */
	static String sackKey(String itemId) {
		if (itemId == null) {
			return "";
		}
		String key = itemId.trim().toUpperCase(Locale.ROOT);
		for (String prefix : new String[] {"SMALL_", "MEDIUM_", "LARGE_"}) {
			if (key.startsWith(prefix)) {
				return key.substring(prefix.length());
			}
		}
		return key;
	}

	/**
	 * @param owned owned sack keys from {@link #ownedSackKeys}; null means ownership is unknown,
	 *              in which case only sacks holding something are shown.
	 */
	static List<InventorySnapshot.Page> buildSackPages(
		Map<String, List<String>> defs,
		Map<String, String> sackItems,
		Map<String, Integer> counts,
		Set<String> owned
	) {
		Map<String, List<String>> contentsByName = new LinkedHashMap<>();
		for (var sack : defs.entrySet()) {
			String sackName = sack.getKey();
			List<String> contents = sack.getValue() == null ? List.of() : sack.getValue();
			if ("Gemstone".equalsIgnoreCase(sackName)) {
				contents = expandGemstoneContents(contents);
			}
			contentsByName.put(sackName, contents);
		}
		if (contentsByName.keySet().stream().noneMatch("Gemstone"::equalsIgnoreCase)) {
			contentsByName.put("Gemstone", expandGemstoneContents(List.of()));
		}
		// NEU's Rune sack has no contents list, so the repo loader drops it; runes would fall into Unsorted.
		if (contentsByName.keySet().stream().noneMatch("Rune"::equalsIgnoreCase)) {
			contentsByName.put("Rune", List.of());
		}

		List<InventorySnapshot.Slot> gemExtras = new ArrayList<>();
		List<InventorySnapshot.Slot> runes = new ArrayList<>();
		List<InventorySnapshot.Slot> other = new ArrayList<>();
		Set<String> neuItems = new HashSet<>();
		for (List<String> contents : contentsByName.values()) {
			for (String itemId : contents) {
				claimSackId(neuItems, itemId);
			}
		}
		for (var entry : counts.entrySet()) {
			String id = entry.getKey();
			if (isClaimedSackId(neuItems, id) || entry.getValue() == null || entry.getValue() <= 0) {
				continue;
			}
			InventorySnapshot.Slot slot = InventoryDecodeSupport.toUiSlot(
				new InventoryDecoder.Stack(id, entry.getValue(), new CompoundTag(), List.of(), false)
			);
			if (isGemstoneSackId(id)) {
				gemExtras.add(slot);
			} else if (isRuneSackId(id)) {
				runes.add(slot);
			} else {
				other.add(slot);
			}
		}

		Set<String> visible = new HashSet<>();
		Set<String> listed = new HashSet<>();
		for (var sack : contentsByName.entrySet()) {
			String sackName = sack.getKey();
			boolean show = owned == null
				? holdsSomething(sackName, sack.getValue(), counts, runes, gemExtras, Set.of())
				: owned.contains(sackKey(sackItemFor(sackItems, sackName)));
			if (show) {
				visible.add(sackName);
				sack.getValue().forEach(id -> claimSackId(listed, id));
			}
		}
		// Ownership comes from sacks_bag, which can miss sacks (e.g. the Rune Sack). A sack still gets its own
		// page when it holds something no visible sack lists, instead of dumping that into a catch-all page.
		if (owned != null) {
			for (var sack : contentsByName.entrySet()) {
				String sackName = sack.getKey();
				if (!visible.contains(sackName) && holdsSomething(sackName, sack.getValue(), counts, runes, gemExtras, listed)) {
					visible.add(sackName);
					sack.getValue().forEach(id -> claimSackId(listed, id));
				}
			}
		}
		Set<String> shown = new LinkedHashSet<>();
		for (String sackName : contentsByName.keySet()) {
			if (visible.contains(sackName)) {
				shown.add(sackName);
			}
		}

		// Events overlaps Winter/Spooky/Fishing etc. sharing one count, so only list what no other owned sack shows.
		Set<String> elsewhere = new HashSet<>();
		for (String sackName : shown) {
			if (!"Events".equalsIgnoreCase(sackName)) {
				for (String itemId : contentsByName.get(sackName)) {
					claimSackId(elsewhere, itemId);
				}
			}
		}

		List<InventorySnapshot.Page> pages = new ArrayList<>();
		for (String sackName : shown) {
			List<InventorySnapshot.Slot> slots = new ArrayList<>();
			if ("Rune".equalsIgnoreCase(sackName)) {
				slots.addAll(runes);
				runes.clear();
			} else {
				boolean events = "Events".equalsIgnoreCase(sackName);
				for (String itemId : contentsByName.get(sackName)) {
					if (events && isClaimedSackId(elsewhere, itemId)) {
						continue;
					}
					slots.add(InventoryDecodeSupport.toUiSlot(
						new InventoryDecoder.Stack(itemId, sackAmount(counts, itemId), new CompoundTag(), List.of(), false)
					));
				}
				if ("Gemstone".equalsIgnoreCase(sackName)) {
					slots.addAll(gemExtras);
					gemExtras.clear();
				}
			}
			pages.add(new InventorySnapshot.Page(sackName, slots, 9));
		}

		other.addAll(gemExtras);
		other.addAll(runes);
		if (!other.isEmpty()) {
			pages.add(new InventorySnapshot.Page(UNSORTED_PAGE, other, 9));
		}
		return pages;
	}

	private static boolean holdsSomething(
		String sackName,
		List<String> contents,
		Map<String, Integer> counts,
		List<InventorySnapshot.Slot> runes,
		List<InventorySnapshot.Slot> gemExtras,
		Set<String> alreadyListed
	) {
		if ("Rune".equalsIgnoreCase(sackName) && !runes.isEmpty()) {
			return true;
		}
		if ("Gemstone".equalsIgnoreCase(sackName) && !gemExtras.isEmpty()) {
			return true;
		}
		for (String id : contents) {
			if (sackAmount(counts, id) > 0 && !isClaimedSackId(alreadyListed, id)) {
				return true;
			}
		}
		return false;
	}

	private static String sackItemFor(Map<String, String> sackItems, String sackName) {
		String item = sackItems == null ? null : sackItems.get(sackName);
		if (item != null && !item.isBlank()) {
			return item;
		}
		return sackName.toUpperCase(Locale.ROOT).replace(' ', '_') + "_SACK";
	}

	static List<String> expandGemstoneContents(List<String> contents) {
		String[] tiers = {"ROUGH_", "FLAWED_", "FINE_", "FLAWLESS_", "PERFECT_"};
		LinkedHashSet<String> seenGems = new LinkedHashSet<>();
		List<String> out = new ArrayList<>();
		if (contents != null) {
			for (String itemId : contents) {
				if (itemId == null || itemId.isBlank()) {
					continue;
				}
				String upper = itemId.toUpperCase(Locale.ROOT);
				String gem = null;
				for (String tier : tiers) {
					if (upper.startsWith(tier) && upper.endsWith("_GEM")) {
						gem = upper.substring(tier.length(), upper.length() - "_GEM".length());
						break;
					}
				}
				if (gem == null) {
					continue;
				}
				seenGems.add(gem);
			}
		}
		// Always include every known gem (incl. Citrine) so Perfect tier rows stay complete.
		for (String gem : GEMSTONE_TYPES) {
			seenGems.add(gem);
		}
		for (String gem : seenGems) {
			for (String tier : tiers) {
				out.add(tier + gem + "_GEM");
			}
		}
		return out;
	}

	static boolean isGemstoneSackId(String id) {
		if (id == null || id.isBlank()) {
			return false;
		}
		String key = id.toUpperCase(Locale.ROOT);
		return key.endsWith("_GEM") || key.endsWith("_GEMSTONE");
	}

	static boolean isRuneSackId(String id) {
		if (id == null || id.isBlank()) {
			return false;
		}
		String key = id.toUpperCase(Locale.ROOT);
		return key.startsWith("RUNE_") || key.contains("_RUNE");
	}

	static int sackAmount(Map<String, Integer> counts, String itemId) {
		if (itemId == null) {
			return 0;
		}
		String key = itemId.toUpperCase(Locale.ROOT);
		Integer amount = counts.get(key);
		if (amount == null) {
			amount = counts.get(key.replace('-', ':'));
		}
		if (amount == null) {
			amount = counts.get(key.replace(':', '-'));
		}
		return amount == null ? 0 : amount;
	}

	static void claimSackId(Set<String> claimed, String id) {
		if (id == null) {
			return;
		}
		String key = id.toUpperCase(Locale.ROOT);
		claimed.add(key);
		claimed.add(key.replace('-', ':'));
		claimed.add(key.replace(':', '-'));
	}

	static boolean isClaimedSackId(Set<String> claimed, String id) {
		if (id == null) {
			return false;
		}
		String key = id.toUpperCase(Locale.ROOT);
		return claimed.contains(key)
			|| claimed.contains(key.replace('-', ':'))
			|| claimed.contains(key.replace(':', '-'));
	}
}
