/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.pv.dungeons.EssenceShopData
import java.util.Locale

class CrimsonSnapshot private constructor(
	selectedFaction: String?,
	@get:JvmName("magesReputation") val magesReputation: Double,
	@get:JvmName("magesReputationHighest") val magesReputationHighest: Double,
	@get:JvmName("barbariansReputation") val barbariansReputation: Double,
	@get:JvmName("barbariansReputationHighest") val barbariansReputationHighest: Double,
	crimsonEssence: Long,
	matriarchPearls: Int,
	matriarchLastAttemptMs: Long,
	minibosses: Map<String, Boolean>?,
	kuudra: Map<KuudraTier, KuudraTierStats>?,
	dojo: Map<DojoChallenge, DojoScore>?,
	lastMinibossesKilled: List<String>?,
	dailyQuests: List<String>?,
	cavityNpcs: Int,
	kuudraFollower: KuudraFollower?,
	abiphoneContacts: List<AbiphoneContact>?,
	abiphoneActive: Int,
	abiphoneRingtone: String?,
	trioContactAddons: Int,
	operatorChipRepaired: Int,
	snakeBestScore: Int,
	tttLosses: Int,
	tttDraws: Int,
	abiphoneSort: String?,
	kuudraCard: CrimsonKuudraCard?,
	crimsonShop: DungeonSnapshot.EssenceShop?
) {
	enum class KuudraTier(@get:JvmName("key") val key: String, @get:JvmName("label") val label: String) {
		NONE("none", "Basic"),
		HOT("hot", "Hot"),
		BURNING("burning", "Burning"),
		FIERY("fiery", "Fiery"),
		INFERNAL("infernal", "Infernal")
	}

	enum class DojoChallenge(@get:JvmName("key") val key: String, @get:JvmName("label") val label: String) {
		MOB_KB("mob_kb", "Force"),
		WALL_JUMP("wall_jump", "Stamina"),
		ARCHER("archer", "Swiftness"),
		SWORD_SWAP("sword_swap", "Discipline"),
		SNAKE("snake", "Mastery"),
		FIREBALL("fireball", "Tenacity"),
		LOCK_HEAD("lock_head", "Control")
	}

	@JvmRecord
	data class KuudraTierStats(val completions: Int, val highestWave: Int) {
		companion object {
			@JvmStatic
			fun empty(): KuudraTierStats = KuudraTierStats(0, 0)
		}
	}

	@JvmRecord
	data class DojoScore(val points: Int, val timeMs: Long) {
		companion object {
			@JvmStatic
			fun empty(): DojoScore = DojoScore(0, 0L)
		}
	}

	@JvmRecord
	data class AbiphoneContact(
		val id: String,
		val name: String,
		val talkedTo: Boolean,
		val completedQuest: Boolean,
		val active: Boolean,
		val incomingCalls: Int,
		val dnd: Boolean
	)

	@JvmRecord
	data class KuudraFollower(val loremaster: Boolean, val cathedralBook: Boolean, val relicClaimedMs: Long, val leggings: Boolean) {
		companion object {
			@JvmField
			val NONE = KuudraFollower(false, false, 0L, false)

			internal fun from(quests: JsonObject?): KuudraFollower {
				if (quests == null) {
					return NONE
				}
				return KuudraFollower(
					bool(quests.get("kuudra_loremaster")),
					bool(quests.get("found_kuudra_book")),
					maxOf(0L, num(quests.get("last_kuudra_relic")).toLong()),
					bool(quests.get("found_kuudra_leggings"))
				)
			}
		}
	}

	@get:JvmName("selectedFaction") val selectedFaction: String = selectedFaction ?: ""
	@get:JvmName("crimsonEssence") val crimsonEssence: Long = maxOf(0L, crimsonEssence)
	@get:JvmName("matriarchPearls") val matriarchPearls: Int = maxOf(0, matriarchPearls)
	@get:JvmName("matriarchLastAttemptMs") val matriarchLastAttemptMs: Long = maxOf(0L, matriarchLastAttemptMs)
	@get:JvmName("minibosses") val minibosses: Map<String, Boolean> = java.util.Map.copyOf(minibosses ?: mapOf())
	private val kuudra: Map<KuudraTier, KuudraTierStats> = java.util.Map.copyOf(kuudra ?: mapOf())
	private val dojo: Map<DojoChallenge, DojoScore> = java.util.Map.copyOf(dojo ?: mapOf())
	@get:JvmName("lastMinibossesKilled") val lastMinibossesKilled: List<String> = java.util.List.copyOf(lastMinibossesKilled ?: listOf())
	@get:JvmName("dailyQuests") val dailyQuests: List<String> = java.util.List.copyOf(dailyQuests ?: listOf())
	@get:JvmName("cavityNpcs") val cavityNpcs: Int = maxOf(0, cavityNpcs)
	@get:JvmName("kuudraFollower") val kuudraFollower: KuudraFollower = kuudraFollower ?: KuudraFollower.NONE
	@get:JvmName("abiphoneContacts") val abiphoneContacts: List<AbiphoneContact> = java.util.List.copyOf(abiphoneContacts ?: listOf())
	@get:JvmName("abiphoneActive") val abiphoneActive: Int = maxOf(0, abiphoneActive)
	@get:JvmName("abiphoneRingtone") val abiphoneRingtone: String = abiphoneRingtone ?: ""
	@get:JvmName("trioContactAddons") val trioContactAddons: Int = maxOf(0, trioContactAddons)
	@get:JvmName("operatorChipRepaired") val operatorChipRepaired: Int = maxOf(0, operatorChipRepaired)
	@get:JvmName("snakeBestScore") val snakeBestScore: Int = maxOf(0, snakeBestScore)
	@get:JvmName("tttLosses") val tttLosses: Int = maxOf(0, tttLosses)
	@get:JvmName("tttDraws") val tttDraws: Int = maxOf(0, tttDraws)
	@get:JvmName("abiphoneSort") val abiphoneSort: String = abiphoneSort ?: ""
	@get:JvmName("kuudraCard") val kuudraCard: CrimsonKuudraCard = kuudraCard ?: CrimsonKuudraCard.empty()
	@get:JvmName("crimsonShop") val crimsonShop: DungeonSnapshot.EssenceShop =
		crimsonShop ?: DungeonSnapshot.EssenceShop.empty("crimson", "Crimson")

	fun withPlayerStats(stats: PlayerStatsSnapshot?): CrimsonSnapshot {
		if (stats == null || stats.isEmpty()) {
			return this
		}
		return rebuild(kuudraCard = kuudraCard.withCombatStats(stats))
	}

	private fun rebuild(
		crimsonEssence: Long = this.crimsonEssence,
		operatorChipRepaired: Int = this.operatorChipRepaired,
		snakeBestScore: Int = this.snakeBestScore,
		tttLosses: Int = this.tttLosses,
		tttDraws: Int = this.tttDraws,
		abiphoneSort: String = this.abiphoneSort,
		kuudraCard: CrimsonKuudraCard = this.kuudraCard,
		crimsonShop: DungeonSnapshot.EssenceShop = this.crimsonShop
	): CrimsonSnapshot = CrimsonSnapshot(
		selectedFaction, magesReputation, magesReputationHighest, barbariansReputation, barbariansReputationHighest,
		crimsonEssence, matriarchPearls, matriarchLastAttemptMs, minibosses, kuudra, dojo, lastMinibossesKilled,
		dailyQuests, cavityNpcs, kuudraFollower, abiphoneContacts, abiphoneActive, abiphoneRingtone, trioContactAddons,
		operatorChipRepaired, snakeBestScore, tttLosses, tttDraws, abiphoneSort, kuudraCard, crimsonShop
	)

	fun factionLabel(): String {
		if (selectedFaction.isBlank()) {
			return "None"
		}
		val lower = selectedFaction.lowercase(Locale.ROOT)
		return when {
			lower.startsWith("mage") -> "MAGE"
			lower.startsWith("barb") -> "BARBARIAN"
			else -> prettyContact(selectedFaction)
		}
	}

	fun factionColor(mageColor: Int, barbColor: Int, noneColor: Int): Int {
		val lower = selectedFaction.lowercase(Locale.ROOT)
		return when {
			lower.startsWith("mage") -> mageColor
			lower.startsWith("barb") -> barbColor
			else -> noneColor
		}
	}

	fun minibossesKilled(): Int = minibosses.values.count { it }

	fun kuudra(tier: KuudraTier): KuudraTierStats = kuudra[tier] ?: KuudraTierStats.empty()

	fun kuudraTotalCompletions(): Int = kuudra.values.sumOf { it.completions }

	fun kuudraHighestClearedWave(): Int =
		KuudraTier.values().reversed().map { kuudra(it) }.firstOrNull { it.completions > 0 }?.highestWave ?: 0

	fun dojo(challenge: DojoChallenge): DojoScore = dojo[challenge] ?: DojoScore.empty()

	fun dojoTotalPoints(): Int = dojo.values.sumOf { it.points }

	fun kuudraLoremaster(): Boolean = kuudraFollower.loremaster

	fun abiphoneQuestsDone(): Int = abiphoneContacts.count { it.completedQuest }

	fun abiphoneDndCount(): Int = abiphoneContacts.count { it.dnd }

	companion object {
		private val MINIBOSSES = arrayOf("BLADESOUL", "MAGE_OUTLAW", "BARBARIAN_DUKE_X", "ASHFANG", "MAGMA_BOSS")

		@JvmStatic
		fun empty(): CrimsonSnapshot {
			val bosses = LinkedHashMap<String, Boolean>()
			for (id in MINIBOSSES) {
				bosses[id] = false
			}
			val kuudra = LinkedHashMap<KuudraTier, KuudraTierStats>()
			for (tier in KuudraTier.values()) {
				kuudra[tier] = KuudraTierStats.empty()
			}
			val dojo = LinkedHashMap<DojoChallenge, DojoScore>()
			for (challenge in DojoChallenge.values()) {
				dojo[challenge] = DojoScore.empty()
			}
			return CrimsonSnapshot(
				"", 0.0, 0.0, 0.0, 0.0, 0L, 0, 0L,
				bosses, kuudra, dojo, listOf(), listOf(), 0, KuudraFollower.NONE,
				listOf(), 0, "", 0, 0, 0, 0, 0, "",
				CrimsonKuudraCard.empty(),
				DungeonSnapshot.EssenceShop.empty("crimson", "Crimson")
			)
		}

		@JvmStatic
		fun fromMember(member: JsonObject?): CrimsonSnapshot {
			if (member == null) {
				return empty()
			}
			val nether = Leveling.obj(member.get("nether_island_player_data"))
			if (nether == null) {
				val base = empty()
				return base.rebuild(
					crimsonEssence = readCrimsonEssence(member),
					operatorChipRepaired = 0, snakeBestScore = 0, tttLosses = 0, tttDraws = 0, abiphoneSort = "",
					kuudraCard = CrimsonKuudraCard.from(member, base.kuudra),
					crimsonShop = EssenceShopData.crimson(member)
				)
			}

			val faction = str(nether.get("selected_faction"))
			val mage = num(nether.get("mages_reputation"))
			val mageHigh = maxOf(mage, num(nether.get("mages_reputation_highest")))
			val barb = num(nether.get("barbarians_reputation"))
			val barbHigh = maxOf(barb, num(nether.get("barbarians_reputation_highest")))

			val matriarch = Leveling.obj(nether.get("matriarch"))
			val pearls = if (matriarch == null) 0 else num(matriarch.get("pearls_collected")).toInt()
			val lastPearl = if (matriarch == null) 0L else num(matriarch.get("last_attempt")).toLong()

			val quests = Leveling.obj(nether.get("quests"))
			val bosses = LinkedHashMap<String, Boolean>()
			val minibossData = quests?.let { Leveling.obj(it.get("miniboss_data")) }
			for (id in MINIBOSSES) {
				bosses[id] = minibossData != null && bool(minibossData.get(id))
			}
			val follower = KuudraFollower.from(quests)
			val cavity = (quests?.get("unlocked_cavity_npcs") as? JsonArray)?.size() ?: 0
			val dailies = ArrayList<String>()
			val questList = quests?.let { Leveling.obj(it.get("quest_data")) }?.get("quest_list") as? JsonArray
			if (questList != null) {
				for (el in questList) {
					if (el != null && el.isJsonPrimitive) {
						dailies.add(el.asString)
					}
				}
			}

			val kuudraObj = Leveling.obj(nether.get("kuudra_completed_tiers"))
			val kuudra = LinkedHashMap<KuudraTier, KuudraTierStats>()
			for (tier in KuudraTier.values()) {
				val comps = if (kuudraObj == null) 0 else num(kuudraObj.get(tier.key)).toInt()
				val wave = if (kuudraObj == null) 0 else num(kuudraObj.get("highest_wave_" + tier.key)).toInt()
				kuudra[tier] = KuudraTierStats(maxOf(0, comps), maxOf(0, wave))
			}

			val dojoObj = Leveling.obj(nether.get("dojo"))
			val dojo = LinkedHashMap<DojoChallenge, DojoScore>()
			for (challenge in DojoChallenge.values()) {
				val points = if (dojoObj == null) 0 else num(dojoObj.get("dojo_points_" + challenge.key)).toInt()
				val time = if (dojoObj == null) 0L else num(dojoObj.get("dojo_time_" + challenge.key)).toLong()
				dojo[challenge] = DojoScore(maxOf(0, points), maxOf(0L, time))
			}

			val lastBosses = stringList(nether.get("last_minibosses_killed"))

			val abiphone = Leveling.obj(nether.get("abiphone"))
			val active = if (abiphone == null) listOf() else stringList(abiphone.get("active_contacts"))
			val activeSet = active.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
			val contacts = ArrayList<AbiphoneContact>()
			val byNpc = LinkedHashMap<String, AbiphoneContact>()
			val contactData = abiphone?.let { Leveling.obj(it.get("contact_data")) }
			if (contactData != null) {
				for ((id, value) in contactData.entrySet()) {
					val row = Leveling.obj(value)
					val talked = row != null && bool(row.get("talked_to"))
					val done = row != null && bool(row.get("completed_quest"))
					val calls = if (row == null) 0 else num(row.get("incoming_calls_count")).toInt()
					val dnd = row != null && bool(row.get("dnd_enabled"))
					val isActive = activeSet.contains(id.lowercase(Locale.ROOT))
					val npcKey = AbiphoneNpcs.neuId(id)
					val prev = byNpc[npcKey]
					byNpc[npcKey] = if (prev == null) {
						AbiphoneContact(id, AbiphoneNpcs.displayName(id) ?: prettyContact(id), talked, done, isActive, maxOf(0, calls), dnd)
					} else {
						AbiphoneContact(
							prev.id,
							prev.name,
							prev.talkedTo || talked,
							prev.completedQuest || done,
							prev.active || isActive,
							maxOf(prev.incomingCalls, maxOf(0, calls)),
							prev.dnd || dnd
						)
					}
				}
				contacts.addAll(byNpc.values)
				contacts.sortWith(compareBy<AbiphoneContact> { !it.active }.thenComparing({ it.name }, String.CASE_INSENSITIVE_ORDER))
			}
			val ringtone = if (abiphone == null) "" else str(abiphone.get("selected_ringtone"))
			val trio = if (abiphone == null) 0 else num(abiphone.get("trio_contact_addons")).toInt()
			val chip = abiphone?.let { Leveling.obj(it.get("operator_chip")) }
			val chipRepaired = if (chip == null) 0 else num(chip.get("repaired_index")).toInt()
			val games = abiphone?.let { Leveling.obj(it.get("games")) }
			val snake = if (games == null) 0 else num(games.get("snake_best_score")).toInt()
			val tttL = if (games == null) 0 else num(games.get("tic_tac_toe_losses")).toInt()
			val tttD = if (games == null) 0 else num(games.get("tic_tac_toe_draws")).toInt()
			val sort = if (abiphone == null) "" else str(abiphone.get("selected_sort"))

			return CrimsonSnapshot(
				faction, mage, mageHigh, barb, barbHigh,
				readCrimsonEssence(member),
				pearls, lastPearl,
				bosses, kuudra, dojo, lastBosses, dailies, cavity, follower,
				contacts, active.size, ringtone, trio, chipRepaired, snake, tttL, tttD, sort,
				CrimsonKuudraCard.from(member, kuudra),
				EssenceShopData.crimson(member)
			)
		}

		private fun readCrimsonEssence(member: JsonObject): Long {
			val currencies = Leveling.obj(member.get("currencies"))
			val essence = currencies?.let { Leveling.obj(it.get("essence")) }
			val crimson = essence?.let { Leveling.obj(it.get("CRIMSON")) } ?: return 0L
			return num(crimson.get("current")).toLong()
		}

		@JvmStatic
		fun dojoRank(points: Int): String = when {
			points >= 1000 -> "S"
			points >= 800 -> "A"
			points >= 600 -> "B"
			points >= 400 -> "C"
			points >= 200 -> "D"
			else -> "F"
		}

		@JvmStatic
		fun dojoRankColor(rank: String?): Int = when (rank ?: "") {
			"S" -> 0xFFFFAA00.toInt()
			"A" -> 0xFF55FF55.toInt()
			"B" -> 0xFF55FFFF.toInt()
			"C" -> 0xFFFFFF55.toInt()
			"D" -> 0xFFAAAAAA.toInt()
			else -> 0xFFFF5555.toInt()
		}

		@JvmStatic
		fun prettyMiniboss(id: String?): String {
			if (id.isNullOrBlank()) {
				return ""
			}
			return when (id.uppercase(Locale.ROOT)) {
				"BLADESOUL" -> "Bladesoul"
				"MAGE_OUTLAW" -> "Mage Outlaw"
				"BARBARIAN_DUKE_X" -> "Barbarian Duke X"
				"ASHFANG" -> "Ashfang"
				"MAGMA_BOSS" -> "Magma Boss"
				else -> prettyContact(id)
			}
		}

		@JvmStatic
		fun prettyDaily(id: String?): String {
			if (id.isNullOrBlank()) {
				return ""
			}
			var raw: String = id.removePrefix("crimson_isle_")
			if (raw.length > 2 && raw[raw.length - 2] == '_' && Character.isLetter(raw[raw.length - 1])) {
				raw = raw.substring(0, raw.length - 2)
			}
			return prettyContact(raw)
		}

		private fun prettyContact(id: String?): String {
			if (id.isNullOrBlank()) {
				return ""
			}
			val sb = StringBuilder()
			for (part in id.replace('-', '_').split("_")) {
				if (part.isBlank()) {
					continue
				}
				if (sb.isNotEmpty()) {
					sb.append(' ')
				}
				sb.append(part[0].uppercaseChar())
				if (part.length > 1) {
					sb.append(part.substring(1).lowercase(Locale.ROOT))
				}
			}
			return sb.toString()
		}

		private fun stringList(el: JsonElement?): List<String> {
			val out = ArrayList<String>()
			if (el !is JsonArray) {
				return out
			}
			for (item in el) {
				if (item != null && item.isJsonPrimitive) {
					val s: String? = item.asString
					if (!s.isNullOrBlank()) {
						out.add(s)
					}
				}
			}
			return out
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

		private fun bool(el: JsonElement?): Boolean {
			if (el == null || el.isJsonNull || !el.isJsonPrimitive) {
				return false
			}
			return try {
				val primitive = el.asJsonPrimitive
				when {
					primitive.isBoolean -> el.asBoolean
					primitive.isNumber -> el.asInt != 0
					else -> {
						val s = el.asString
						"true".equals(s, ignoreCase = true) || s == "1"
					}
				}
			} catch (ignored: RuntimeException) {
				false
			}
		}
	}
}
