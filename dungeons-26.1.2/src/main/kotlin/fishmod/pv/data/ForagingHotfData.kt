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

/** NEU {@code hotflayout.json} perk defs for Heart of the Forest. */
object ForagingHotfData {
	@JvmRecord
	data class PerkDef(val id: String, val name: String, val x: Int, val y: Int, val maxLevel: Int, val ability: Boolean)

	@Volatile
	private var loaded = false

	@Volatile
	private var perks: List<PerkDef> = listOf()

	@Volatile
	private var byId: Map<String, PerkDef> = mapOf()

	@Volatile
	private var maxX = 0

	@Volatile
	private var maxY = 0

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
	fun perks(): List<PerkDef> {
		ensureLoaded()
		return perks
	}

	@JvmStatic
	fun perk(id: String?): PerkDef? {
		ensureLoaded()
		if (id.isNullOrBlank()) {
			return null
		}
		return byId[id]
	}

	@JvmStatic
	fun displayName(apiId: String?): String = perk(apiId)?.name ?: titleCase(apiId ?: "")

	/** 0 when the perk isn't in NEU's layout, so its max is unknown. */
	@JvmStatic
	fun maxLevel(apiId: String?): Int {
		val def = perk(apiId) ?: return 0
		return maxOf(1, def.maxLevel)
	}

	@JvmStatic
	fun maxX(): Int {
		ensureLoaded()
		return maxX
	}

	@JvmStatic
	fun maxY(): Int {
		ensureLoaded()
		return maxY
	}

	private fun clear() {
		perks = listOf()
		byId = mapOf()
		maxX = 0
		maxY = 0
	}

	private fun loadFromDisk() {
		val path = ProfileViewer.DIR.resolve("neu-repo", "repo", "constants", "hotflayout.json")
		if (!Files.isRegularFile(path)) {
			ProfileViewer.LOGGER.debug("HOTF layout missing at {}", path)
			clear()
			return
		}
		try {
			Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
				val root = JsonParser.parseReader(reader).asJsonObject
				val hotf = if (root.has("hotf") && root.get("hotf").isJsonObject) root.getAsJsonObject("hotf") else root
				val perkObj = if (hotf.has("perks") && hotf.get("perks").isJsonObject) hotf.getAsJsonObject("perks") else null
				if (perkObj == null) {
					return
				}
				val map = LinkedHashMap<String, PerkDef>()
				val list = ArrayList<PerkDef>()
				var mx = 0
				var my = 0
				for ((id, value) in perkObj.entrySet()) {
					if (value == null || !value.isJsonObject) {
						continue
					}
					val o = value.asJsonObject
					val name = str(o, "name").ifBlank { titleCase(id) }
					val x = num(o, "x").toInt()
					val y = num(o, "y").toInt()
					val max = maxOf(1, num(o, "maxLevel").toInt())
					val ability = if (o.has("isAbility")) bool(o, "isAbility") else id == "axe_toss" || id == "maniac_slicer"
					val def = PerkDef(id, name, x, y, max, ability)
					map[id] = def
					list.add(def)
					mx = maxOf(mx, x)
					my = maxOf(my, y)
				}
				list.sortWith(compareBy<PerkDef> { it.y }.thenBy { it.x })
				byId = java.util.Map.copyOf(map)
				perks = java.util.List.copyOf(list)
				maxX = mx
				maxY = my
			}
		} catch (ex: IOException) {
			ProfileViewer.LOGGER.warn("Failed to load HOTF layout", ex)
			clear()
		} catch (ex: RuntimeException) {
			if (!SoftDataFailure.isSoft(ex)) {
				throw ex
			}
			ProfileViewer.LOGGER.warn("Failed to load HOTF layout", ex)
			clear()
		}
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

	private fun bool(o: JsonObject, key: String): Boolean = try {
		o.get(key).isJsonPrimitive && o.get(key).asBoolean
	} catch (ignored: IllegalStateException) {
		false
	} catch (ignored: ClassCastException) {
		false
	} catch (ignored: UnsupportedOperationException) {
		false
	}

	private fun num(o: JsonObject?, key: String): Float = Leveling.num(o?.get(key)) ?: 0f

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
