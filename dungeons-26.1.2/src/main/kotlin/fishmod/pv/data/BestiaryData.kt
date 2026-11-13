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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale

object BestiaryData {
	@JvmRecord
	data class Family(
		val id: String,
		val name: String,
		val categoryId: String,
		val bracket: Int,
		val bracketType: String,
		val cap: Int,
		val mobIds: List<String>,
		val textureValue: String,
		val itemIcon: String
	)

	@JvmRecord
	data class Category(
		val id: String,
		val name: String,
		val textureValue: String,
		val itemIcon: String,
		val families: List<Family>
	)

	private data class Icon(val texture: String, val item: String)

	private val FORMAT_CODE = Regex("§[0-9a-fk-or]")
	private val NON_ALNUM = Regex("[^a-z0-9]+")
	private val EDGE_UNDERSCORE = Regex("^_|_$")
	private val ID_SEPARATORS = Regex("[:_]+")

	@Volatile
	private var loaded = false

	@Volatile
	private var categories: List<Category> = listOf()

	@Volatile
	private var brackets: Map<String, List<Int>> = mapOf()

	@Volatile
	private var bracketSets: Map<String, Map<String, List<Int>>> = mapOf()

	@Volatile
	private var familiesById: Map<String, Family> = mapOf()

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

	@JvmStatic
	fun reload() {
		synchronized(this) {
			loaded = false
			loadFromDisk()
			loaded = true
		}
	}

	@JvmStatic
	fun categories(): List<Category> {
		ensureLoaded()
		return categories
	}

	@JvmStatic
	fun category(id: String?): Category? {
		ensureLoaded()
		if (id == null) {
			return null
		}
		return categories.firstOrNull { it.id.equals(id, ignoreCase = true) }
	}

	@JvmStatic
	fun bracket(bracketId: Int): List<Int> = bracket("", bracketId)

	@JvmStatic
	fun bracket(bracketType: String?, bracketId: Int): List<Int> {
		ensureLoaded()
		val id = bracketId.toString()
		if (!bracketType.isNullOrBlank()) {
			val ladder = bracketSets[bracketType]?.get(id)
			if (!ladder.isNullOrEmpty()) {
				return ladder
			}
		}
		return brackets[id] ?: listOf()
	}

	@JvmStatic
	fun family(id: String?): Family? {
		ensureLoaded()
		return if (id == null) null else familiesById[id]
	}

	private fun clear() {
		categories = listOf()
		brackets = mapOf()
		bracketSets = mapOf()
		familiesById = mapOf()
	}

	private fun loadFromDisk() {
		val path = ProfileViewer.DIR.resolve("neu-repo", "repo", "constants", "bestiary.json")
		if (!Files.isRegularFile(path)) {
			ProfileViewer.LOGGER.warn("Missing NEU bestiary.json at {}", path)
			clear()
			return
		}
		try {
			Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
				val rootEl = JsonParser.parseReader(reader)
				if (rootEl == null || !rootEl.isJsonObject) {
					return
				}
				val root = rootEl.asJsonObject
				val br = parseBracketLadders(obj(root.get("brackets")))
				val sets = LinkedHashMap<String, Map<String, List<Int>>>()
				obj(root.get("bracketSets"))?.let { setsObj ->
					for ((key, value) in setsObj.entrySet()) {
						if (key == null || !value.isJsonObject) {
							continue
						}
						sets[key] = parseBracketLadders(value.asJsonObject)
					}
				}

				val cats = ArrayList<Category>()
				val byId = LinkedHashMap<String, Family>()
				for ((key, value) in root.entrySet()) {
					if (isCatalogMetaKey(key) || !value.isJsonObject) {
						continue
					}
					parseCategory(key, value.asJsonObject, cats, byId)
				}
				brackets = java.util.Map.copyOf(br)
				bracketSets = copyBracketSets(sets)
				categories = java.util.List.copyOf(cats)
				familiesById = java.util.Map.copyOf(byId)
				ProfileViewer.LOGGER.info("Loaded bestiary catalog ({} categories, {} families)", cats.size, byId.size)
			}
		} catch (exception: IOException) {
			ProfileViewer.LOGGER.warn("Failed to load bestiary.json", exception)
			clear()
		} catch (exception: RuntimeException) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception
			}
			ProfileViewer.LOGGER.warn("Failed to load bestiary.json", exception)
			clear()
		}
	}

	private fun parseCategory(id: String, obj: JsonObject, cats: MutableList<Category>, byId: MutableMap<String, Family>) {
		val name = strip(str(obj, "name")).ifBlank { pretty(id) }
		val icon = parseIcon(obj.get("icon"))
		val families = ArrayList<Family>()
		if (obj.has("hasSubcategories") && obj.get("hasSubcategories").isJsonPrimitive && obj.get("hasSubcategories").asBoolean) {
			for ((subKey, value) in obj.entrySet()) {
				if (subKey == null || subKey == "name" || subKey == "icon" || subKey == "hasSubcategories" || !value.isJsonObject) {
					continue
				}
				families.addAll(parseFamilies(id, value.asJsonObject.get("mobs"), byId))
			}
		} else {
			families.addAll(parseFamilies(id, obj.get("mobs"), byId))
		}
		if (families.isEmpty()) {
			return
		}
		cats.add(Category(id, name.ifBlank { id }, icon.texture, icon.item, java.util.List.copyOf(families)))
	}

	@JvmStatic
	private fun parseFamilies(categoryId: String, mobsEl: JsonElement?, byId: MutableMap<String, Family>): List<Family> {
		val out = ArrayList<Family>()
		if (mobsEl == null || !mobsEl.isJsonArray) {
			return out
		}
		var index = 0
		for (el in mobsEl.asJsonArray) {
			if (el == null || !el.isJsonObject) {
				continue
			}
			val mob = el.asJsonObject
			val name = strip(str(mob, "name"))
			val bracket = maxOf(1, intOr(mob, "bracket", 1))
			val bracketType = str(mob, "bracketType")
			val cap = maxOf(0, intOr(mob, "cap", 0))
			val mobIds = ArrayList<String>()
			if (mob.has("mobs") && mob.get("mobs").isJsonArray) {
				for (mid in mob.getAsJsonArray("mobs")) {
					if (mid != null && mid.isJsonPrimitive) {
						val id: String? = mid.asString
						if (!id.isNullOrBlank()) {
							mobIds.add(id.lowercase(Locale.ROOT))
						}
					}
				}
			}
			val icon = parseIcon(mob)
			val famId = categoryId + ":" + index + ":" + slug(name.ifBlank { "mob" })
			val displayName = name.ifBlank { pretty(famId) }.ifBlank { famId }
			val family = Family(famId, displayName, categoryId, bracket, bracketType, cap, java.util.List.copyOf(mobIds),
				icon.texture, icon.item)
			out.add(family)
			byId[famId] = family
			index++
		}
		return out
	}

	@JvmStatic
	private fun parseIcon(el: JsonElement?): Icon {
		if (el == null || !el.isJsonObject) {
			return Icon("", "")
		}
		val obj = el.asJsonObject
		var texture = str(obj, "texture")
		if (texture.isBlank() && obj.has("texture") && obj.get("texture").isJsonArray) {
			for (t in obj.getAsJsonArray("texture")) {
				if (t != null && t.isJsonPrimitive && t.asString.isNotBlank()) {
					texture = t.asString
					break
				}
			}
		}
		val item = str(obj, "item").ifBlank { str(obj, "vanilla") }
		return Icon(texture, item)
	}

	private fun isCatalogMetaKey(key: String?): Boolean =
		key == null || "brackets".equals(key, ignoreCase = true) || "bracketSets".equals(key, ignoreCase = true)

	@JvmStatic
	private fun parseBracketLadders(obj: JsonObject?): Map<String, List<Int>> {
		val out = LinkedHashMap<String, List<Int>>()
		if (obj == null) {
			return out
		}
		for ((key, value) in obj.entrySet()) {
			if (key == null || !value.isJsonArray) {
				continue
			}
			val ladder = ArrayList<Int>()
			for (el in value.asJsonArray) {
				if (el != null && el.isJsonPrimitive) {
					try {
						ladder.add(maxOf(0, el.asInt))
					} catch (ignored: IllegalStateException) {
					} catch (ignored: ClassCastException) {
					} catch (ignored: NumberFormatException) {
					} catch (ignored: UnsupportedOperationException) {
					}
				}
			}
			out[key] = java.util.List.copyOf(ladder)
		}
		return out
	}

	private fun copyBracketSets(sets: Map<String, Map<String, List<Int>>>): Map<String, Map<String, List<Int>>> {
		val out = LinkedHashMap<String, Map<String, List<Int>>>()
		for ((key, value) in sets) {
			out[key] = java.util.Map.copyOf(value)
		}
		return java.util.Map.copyOf(out)
	}

	private fun obj(el: JsonElement?): JsonObject? = if (el != null && el.isJsonObject) el.asJsonObject else null

	private fun str(obj: JsonObject?, key: String): String {
		if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) {
			return ""
		}
		return try {
			obj.get(key).asString
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

	private fun intOr(obj: JsonObject?, key: String, fallback: Int): Int {
		if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) {
			return fallback
		}
		return try {
			obj.get(key).asInt
		} catch (ignored: IllegalStateException) {
			fallback
		} catch (ignored: ClassCastException) {
			fallback
		} catch (ignored: NumberFormatException) {
			fallback
		} catch (ignored: UnsupportedOperationException) {
			fallback
		}
	}

	private fun strip(raw: String?): String {
		if (raw.isNullOrBlank()) {
			return ""
		}
		return FORMAT_CODE.replace(raw, "").trim()
	}

	private fun slug(name: String): String =
		EDGE_UNDERSCORE.replace(NON_ALNUM.replace(name.lowercase(Locale.ROOT), "_"), "")

	@JvmStatic
	fun prettyId(id: String?): String {
		if (id.isNullOrBlank()) {
			return ""
		}
		val out = StringBuilder()
		for (part in id.lowercase(Locale.ROOT).split(ID_SEPARATORS)) {
			if (part.isBlank()) {
				continue
			}
			if (out.isNotEmpty()) {
				out.append(' ')
			}
			out.append(part[0].uppercaseChar())
			if (part.length > 1) {
				out.append(part.substring(1))
			}
		}
		return out.toString()
	}

	private fun pretty(id: String): String = prettyId(id)
}
