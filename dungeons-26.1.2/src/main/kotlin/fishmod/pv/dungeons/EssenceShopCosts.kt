/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.dungeons

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale
import kotlin.math.max

object EssenceShopCosts {
	private val NEU_IDS = mapOf("eagles_advantage" to "eagle_advantage")

	@Volatile
	private var costs: Map<String, IntArray>? = null

	/** Cost of each tier (index 0 = tier 1), or null when the repo doesn't list the perk. */
	@JvmStatic
	fun costs(perkId: String?): IntArray? {
		if (perkId == null) return null
		return lookup(costs ?: load(), perkId)
	}

	@JvmStatic
	fun lookup(map: Map<String, IntArray>, perkId: String): IntArray? {
		val key = perkId.lowercase(Locale.ROOT)
		return map[key] ?: map[NEU_IDS[key] ?: key]
	}

	@JvmStatic
	fun nextTier(costs: IntArray?, level: Int): Long =
		if (costs == null || level < 0 || level >= costs.size) 0L else costs[level].toLong()

	@JvmStatic
	fun toMax(costs: IntArray?, level: Int): Long {
		if (costs == null) return 0L
		var sum = 0L
		for (i in max(0, level) until costs.size) sum += costs[i]
		return sum
	}

	@Synchronized
	private fun load(): Map<String, IntArray> {
		costs?.let { return it }
		val path = ProfileViewer.DIR.resolve("neu-repo", "repo", "constants", "essenceshops.json")
		if (!Files.isRegularFile(path)) return emptyMap()
		val out = HashMap<String, IntArray>()
		try {
			Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
				out.putAll(parse(JsonParser.parseReader(reader).asJsonObject))
			}
		} catch (ex: Exception) {
			ProfileViewer.LOGGER.warn("Failed to load essence shop costs", ex)
		}
		return java.util.Map.copyOf(out).also { costs = it }
	}

	@JvmStatic
	fun parse(root: JsonObject): Map<String, IntArray> {
		val out = HashMap<String, IntArray>()
		for ((_, shop) in root.entrySet()) {
			if (!shop.isJsonObject) continue
			for ((perkKey, perk) in shop.asJsonObject.entrySet()) {
				if (!perk.isJsonObject) continue
				val arr = perk.asJsonObject.get("costs")
				if (arr == null || !arr.isJsonArray) continue
				val list = arr.asJsonArray
				val tiers = IntArray(list.size()) { i ->
					val t = list.get(i)
					if (t.isJsonPrimitive && t.asJsonPrimitive.isNumber) t.asInt else 0
				}
				out[perkKey.lowercase(Locale.ROOT)] = tiers
			}
		}
		return out
	}
}
