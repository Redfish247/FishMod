/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale

/** NEU {@code attribute_shards.json} catalog + level costs. */
object AttributeShardsData {
	@JvmRecord
	data class Def(
		val stackId: String, val displayName: String, val abilityName: String, val rarity: String,
		val internalName: String, val bazaarName: String
	) {
		fun iconId(): String =
			if (internalName.isBlank()) "ATTRIBUTE_SHARD_" + stackId.uppercase(Locale.ROOT) + ";1" else internalName

		/** Prefer bazaar product id (SHARD_*), then stripped internal name. */
		fun priceId(): String {
			if (bazaarName.isNotBlank()) {
				return bazaarName
			}
			val icon = iconId()
			val semi = icon.indexOf(';')
			return if (semi >= 0) icon.substring(0, semi) else icon
		}
	}

	@Volatile
	private var loaded = false

	@Volatile
	private var defs: List<Def> = listOf()

	@Volatile
	private var byStackId: Map<String, Def> = mapOf()

	@Volatile
	private var costsByRarity: Map<String, IntArray> = mapOf()

	private val RARITY_ORDER = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE")

	@JvmStatic
	fun ensureLoaded() {
		if (loaded) {
			return
		}
		synchronized(this) {
			if (loaded) {
				return
			}
			loadFromDisk()
			loaded = true
		}
	}

	/** Reload after NEU-REPO updates. */
	@JvmStatic
	fun reload() {
		synchronized(this) {
			loaded = false
			loadFromDisk()
			loaded = true
		}
	}

	@JvmStatic
	fun all(): List<Def> {
		ensureLoaded()
		return defs
	}

	@JvmStatic
	fun def(stackId: String?): Def? {
		ensureLoaded()
		if (stackId.isNullOrBlank()) {
			return null
		}
		return byStackId[stackId.lowercase(Locale.ROOT)]
	}

	/** Match by display name or {@code SHARD_<NAME>} bazaar id (safari critters). */
	@JvmStatic
	fun byCritterId(critterId: String?): Def? {
		ensureLoaded()
		if (critterId.isNullOrBlank()) {
			return null
		}
		val key = critterId.trim().lowercase(Locale.ROOT).replace(' ', '_')
		val bazaar = "shard_$key"
		val pretty = key.replace('_', ' ')
		val squashed = key.replace("_", "")
		return defs.firstOrNull { def ->
			def.bazaarName.equals(bazaar, ignoreCase = true) ||
				def.displayName.equals(pretty, ignoreCase = true) ||
				def.displayName.replace(" ", "").equals(squashed, ignoreCase = true)
		}
	}

	/** Higher is rarer; unknown rarities sort below COMMON. */
	@JvmStatic
	fun rarityRank(rarity: String?): Int = if (rarity == null) -1 else RARITY_ORDER.indexOf(rarity.uppercase(Locale.ROOT))

	@JvmStatic
	fun maxLevel(rarity: String?): Int = costs(rarity).size

	@JvmStatic
	fun shardsForMax(rarity: String?): Int = costs(rarity).sumOf { maxOf(0, it) }

	@JvmStatic
	fun levelFromShards(rarity: String?, shards: Int): Int {
		val costs = costs(rarity)
		if (costs.isEmpty() || shards <= 0) {
			return 0
		}
		var level = 0
		var remaining = shards
		for (cost in costs) {
			if (cost <= 0 || remaining < cost) {
				break
			}
			remaining -= cost
			level++
		}
		return level
	}

	private fun costs(rarity: String?): IntArray {
		ensureLoaded()
		val common = costsByRarity["COMMON"] ?: IntArray(0)
		if (rarity.isNullOrBlank()) {
			return common
		}
		return costsByRarity[rarity.uppercase(Locale.ROOT)] ?: common
	}

	private fun clear() {
		defs = listOf()
		byStackId = mapOf()
		costsByRarity = mapOf()
	}

	private fun loadFromDisk() {
		val path = ProfileViewer.DIR.resolve("neu-repo", "repo", "constants", "attribute_shards.json")
		if (!Files.isRegularFile(path)) {
			ProfileViewer.LOGGER.debug("Attribute shards missing at {}", path)
			clear()
			return
		}
		try {
			Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
				val root = JsonParser.parseReader(reader).asJsonObject
				val costs = LinkedHashMap<String, IntArray>()
				val levelling = if (root.has("attribute_levelling") && root.get("attribute_levelling").isJsonObject) {
					root.getAsJsonObject("attribute_levelling")
				} else {
					null
				}
				if (levelling != null) {
					for ((key, value) in levelling.entrySet()) {
						if (value == null || !value.isJsonArray) {
							continue
						}
						val arr = value.asJsonArray
						costs[key.uppercase(Locale.ROOT)] = IntArray(arr.size()) { i ->
							try {
								arr.get(i).asInt
							} catch (ignored: IllegalStateException) {
								0
							} catch (ignored: ClassCastException) {
								0
							} catch (ignored: NumberFormatException) {
								0
							} catch (ignored: UnsupportedOperationException) {
								0
							}
						}
					}
				}
				costsByRarity = java.util.Map.copyOf(costs)

				val list = ArrayList<Def>()
				val map = LinkedHashMap<String, Def>()
				if (root.has("attributes") && root.get("attributes").isJsonArray) {
					for (el in root.getAsJsonArray("attributes")) {
						if (el == null || !el.isJsonObject) {
							continue
						}
						val o = el.asJsonObject
						val internal = str(o, "internalName")
						val stackId = stackIdFromInternal(internal)
						if (stackId.isBlank()) {
							continue
						}
						val ability = str(o, "abilityName")
						var display = str(o, "displayName")
						if (display.isBlank()) {
							display = if (ability.isBlank()) titleCase(stackId) else ability
						}
						val rarity = str(o, "rarity").ifBlank { "COMMON" }
						val bazaar = str(o, "bazaarName")
						val def = Def(stackId, display, ability, rarity.uppercase(Locale.ROOT), internal, bazaar)
						map.putIfAbsent(stackId, def)
						list.add(def)
					}
				}
				list.sortWith { a, b -> a.displayName.compareTo(b.displayName, ignoreCase = true) }
				defs = java.util.List.copyOf(list)
				byStackId = java.util.Map.copyOf(map)
			}
		} catch (ex: IOException) {
			ProfileViewer.LOGGER.warn("Failed to load attribute shards", ex)
			clear()
		} catch (ex: RuntimeException) {
			if (!SoftDataFailure.isSoft(ex)) {
				throw ex
			}
			ProfileViewer.LOGGER.warn("Failed to load attribute shards", ex)
			clear()
		}
	}

	private fun stackIdFromInternal(internalName: String?): String {
		if (internalName.isNullOrBlank()) {
			return ""
		}
		var id: String = internalName
		if (id.startsWith("ATTRIBUTE_SHARD_")) {
			id = id.substring("ATTRIBUTE_SHARD_".length)
		}
		val semi = id.indexOf(';')
		if (semi >= 0) {
			id = id.substring(0, semi)
		}
		return id.lowercase(Locale.ROOT)
	}

	private fun str(o: JsonObject?, key: String?): String {
		if (o == null || key == null || !o.has(key) || !o.get(key).isJsonPrimitive) {
			return ""
		}
		return try {
			o.get(key).asString
		} catch (ignored: IllegalStateException) {
			""
		} catch (ignored: ClassCastException) {
			""
		} catch (ignored: NumberFormatException) {
			""
		} catch (ignored: UnsupportedOperationException) {
			""
		}
	}

	private fun titleCase(raw: String?): String {
		if (raw.isNullOrBlank()) {
			return ""
		}
		val sb = StringBuilder()
		for (part in raw.lowercase(Locale.ROOT).replace('-', '_').split("_")) {
			if (part.isBlank()) {
				continue
			}
			if (sb.isNotEmpty()) {
				sb.append(' ')
			}
			sb.append(part[0].uppercaseChar())
			if (part.length > 1) {
				sb.append(part.substring(1))
			}
		}
		return sb.toString()
	}
}
