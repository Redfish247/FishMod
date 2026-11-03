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
import kotlin.math.floor

/** Member-side fishing: Overview + Trophy Fish / Frogs. */
class FishingSnapshot private constructor(
	fishingLevel: Int, fishingFill: Float, fishingMaxed: Boolean, fishingHover: String?,
	itemsFishedTotal: Long, itemsFishedNormal: Long, itemsFishedTreasure: Long, itemsFishedLargeTreasure: Long,
	itemsFishedTrophyFish: Long, itemsFishedTrophyFrog: Long, itemsFishedOutstanding: Long,
	seaCreatureKills: Long, festivalSharksKilled: Long, dolphinMilestone: Long,
	trophyFishTotal: Long, trophyFishLastCaught: String?,
	trophyFish: List<TrophyRow>?, trophyFrogs: List<TrophyRow>?,
	peltCount: Long
) {
	@JvmRecord
	data class TierCounts(val bronze: Int, val silver: Int, val gold: Int, val diamond: Int) {
		fun total(): Int = maxOf(0, bronze) + maxOf(0, silver) + maxOf(0, gold) + maxOf(0, diamond)

		fun of(tier: TrophyFishData.Tier?): Int = when (tier) {
			null -> 0
			TrophyFishData.Tier.BRONZE -> maxOf(0, bronze)
			TrophyFishData.Tier.SILVER -> maxOf(0, silver)
			TrophyFishData.Tier.GOLD -> maxOf(0, gold)
			TrophyFishData.Tier.DIAMOND -> maxOf(0, diamond)
		}

		fun discovered(tier: TrophyFishData.Tier?): Boolean = of(tier) > 0

		fun discoveredTier(): Int = TrophyFishData.Tier.values().count { discovered(it) }

		/** Diamond=4 … Bronze=1, none=0 - for sort / completion. */
		fun bestRank(): Int = when (bestDiscovered()) {
			null -> 0
			TrophyFishData.Tier.DIAMOND -> 4
			TrophyFishData.Tier.GOLD -> 3
			TrophyFishData.Tier.SILVER -> 2
			TrophyFishData.Tier.BRONZE -> 1
		}

		fun bestDiscovered(): TrophyFishData.Tier? = TrophyFishData.Tier.topToBottom().firstOrNull { discovered(it) }

		companion object {
			@JvmStatic
			fun zero(): TierCounts = TierCounts(0, 0, 0, 0)
		}
	}

	@JvmRecord
	data class TrophyRow(val id: String, val name: String, val counts: TierCounts?, val countsExact: Boolean) {
		fun totalCaught(): Int = counts?.total() ?: 0

		fun anyDiscovered(): Boolean = counts != null && counts.total() > 0
	}

	@get:JvmName("fishingLevel") val fishingLevel: Int = maxOf(0, fishingLevel)
	@get:JvmName("fishingFill") val fishingFill: Float = fishingFill.coerceIn(0f, 1f)
	@get:JvmName("fishingMaxed") val fishingMaxed: Boolean = fishingMaxed
	@get:JvmName("fishingHover") val fishingHover: String = fishingHover ?: ""

	@get:JvmName("itemsFishedTotal") val itemsFishedTotal: Long = maxOf(0L, itemsFishedTotal)
	@get:JvmName("itemsFishedNormal") val itemsFishedNormal: Long = maxOf(0L, itemsFishedNormal)
	@get:JvmName("itemsFishedTreasure") val itemsFishedTreasure: Long = maxOf(0L, itemsFishedTreasure)
	@get:JvmName("itemsFishedLargeTreasure") val itemsFishedLargeTreasure: Long = maxOf(0L, itemsFishedLargeTreasure)
	@get:JvmName("itemsFishedTrophyFish") val itemsFishedTrophyFish: Long = maxOf(0L, itemsFishedTrophyFish)
	@get:JvmName("itemsFishedTrophyFrog") val itemsFishedTrophyFrog: Long = maxOf(0L, itemsFishedTrophyFrog)
	@get:JvmName("itemsFishedOutstanding") val itemsFishedOutstanding: Long = maxOf(0L, itemsFishedOutstanding)
	@get:JvmName("seaCreatureKills") val seaCreatureKills: Long = maxOf(0L, seaCreatureKills)
	@get:JvmName("festivalSharksKilled") val festivalSharksKilled: Long = maxOf(0L, festivalSharksKilled)
	/** -1 when the profile has no Dolphin milestone field. */
	@get:JvmName("dolphinMilestone") val dolphinMilestone: Long = maxOf(-1L, dolphinMilestone)

	@get:JvmName("trophyFishTotal") val trophyFishTotal: Long = maxOf(0L, trophyFishTotal)
	@get:JvmName("trophyFishLastCaught") val trophyFishLastCaught: String = trophyFishLastCaught ?: ""
	@get:JvmName("trophyFish") val trophyFish: List<TrophyRow> = java.util.List.copyOf(trophyFish.orEmpty())
	@get:JvmName("trophyFrogs") val trophyFrogs: List<TrophyRow> = java.util.List.copyOf(trophyFrogs.orEmpty())
	@get:JvmName("peltCount") val peltCount: Long = maxOf(0L, peltCount)

	companion object {
		@JvmStatic
		fun empty(): FishingSnapshot = FishingSnapshot(
			0, 0f, false, "",
			0, 0, 0, 0, 0, 0, 0,
			0, 0, -1L,
			0, "", listOf(), listOf(),
			0L
		)

		@JvmStatic
		fun fromMember(member: JsonObject?): FishingSnapshot {
			if (member == null) {
				return empty()
			}
			TrophyFishData.ensureLoaded()
			TrophySkulls.ensureLoaded()

			val fishingXp = Leveling.readSkillXpDouble(member, "fishing")
			val fishingCap = Leveling.skillCap("fishing", member)
			val fishing = Leveling.getLevel(Leveling.skillTable("fishing"), fishingXp, fishingCap, false)

			val stats = Leveling.obj(member.get("player_stats"))
			val fished = Leveling.obj(stats?.get("items_fished"))
			val leveling = Leveling.obj(member.get("leveling"))
			val pets = Leveling.obj(stats?.get("pets"))
			val milestones = Leveling.obj(pets?.get("milestone"))
			val dolphin = if (milestones != null && milestones.has("sea_creatures_killed")) {
				longOf(milestones, "sea_creatures_killed")
			} else {
				-1L
			}

			val trophyRoot = Leveling.obj(member.get("trophy_fish"))
			val totalCaught = longOf(trophyRoot, "total_caught")
			val last = str(trophyRoot, "last_caught")
			val fishRows = parseTrophyRows(trophyRoot, TrophyFishData.all())

			val frogRoot = Leveling.obj(member.get("trophy_frog")) ?: Leveling.obj(member.get("trophy_frogs"))
			val frogRows = if (frogRoot != null) {
				parseTrophyRows(frogRoot, TrophyFrogData.all())
			} else {
				parseTrophyFrogsFromTasks(leveling?.get("completed_tasks"))
			}

			val quests = Leveling.obj(member.get("quests"))
			val trapper = Leveling.obj(quests?.get("trapper_quest"))

			return FishingSnapshot(
				floor(fishing.level.toDouble()).toInt(), fishing.fill(), fishing.maxed, fishing.skillHoverText("Fishing"),
				longOf(fished, "total"),
				longOf(fished, "normal"),
				longOf(fished, "treasure"),
				longOf(fished, "large_treasure"),
				longOf(fished, "trophy_fish"),
				longOf(fished, "trophy_frog"),
				longOf(fished, "outstanding"),
				longOf(stats, "sea_creature_kills"),
				longOf(leveling, "fishing_festival_sharks_killed"),
				dolphin,
				totalCaught, last, fishRows, frogRows,
				longOf(trapper, "pelt_count")
			)
		}

		private fun parseTrophyRows(root: JsonObject?, defs: List<TrophyFishData.Def>): List<TrophyRow> = defs.map { def ->
			val key = def.id.lowercase(Locale.ROOT)
			val counts = TierCounts(
				longOf(root, key + "_bronze").toInt(),
				longOf(root, key + "_silver").toInt(),
				longOf(root, key + "_gold").toInt(),
				longOf(root, key + "_diamond").toInt()
			)
			TrophyRow(def.id, def.name, counts, true)
		}

		/** Discovery-only fallback when Hypixel has no {@code trophy_frog} object yet. */
		private fun parseTrophyFrogsFromTasks(completedTasks: JsonElement?): List<TrophyRow> {
			val tasks = HashSet<String>()
			if (completedTasks != null && completedTasks.isJsonArray) {
				for (el in completedTasks.asJsonArray) {
					if (el != null && el.isJsonPrimitive) {
						try {
							tasks.add(el.asString.uppercase(Locale.ROOT))
						} catch (ignored: RuntimeException) {
						}
					}
				}
			}
			return TrophyFrogData.all().map { def ->
				fun has(tier: String) = if (tasks.contains("TROPHY_" + def.id + "_" + tier)) 1 else 0
				TrophyRow(def.id, def.name, TierCounts(has("BRONZE"), has("SILVER"), has("GOLD"), has("DIAMOND")), false)
			}
		}

		private fun longOf(obj: JsonObject?, key: String): Long = if (obj == null) 0L else Leveling.longAmount(obj.get(key))

		private fun str(obj: JsonObject?, key: String?): String {
			if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) {
				return ""
			}
			return try {
				obj.get(key).asString
			} catch (ignored: RuntimeException) {
				""
			}
		}
	}
}
