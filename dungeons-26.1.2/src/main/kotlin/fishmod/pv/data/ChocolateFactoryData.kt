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
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.TreeMap

object ChocolateFactoryData {
	@JvmRecord
	data class Prestige(val level: Int, val maxLevel: Int, val threshold: Long, val earned: Long) {
		fun maxed(): Boolean = level >= maxLevel || threshold <= 0L

		fun needed(): Long = if (maxed()) 0L else maxOf(0L, threshold - earned)

		fun ready(): Boolean = !maxed() && earned >= threshold
	}

	@JvmRecord
	data class Hitmen(val slots: Int, val maxSlots: Int, val paid: Long, val total: Long, val nextSlotCost: Long) {
		fun fraction(): Double = if (total <= 0L) 0.0 else minOf(1.0, paid / total.toDouble())

		fun maxed(): Boolean = maxSlots > 0 && slots >= maxSlots
	}

	@JvmRecord
	data class Faction(val id: String, val name: String, val rabbitsByRarity: Map<String, List<String>>) {
		fun size(): Int = rabbitsByRarity.values.sumOf { it.size }

		fun found(ownedRabbits: Collection<String>): Int =
			rabbitsByRarity.values.sumOf { ids -> ids.count { ownedRabbits.contains(it) } }
	}

	private const val SKYBLOCK_EPOCH_MS = 1_560_275_700_000L
	private const val SKYBLOCK_YEAR_MS = 124L * 60L * 60L * 1000L

	@Volatile
	private var loaded = false
	private var prestigeChocolate = TreeMap<Int, Long>()
	private var hitmanSlotCost = LongArray(0)
	private var factions: List<Faction> = listOf()

	@JvmStatic
	fun prestige(level: Int, earnedThisPrestige: Long): Prestige {
		ensureLoaded()
		val lvl = maxOf(1, level)
		val max = if (prestigeChocolate.isEmpty()) lvl else maxOf(1, prestigeChocolate.lastKey())
		val next = prestigeChocolate[lvl + 1]
		return Prestige(lvl, max, next ?: 0L, maxOf(0L, earnedThisPrestige))
	}

	@JvmStatic
	fun hitmen(slots: Int): Hitmen {
		ensureLoaded()
		val max = hitmanSlotCost.size
		val owned = maxOf(0, minOf(slots, max))
		var paid = 0L
		var total = 0L
		for (i in 0 until max) {
			total += hitmanSlotCost[i]
			if (i < owned) {
				paid += hitmanSlotCost[i]
			}
		}
		val next = if (owned < max) hitmanSlotCost[owned] else 0L
		return Hitmen(maxOf(0, slots), max, paid, total, next)
	}

	@JvmStatic
	fun factions(): List<Faction> {
		ensureLoaded()
		return factions
	}

	@JvmStatic
	fun faction(id: String?): Faction? {
		if (id.isNullOrBlank()) {
			return null
		}
		val key = id.lowercase(Locale.ROOT)
		return factions().firstOrNull { it.id == key }
	}

	@JvmStatic
	fun factionOf(rabbitId: String?): Faction? {
		if (rabbitId.isNullOrBlank()) {
			return null
		}
		val key = rabbitId.lowercase(Locale.ROOT)
		return factions().firstOrNull { faction -> faction.rabbitsByRarity.values.any { it.contains(key) } }
	}

	/** Employee level tier colours (white, green, blue, purple, gold, pink, aqua), matching Skyblock-PV. */
	@JvmStatic
	fun employeeLevelColor(level: Int): Int = when {
		level < 10 -> 0xFFFFFFFF.toInt()
		level < 75 -> 0xFF55FF55.toInt()
		level < 125 -> 0xFF5555FF.toInt()
		level < 175 -> 0xFFAA00AA.toInt()
		level < 200 -> 0xFFFFAA00.toInt()
		level < 220 -> 0xFFFF55FF.toInt()
		else -> 0xFF55FFFF.toInt()
	}

	@JvmStatic
	fun currentSkyBlockYear(nowMs: Long): Int = ((nowMs - SKYBLOCK_EPOCH_MS) / SKYBLOCK_YEAR_MS).toInt() + 1

	private fun ensureLoaded() {
		if (loaded) {
			return
		}
		synchronized(this) {
			if (loaded) {
				return
			}
			try {
				val stream = ChocolateFactoryData::class.java.classLoader
					.getResourceAsStream("assets/fishmod/pv/data/chocolate_factory.json")
				if (stream == null) {
					ProfileViewer.LOGGER.warn("Missing chocolate_factory.json")
				} else {
					stream.use { parse(JsonParser.parseReader(InputStreamReader(it, StandardCharsets.UTF_8)).asJsonObject) }
				}
			} catch (exception: IOException) {
				ProfileViewer.LOGGER.warn("Failed to load chocolate_factory.json", exception)
			} catch (exception: RuntimeException) {
				if (!SoftDataFailure.isSoft(exception)) {
					throw exception
				}
				ProfileViewer.LOGGER.warn("Failed to load chocolate_factory.json", exception)
			}
			loaded = true
		}
	}

	@JvmStatic
	private fun parse(root: JsonObject) {
		val prestige = TreeMap<Int, Long>()
		obj(root.get("prestige_chocolate"))?.let { prestigeObj ->
			for ((key, value) in prestigeObj.entrySet()) {
				try {
					prestige[key.toInt()] = maxOf(0L, value.asLong)
				} catch (ignored: RuntimeException) {
				}
			}
		}
		prestigeChocolate = prestige

		val costs = root.get("hitman_slot_cost")
		if (costs != null && costs.isJsonArray) {
			val array = costs.asJsonArray
			hitmanSlotCost = LongArray(array.size()) { i ->
				try {
					maxOf(0L, array.get(i).asLong)
				} catch (ignored: RuntimeException) {
					0L
				}
			}
		}

		val list = ArrayList<Faction>()
		obj(root.get("factions"))?.let { factionObj ->
			for ((rawId, value) in factionObj.entrySet()) {
				val rarities = obj(value) ?: continue
				val byRarity = LinkedHashMap<String, List<String>>()
				for ((rarity, entry) in rarities.entrySet()) {
					val ids = ArrayList<String>()
					if (entry.isJsonArray) {
						for (el in entry.asJsonArray) {
							if (el.isJsonPrimitive) {
								ids.add(el.asString.lowercase(Locale.ROOT))
							}
						}
					} else if (entry.isJsonPrimitive) {
						ids.add(entry.asString.lowercase(Locale.ROOT))
					}
					byRarity[rarity.uppercase(Locale.ROOT)] = java.util.List.copyOf(ids)
				}
				val id = rawId.lowercase(Locale.ROOT)
				if (id.isEmpty()) {
					continue
				}
				val name = id[0].uppercaseChar() + id.substring(1)
				list.add(Faction(id, name, Collections.unmodifiableMap(byRarity)))
			}
		}
		factions = java.util.List.copyOf(list)
	}

	private fun obj(el: JsonElement?): JsonObject? = if (el != null && el.isJsonObject) el.asJsonObject else null
}
