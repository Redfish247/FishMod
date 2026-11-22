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
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.price.HypixelItemsCache
import fishmod.utils.TextStrip
import java.util.Locale
import java.util.function.Function

@JvmRecord
data class DungeonChestHistory(val runs: List<Run>) {
	@JvmRecord
	data class Chest(val type: String, val paid: Boolean, val rerolls: Int, val rewards: List<String>)

	@JvmRecord
	data class Run(val floor: String, val completedMs: Long, val party: List<String>, val chests: List<Chest>) {
		/** Croesus drops runs 72h after completion; the API can keep them longer until the list is rewritten. */
		fun expiresMs(): Long = if (completedMs <= 0L) 0L else completedMs + EXPIRY_MS

		fun expired(nowMs: Long): Boolean = completedMs > 0L && nowMs >= expiresMs()

		fun opened(): Chest? {
			var best: Chest? = null
			for (chest in chests) {
				if (chest.paid && (best == null || typeRank(chest.type) > typeRank(best.type))) {
					best = chest
				}
			}
			return best
		}
	}

	fun present(): Boolean = runs.isNotEmpty()

	companion object {
		const val EXPIRY_MS = 72L * 60L * 60L * 1000L


		private val NAMED_ULTIMATES = setOf("wise", "jerry")

		private val REWARD_ITEM_IDS: Map<String, String> = mapOf(
			"shadow_helmet" to "SHADOW_ASSASSIN_HELMET",
			"shadow_chestplate" to "SHADOW_ASSASSIN_CHESTPLATE",
			"shadow_leggings" to "SHADOW_ASSASSIN_LEGGINGS",
			"shadow_boots" to "SHADOW_ASSASSIN_BOOTS",
			"necro_brooch" to "NECROMANCER_BROOCH"
		)

		// NEU constants/enchants.json ultimate pool, without the ultimate_ prefix. Croesus ids drop it too.
		private val ULTIMATE_ENCHANTS = setOf(
			"chimera", "combo", "swarm", "wise", "rend", "bank", "last_stand", "legion", "no_pain_no_gain",
			"wisdom", "one_for_all", "soul_eater", "reiterate", "inferno", "fatal_tempo", "flash",
			"habanero_tactics", "bobbin_time", "refrigerate", "flowstate", "missile", "first_impression",
			"sunset", "master_jerry", "jerry", "duplex", "the_one"
		)

		private val RARITIES = listOf(
			"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL", "SUPREME"
		)

		private val KUUDRA_TIERS = arrayOf("Basic", "Hot", "Burning", "Fiery", "Infernal")

		@JvmStatic
		fun empty(): DungeonChestHistory = DungeonChestHistory(listOf())

		@JvmStatic
		fun from(member: JsonObject?): DungeonChestHistory {
			val dungeons = Leveling.obj(member?.get("dungeons"))
			val treasures = Leveling.obj(dungeons?.get("treasures")) ?: return empty()
			val chestsByRun = LinkedHashMap<String, MutableList<Chest>>()
			val chestsEl = treasures.get("chests")
			if (chestsEl != null && chestsEl.isJsonArray) {
				for (el in chestsEl.asJsonArray) {
					val c = Leveling.obj(el) ?: continue
					val rewards = ArrayList<String>()
					val rewardList = Leveling.obj(c.get("rewards"))?.get("rewards")
					if (rewardList != null && rewardList.isJsonArray) {
						for (r in rewardList.asJsonArray) {
							val raw = str(r)
							if (raw.isNotBlank()) {
								rewards.add(raw)
							}
						}
					}
					val type = str(c.get("treasure_type")).ifBlank { str(c.get("chest_type")) }
					var rerolls = num(c.get("rerolls")).toInt()
					if (rerolls == 0 && bool(c.get("is_rerolled"))) {
						rerolls = 1
					}
					chestsByRun.getOrPut(str(c.get("run_id"))) { ArrayList() }.add(Chest(
						type,
						bool(c.get("paid")) || bool(c.get("is_opened")),
						rerolls,
						java.util.List.copyOf(rewards)
					))
				}
			}
			val runs = ArrayList<Run>()
			val runsEl = treasures.get("runs")
			if (runsEl != null && runsEl.isJsonArray) {
				for (el in runsEl.asJsonArray) {
					val r = Leveling.obj(el) ?: continue
					val party = ArrayList<String>()
					val participants = r.get("participants")
					if (participants != null && participants.isJsonArray) {
						for (p in participants.asJsonArray) {
							val po = Leveling.obj(p)
							val name = if (po == null) "" else str(po.get("display_name")).trim()
							if (TextStrip.plain(name).isNotBlank()) {
								party.add(name)
							}
						}
					}
					val chests = (chestsByRun[str(r.get("run_id"))] ?: listOf()).sortedByDescending { typeRank(it.type) }
					runs.add(Run(
						floorLabel(str(r.get("type")), str(r.get("dungeon_type")),
							if (r.has("dungeon_tier")) r.get("dungeon_tier") else r.get("tier_id")),
						num(r.get("completion_ts")),
						java.util.List.copyOf(party),
						java.util.List.copyOf(chests)
					))
				}
			}
			runs.sortByDescending { it.completedMs }
			return DungeonChestHistory(java.util.List.copyOf(runs))
		}

		@JvmStatic
		fun typeRank(type: String?): Int = when (type?.lowercase(Locale.ROOT) ?: "") {
			"free" -> 1
			"paid" -> 2
			"wood" -> 1
			"gold" -> 2
			"diamond" -> 3
			"emerald" -> 4
			"obsidian" -> 5
			"bedrock" -> 6
			else -> 0
		}

		@JvmStatic
		fun prettyReward(raw: String?): String =
			prettyReward(raw, { HypixelItemsCache.get(it) }, { NeuRepoCache.get(it) })

		internal fun prettyReward(raw: String?, hypixel: Function<String, JsonObject?>, neu: Function<String, JsonObject?>): String {
			if (raw.isNullOrBlank()) {
				return ""
			}
			if (raw.startsWith("ESSENCE:")) {
				val parts = splitColon(raw)
				val type = if (parts.size > 1) InventoryDecoder.prettyWords(parts[1]) else ""
				return if (parts.size > 2) parts[2] + "x " + type + " Essence" else "$type Essence"
			}
			if (raw.startsWith("SHARD:")) {
				val parts = splitColon(raw)
				val type = if (parts.size > 1) InventoryDecoder.prettyWords(parts[1]) else ""
				val count = if (parts.size > 2) maxOf(1L, parseLongOr(parts[2], 1L)).toInt() else 1
				return (if (count > 1) "${count}x " else "") + type + " Shard"
			}
			val id = raw.lowercase(Locale.ROOT)
			rewardItemName(id, hypixel, neu)?.let { return it }
			if (isBook(raw)) {
				val us = id.lastIndexOf('_')
				val level = id.substring(us + 1).toInt()
				bookLoreName(bookDef(id, neu), level)?.let { return it }
				val core = enchantCore(id.substring(0, us))
				val name = InventoryDecoder.prettyWords(core) + " " + roman(level)
				return if (core in NAMED_ULTIMATES) "Ultimate $name" else name
			}
			return InventoryDecoder.prettyWords(id)
		}

		private fun splitColon(raw: String): List<String> = raw.split(":").dropLastWhile { it.isEmpty() }

		private fun enchantCore(base: String): String {
			if (base == "master_jerry") {
				return "jerry"
			}
			return base.removePrefix("ultimate_")
		}

		private fun bookDef(id: String, neu: Function<String, JsonObject?>): JsonObject? {
			val us = id.lastIndexOf('_')
			val level = id.substring(us + 1)
			val core = enchantCore(id.substring(0, us)).uppercase(Locale.ROOT)
			return neu.apply("ULTIMATE_$core;$level") ?: neu.apply("$core;$level")
		}

		private fun isUltimateBook(def: JsonObject?): Boolean =
			def != null && def.has("internalname") && def.get("internalname").isJsonPrimitive &&
				def.get("internalname").asString.uppercase(Locale.ROOT).startsWith("ULTIMATE_")

		private fun bookLoreName(def: JsonObject?, level: Int): String? {
			if (def == null || !def.has("lore") || !def.get("lore").isJsonArray) {
				return null
			}
			val suffix = " " + roman(level)
			for (line in def.getAsJsonArray("lore")) {
				val text = TextStrip.plain(str(line)).trim()
				if (text.endsWith(suffix)) {
					return text
				}
			}
			return null
		}

		@JvmStatic
		fun isEssence(raw: String?): Boolean = raw != null && raw.startsWith("ESSENCE:")

		@JvmStatic
		fun isBook(raw: String?): Boolean {
			if (raw == null || isEssence(raw) || raw.contains("tier")) {
				return false
			}
			val us = raw.lastIndexOf('_')
			if (us <= 0 || us >= raw.length - 1 || !raw.substring(us + 1).all { Character.isDigit(it) }) {
				return false
			}
			return parseLongOr(raw.substring(us + 1), 0L) <= 10L
		}

		@JvmStatic
		fun rewardTier(raw: String?): String = rewardTier(raw, { HypixelItemsCache.get(it) }, { NeuRepoCache.get(it) })

		internal fun rewardTier(raw: String?, hypixel: Function<String, JsonObject?>, neu: Function<String, JsonObject?>): String {
			if (raw.isNullOrBlank()) {
				return ""
			}
			if (isEssence(raw)) {
				return "ESSENCE"
			}
			val itemId = if (raw.startsWith("SHARD:")) {
				val parts = splitColon(raw)
				if (parts.size > 1) "SHARD_" + parts[1].uppercase(Locale.ROOT) else ""
			} else {
				itemIdFor(raw.lowercase(Locale.ROOT))
			}
			val def = hypixel.apply(itemId)
			if (def != null && def.has("tier") && def.get("tier").isJsonPrimitive) {
				return def.get("tier").asString.uppercase(Locale.ROOT)
			}
			if (isBook(raw)) {
				val id = raw.lowercase(Locale.ROOT)
				val book = bookDef(id, neu)
				if (book != null) {
					return if (isUltimateBook(book)) "ULTIMATE" else "BOOK"
				}
				val base = id.substring(0, id.lastIndexOf('_'))
				return if (enchantCore(base) in ULTIMATE_ENCHANTS) "ULTIMATE" else "BOOK"
			}
			return neuRarity(neu.apply(itemId))
		}

		private fun neuRarity(def: JsonObject?): String {
			if (def == null || !def.has("lore") || !def.get("lore").isJsonArray || def.getAsJsonArray("lore").isEmpty) {
				return ""
			}
			val lore = def.getAsJsonArray("lore")
			val last = TextStrip.plain(str(lore.get(lore.size() - 1))).trim().uppercase(Locale.ROOT)
			var rarity = ""
			for (r in RARITIES) {
				val spaced = r.replace('_', ' ')
				if ((last == spaced || last.startsWith("$spaced ")) && spaced.length > rarity.length) {
					rarity = r
				}
			}
			return rarity
		}

		internal fun itemIdFor(id: String): String {
			REWARD_ITEM_IDS[id]?.let { return it }
			if (id.endsWith("_dye") && id.length > 4) {
				return "DYE_" + id.substring(0, id.length - 4).uppercase(Locale.ROOT)
			}
			return id.uppercase(Locale.ROOT)
		}

		private fun rewardItemName(id: String, hypixel: Function<String, JsonObject?>, neu: Function<String, JsonObject?>): String? {
			val itemId = itemIdFor(id)
			val def = hypixel.apply(itemId)
			if (def != null && def.has("name") && def.get("name").isJsonPrimitive) {
				val name = TextStrip.plain(def.get("name").asString).trim()
				if (name.isNotBlank()) {
					return name
				}
			}
			val neuDef = neu.apply(itemId)
			if (neuDef != null && neuDef.has("displayname") && neuDef.get("displayname").isJsonPrimitive) {
				val name = TextStrip.plain(neuDef.get("displayname").asString).trim()
				if (name.isNotBlank()) {
					return name
				}
			}
			return REWARD_ITEM_IDS[id]?.let { InventoryDecoder.prettyWords(it.lowercase(Locale.ROOT)) }
		}

		private fun parseLongOr(s: String, fallback: Long): Long = s.trim().toLongOrNull() ?: fallback

		// Kuudra runs have type KUUDRA and tier_id (e.g. "INFERNAL") instead of dungeon_type / dungeon_tier.
		private fun floorLabel(runType: String?, dungeonType: String?, tierEl: JsonElement?): String {
			val type = dungeonType?.lowercase(Locale.ROOT) ?: ""
			val tierName = str(tierEl).lowercase(Locale.ROOT)
			val tier = num(tierEl).toInt()
			if ("kuudra".equals(runType, ignoreCase = true) || type.contains("kuudra")) {
				for (name in KUUDRA_TIERS) {
					val lower = name.lowercase(Locale.ROOT)
					if (tierName == lower || type.contains(lower)) {
						return "Kuudra $name"
					}
				}
				if (tierName == "none" || type == "kuudra" && tier == 0) {
					return "Kuudra Basic"
				}
				return if (tier >= 1 && tier <= KUUDRA_TIERS.size) "Kuudra " + KUUDRA_TIERS[tier - 1] else "Kuudra"
			}
			val master = type.startsWith("master")
			if (tier <= 0) {
				return if (master) "M?" else "Entrance"
			}
			return (if (master) "M" else "F") + tier
		}

		@JvmStatic
		fun isKuudra(floor: String?): Boolean = floor != null && floor.startsWith("Kuudra")

		private fun roman(n: Int): String = when (n) {
			1 -> "I"
			2 -> "II"
			3 -> "III"
			4 -> "IV"
			5 -> "V"
			6 -> "VI"
			7 -> "VII"
			8 -> "VIII"
			9 -> "IX"
			10 -> "X"
			else -> n.toString()
		}

		private fun str(el: JsonElement?): String {
			if (el == null || !el.isJsonPrimitive) {
				return ""
			}
			return try {
				el.asString
			} catch (ignored: RuntimeException) {
				""
			}
		}

		private fun num(el: JsonElement?): Long {
			if (el == null || !el.isJsonPrimitive || !el.asJsonPrimitive.isNumber) {
				return 0L
			}
			return maxOf(0L, el.asDouble.toLong())
		}

		private fun bool(el: JsonElement?): Boolean {
			if (el == null || !el.isJsonPrimitive) {
				return false
			}
			return try {
				el.asBoolean
			} catch (ignored: RuntimeException) {
				false
			}
		}
	}
}
