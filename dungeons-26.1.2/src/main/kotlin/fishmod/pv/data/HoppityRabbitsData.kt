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
import java.util.Collections
import java.util.Locale

/** NEU {@code hoppity.json} rabbit id → rarity (COMMON … DIVINE). */
object HoppityRabbitsData {
	@Volatile
	private var loaded = false

	@Volatile
	private var rarityById: Map<String, String> = mapOf()

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

	/** Uppercase rarity, or {@code COMMON} when unknown. */
	@JvmStatic
	fun rarityOf(rabbitId: String?): String {
		ensureLoaded()
		if (rabbitId.isNullOrBlank()) {
			return "COMMON"
		}
		return rarityById[rabbitId.lowercase(Locale.ROOT)] ?: "COMMON"
	}

	private fun loadFromDisk() {
		val path = ProfileViewer.DIR.resolve("neu-repo", "repo", "constants", "hoppity.json")
		if (!Files.isRegularFile(path)) {
			ProfileViewer.LOGGER.warn("hoppity.json missing at {}", path)
			rarityById = mapOf()
			return
		}
		try {
			Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
				val root = JsonParser.parseReader(reader).asJsonObject
				val hoppity = objectAt(root, "hoppity") ?: root
				val map = HashMap<String, String>()
				objectAt(hoppity, "rarities")?.let { rarities ->
					for ((rarityKey, value) in rarities.entrySet()) {
						if (rarityKey == null || !value.isJsonObject) {
							continue
						}
						val tier = rarityKey.uppercase(Locale.ROOT)
						val holder = value.asJsonObject
						val rabbits = if (holder.has("rabbits") && holder.get("rabbits").isJsonArray) holder.getAsJsonArray("rabbits") else null
						if (rabbits == null) {
							continue
						}
						for (el in rabbits) {
							if (el != null && el.isJsonPrimitive) {
								map[el.asString.lowercase(Locale.ROOT)] = tier
							}
						}
					}
				}
				// Specials are mythic/divine named rabbits; keep existing rarity if listed, else MYTHIC.
				objectAt(hoppity, "special")?.let { special ->
					for (id in special.keySet()) {
						if (id.isNullOrBlank()) {
							continue
						}
						map.putIfAbsent(id.lowercase(Locale.ROOT), "MYTHIC")
					}
				}
				rarityById = Collections.unmodifiableMap(map)
				ProfileViewer.LOGGER.info("Loaded {} chocolate rabbits from hoppity.json", rarityById.size)
			}
		} catch (exception: Exception) {
			if (exception !is IOException && exception !is RuntimeException) {
				throw exception
			}
			if (exception is RuntimeException && !SoftDataFailure.isSoft(exception)) {
				throw exception
			}
			ProfileViewer.LOGGER.warn("Failed loading hoppity.json", exception)
			rarityById = mapOf()
		}
	}

	private fun objectAt(obj: JsonObject, key: String): JsonObject? =
		if (obj.has(key) && obj.get(key).isJsonObject) obj.getAsJsonObject(key) else null
}
