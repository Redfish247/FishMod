/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import java.util.Locale

/** Parsed bestiary progress for one profile member. */
class BestiarySnapshot private constructor(
	private val byCategory: Map<String, CategoryProgress>,
	@get:JvmName("claimedMilestone") val claimedMilestone: Int,
	@get:JvmName("maxKillsVisible") val maxKillsVisible: Boolean,
	lastKilledMob: String?,
	@get:JvmName("totalUnlockedTiers") val totalUnlockedTiers: Int,
	@get:JvmName("totalMaxTiers") val totalMaxTiers: Int
) {
	class FamilyProgress(
		private val def: BestiaryData.Family,
		@get:JvmName("kills") val kills: Long,
		@get:JvmName("deaths") val deaths: Long,
		tier: Int,
		tiersMax: Int,
		intoTier: Long,
		nextNeed: Long,
		@get:JvmName("maxed") val maxed: Boolean
	) {
		@get:JvmName("tier") val tier: Int = maxOf(0, tier)
		@get:JvmName("tiersMax") val tiersMax: Int = maxOf(0, tiersMax)
		@get:JvmName("intoTier") val intoTier: Long = maxOf(0L, intoTier)
		@get:JvmName("nextNeed") val nextNeed: Long = maxOf(0L, nextNeed)

		fun family(): BestiaryData.Family = def

		fun progress(): Float {
			if (maxed || nextNeed <= 0L) {
				return 1f
			}
			return (intoTier / nextNeed.toFloat()).coerceIn(0f, 1f)
		}
	}

	@JvmRecord
	data class CategoryProgress(
		val category: BestiaryData.Category,
		val families: List<FamilyProgress>,
		val unlockedTiers: Int,
		val maxTiers: Int,
		val familiesUnlocked: Int,
		val familiesMaxed: Int
	)

	@get:JvmName("lastKilledMob") val lastKilledMob: String = lastKilledMob ?: ""

	fun category(id: String?): CategoryProgress? = if (id == null) null else byCategory[id]

	fun categories(): List<CategoryProgress> = BestiaryData.categories().map { cat ->
		byCategory[cat.id] ?: CategoryProgress(cat, listOf(), 0, 0, 0, 0)
	}

	/** In-game Bestiary milestone = unique family tiers / 10 (not last claimed reward). */
	fun milestone(): Int = totalUnlockedTiers / 10

	fun milestoneProgress(): Int = totalUnlockedTiers % 10

	companion object {
		@JvmStatic
		fun empty(): BestiarySnapshot = BestiarySnapshot(mapOf(), 0, false, "", 0, 0)

		@JvmStatic
		fun fromMember(member: JsonObject?): BestiarySnapshot {
			BestiaryData.ensureLoaded()
			val bestiary = Leveling.obj(member?.get("bestiary"))
			val killsObj = bestiary?.let { Leveling.obj(it.get("kills")) }
			val deathsObj = bestiary?.let { Leveling.obj(it.get("deaths")) }
			val milestone = bestiary?.let { Leveling.obj(it.get("milestone")) }
			val misc = bestiary?.let { Leveling.obj(it.get("miscellaneous")) }

			val kills = readCounts(killsObj)
			val deaths = readCounts(deathsObj)
			var lastKilled = ""
			if (killsObj != null && killsObj.has("last_killed_mob") && killsObj.get("last_killed_mob").isJsonPrimitive) {
				try {
					lastKilled = killsObj.get("last_killed_mob").asString
				} catch (ignored: RuntimeException) {
				}
			}
			var claimed = 0
			if (milestone != null && milestone.has("last_claimed_milestone") &&
				milestone.get("last_claimed_milestone").isJsonPrimitive
			) {
				try {
					claimed = maxOf(0, milestone.get("last_claimed_milestone").asInt)
				} catch (ignored: RuntimeException) {
				}
			}
			val maxVisible = misc != null && misc.has("max_kills_visible") &&
				misc.get("max_kills_visible").isJsonPrimitive &&
				misc.get("max_kills_visible").asBoolean

			val byCat = LinkedHashMap<String, CategoryProgress>()
			var totalUnlocked = 0
			var totalMax = 0
			for (cat in BestiaryData.categories()) {
				val families = ArrayList<FamilyProgress>()
				var unlockedTiers = 0
				var maxTiers = 0
				var famUnlocked = 0
				var famMaxed = 0
				for (fam in cat.families) {
					val fp = progressFor(fam, kills, deaths)
					families.add(fp)
					unlockedTiers += fp.tier
					maxTiers += fp.tiersMax
					if (fp.tier > 0) {
						famUnlocked++
					}
					if (fp.maxed) {
						famMaxed++
					}
				}
				totalUnlocked += unlockedTiers
				totalMax += maxTiers
				byCat[cat.id] = CategoryProgress(cat, java.util.List.copyOf(families), unlockedTiers, maxTiers, famUnlocked, famMaxed)
			}
			return BestiarySnapshot(java.util.Map.copyOf(byCat), claimed, maxVisible, lastKilled, totalUnlocked, totalMax)
		}

		private fun progressFor(fam: BestiaryData.Family, kills: Map<String, Long>, deaths: Map<String, Long>): FamilyProgress {
			var killSum = 0L
			var deathSum = 0L
			for (mobId in fam.mobIds) {
				killSum += kills[mobId] ?: 0L
				deathSum += deaths[mobId] ?: 0L
			}
			return progressFor(fam, killSum, deathSum, BestiaryData.bracket(fam.bracketType, fam.bracket))
		}

		/** {@code killSum} stays the real total; only tier math is clamped to the family cap. */
		@JvmStatic
		fun progressFor(fam: BestiaryData.Family, killSum: Long, deathSum: Long, ladder: List<Int>): FamilyProgress {
			val tierKills = if (fam.cap > 0) minOf(killSum, fam.cap.toLong()) else killSum
			// NEU brackets are cumulative kill thresholds. Family cap = kills for that family's max tier.
			val tiersMax = tiersMaxFor(ladder, fam.cap)
			var tier = 0
			for (i in 0 until tiersMax) {
				if (tierKills >= ladder[i]) {
					tier = i + 1
				} else {
					break
				}
			}
			val maxed = tiersMax > 0 && tier >= tiersMax
			var into = 0L
			var next = 0L
			if (!maxed && tier < ladder.size) {
				val prev = if (tier > 0) ladder[tier - 1].toLong() else 0L
				val nextThresh = ladder[tier].toLong()
				into = maxOf(0L, tierKills - prev)
				next = maxOf(0L, nextThresh - prev)
			}
			return FamilyProgress(fam, killSum, deathSum, tier, tiersMax, into, next, maxed)
		}

		/** Highest ladder index whose cumulative threshold is within the family kill cap. */
		private fun tiersMaxFor(ladder: List<Int>?, cap: Int): Int {
			if (ladder.isNullOrEmpty()) {
				return 0
			}
			if (cap <= 0) {
				return ladder.size
			}
			var max = 0
			for (i in ladder.indices) {
				if (ladder[i] <= cap) {
					max = i + 1
				} else {
					break
				}
			}
			return max
		}

		private fun readCounts(obj: JsonObject?): Map<String, Long> {
			val out = HashMap<String, Long>()
			if (obj == null) {
				return out
			}
			for ((key, value) in obj.entrySet()) {
				if (key.isNullOrBlank() || key == "last_killed_mob" || !value.isJsonPrimitive) {
					continue
				}
				try {
					val v = value.asLong
					if (v > 0L) {
						out[key.lowercase(Locale.ROOT)] = v
					}
				} catch (ignored: RuntimeException) {
				}
			}
			return out
		}
	}
}
