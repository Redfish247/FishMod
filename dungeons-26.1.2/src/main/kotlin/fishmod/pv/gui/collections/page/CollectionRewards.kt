/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections.page

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import fishmod.pv.data.MinionCosts
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.SkyBlockStats
import fishmod.pv.gui.SkyBlockSymbols
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.price.HypixelItemsCache
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier

/** Maps Hypixel's collection unlock text ("Enchanted Bread Recipe") to NEU items. */
internal object CollectionRewards {
	enum class Kind { ITEM, MINION, XP, STAT, OTHER }

	/**
	 * `itemId` is a real item (tooltip, rarity, recipe). `iconId` is what gets drawn and can be a
	 * stand-in, like a level I potion for a brewing recipe or a plain enchanted book NEU doesn't have.
	 */
	data class Reward(
		val text: String, val kind: Kind, val itemId: String?, val label: String, val suffix: String, val iconId: String?,
	) {
		fun isItem(): Boolean = itemId != null

		fun hasIcon(): Boolean = iconId != null
	}

	/** Recipe cells A1..C3 in reading order; blank cells have a null id. Forge inputs fill cells in order. */
	class Recipe(val ids: Array<String?>, val counts: IntArray, val outputCount: Int, val forge: Boolean)

	const val XP_COLOR = 0xFF55FFFF.toInt()
	private val MINION = Regex("^(.+) Minion Recipes?$")
	private val RECIPE = Regex("^(.+) (Recipes?)$")
	private val PET = Regex("^\\[Lvl 1] (.+)$")
	private val BOOK = Regex("^Enchanted Book \\((.+) ([IVXL]+)\\)$")
	private val POTION = Regex("^(.+) Potion$")
	private val STAT = Regex("^\\+[\\d,.]+%?\\S*\\s+(.+)$")
	private val FORMAT_CODE = Regex("§.")
	private const val FORGE = " Dwarven Forge"
	private val CELLS = arrayOf("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3")
	private val RESOLVED = ConcurrentHashMap<String, Reward>()
	private val COLORS = ConcurrentHashMap<String, Int>()
	@Volatile private var hypixelNames: Map<String, String> = mapOf()
	@Volatile private var hypixelNamesSize = -1

	@JvmStatic
	fun resolve(unlock: String): Reward {
		RESOLVED[unlock]?.let { return it }
		val reward = parse(unlock, { idForName(it) }, { NeuRepoCache.get(it) != null })
		if (NeuRepoCache.isReady() && HypixelItemsCache.allItems().isNotEmpty()) {
			RESOLVED[unlock] = reward
		}
		return reward
	}

	@JvmStatic
	fun parse(unlock: String?, idForName: Function<String, String?>): Reward = parse(unlock, idForName) { false }

	/** `exists` answers whether an NEU item id exists, for rewards whose id follows a naming pattern. */
	@JvmStatic
	fun parse(unlock: String?, idForName: Function<String, String?>, exists: Predicate<String>): Reward {
		val raw = unlock?.trim() ?: ""
		val text = SkyBlockSymbols.replace(raw)
		if (text.endsWith("SkyBlock XP")) {
			return Reward(text, Kind.XP, null, text, "", null)
		}
		MINION.matchEntire(raw)?.let { minion ->
			val base = minion.groupValues[1] + " Minion"
			val id = idForName.apply("$base I")
			return Reward(text, Kind.MINION, id, base, "Recipes", id)
		}
		RECIPE.matchEntire(raw)?.let { recipe ->
			var name = recipe.groupValues[1]
			var suffix = recipe.groupValues[2]
			if (name.endsWith(FORGE)) {
				name = name.substring(0, name.length - FORGE.length)
				suffix = "Forge $suffix"
			}
			return item(text, name, SkyBlockSymbols.replace(name).trim(), suffix, idForName, exists)
		}
		val stat = STAT.matchEntire(text)
		if (stat != null && SkyBlockStats.byName(stat.groupValues[1]) != null) {
			return Reward(text, Kind.STAT, null, text, "", null)
		}
		return Reward(text, Kind.OTHER, null, text, "", null)
	}

	private fun item(
		text: String, name: String, label: String, suffix: String, idForName: Function<String, String?>, exists: Predicate<String>,
	): Reward {
		PET.matchEntire(name)?.let { pet ->
			// Collection pet recipes craft the Common pet, which NEU keeps as TYPE;0.
			val id = idPart(pet.groupValues[1]) + ";0"
			val found = if (exists.test(id)) id else null
			return Reward(text, Kind.ITEM, found, label, suffix, found)
		}
		BOOK.matchEntire(name)?.let { book ->
			val level = roman(book.groupValues[2])
			val base = idPart(book.groupValues[1])
			val enchant = book.groupValues[1] + " " + book.groupValues[2]
			for (id in arrayOf("$base;$level", "ULTIMATE_$base;$level")) {
				if (level > 0 && exists.test(id)) {
					return Reward(text, Kind.ITEM, id, enchant, suffix, id)
				}
			}
			return Reward(text, Kind.ITEM, null, enchant, suffix, "ENCHANTED_BOOK")
		}
		// Brewing recipes unlock every level, so only borrow the level I icon, not its rarity or tooltip.
		POTION.matchEntire(name)?.let { potion ->
			val id = "POTION_" + idPart(potion.groupValues[1]) + ";1"
			if (exists.test(id)) {
				return Reward(text, Kind.ITEM, null, label, suffix, id)
			}
		}
		val id = idForName.apply(name)
		return Reward(text, Kind.ITEM, id, label, suffix, id)
	}

	/** NEU name match; when several items share the name, the only one with a recipe wins. Hypixel names last. */
	@JvmStatic
	fun idForName(name: String): String? =
		pickId(NeuRepoCache.idsForDisplayName(name), { recipe(it) != null }, { hypixelIdForName(name) })

	@JvmStatic
	fun pickId(ids: List<String>, hasRecipe: Predicate<String>, fallback: Supplier<String?>): String? {
		if (ids.size == 1) {
			return ids[0]
		}
		if (ids.isEmpty()) {
			return fallback.get()
		}
		val crafted = ids.filter { hasRecipe.test(it) }
		return if (crafted.size == 1) crafted[0] else null
	}

	// Renamed items keep their old NEU name (Scorched Topaz is still "Hot Stuff" there), so ask Hypixel.
	private fun hypixelIdForName(name: String): String? {
		val items = HypixelItemsCache.allItems()
		if (items.size != hypixelNamesSize) {
			val index = HashMap<String, String>()
			for (item in items) {
				val itemName = item.get("name") as? JsonPrimitive ?: continue
				val itemId = item.get("id") as? JsonPrimitive ?: continue
				val key = itemName.asString.replace(FORMAT_CODE, "").trim().lowercase(Locale.ROOT)
				index.merge(key, itemId.asString) { _, _ -> "" }
			}
			hypixelNames = index
			hypixelNamesSize = items.size
		}
		val id = hypixelNames[name.trim().lowercase(Locale.ROOT)]
		return if (id.isNullOrEmpty()) null else id
	}

	@JvmStatic
	fun isPet(itemId: String?): Boolean {
		val name = NeuRepoCache.get(itemId)?.get("displayname") as? JsonPrimitive
		return name != null && name.asString.contains("{LVL}")
	}

	@JvmStatic
	fun color(reward: Reward): Int {
		if (reward.kind == Kind.XP) {
			return XP_COLOR
		}
		if (reward.kind == Kind.STAT) {
			val stat = STAT.matchEntire(reward.text)
			val style = if (stat != null) SkyBlockStats.byName(stat.groupValues[1]) else null
			return style?.color ?: PvDraw.COLOR_TEXT
		}
		val itemId = reward.itemId ?: return PvDraw.COLOR_TEXT
		COLORS[itemId]?.let { return it }
		val tier: String = SkyBlockItemFactory.resolveTier(itemId)
		val color = SkyBlockItemFactory.tierArgb(tier)
		if (tier.isNotBlank()) {
			COLORS[itemId] = color
		}
		return color
	}

	@JvmStatic
	fun recipe(itemId: String?): Recipe? {
		if (itemId == null) {
			return null
		}
		val item = NeuRepoCache.get(itemId)
		return recipe(MinionCosts.recipe(item)) ?: forgeRecipe(item)
	}

	@JvmStatic
	fun recipe(recipe: JsonObject?): Recipe? {
		if (recipe == null) {
			return null
		}
		val ids = arrayOfNulls<String>(CELLS.size)
		val counts = IntArray(CELLS.size)
		var any = false
		for (i in CELLS.indices) {
			val cell = recipe.get(CELLS[i])
			if (cell == null || !cell.isJsonPrimitive) {
				continue
			}
			val raw = cell.asString.trim()
			if (raw.isEmpty()) {
				continue
			}
			any = any or putCell(ids, counts, i, raw)
		}
		return if (any) Recipe(ids, counts, outputCount(recipe), false) else null
	}

	/** Dwarven Forge inputs from an NEU item's `recipes` list, laid out in reading order. */
	@JvmStatic
	fun forgeRecipe(item: JsonObject?): Recipe? {
		if (item == null || !item.has("recipes") || !item.get("recipes").isJsonArray) {
			return null
		}
		for (el in item.getAsJsonArray("recipes")) {
			if (!el.isJsonObject) {
				continue
			}
			val r = el.asJsonObject
			val type = r.get("type") as? JsonPrimitive
			if (type == null || !"forge".equals(type.asString, ignoreCase = true)
				|| !r.has("inputs") || !r.get("inputs").isJsonArray
			) {
				continue
			}
			val ids = arrayOfNulls<String>(CELLS.size)
			val counts = IntArray(CELLS.size)
			var slot = 0
			for (input in r.getAsJsonArray("inputs")) {
				if (slot < CELLS.size && input.isJsonPrimitive && putCell(ids, counts, slot, input.asString.trim())) {
					slot++
				}
			}
			return if (slot == 0) null else Recipe(ids, counts, outputCount(r), true)
		}
		return null
	}

	private fun putCell(ids: Array<String?>, counts: IntArray, i: Int, raw: String): Boolean {
		if (raw.isEmpty()) {
			return false
		}
		val colon = raw.lastIndexOf(':')
		var id = raw
		var count = 1
		if (colon > 0) {
			// ids like "PET:3" without a count keep their colon
			raw.substring(colon + 1).trim().toDoubleOrNull()?.let {
				count = maxOf(1, it.toInt())
				id = raw.substring(0, colon)
			}
		}
		ids[i] = id.uppercase(Locale.ROOT)
		counts[i] = count
		return true
	}

	private fun outputCount(recipe: JsonObject): Int {
		val count = recipe.get("count")
		if (count != null && count.isJsonPrimitive && count.asJsonPrimitive.isNumber) {
			return maxOf(1, count.asDouble.toInt())
		}
		return 1
	}

	private fun idPart(name: String): String = name.trim().uppercase(Locale.ROOT).replace(' ', '_')

	@JvmStatic
	fun roman(numeral: String): Int {
		var total = 0
		var prev = 0
		for (i in numeral.length - 1 downTo 0) {
			val v = when (numeral[i]) {
				'I' -> 1
				'V' -> 5
				'X' -> 10
				'L' -> 50
				else -> 0
			}
			total += if (v < prev) -v else v
			prev = maxOf(prev, v)
		}
		return total
	}
}
