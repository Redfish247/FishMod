/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import fishmod.pv.price.ItemPricer
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.function.ToDoubleFunction

/**
 * Garden upgrade cost tables from {@code garden_costs.json} (NEU {@code constants/garden.json} plot, crop upgrade and
 * composter tables, plus the per-level chip Sowdust costs from the wiki). All costs are per level, never cumulative.
 */
object GardenCosts {
	@JvmRecord
	data class Cost(val itemId: String, val amount: Long)

	/** {@code x}/{@code y} are the 5x5 map column/row, row 0 at the top. */
	@JvmRecord
	data class Plot(val id: String, val tier: String, val number: Int, val x: Int, val y: Int)

	@JvmRecord
	data class Progress(val spent: Long, val total: Long) {
		fun fraction(): Double = if (total <= 0L) 0.0 else minOf(1.0, spent / total.toDouble())

		fun maxed(): Boolean = total > 0L && spent >= total
	}

	/** Resources still needed to max one or more composter upgrades. */
	@JvmRecord
	data class ComposterRemaining(val copper: Long, val crops: Map<String, Long>, val rare: Map<String, Long>) {
		fun none(): Boolean = copper <= 0L && crops.isEmpty() && rare.isEmpty()

		fun needsItems(): Boolean = crops.isNotEmpty() || rare.isNotEmpty()

		/** Bazaar coin value of the remaining crops; copper has no coin price. */
		fun coins(): Double = coins { ItemPricer.materialPrice(it) }

		fun coins(price: ToDoubleFunction<String>): Double {
			var total = 0.0
			for ((id, amount) in crops) {
				total += maxOf(0.0, price.applyAsDouble(id)) * maxOf(0L, amount)
			}
			for ((id, amount) in rare) {
				total += maxOf(0.0, price.applyAsDouble(id)) * maxOf(0L, amount)
			}
			return total
		}

		fun fullyPriced(): Boolean = unpriced { ItemPricer.materialPrice(it) } == 0

		fun unpriced(price: ToDoubleFunction<String>): Int =
			crops.keys.count { price.applyAsDouble(it) <= 0.0 } + rare.keys.count { price.applyAsDouble(it) <= 0.0 }
	}

	@Volatile
	private var loaded = false
	private var chipSowdust = LongArray(0)
	private var cropUpgradeCopper = LongArray(0)
	private var rareItems: Set<String> = setOf()
	private var plots: List<Plot> = listOf()
	private var plotCosts: Map<String, List<Cost>> = mapOf()
	private var composterLevels: Map<String, List<JsonObject>> = mapOf()

	@JvmStatic
	fun itemCoins(itemId: String?, amount: Long): Double = ItemPricer.materialPrice(itemId) * maxOf(0L, amount)

	/** Sowdust spent getting a chip to {@code level} (level 1 is free) vs. the level 20 total. */
	@JvmStatic
	fun chipSowdust(level: Int): Progress {
		ensureLoaded()
		return Progress(sum(chipSowdust, maxOf(0, level - 1)), sum(chipSowdust, chipSowdust.size))
	}

	@JvmStatic
	fun cropUpgradeMax(): Int {
		ensureLoaded()
		return cropUpgradeCopper.size
	}

	@JvmStatic
	fun cropUpgradeCopper(level: Int): Progress {
		ensureLoaded()
		return Progress(sum(cropUpgradeCopper, maxOf(0, level)), sum(cropUpgradeCopper, cropUpgradeCopper.size))
	}

	@JvmStatic
	fun plots(): List<Plot> {
		ensureLoaded()
		return plots
	}

	/**
	 * Price of the next plot bought in {@code tier}: Hypixel prices by how many plots of that tier are
	 * already unlocked, not by which specific plot. Null when the tier is fully unlocked or unknown.
	 */
	@JvmStatic
	fun nextPlotCost(tier: String?, unlockedInTier: Int): Cost? {
		ensureLoaded()
		val costs = plotCosts[tier?.lowercase(Locale.ROOT) ?: ""]
		if (costs == null || unlockedInTier < 0 || unlockedInTier >= costs.size) {
			return null
		}
		return costs[unlockedInTier]
	}

	@JvmStatic
	fun hasComposterCosts(upgradeId: String?): Boolean {
		ensureLoaded()
		return upgradeId != null && composterLevels.containsKey(upgradeId.lowercase(Locale.ROOT))
	}

	@JvmStatic
	fun composterRemaining(upgradeId: String?, level: Int): ComposterRemaining =
		composterRemaining(mapOf((upgradeId ?: "") to level))

	/** Sums every unpurchased level above the current one; ids without cost data are ignored. */
	@JvmStatic
	fun composterRemaining(levels: Map<String, Int?>): ComposterRemaining {
		ensureLoaded()
		var copper = 0L
		val crops = LinkedHashMap<String, Long>()
		val rare = LinkedHashMap<String, Long>()
		for ((key, value) in levels) {
			val table = composterLevels[key.lowercase(Locale.ROOT)] ?: continue
			val owned = maxOf(0, value ?: 0)
			for (i in owned until table.size) {
				val level = table[i]
				copper += longOf(level, "copper")
				val items = if (level.has("items") && level.get("items").isJsonObject) level.getAsJsonObject("items") else null
				if (items == null) {
					continue
				}
				for (itemKey in items.keySet()) {
					val amount = longOf(items, itemKey)
					if (amount <= 0L) {
						continue
					}
					val id = itemKey.uppercase(Locale.ROOT)
					(if (rareItems.contains(id)) rare else crops).merge(id, amount) { a, b -> a + b }
				}
			}
		}
		return ComposterRemaining(copper, sortedByAmount(crops), sortedByAmount(rare))
	}

	private fun sortedByAmount(input: Map<String, Long>): Map<String, Long> {
		val out = LinkedHashMap<String, Long>()
		for ((id, amount) in input.entries.sortedByDescending { it.value }) {
			out[id] = amount
		}
		return out
	}

	private fun sum(costs: LongArray, count: Int): Long {
		var total = 0L
		for (i in 0 until minOf(count, costs.size)) {
			total += costs[i]
		}
		return total
	}

	private fun ensureLoaded() {
		if (loaded) {
			return
		}
		synchronized(this) {
			if (loaded) {
				return
			}
			try {
				val stream = GardenCosts::class.java.classLoader.getResourceAsStream("assets/fishmod/pv/data/garden_costs.json")
				if (stream == null) {
					ProfileViewer.LOGGER.warn("Missing garden_costs.json")
				} else {
					stream.use { parse(JsonParser.parseReader(InputStreamReader(it, StandardCharsets.UTF_8)).asJsonObject) }
				}
			} catch (exception: IOException) {
				ProfileViewer.LOGGER.warn("Failed to load garden_costs.json", exception)
			} catch (exception: RuntimeException) {
				if (!SoftDataFailure.isSoft(exception)) {
					throw exception
				}
				ProfileViewer.LOGGER.warn("Failed to load garden_costs.json", exception)
			}
			loaded = true
		}
	}

	@JvmStatic
	private fun parse(root: JsonObject) {
		chipSowdust = longArray(root.get("chip_sowdust"))
		cropUpgradeCopper = longArray(root.get("crop_upgrade_copper"))

		val rare = ArrayList<String>()
		if (root.has("composter_rare_items") && root.get("composter_rare_items").isJsonArray) {
			for (el in root.getAsJsonArray("composter_rare_items")) {
				if (el.isJsonPrimitive) {
					rare.add(el.asString.uppercase(Locale.ROOT))
				}
			}
		}
		rareItems = java.util.Set.copyOf(rare)

		val plotList = ArrayList<Plot>()
		obj(root.get("plots"))?.let { plotObj ->
			for ((key, value) in plotObj.entrySet()) {
				val p = obj(value) ?: continue
				val id = key.lowercase(Locale.ROOT)
				val cut = id.lastIndexOf('_')
				val tier = if (cut > 0) id.substring(0, cut) else id
				plotList.add(Plot(id, tier, longOf(p, "number").toInt(), longOf(p, "x").toInt(), longOf(p, "y").toInt()))
			}
		}
		plotList.sortBy { it.number }
		plots = java.util.List.copyOf(plotList)

		val costs = LinkedHashMap<String, List<Cost>>()
		obj(root.get("plot_costs"))?.let { costObj ->
			for ((key, value) in costObj.entrySet()) {
				if (!value.isJsonArray) {
					continue
				}
				val tier = ArrayList<Cost>()
				for (el in value.asJsonArray) {
					val c = obj(el)
					if (c != null && c.has("item") && c.get("item").isJsonPrimitive) {
						tier.add(Cost(c.get("item").asString, longOf(c, "amount")))
					}
				}
				costs[key.lowercase(Locale.ROOT)] = java.util.List.copyOf(tier)
			}
		}
		plotCosts = java.util.Map.copyOf(costs)

		val composter = LinkedHashMap<String, List<JsonObject>>()
		obj(root.get("composter_upgrades"))?.let { compObj ->
			for ((key, value) in compObj.entrySet()) {
				if (!value.isJsonArray) {
					continue
				}
				val levels = value.asJsonArray.map { obj(it) ?: JsonObject() }
				composter[key.lowercase(Locale.ROOT)] = java.util.List.copyOf(levels)
			}
		}
		composterLevels = java.util.Map.copyOf(composter)
	}

	private fun obj(el: JsonElement?): JsonObject? = if (el != null && el.isJsonObject) el.asJsonObject else null

	private fun longArray(el: JsonElement?): LongArray {
		if (el == null || !el.isJsonArray) {
			return LongArray(0)
		}
		val array = el.asJsonArray
		return LongArray(array.size()) { i ->
			try {
				maxOf(0L, array.get(i).asLong)
			} catch (ignored: RuntimeException) {
				0L
			}
		}
	}

	private fun longOf(obj: JsonObject?, key: String): Long {
		if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) {
			return 0L
		}
		return try {
			maxOf(0L, obj.get(key).asLong)
		} catch (ignored: RuntimeException) {
			0L
		}
	}
}
