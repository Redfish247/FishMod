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
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.price.HypixelCollectionsCache
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

object Leveling {
	@JvmRecord
	data class Progress(
		val level: Float,
		val maxXpForLevel: Double,
		val maxed: Boolean,
		val maxLevel: Int,
		val totalXp: Double,
		val xpIntoLevel: Double,
		val overflowXp: Double,
		val overflowLevel: Float
	) {
		constructor(
			level: Float,
			maxXpForLevel: Double,
			maxed: Boolean,
			maxLevel: Int,
			totalXp: Double,
			xpIntoLevel: Double,
			overflowXp: Double
		) : this(
			level,
			maxXpForLevel,
			maxed,
			maxLevel,
			totalXp,
			xpIntoLevel,
			overflowXp,
			repeatedStepOverflowLevel(maxed, level, maxLevel, overflowXp, maxXpForLevel)
		)

		fun fill(): Float {
			if (maxed || maxXpForLevel <= 0.0) {
				return 1.0f
			}
			return (xpIntoLevel / maxXpForLevel).coerceIn(0.0, 1.0).toFloat()
		}

		/** Uncapped skill level including overflow (SkyHanni / in-game Skills menu). */
		fun displayLevel(): Int {
			if (maxed) {
				return maxOf(maxLevel, floor(overflowLevel.toDouble()).toInt())
			}
			return floor(level.toDouble()).toInt()
		}

		/** Soft-cap level for skill bar labels. Overflow stays hover-only. */
		fun cappedLevel(): Int = if (maxed) maxLevel else floor(level.toDouble()).toInt()

		fun hoverText(): String {
			if (maxed) {
				return overflowHoverText()
			}
			val into = Math.round(xpIntoLevel)
			val need = Math.round(maxXpForLevel)
			if (need <= 0L) {
				return FormatUtil.commas(Math.round(totalXp)) + "/" + FormatUtil.commas(Math.round(totalXp)) + " (100%)"
			}
			val pct = fill() * 100.0
			return FormatUtil.commas(into) + "/" + FormatUtil.commas(need) + " (" + FormatUtil.oneDecimal(pct) + "%)"
		}

		fun skillHover(name: String?): String {
			val lvl = cappedLevel()
			if (maxed) {
				return "$name $lvl - " + overflowHoverText()
			}
			val next = minOf(maxLevel, lvl + 1)
			val into = Math.round(xpIntoLevel)
			val need = Math.round(maxXpForLevel)
			return "$name $lvl - " + FormatUtil.shortXp(into.toDouble()) + " / " + FormatUtil.shortXp(need.toDouble()) +
				" to Level " + next
		}

		fun skillHoverLines(name: String?): List<PvTooltip.Line> {
			// Match Hypixel Skills menu: title is the soft cap, overflow is listed separately.
			val lines = ArrayList<PvTooltip.Line>(6)
			lines.add(PvTooltip.Line.of(skillTitle(name), PvDraw.COLOR_ACCENT))
			for ((label, value) in skillHoverRows()) {
				lines.add(PvTooltip.Line.row(label, PvDraw.COLOR_MUTED, value, PvDraw.COLOR_GOLD))
			}
			return lines
		}

		fun skillHoverText(name: String?): String {
			val text = StringBuilder(skillTitle(name))
			for ((label, value) in skillHoverRows()) {
				text.append('\n').append(label).append('\t').append(value)
			}
			return text.toString()
		}

		private fun skillTitle(name: String?): String = (name ?: "?") + " " + cappedLevel()

		private fun skillHoverRows(): List<Pair<String, String>> {
			val rows = ArrayList<Pair<String, String>>(4)
			rows.add("Total XP" to FormatUtil.commas(Math.round(maxOf(0.0, totalXp))))
			if (overflowXp > 0.5) {
				rows.add("Overflow XP" to FormatUtil.commas(Math.round(overflowXp)))
			}
			if (maxed) {
				if (overflowLevel > maxLevel) {
					val current = floor(overflowLevel.toDouble()).toInt()
					val pct = (overflowLevel - current) * 100.0
					rows.add("Overflow Level" to FormatUtil.oneDecimal(overflowLevel.toDouble()))
					rows.add("Progress to " + (current + 1) to FormatUtil.oneDecimal(pct) + "%")
				}
			} else {
				val next = minOf(maxLevel, cappedLevel() + 1)
				val into = Math.round(xpIntoLevel)
				val need = Math.round(maxXpForLevel)
				rows.add("Progress to $next" to FormatUtil.shortXp(into.toDouble()) + " / " + FormatUtil.shortXp(need.toDouble()) +
					" (" + FormatUtil.oneDecimal(fill() * 100.0) + "%)")
			}
			return rows
		}

		private fun overflowHoverText(): String {
			var text = "Overflow Level: " + FormatUtil.oneDecimal(overflowLevel.toDouble()) +
				" · Overflow XP: " + FormatUtil.shortXp(maxOf(0.0, overflowXp))
			if (maxXpForLevel > 0.0) {
				text += " · " + FormatUtil.commas(Math.round(xpIntoLevel)) +
					" / " + FormatUtil.commas(Math.round(maxXpForLevel)) +
					" to " + (maxLevel + 1)
			}
			return text
		}

		/** e.g. {@code Revenant 9 - 1,240,000 / 2,000,000 XP} or overflow when maxed. */
		fun slayerHover(name: String?): String {
			val tier = floor(level.toDouble()).toInt()
			if (maxed) {
				return "$name $tier - Overflow: " + FormatUtil.shortXp(maxOf(0.0, overflowXp))
			}
			val into = Math.round(xpIntoLevel)
			val need = Math.round(maxXpForLevel)
			return "$name $tier - " + FormatUtil.commas(into) + " / " + FormatUtil.commas(need) + " XP"
		}

		fun slayerHoverLines(name: String?): List<PvTooltip.Line> {
			val tier = floor(level.toDouble()).toInt()
			val title = (name ?: "?") + " " + tier
			val lines = ArrayList<PvTooltip.Line>(5)
			lines.add(PvTooltip.Line.of(title, PvDraw.COLOR_ACCENT))
			lines.add(PvTooltip.Line.row("Total XP", PvDraw.COLOR_MUTED, FormatUtil.commas(Math.round(maxOf(0.0, totalXp))), PvDraw.COLOR_GOLD))
			if (overflowXp > 0.5) {
				lines.add(PvTooltip.Line.row("Overflow", PvDraw.COLOR_MUTED, FormatUtil.shortXp(overflowXp), PvDraw.COLOR_GOLD))
			}
			if (!maxed) {
				val into = Math.round(xpIntoLevel)
				val need = Math.round(maxXpForLevel)
				lines.add(PvTooltip.Line.of(FormatUtil.commas(into) + " / " + FormatUtil.commas(need) + " XP", PvDraw.COLOR_GOLD))
			}
			return lines
		}

		fun slayerHoverWithKills(name: String?, slayerId: String?, tierKills: IntArray?): String {
			val sb = StringBuilder(slayerHover(name))
			if (tierKills != null && tierKills.isNotEmpty()) {
				sb.append('\n').append(killsLine(tierKills))
			}
			killsToNext(slayerId)?.let { sb.append('\n').append(it) }
			return sb.toString()
		}

		fun slayerHoverLinesWithKills(name: String?, slayerId: String?, tierKills: IntArray?): List<PvTooltip.Line> {
			val lines = ArrayList(slayerHoverLines(name))
			if (tierKills != null && tierKills.isNotEmpty()) {
				lines.add(PvTooltip.Line.of(killsLine(tierKills), PvDraw.COLOR_MUTED))
			}
			killsToNext(slayerId)?.let { lines.add(PvTooltip.Line.of(it, PvDraw.COLOR_MUTED)) }
			return lines
		}

		private fun killsLine(tierKills: IntArray): String {
			val kills = StringBuilder()
			for (i in tierKills.indices) {
				if (i > 0) {
					kills.append("  ")
				}
				kills.append('T').append(i + 1).append(": ").append(FormatUtil.commas(maxOf(0, tierKills[i]).toLong()))
			}
			return kills.toString()
		}

		private fun killsToNext(slayerId: String?): String? {
			val bossXp = RepoData.slayerBossXp(slayerId)
			if (bossXp == null || bossXp.isEmpty || maxed || maxXpForLevel <= 0.0) {
				return null
			}
			val xpNeeded = maxOf(0.0, maxXpForLevel - xpIntoLevel)
			val highest = minOf(bossXp.size(), RepoData.slayerHighestTier(slayerId))
			val idx = maxOf(0, highest - 1)
			val perKill = bossXp.get(idx).asFloat
			if (perKill > 0f && xpNeeded > 0.0) {
				val killsNeeded = ceil(xpNeeded / perKill).toLong()
				return "≈ " + FormatUtil.commas(killsNeeded) + " T" + (idx + 1) + " kills to next"
			}
			return null
		}
	}

	/**
	 * SkyHanni {@code SkillUtil.calculateSkillLevel}: after 60 the step starts at 7.6M and
	 * the slope doubles every tenth level. Matches the in-game Skills menu overflow level.
	 */
	private const val HYPIXEL_OVERFLOW_START = 60
	private const val HYPIXEL_OVERFLOW_BASE = 7_000_000L
	private const val HYPIXEL_OVERFLOW_SLOPE = 600_000L

	@JvmStatic
	fun hoverTextLines(hover: String?): List<PvTooltip.Line> {
		if (hover.isNullOrBlank()) {
			return listOf()
		}
		if (hover.indexOf('\n') < 0) {
			return listOf(PvTooltip.Line.of(hover, PvDraw.COLOR_TEXT))
		}
		val parts = hover.split("\n").dropLastWhile { it.isEmpty() }
		val lines = ArrayList<PvTooltip.Line>(parts.size)
		lines.add(PvTooltip.Line.of(parts[0], PvDraw.COLOR_ACCENT))
		for (i in 1 until parts.size) {
			val tab = parts[i].indexOf('\t')
			if (tab < 0) {
				lines.add(PvTooltip.Line.of(parts[i], PvDraw.COLOR_TEXT))
			} else {
				lines.add(PvTooltip.Line.row(parts[i].substring(0, tab), PvDraw.COLOR_MUTED, parts[i].substring(tab + 1), PvDraw.COLOR_GOLD))
			}
		}
		return lines
	}

	@JvmStatic
	fun getLevel(table: JsonArray?, xp: Float, levelCap: Int, cumulative: Boolean): Progress =
		getLevel(table, xp.toDouble(), levelCap, cumulative)

	@JvmStatic
	fun getLevel(table: JsonArray?, xp: Double, levelCap: Int, cumulative: Boolean): Progress {
		val xpF = maxOf(0.0, xp)
		if (table == null || table.isEmpty) {
			return Progress(0f, 0.0, false, levelCap, xpF, 0.0, 0.0)
		}
		if (!cumulative && usesHypixelSkillOverflow(table)) {
			return hypixelSkillProgress(table, xp, levelCap)
		}
		val xpToCap = xpRequiredForLevel(table, levelCap, cumulative).toDouble()
		val overflowStep = overflowStepXp(table, levelCap, cumulative)
		var remaining = maxOf(0.0, xp)
		for (level in 0 until table.size()) {
			val levelXp = table.get(level).asDouble
			if (levelXp > remaining) {
				val maxXpForLevel: Float
				val resultLevel: Float
				val into: Float
				if (cumulative) {
					val previous = if (level > 0) table.get(level - 1).asDouble else 0.0
					maxXpForLevel = (levelXp - previous).toFloat()
					into = (remaining - previous).toFloat()
					resultLevel = level + into / maxOf(1f, maxXpForLevel)
				} else {
					maxXpForLevel = levelXp.toFloat()
					into = remaining.toFloat()
					resultLevel = level + (remaining / maxOf(1.0, levelXp)).toFloat()
				}
				val maxed = resultLevel >= levelCap
				if (maxed) {
					val step = if (overflowStep > 0f) overflowStep else maxXpForLevel
					return Progress(
						levelCap.toFloat(), step.toDouble(), true, levelCap, xpF, step.toDouble(), maxOf(0.0, xp - xpToCap)
					)
				}
				return Progress(resultLevel, maxXpForLevel.toDouble(), false, levelCap, xpF, into.toDouble(), 0.0)
			}
			if (!cumulative) {
				remaining -= levelXp
			}
		}
		val capped = minOf(table.size(), levelCap)
		val step = if (overflowStep > 0f) overflowStep else 0f
		return Progress(capped.toFloat(), step.toDouble(), true, levelCap, xpF, 0.0, maxOf(0.0, xp - xpToCap))
	}

	/**
	 * Walk the 60-step skill table, then Hypixel's overflow slope (SkyHanni SkillUtil).
	 * {@code level} stays capped; {@code overflowLevel} is the uncapped in-game level.
	 */
	private fun hypixelSkillProgress(table: JsonArray, xp: Double, levelCap: Int): Progress {
		var remaining = maxOf(0.0, xp)
		var level = 0
		val tableLevels = minOf(HYPIXEL_OVERFLOW_START, table.size())
		while (level < tableLevels) {
			val need = table.get(level).asDouble
			if (remaining < need) {
				break
			}
			remaining -= need
			level++
		}
		var xpForNext: Double
		if (level >= HYPIXEL_OVERFLOW_START) {
			var slope = HYPIXEL_OVERFLOW_SLOPE
			xpForNext = (HYPIXEL_OVERFLOW_BASE + slope).toDouble()
			while (remaining >= xpForNext) {
				level++
				remaining -= xpForNext
				xpForNext += slope
				if (level % 10 == 0) {
					slope *= 2L
				}
			}
		} else if (level < table.size()) {
			xpForNext = table.get(level).asDouble
		} else {
			xpForNext = 0.0
		}
		var xpToCap = 0.0
		val capSteps = minOf(maxOf(1, levelCap), table.size())
		for (i in 0 until capSteps) {
			xpToCap += table.get(i).asDouble
		}
		val overflowXp = maxOf(0.0, xp - xpToCap)
		val maxed = level >= levelCap || xp + 0.5 >= xpToCap
		val overflowLevel = (level + (if (xpForNext > 0.0) remaining / xpForNext else 0.0)).toFloat()
		val cappedLevel = if (maxed) levelCap.toFloat() else overflowLevel
		var into: Double
		var step: Double
		if (maxed) {
			step = if (levelCap < table.size()) table.get(levelCap).asDouble else overflowStepXp(table, levelCap, false).toDouble()
			if (step <= 0.0) {
				step = xpForNext
			}
			into = overflowXp
		} else {
			into = remaining
			step = xpForNext
		}
		if (maxed && step <= 0.0) {
			step = overflowStepXp(table, levelCap, false).toDouble()
			into = step
		}
		return Progress(cappedLevel, step, maxed, levelCap, maxOf(0.0, xp), into, overflowXp, overflowLevel)
	}

	private fun usesHypixelSkillOverflow(table: JsonArray?): Boolean {
		if (table == null || table.size() < HYPIXEL_OVERFLOW_START) {
			return false
		}
		return try {
			abs(table.get(0).asDouble - 50.0) < 0.01 &&
				abs(table.get(HYPIXEL_OVERFLOW_START - 1).asDouble - 7_000_000.0) < 0.01
		} catch (ignored: IllegalStateException) {
			false
		} catch (ignored: ClassCastException) {
			false
		} catch (ignored: NumberFormatException) {
			false
		} catch (ignored: UnsupportedOperationException) {
			false
		}
	}

	private fun repeatedStepOverflowLevel(maxed: Boolean, level: Float, maxLevel: Int, overflowXp: Double, maxXpForLevel: Double): Float {
		if (!maxed) {
			return level
		}
		if (maxXpForLevel <= 0.0 || overflowXp <= 0.0) {
			return maxLevel.toFloat()
		}
		return (maxLevel + overflowXp / maxXpForLevel).toFloat()
	}

	/** XP for one overflow level past the soft cap (repeats the last capped step). */
	private fun overflowStepXp(table: JsonArray?, levelCap: Int, cumulative: Boolean): Float {
		if (table == null || table.isEmpty || levelCap <= 0) {
			return 0f
		}
		val capped = minOf(levelCap, table.size())
		if (cumulative) {
			val atCap = table.get(capped - 1).asFloat
			val previous = if (capped > 1) table.get(capped - 2).asFloat else 0f
			return maxOf(0f, atCap - previous)
		}
		return maxOf(0f, table.get(capped - 1).asFloat)
	}

	@JvmStatic
	fun xpRequiredForLevel(table: JsonArray?, levelCap: Int, cumulative: Boolean): Float {
		if (table == null || table.isEmpty || levelCap <= 0) {
			return 0f
		}
		val capped = minOf(levelCap, table.size())
		if (cumulative) {
			return table.get(capped - 1).asFloat
		}
		var sum = 0.0
		for (i in 0 until capped) {
			sum += table.get(i).asDouble
		}
		return sum.toFloat()
	}

	@JvmStatic
	fun readSkillXp(member: JsonObject?, skill: String): Float = readSkillXpDouble(member, skill).toFloat()

	@JvmStatic
	fun readSkillXpDouble(member: JsonObject?, skill: String): Double {
		val apiSkill = if (skill == "social") "SOCIAL" else skill.uppercase(Locale.ROOT)
		val playerData = obj(member?.get("player_data"))
		val experience = playerData?.let { obj(it.get("experience")) }
		if (experience != null) {
			val keys = arrayOf("SKILL_$apiSkill", "SKILL_" + skill.uppercase(Locale.ROOT), skill.uppercase(Locale.ROOT))
			for (key in keys) {
				val value = numDouble(experience.get(key))
				if (value != null && value > 0.0) {
					return value
				}
			}
			for ((key, element) in experience.entrySet()) {
				if (key == null) {
					continue
				}
				val upper = key.uppercase(Locale.ROOT)
				if (upper.contains("EXTRA_LEVEL_CAP")) {
					continue
				}
				if (upper.contains(apiSkill)) {
					val value = numDouble(element)
					if (value != null && value > 0.0) {
						return value
					}
				}
			}
		}
		val legacy = numDouble(member?.get("experience_skill_" + (if (skill == "social") "social2" else skill)))
		return legacy ?: 0.0
	}

	@JvmStatic
	fun readSlayerXp(member: JsonObject?, slayer: String?): Float =
		slayerBossObject(member, slayer)?.let { num(it.get("xp")) } ?: 0f

	@JvmStatic
	fun slayerBossObject(member: JsonObject?, slayer: String?): JsonObject? {
		if (member == null || slayer == null) {
			return null
		}
		val slayerRoot = obj(member.get("slayer"))
		val bosses = slayerRoot?.let { obj(it.get("slayer_bosses")) } ?: obj(member.get("slayer_bosses"))
		return bosses?.let { obj(it.get(slayer)) }
	}

	@JvmStatic
	fun readSlayerBossKills(member: JsonObject?, slayer: String?): IntArray {
		val kills = IntArray(5)
		val boss = slayerBossObject(member, slayer) ?: return kills
		var foundZeroBased = false
		for (i in 0 until 5) {
			val value = num(boss.get("boss_kills_tier_$i"))
			if (value != null) {
				kills[i] = maxOf(0, Math.round(value))
				foundZeroBased = true
			}
		}
		if (foundZeroBased) {
			return kills
		}
		for (i in 0 until 5) {
			val value = num(boss.get("boss_kills_tier_" + (i + 1)))
			if (value != null) {
				kills[i] = maxOf(0, Math.round(value))
			}
		}
		return kills
	}

	@JvmStatic
	fun readClassXp(member: JsonObject, className: String?): Float {
		val dungeons = obj(member.get("dungeons"))
		val classes = dungeons?.let { obj(it.get("player_classes")) }
		val clazz = classes?.let { obj(it.get(className)) } ?: return 0f
		return num(clazz.get("experience")) ?: num(clazz.get("xp")) ?: 0f
	}

	@JvmStatic
	fun readCatacombsXp(member: JsonObject): Float {
		val dungeons = obj(member.get("dungeons"))
		val types = dungeons?.let { obj(it.get("dungeon_types")) }
		val type = types?.let { obj(it.get("catacombs")) }
		return type?.let { num(it.get("experience")) } ?: 0f
	}

	@JvmStatic
	fun skillLevel(member: JsonObject?, skill: String): Int {
		val xp = readSkillXpDouble(member, skill)
		return floor(getLevel(skillTable(skill), xp, skillCap(skill, member), false).level.toDouble()).toInt()
	}

	@JvmStatic
	fun skillCap(skill: String?, member: JsonObject?): Int {
		var base = RepoData.skillCap(skill)
		if (member == null) {
			return if (skill == "runecrafting" || skill == "social") minOf(base, 25) else base
		}
		if (skill == "farming") {
			val jacobs = obj(member.get("jacobs_contest")) ?: obj(member.get("jacob2"))
			val perks = jacobs?.let { obj(it.get("perks")) }
			val extra = perks?.let { num(it.get("farming_level_cap")) }
			if (extra != null) {
				base += Math.round(extra)
			}
		}
		if (skill == "foraging") {
			base += foragingExtraLevelCap(member)
		}
		if (skill == "taming") {
			val petsData = obj(member.get("pets_data"))
			val petCare = petsData?.let { obj(it.get("pet_care")) }
			val sacrificedEl = petCare?.get("pet_types_sacrificed")
			if (sacrificedEl != null && sacrificedEl.isJsonArray) {
				val sacrificed = sacrificedEl.asJsonArray.size()
				base = minOf(60, base + maxOf(0, sacrificed))
			}
		}
		if (skill == "runecrafting" || skill == "social") {
			return minOf(base, 25)
		}
		return base
	}

	/**
	 * Extra Foraging levels past the base soft cap (50 → up to 57).
	 *
	 * <p>Uses {@code SKILL_FORAGING_extra_level_cap} when present, and also recomputes from
	 * Fig/Mangrove/Helix IX collections + Heart of the Forest (same approach as Skyblocker),
	 * taking the higher of the two so a stale API field cannot under-report the in-game cap.
	 */
	@JvmStatic
	fun foragingExtraLevelCap(member: JsonObject?): Int {
		val fromApi = foragingExtraLevelCapFromApi(member)
		val fromProgress = foragingExtraLevelCapFromProgress(member)
		return minOf(7, maxOf(fromApi, fromProgress))
	}

	private fun foragingExtraLevelCapFromApi(member: JsonObject?): Int {
		val playerData = obj(member?.get("player_data"))
		val experience = playerData?.let { obj(it.get("experience")) } ?: return 0
		val extra = num(experience.get("SKILL_FORAGING_extra_level_cap"))
		if (extra == null || extra.isNaN() || extra.isInfinite()) {
			return 0
		}
		return maxOf(0, Math.round(extra))
	}

	/**
	 * Skyblocker-style recompute: +1 per Fig/Mangrove/Helix collection IX, +2 Agatha
	 * once any foraging-cap progress exists, +2 when HOTF level is at least 4 (Miria).
	 */
	private fun foragingExtraLevelCapFromProgress(member: JsonObject?): Int {
		var collections = 0
		if (collectionTierAtLeast(member, "FIG_LOG", 9)) {
			collections++
		}
		if (collectionTierAtLeast(member, "MANGROVE_LOG", 9)) {
			collections++
		}
		if (collectionTierAtLeast(member, "HELIX_LOG", 9)) {
			collections++
		}
		val hotf = hotfLevel(member)
		val api = foragingExtraLevelCapFromApi(member)
		var extra = collections
		// Agatha shop pair. Skyblocker always adds these once foraging-cap progress exists.
		if (collections > 0 || hotf > 0 || api > 0) {
			extra += 2
		}
		if (hotf >= 4) {
			extra += 2
		}
		return extra
	}

	private fun collectionTierAtLeast(member: JsonObject?, collectionId: String?, minTier: Int): Boolean {
		if (member == null || collectionId.isNullOrBlank() || minTier <= 0) {
			return false
		}
		if (unlockedCollectionTier(member, collectionId) >= minTier) {
			return true
		}
		// Fallback: derive tier from collection amounts when unlocked_coll_tiers is incomplete.
		val collection = obj(member.get("collection")) ?: return false
		val amount = numDouble(collection.get(collectionId)) ?: numDouble(collection.get(collectionId.uppercase(Locale.ROOT)))
		if (amount == null || amount <= 0.0) {
			return false
		}
		for (category in HypixelCollectionsCache.categories()) {
			for (item in category.items) {
				if (!item.id.equals(collectionId, ignoreCase = true)) {
					continue
				}
				return item.tierFor(Math.round(amount)) >= minTier
			}
		}
		return false
	}

	private fun unlockedCollectionTier(member: JsonObject, collectionId: String): Int {
		val playerData = obj(member.get("player_data"))
		val playerTiers = playerData?.get("unlocked_coll_tiers")
		val memberTiers = member.get("unlocked_coll_tiers")
		val array = when {
			playerTiers != null && playerTiers.isJsonArray -> playerTiers.asJsonArray
			memberTiers != null && memberTiers.isJsonArray -> memberTiers.asJsonArray
			else -> return 0
		}
		val prefix = collectionId.trim().uppercase(Locale.ROOT) + "_"
		var best = 0
		for (el in array) {
			if (el == null || !el.isJsonPrimitive) {
				continue
			}
			val key: String? = try {
				el.asString
			} catch (exception: RuntimeException) {
				if (!SoftDataFailure.isSoft(exception)) {
					throw exception
				}
				continue
			}
			if (key == null) {
				continue
			}
			val upper = key.trim().uppercase(Locale.ROOT)
			if (!upper.startsWith(prefix)) {
				continue
			}
			// Malformed tier suffixes are ignored.
			upper.substring(prefix.length).toIntOrNull()?.let { best = maxOf(best, it) }
		}
		return best
	}

	private fun hotfLevel(member: JsonObject?): Int {
		val skillTree = obj(member?.get("skill_tree"))
		val experience = skillTree?.let { obj(it.get("experience")) }
		val xp = experience?.let { numDouble(it.get("foraging")) }
		if (xp == null || xp <= 0.0) {
			return 0
		}
		val leveling = RepoData.leveling()
		val table = if (leveling != null && leveling.has("HOTF") && leveling.get("HOTF").isJsonArray) leveling.getAsJsonArray("HOTF") else null
		if (table == null || table.isEmpty) {
			return 0
		}
		val cap = maxOf(1, RepoData.skillCap("HOTF"))
		return floor(getLevel(table, xp, cap, false).level.toDouble()).toInt()
	}

	@JvmStatic
	fun skillTable(skill: String?): JsonArray? {
		val leveling = RepoData.leveling() ?: return null
		if (skill == "runecrafting" && leveling.get("runecrafting_xp") is JsonArray) {
			return leveling.getAsJsonArray("runecrafting_xp")
		}
		if (skill == "social" && leveling.get("social") is JsonArray) {
			return leveling.getAsJsonArray("social")
		}
		return RepoData.levelingXp()
	}

	@JvmStatic
	fun num(element: JsonElement?): Float? = numDouble(element)?.toFloat()

	@JvmStatic
	fun numDouble(element: JsonElement?): Double? {
		if (element == null || element.isJsonNull || !element.isJsonPrimitive) {
			return null
		}
		return try {
			element.asDouble
		} catch (exception: RuntimeException) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception
			}
			null
		}
	}

	@JvmStatic
	fun longAmount(element: JsonElement?): Long {
		val value = numDouble(element)
		if (value == null || value.isNaN() || value.isInfinite()) {
			return 0L
		}
		return maxOf(0L, Math.round(value))
	}

	@JvmStatic
	fun obj(element: JsonElement?): JsonObject? = if (element != null && element.isJsonObject) element.asJsonObject else null
}
