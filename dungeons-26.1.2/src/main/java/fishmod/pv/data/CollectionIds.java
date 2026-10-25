/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Hypixel collection ids often use legacy forms ({@code LOG:1}, {@code INK_SACK:3}). */
public final class CollectionIds {
	private static final Map<String, String[]> ALIASES = Map.ofEntries(
		Map.entry("INK_SACK:3", new String[] { "INK_SACK-3", "COCOA_BEANS", "DYE_COCOA" }),
		Map.entry("INK_SACK:4", new String[] { "INK_SACK-4", "LAPIS_LAZULI", "DYE_LAPIS" }),
		Map.entry("INK_SACK", new String[] { "INK_SAC", "DYE_BLACK" }),
		Map.entry("CARROT_ITEM", new String[] { "CARROT" }),
		Map.entry("POTATO_ITEM", new String[] { "POTATO" }),
		Map.entry("NETHER_STALK", new String[] { "NETHER_WART" }),
		Map.entry("SULPHUR", new String[] { "GUNPOWDER" }),
		Map.entry("SAND:1", new String[] { "SAND-1", "RED_SAND" }),
		Map.entry("LOG", new String[] { "OAK_LOG", "LOG-0" }),
		Map.entry("LOG:1", new String[] { "LOG-1", "SPRUCE_LOG" }),
		Map.entry("LOG:2", new String[] { "LOG-2", "BIRCH_LOG" }),
		Map.entry("LOG:3", new String[] { "LOG-3", "JUNGLE_LOG" }),
		Map.entry("LOG_2", new String[] { "LOG_2-0", "ACACIA_LOG" }),
		Map.entry("LOG_2:1", new String[] { "LOG_2-1", "DARK_OAK_LOG" }),
		Map.entry("RAW_FISH", new String[] { "RAW_FISH-0", "COD" }),
		Map.entry("RAW_FISH:1", new String[] { "RAW_FISH-1", "SALMON" }),
		Map.entry("RAW_FISH:2", new String[] { "RAW_FISH-2", "CLOWNFISH", "TROPICAL_FISH" }),
		Map.entry("RAW_FISH:3", new String[] { "RAW_FISH-3", "PUFFERFISH" }),
		Map.entry("MUTTON", new String[] { "MUTTON", "RAW_MUTTON" }),
		Map.entry("PORK", new String[] { "PORK", "PORKCHOP", "RAW_PORKCHOP" }),
		Map.entry("RABBIT", new String[] { "RABBIT", "RAW_RABBIT" }),
		Map.entry("FEATHER", new String[] { "FEATHER" }),
		Map.entry("LEATHER", new String[] { "LEATHER" }),
		Map.entry("SEEDS", new String[] { "SEEDS", "WHEAT_SEEDS" }),
		Map.entry("MELON", new String[] { "MELON", "MELON_SLICE" }),
		Map.entry("PUMPKIN", new String[] { "PUMPKIN" }),
		Map.entry("CACTUS", new String[] { "CACTUS" }),
		Map.entry("SUGAR_CANE", new String[] { "SUGAR_CANE" }),
		Map.entry("MUSHROOM_COLLECTION", new String[] { "MUSHROOM_COLLECTION" }),
		Map.entry("DOUBLE_PLANT", new String[] { "DOUBLE_PLANT", "SUNFLOWER", "DOUBLE_PLANT-0" }),
		Map.entry("WILD_ROSE", new String[] { "WILD_ROSE", "POPPY", "RED_ROSE", "RED_FLOWER" }),
		Map.entry("MOONFLOWER", new String[] { "MOONFLOWER" }),
		Map.entry("MYCEL", new String[] { "MYCEL", "MYCELIUM" }),
		Map.entry("ENDER_STONE", new String[] { "ENDER_STONE", "END_STONE" }),
		Map.entry("RAW_CHICKEN", new String[] { "RAW_CHICKEN", "CHICKEN" })
	);

	/**
	 * Collection ids whose profile amounts are stored as separate component items
	 * (sum these when the aggregate key is missing).
	 */
	private static final Map<String, String[]> COMPOSITE_PARTS = Map.of(
		"MUSHROOM_COLLECTION", new String[] { "RED_MUSHROOM", "BROWN_MUSHROOM" },
		"GEMSTONE_COLLECTION", new String[] {
			"RUBY", "JADE", "SAPPHIRE", "AMETHYST", "AMBER", "TOPAZ", "JASPER", "OPAL",
			"AQUAMARINE", "CITRINE", "ONYX", "PERIDOT"
		}
	);

	/** Prefer modern / NEU-friendly ids for icons. */
	private static final Map<String, String> ICON_IDS = Map.ofEntries(
		Map.entry("INK_SACK:3", "INK_SACK-3"),
		Map.entry("INK_SACK:4", "INK_SACK-4"),
		Map.entry("INK_SACK", "INK_SAC"),
		Map.entry("CARROT_ITEM", "CARROT_ITEM"),
		Map.entry("POTATO_ITEM", "POTATO_ITEM"),
		Map.entry("NETHER_STALK", "NETHER_STALK"),
		Map.entry("SAND:1", "SAND-1"),
		Map.entry("LOG", "LOG"),
		Map.entry("LOG:1", "LOG-1"),
		Map.entry("LOG:2", "LOG-2"),
		Map.entry("LOG:3", "LOG-3"),
		Map.entry("LOG_2", "LOG_2"),
		Map.entry("LOG_2:1", "LOG_2-1"),
		Map.entry("RAW_FISH", "RAW_FISH"),
		Map.entry("RAW_FISH:1", "RAW_FISH-1"),
		Map.entry("RAW_FISH:2", "RAW_FISH-2"),
		Map.entry("RAW_FISH:3", "RAW_FISH-3"),
		Map.entry("MUSHROOM_COLLECTION", "RED_MUSHROOM"),
		Map.entry("DOUBLE_PLANT", "DOUBLE_PLANT"),
		Map.entry("MYCEL", "MYCELIUM"),
		Map.entry("ENDER_STONE", "ENDER_STONE"),
		Map.entry("SEEDS", "SEEDS"),
		Map.entry("MELON", "MELON")
	);

	private CollectionIds() {
	}

	public static String normalize(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		return id.trim().toUpperCase(Locale.ROOT);
	}

	public static String iconId(String collectionId) {
		String key = normalize(collectionId);
		if (key.isEmpty()) {
			return "";
		}
		String mapped = ICON_IDS.get(key);
		if (mapped != null) {
			return mapped;
		}
		return key.replace(':', '-');
	}

	public static List<String> lookupKeys(String collectionId) {
		String key = normalize(collectionId);
		Set<String> keys = new LinkedHashSet<>();
		if (key.isEmpty()) {
			return List.of();
		}
		addVariants(keys, key);
		String[] aliases = ALIASES.get(key);
		if (aliases != null) {
			for (String alias : aliases) {
				addVariants(keys, alias);
			}
		}
		// Reverse: if UI id is an alias of a legacy key, include the legacy key.
		for (var entry : ALIASES.entrySet()) {
			for (String alias : entry.getValue()) {
				if (normalize(alias).equals(key) || normalize(alias).replace('-', ':').equals(key.replace('-', ':'))) {
					addVariants(keys, entry.getKey());
					for (String other : entry.getValue()) {
						addVariants(keys, other);
					}
				}
			}
		}
		return new ArrayList<>(keys);
	}

	/** Component item ids to sum for aggregate collections, or empty. */
	public static List<String> compositeParts(String collectionId) {
		String key = normalize(collectionId);
		String[] parts = COMPOSITE_PARTS.get(key);
		if (parts == null || parts.length == 0) {
			return List.of();
		}
		return List.of(parts);
	}

	private static void addVariants(Set<String> keys, String raw) {
		String key = normalize(raw);
		if (key.isEmpty()) {
			return;
		}
		keys.add(key);
		keys.add(key.replace(':', '-'));
		keys.add(key.replace('-', ':'));
	}
}
