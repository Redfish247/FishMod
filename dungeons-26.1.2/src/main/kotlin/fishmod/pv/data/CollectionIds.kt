/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object CollectionIds {
	private val ALIASES: Map<String, Array<String>> = mapOf(
		"INK_SACK:3" to arrayOf("INK_SACK-3", "COCOA_BEANS", "DYE_COCOA"),
		"INK_SACK:4" to arrayOf("INK_SACK-4", "LAPIS_LAZULI", "DYE_LAPIS"),
		"INK_SACK" to arrayOf("INK_SAC", "DYE_BLACK"),
		"CARROT_ITEM" to arrayOf("CARROT"),
		"POTATO_ITEM" to arrayOf("POTATO"),
		"NETHER_STALK" to arrayOf("NETHER_WART"),
		"SULPHUR" to arrayOf("GUNPOWDER"),
		"SAND:1" to arrayOf("SAND-1", "RED_SAND"),
		"LOG" to arrayOf("OAK_LOG", "LOG-0"),
		"LOG:1" to arrayOf("LOG-1", "SPRUCE_LOG"),
		"LOG:2" to arrayOf("LOG-2", "BIRCH_LOG"),
		"LOG:3" to arrayOf("LOG-3", "JUNGLE_LOG"),
		"LOG_2" to arrayOf("LOG_2-0", "ACACIA_LOG"),
		"LOG_2:1" to arrayOf("LOG_2-1", "DARK_OAK_LOG"),
		"RAW_FISH" to arrayOf("RAW_FISH-0", "COD"),
		"RAW_FISH:1" to arrayOf("RAW_FISH-1", "SALMON"),
		"RAW_FISH:2" to arrayOf("RAW_FISH-2", "CLOWNFISH", "TROPICAL_FISH"),
		"RAW_FISH:3" to arrayOf("RAW_FISH-3", "PUFFERFISH"),
		"MUTTON" to arrayOf("MUTTON", "RAW_MUTTON"),
		"PORK" to arrayOf("PORK", "PORKCHOP", "RAW_PORKCHOP"),
		"RABBIT" to arrayOf("RABBIT", "RAW_RABBIT"),
		"FEATHER" to arrayOf("FEATHER"),
		"LEATHER" to arrayOf("LEATHER"),
		"SEEDS" to arrayOf("SEEDS", "WHEAT_SEEDS"),
		"MELON" to arrayOf("MELON", "MELON_SLICE"),
		"PUMPKIN" to arrayOf("PUMPKIN"),
		"CACTUS" to arrayOf("CACTUS"),
		"SUGAR_CANE" to arrayOf("SUGAR_CANE"),
		"MUSHROOM_COLLECTION" to arrayOf("MUSHROOM_COLLECTION"),
		"DOUBLE_PLANT" to arrayOf("DOUBLE_PLANT", "SUNFLOWER", "DOUBLE_PLANT-0"),
		"WILD_ROSE" to arrayOf("WILD_ROSE", "POPPY", "RED_ROSE", "RED_FLOWER"),
		"MOONFLOWER" to arrayOf("MOONFLOWER"),
		"MYCEL" to arrayOf("MYCEL", "MYCELIUM"),
		"ENDER_STONE" to arrayOf("ENDER_STONE", "END_STONE"),
		"RAW_CHICKEN" to arrayOf("RAW_CHICKEN", "CHICKEN")
	)

	private val COMPOSITE_PARTS: Map<String, Array<String>> = mapOf(
		"MUSHROOM_COLLECTION" to arrayOf("RED_MUSHROOM", "BROWN_MUSHROOM"),
		"GEMSTONE_COLLECTION" to arrayOf("RUBY", "JADE", "SAPPHIRE", "AMETHYST", "AMBER", "TOPAZ", "JASPER", "OPAL", "AQUAMARINE", "CITRINE", "ONYX", "PERIDOT")
	)

	private val ICON_IDS: Map<String, String> = mapOf(
		"INK_SACK:3" to "INK_SACK-3",
		"INK_SACK:4" to "INK_SACK-4",
		"INK_SACK" to "INK_SAC",
		"CARROT_ITEM" to "CARROT_ITEM",
		"POTATO_ITEM" to "POTATO_ITEM",
		"NETHER_STALK" to "NETHER_STALK",
		"SAND:1" to "SAND-1",
		"LOG" to "LOG",
		"LOG:1" to "LOG-1",
		"LOG:2" to "LOG-2",
		"LOG:3" to "LOG-3",
		"LOG_2" to "LOG_2",
		"LOG_2:1" to "LOG_2-1",
		"RAW_FISH" to "RAW_FISH",
		"RAW_FISH:1" to "RAW_FISH-1",
		"RAW_FISH:2" to "RAW_FISH-2",
		"RAW_FISH:3" to "RAW_FISH-3",
		"MUSHROOM_COLLECTION" to "RED_MUSHROOM",
		"DOUBLE_PLANT" to "DOUBLE_PLANT",
		"MYCEL" to "MYCELIUM",
		"ENDER_STONE" to "ENDER_STONE",
		"SEEDS" to "SEEDS",
		"MELON" to "MELON"
	)

	// Memo: called per collection item per frame; ids are a small fixed set.
	private val LOOKUP_CACHE = ConcurrentHashMap<String, List<String>>()

	@JvmStatic
	fun normalize(id: String?): String {
		if (id.isNullOrBlank()) {
			return ""
		}
		return id.trim().uppercase(Locale.ROOT)
	}

	@JvmStatic
	fun iconId(collectionId: String?): String {
		val key = normalize(collectionId)
		if (key.isEmpty()) {
			return ""
		}
		return ICON_IDS[key] ?: key.replace(':', '-')
	}

	@JvmStatic
	fun lookupKeys(collectionId: String?): List<String> {
		val key = normalize(collectionId)
		if (key.isEmpty()) {
			return listOf()
		}
		LOOKUP_CACHE[key]?.let { return it }
		if (LOOKUP_CACHE.size > 4096) {
			LOOKUP_CACHE.clear()
		}
		val computed = java.util.List.copyOf(computeLookupKeys(key))
		LOOKUP_CACHE[key] = computed
		return computed
	}

	private fun computeLookupKeys(key: String): Set<String> {
		val keys = LinkedHashSet<String>()
		addVariants(keys, key)
		ALIASES[key]?.forEach { addVariants(keys, it) }
		for ((legacy, aliases) in ALIASES) {
			for (alias in aliases) {
				if (normalize(alias) == key || normalize(alias).replace('-', ':') == key.replace('-', ':')) {
					addVariants(keys, legacy)
					for (other in aliases) {
						addVariants(keys, other)
					}
				}
			}
		}
		return keys
	}

	@JvmStatic
	fun compositeParts(collectionId: String?): List<String> {
		val parts = COMPOSITE_PARTS[normalize(collectionId)]
		if (parts == null || parts.isEmpty()) {
			return listOf()
		}
		return java.util.List.of(*parts)
	}

	private fun addVariants(keys: MutableSet<String>, raw: String?) {
		val key = normalize(raw)
		if (key.isEmpty()) {
			return
		}
		keys.add(key)
		keys.add(key.replace(':', '-'))
		keys.add(key.replace('-', ':'))
	}
}
