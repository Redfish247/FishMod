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
import java.util.Locale
import java.util.UUID

/**
 * Event tabs: Bingo (resources + bingo profile) and Chocolate Factory ({@code events.easter}).
 */
class EventsSnapshot private constructor(chocolate: Chocolate?, bingo: Bingo?) {
	@JvmRecord
	data class Employee(val id: String, val name: String, val level: Int)

	@JvmRecord
	data class Rabbit(val id: String, val name: String, val count: Int, val rarity: String)

	/** Years are SkyBlock years; 0 means the API didn't record one. */
	@JvmRecord
	data class Chocobit(val id: Int, val ownedYear: Int, val expiryYear: Int) {
		fun expired(currentYear: Int): Boolean = expiryYear > 0 && expiryYear < currentYear

		companion object {
			@JvmStatic
			fun active(bits: List<Chocobit>, currentYear: Int): Int = bits.count { !it.expired(currentYear) }
		}
	}

	@JvmRecord
	data class BingoGoal(val id: String, val name: String, val lore: String, val progress: Long, val required: Long, val community: Boolean)

	@JvmRecord
	data class BingoEvent(val key: Int, val points: Long, val completedGoals: List<String>)

	@JvmRecord
	data class Chocolate(
		val chocolate: Long,
		val totalChocolate: Long,
		val chocolateSincePrestige: Long,
		val chocolateLevel: Int,
		val clickUpgrades: Int,
		val multiplierUpgrades: Int,
		val rabbitRarityUpgrades: Int,
		val barnCapacityLevel: Int,
		val timeTowerLevel: Int,
		val timeTowerCharges: Int,
		val timeTowerActivationMs: Long,
		val employees: List<Employee>,
		val uniqueRabbits: Int,
		val totalRabbitDuplicates: Int,
		val breakfastEggs: Long,
		val lunchEggs: Long,
		val dinnerEggs: Long,
		val hitmenSlots: Int,
		val missedEggs: Long,
		val cocoaFortuneUpgrades: Int,
		val chocolateSpent: Long,
		val topRabbits: List<Rabbit>,
		val rabbitCounts: Map<String, Int>,
		val selectedFaction: String,
		val factionLevel: Int,
		val chocobits: List<Chocobit>,
		val chocobitsFound: Int,
		val chocobitsPresent: Boolean
	) {
		fun ownedRabbits(): Set<String> = rabbitCounts.keys

		fun prestige(): ChocolateFactoryData.Prestige = ChocolateFactoryData.prestige(chocolateLevel, chocolateSincePrestige)

		fun hitmen(): ChocolateFactoryData.Hitmen = ChocolateFactoryData.hitmen(hitmenSlots)

		fun present(): Boolean = totalChocolate > 0 || chocolate > 0 || chocolateLevel > 0 || employees.isNotEmpty()

		companion object {
			@JvmStatic
			fun empty(): Chocolate = Chocolate(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, listOf(), 0, 0, 0, 0, 0, 0, 0, 0, 0, listOf(),
				mapOf(), "", 0, listOf(), 0, false)
		}
	}

	@JvmRecord
	data class Bingo(
		val hasBingoProfile: Boolean,
		val bingoProfileName: String,
		val bingoFirstJoinMs: Long,
		val currentEventId: Int,
		val currentEventName: String,
		val currentModifier: String,
		val currentStartMs: Long,
		val currentEndMs: Long,
		val currentGoals: List<BingoGoal>,
		val history: List<BingoEvent>,
		val totalPoints: Long,
		val eventsPlayed: Int
	) {
		fun present(): Boolean = hasBingoProfile || currentGoals.isNotEmpty() || history.isNotEmpty()

		companion object {
			@JvmStatic
			fun empty(): Bingo = Bingo(false, "", 0L, 0, "", "", 0L, 0L, listOf(), listOf(), 0L, 0)
		}
	}

	@get:JvmName("chocolate") val chocolate: Chocolate = chocolate ?: Chocolate.empty()
	@get:JvmName("bingo") val bingo: Bingo = bingo ?: Bingo.empty()

	fun withBingoResources(resources: JsonObject?): EventsSnapshot = EventsSnapshot(chocolate, mergeBingoResources(bingo, resources))

	fun withBingoHistory(playerBingo: JsonObject?): EventsSnapshot = EventsSnapshot(chocolate, mergeBingoHistory(bingo, playerBingo))

	companion object {
		private val ID_SEPARATORS = Regex("[_\\-]+")
		private val FORMAT_CODE = Regex("§[0-9a-fk-or]")

		@JvmStatic
		fun empty(): EventsSnapshot = EventsSnapshot(Chocolate.empty(), Bingo.empty())

		@JvmStatic
		fun fromMember(member: JsonObject?, profilesRoot: JsonObject?, uuid: UUID?): EventsSnapshot =
			EventsSnapshot(parseChocolate(member), parseBingoProfile(profilesRoot, uuid))

		private fun parseChocolate(member: JsonObject?): Chocolate {
			val events = Leveling.obj(member?.get("events"))
			val easter = events?.let { Leveling.obj(it.get("easter")) } ?: return Chocolate.empty()
			val chocolate = longOf(easter, "chocolate")
			val total = longOf(easter, "total_chocolate")
			val sincePrestige = longOf(easter, "chocolate_since_prestige")
			val level = intOf(easter, "chocolate_level")
			val click = intOf(easter, "click_upgrades")
			val multi = intOf(easter, "chocolate_multiplier_upgrades")
			val rarity = intOf(easter, "rabbit_rarity_upgrades")
			val barn = intOf(easter, "rabbit_barn_capacity_level")

			val tower = Leveling.obj(easter.get("time_tower"))
			val towerLevel = if (tower == null) 0 else intOf(tower, "level")
			val towerCharges = if (tower == null) 0 else intOf(tower, "charges")
			val towerActive = if (tower == null) 0L else longOf(tower, "activation_time")

			val employees = ArrayList<Employee>()
			val emp = Leveling.obj(easter.get("employees"))
			if (emp != null) {
				for ((id, value) in emp.entrySet()) {
					if (id == null || !value.isJsonPrimitive) {
						continue
					}
					val lvl = maxOf(0, Math.round(num(value)).toInt())
					employees.add(Employee(id, ChocolateEmployees.displayName(id, prettyId(id)) ?: id, lvl))
				}
				employees.sortWith(compareBy<Employee> { ChocolateEmployees.orderOf(it.id) }.thenBy { it.name })
			}

			val rabbits = Leveling.obj(easter.get("rabbits"))
			var unique = 0
			var duplicates = 0
			var top = ArrayList<Rabbit>()
			val owned = HashMap<String, Int>()
			var breakfast = 0L
			var lunch = 0L
			var dinner = 0L
			var faction = ""
			var factionLevel = 0
			if (rabbits != null) {
				val eggs = Leveling.obj(rabbits.get("collected_eggs"))
				if (eggs != null) {
					breakfast = longOf(eggs, "breakfast")
					lunch = longOf(eggs, "lunch")
					dinner = longOf(eggs, "dinner")
				}
				faction = str(rabbits, "selected_faction").lowercase(Locale.ROOT)
				factionLevel = intOf(rabbits, "faction_level")
				for ((id, value) in rabbits.entrySet()) {
					// Faction fields share the rabbits object with the rabbit counts.
					if (id == null || id == "collected_eggs" || id == "collected_locations" ||
						id == "selected_faction" || id == "faction_level"
					) {
						continue
					}
					if (!value.isJsonPrimitive) {
						continue
					}
					val count = maxOf(0, Math.round(num(value)).toInt())
					if (count <= 0) {
						continue
					}
					unique++
					duplicates += count
					owned.merge(id.lowercase(Locale.ROOT), count) { a, b -> a + b }
					top.add(Rabbit(id, prettyId(id), count, HoppityRabbitsData.rarityOf(id).uppercase(Locale.ROOT)))
				}
				top.sortWith(compareByDescending<Rabbit> { it.count }.thenBy { it.name })
				if (top.size > 12) {
					top = ArrayList(top.subList(0, 12))
				}
			}

			val hitmen = Leveling.obj(easter.get("rabbit_hitmen"))
			val hitmenSlots = if (hitmen == null) 0 else intOf(hitmen, "rabbit_hitmen_slots")
			val missed = if (hitmen == null) 0L else longOf(hitmen, "missed_uncollected_eggs")

			val shop = Leveling.obj(easter.get("shop"))
			val fortune = if (shop == null) 0 else intOf(shop, "cocoa_fortune_upgrades")
			val spent = if (shop == null) 0L else longOf(shop, "chocolate_spent")

			val bits = Leveling.obj(easter.get("chocobits"))
			val bitsFound = if (bits == null) 0 else intOf(bits, "total_found")
			val chocobits = parseChocobits(bits)

			return Chocolate(
				chocolate, total, sincePrestige, level, click, multi, rarity, barn,
				towerLevel, towerCharges, towerActive,
				java.util.List.copyOf(employees), unique, duplicates,
				breakfast, lunch, dinner, hitmenSlots, missed, fortune, spent,
				java.util.List.copyOf(top), java.util.Map.copyOf(owned), faction, factionLevel,
				java.util.List.copyOf(chocobits), bitsFound, bits != null
			)
		}

		/** Soonest expiry first; bits without a recorded expiry go last. */
		@JvmStatic
		private fun parseChocobits(bits: JsonObject?): List<Chocobit> {
			val out = ArrayList<Chocobit>()
			if (bits == null || !bits.has("owned") || !bits.get("owned").isJsonArray) {
				return out
			}
			for (el in bits.getAsJsonArray("owned")) {
				val bit = Leveling.obj(el)
				val id = if (bit == null) 0 else intOf(bit, "id")
				if (id <= 0) {
					continue
				}
				out.add(Chocobit(id, maxOf(0, intOf(bit, "owned_year")), maxOf(0, intOf(bit, "expiry_year"))))
			}
			out.sortWith(compareBy<Chocobit> { if (it.expiryYear > 0) it.expiryYear else Int.MAX_VALUE }.thenBy { it.id })
			return out
		}

		@JvmStatic
		private fun parseBingoProfile(profilesRoot: JsonObject?, uuid: UUID?): Bingo {
			if (profilesRoot == null || uuid == null) {
				return Bingo.empty()
			}
			val profiles = if (profilesRoot.has("profiles") && profilesRoot.get("profiles").isJsonArray) {
				profilesRoot.getAsJsonArray("profiles")
			} else {
				return Bingo.empty()
			}
			val undashed = uuid.toString().replace("-", "").lowercase(Locale.ROOT)
			for (el in profiles) {
				if (el == null || !el.isJsonObject) {
					continue
				}
				val profile = el.asJsonObject
				val mode = if (profile.has("game_mode") && profile.get("game_mode").isJsonPrimitive) profile.get("game_mode").asString else ""
				if (!"bingo".equals(mode, ignoreCase = true)) {
					continue
				}
				val cute = if (profile.has("cute_name") && profile.get("cute_name").isJsonPrimitive) {
					profile.get("cute_name").asString
				} else {
					"Bingo"
				}
				val members = Leveling.obj(profile.get("members"))
					?: return Bingo(true, cute, 0L, 0, "", "", 0L, 0L, listOf(), listOf(), 0L, 0)
				var member: JsonObject? = null
				for ((rawKey, value) in members.entrySet()) {
					val key = rawKey?.replace("-", "")?.lowercase(Locale.ROOT) ?: ""
					if (key == undashed && value.isJsonObject) {
						member = value.asJsonObject
						break
					}
				}
				if (member == null) {
					member = members.entrySet().firstOrNull { it.value.isJsonObject }?.value?.asJsonObject
				}
				val profileMeta = member?.let { Leveling.obj(it.get("profile")) }
				val join = if (profileMeta != null) longOf(profileMeta, "first_join") else 0L
				return Bingo(true, cute, join, 0, "", "", 0L, 0L, listOf(), listOf(), 0L, 0)
			}
			return Bingo.empty()
		}

		private fun mergeBingoResources(base: Bingo?, resources: JsonObject?): Bingo {
			val b = base ?: Bingo.empty()
			if (resources == null) {
				return b
			}
			val id = intOf(resources, "id")
			val name = str(resources, "name")
			val modifier = str(resources, "modifier")
			val start = longOf(resources, "start")
			val end = longOf(resources, "end")
			val goals = ArrayList<BingoGoal>()
			if (resources.has("goals") && resources.get("goals").isJsonArray) {
				for (el in resources.getAsJsonArray("goals")) {
					if (el == null || !el.isJsonObject) {
						continue
					}
					val g = el.asJsonObject
					val gid = str(g, "id")
					val gname = str(g, "name")
					val lore = str(g, "lore")
					val progress = longOf(g, "progress")
					var required = 0L
					if (g.has("requiredAmount") && g.get("requiredAmount").isJsonPrimitive) {
						required = longOf(g, "requiredAmount")
					} else if (g.has("tiers") && g.get("tiers").isJsonArray) {
						val tiers = g.getAsJsonArray("tiers")
						if (!tiers.isEmpty && tiers.get(tiers.size() - 1).isJsonPrimitive) {
							required = Math.round(num(tiers.get(tiers.size() - 1)))
						}
					}
					val community = g.has("tiers") && g.get("tiers").isJsonArray
					goals.add(BingoGoal(gid, if (gname.isBlank()) prettyId(gid) else strip(gname), strip(lore), progress, required, community))
				}
			}
			return Bingo(
				b.hasBingoProfile, b.bingoProfileName, b.bingoFirstJoinMs,
				id, name, modifier, start, end, java.util.List.copyOf(goals),
				b.history, b.totalPoints, b.eventsPlayed
			)
		}

		private fun mergeBingoHistory(base: Bingo?, playerBingo: JsonObject?): Bingo {
			val b = base ?: Bingo.empty()
			if (playerBingo == null || !playerBingo.has("events") || !playerBingo.get("events").isJsonArray) {
				return b
			}
			val history = ArrayList<BingoEvent>()
			var points = 0L
			for (el in playerBingo.getAsJsonArray("events")) {
				if (el == null || !el.isJsonObject) {
					continue
				}
				val ev = el.asJsonObject
				val key = intOf(ev, "key")
				val pts = longOf(ev, "points")
				points += pts
				val goals = ArrayList<String>()
				val completed = ev.get("completed_goals")
				if (ev.has("completed_goals") && completed.isJsonArray) {
					for (g in completed.asJsonArray) {
						if (g != null && g.isJsonPrimitive) {
							goals.add(g.asString)
						} else if (g != null && g.isJsonArray) {
							for (nested in g.asJsonArray) {
								if (nested != null && nested.isJsonPrimitive) {
									goals.add(nested.asString)
								}
							}
						}
					}
				}
				history.add(BingoEvent(key, pts, java.util.List.copyOf(goals)))
			}
			history.sortByDescending { it.key }
			return Bingo(
				b.hasBingoProfile, b.bingoProfileName, b.bingoFirstJoinMs,
				b.currentEventId, b.currentEventName, b.currentModifier,
				b.currentStartMs, b.currentEndMs, b.currentGoals,
				java.util.List.copyOf(history), points, history.size
			)
		}

		private fun str(obj: JsonObject?, key: String): String {
			if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) {
				return ""
			}
			return try {
				obj.get(key).asString
			} catch (ignored: RuntimeException) {
				""
			}
		}

		private fun intOf(obj: JsonObject?, key: String): Int = maxOf(0L, Math.round(num(obj?.get(key)))).toInt()

		private fun longOf(obj: JsonObject?, key: String): Long = maxOf(0L, Math.round(num(obj?.get(key))))

		private fun num(el: JsonElement?): Double {
			if (el == null || !el.isJsonPrimitive) {
				return 0.0
			}
			return try {
				el.asDouble
			} catch (ignored: RuntimeException) {
				0.0
			}
		}

		private fun prettyId(id: String?): String {
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

		private fun strip(raw: String?): String = if (raw == null) "" else FORMAT_CODE.replace(raw, "").trim()
	}
}
