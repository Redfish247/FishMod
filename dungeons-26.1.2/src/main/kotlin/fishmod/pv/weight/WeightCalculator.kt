/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.weight

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fishmod.pv.data.Leveling
import fishmod.pv.data.RepoData
import java.util.Locale
import kotlin.math.acos
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Senither + Lily weight from NEU weight.json coefficients (formulas reimplemented).
 */
object WeightCalculator {
	private val SKILL_NAMES = listOf(
		"farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "taming"
	)
	private val SLAYER_NAMES = listOf("zombie", "spider", "wolf", "enderman", "blaze", "vampire")
	private val LILY_SLAYER_NAMES = listOf("zombie", "spider", "wolf", "enderman", "blaze")
	private val DUNGEON_CLASSES = listOf("healer", "mage", "berserk", "archer", "tank")
	private const val SKILLS_LEVEL_50 = 55_172_425.0
	private const val SKILLS_LEVEL_60 = 111_672_425.0
	private const val CATACOMBS_LEVEL_50_XP = 569_809_640.0

	@JvmStatic
	fun senither(member: JsonObject?, levels: Map<String, Leveling.Progress>?): WeightBreakdown {
		val weight = RepoData.weight()
		if (weight == null || member == null || levels == null) return WeightBreakdown.empty(WeightSystem.SENITHER)

		val skillLines = ArrayList<WeightBreakdown.Line>()
		var skillBase = 0.0
		var skillOverflow = 0.0
		for (skill in SKILL_NAMES) {
			val part = senitherSkill(weight, levels[skill], skill)
			skillBase += part.base
			skillOverflow += part.overflow
			skillLines.add(WeightBreakdown.Line(title(skill), part.base, part.overflow))
		}

		val slayerLines = ArrayList<WeightBreakdown.Line>()
		var slayerBase = 0.0
		var slayerOverflow = 0.0
		for (slayer in SLAYER_NAMES) {
			val part = senitherSlayer(weight, levels[slayer], slayer)
			if (part.base + part.overflow <= 0) continue
			slayerBase += part.base
			slayerOverflow += part.overflow
			slayerLines.add(WeightBreakdown.Line(title(slayer), part.base, part.overflow))
		}

		val dungeonLines = ArrayList<WeightBreakdown.Line>()
		var dungeonBase = 0.0
		var dungeonOverflow = 0.0
		val cataXp = Leveling.readCatacombsXp(member)
		val cata = Leveling.getLevel(RepoData.catacombsXp(), cataXp, 50, false)
		val cataPart = senitherDungeon(weight, cata, "senither.dungeons.catacombs")
		dungeonBase += cataPart.base
		dungeonOverflow += cataPart.overflow
		dungeonLines.add(WeightBreakdown.Line("Catacombs", cataPart.base, cataPart.overflow))
		for (className in DUNGEON_CLASSES) {
			val xp = Leveling.readClassXp(member, className)
			val progress = Leveling.getLevel(RepoData.catacombsXp(), xp, 50, false)
			val part = senitherDungeon(weight, progress, "senither.dungeons.classes.$className")
			dungeonBase += part.base
			dungeonOverflow += part.overflow
			dungeonLines.add(WeightBreakdown.Line(title(className), part.base, part.overflow))
		}

		val base = skillBase + slayerBase + dungeonBase
		val overflow = skillOverflow + slayerOverflow + dungeonOverflow
		val categories = listOf(
			WeightBreakdown.Category("Skills", skillBase, skillOverflow, skillLines),
			WeightBreakdown.Category("Slayers", slayerBase, slayerOverflow, slayerLines),
			WeightBreakdown.Category("Dungeons", dungeonBase, dungeonOverflow, dungeonLines)
		)
		return WeightBreakdown(WeightSystem.SENITHER, base, overflow, WeightStages.senitherStage(base + overflow), categories)
	}

	@JvmStatic
	fun lily(member: JsonObject?, levels: Map<String, Leveling.Progress>?): WeightBreakdown {
		val weight = RepoData.weight()
		if (weight == null || member == null || levels == null) return WeightBreakdown.empty(WeightSystem.LILY)
		// Lily always uses the shared leveling_xp table capped at 60 (NEU / official Lily).
		val lilySkills = lilySkillLevels(member)

		var skillAverage = 0.0
		for (skill in SKILL_NAMES) {
			skillAverage += lilySkills[skill]?.level?.toInt() ?: 0
		}
		skillAverage /= SKILL_NAMES.size

		var skillBase = 0.0
		var skillOverflow = 0.0
		for (skill in SKILL_NAMES) {
			val part = lilySkill(weight, lilySkills[skill], skill, skillAverage)
			skillBase += part.base
			skillOverflow += part.overflow
		}

		var slayerBase = 0.0
		val slayerLines = ArrayList<WeightBreakdown.Line>()
		for (slayer in LILY_SLAYER_NAMES) {
			val part = lilySlayer(weight, levels[slayer], slayer)
			if (part.base <= 0) continue
			slayerBase += part.base
			slayerLines.add(WeightBreakdown.Line(title(slayer), part.base, 0.0))
		}

		val cataXp = Leveling.readCatacombsXp(member)
		val cata = Leveling.getLevel(RepoData.catacombsXp(), cataXp, 50, false)
		val dungeonExp = lilyDungeonExperience(weight, cata)
		val normalCompletion = lilyCompletionWeight(weight, member, true)
		val masterCompletion = lilyCompletionWeight(weight, member, false)

		val categories = listOf(
			WeightBreakdown.Category("Skills", skillBase, skillOverflow, listOf(
				WeightBreakdown.Line("Base", skillBase, 0.0),
				WeightBreakdown.Line("Overflow", 0.0, skillOverflow)
			)),
			WeightBreakdown.Category("Slayers", slayerBase, 0.0, slayerLines),
			WeightBreakdown.Category("Dungeons", dungeonExp + normalCompletion + masterCompletion, 0.0, listOf(
				WeightBreakdown.Line("Experience", dungeonExp, 0.0),
				WeightBreakdown.Line("Catacombs Completion", normalCompletion, 0.0),
				WeightBreakdown.Line("Master Catacombs Completion", masterCompletion, 0.0)
			))
		)
		val base = skillBase + slayerBase + dungeonExp + normalCompletion + masterCompletion
		val overflow = skillOverflow
		return WeightBreakdown(WeightSystem.LILY, base, overflow, WeightStages.lilyRank(base + overflow), categories)
	}

	/** Lily skill levels ignore Jacob/farming caps - always leveling_xp to 60. */
	private fun lilySkillLevels(member: JsonObject): Map<String, Leveling.Progress> {
		val table = RepoData.levelingXp()
		return SKILL_NAMES.associateWith { skill ->
			Leveling.getLevel(table, Leveling.readSkillXpDouble(member, skill), 60, false)
		}
	}

	private fun lilySkill(weight: JsonObject, levelObj: Leveling.Progress?, skillName: String, skillAverage: Double): Part {
		if (levelObj == null) return Part.ZERO
		val srwTable = arrayAt(weight, "lily.skills.ratio_weight.$skillName")
		val overallEl = RepoData.path(weight, "lily.skills.overall")
		if (srwTable == null || srwTable.isEmpty || overallEl == null || !overallEl.isJsonPrimitive) return Part.ZERO
		val currentLevel = min(levelObj.level.toInt(), srwTable.size() - 1)
		val overall = overallEl.asDouble
		val last = srwTable.get(srwTable.size() - 1).asDouble
		var base = ((12 * (skillAverage / 60).pow(2.44780217148309)) * srwTable.get(currentLevel).asDouble * last) +
			(last * (currentLevel / 60.0).pow(2.0.pow(0.5)))
		base *= overall

		var overflow = 0.0
		if (levelObj.totalXp > SKILLS_LEVEL_60) {
			val factorEl = RepoData.path(weight, "lily.skills.factors.$skillName")
			val multEl = RepoData.path(weight, "lily.skills.overflow_multipliers.$skillName")
			if (factorEl != null && factorEl.isJsonPrimitive && multEl != null && multEl.isJsonPrimitive) {
				val factor = factorEl.asDouble
				val effectiveOver = (levelObj.totalXp - SKILLS_LEVEL_60).pow(factor)
				val t = (effectiveOver / SKILLS_LEVEL_60) * multEl.asDouble
				if (t > 0) overflow = overall * t
			}
		}
		return Part(base, overflow)
	}

	private fun lilySlayer(weight: JsonObject, levelObj: Leveling.Progress?, slayerName: String): Part {
		if (levelObj == null || levelObj.totalXp <= 0) return Part.ZERO
		val currentSlayerXp = levelObj.totalXp.toInt()
		val d = currentSlayerXp / 100000.0
		val score = if (currentSlayerXp >= 6416) {
			val dd = (d - 3.0.pow(-5.0 / 2)) * (d + 3.0.pow(-5.0 / 2))
			val u = cbrt(3 * (d + sqrt(max(0.0, dd))))
			val v = cbrt(3 * (d - sqrt(max(0.0, dd))))
			u + v - 1
		} else {
			sqrt(4.0 / 3) * cos(acos(min(1.0, max(-1.0, d * 3.0.pow(5.0 / 2)))) / 3) - 1
		}

		val scaleEl = RepoData.path(weight, "lily.slayer.deprecation_scaling.$slayerName")
		if (scaleEl == null || !scaleEl.isJsonPrimitive) return Part.ZERO
		val scaleFactor = scaleEl.asDouble
		val intScore = score.toInt()
		val distance = currentSlayerXp - lilyActualInt(intScore)
		val effectiveDistance = distance * scaleFactor.pow(intScore)
		val effectiveScore = lilyEffectiveInt(intScore, scaleFactor) + effectiveDistance
		val weightValue = when (slayerName) {
			"zombie" -> (effectiveScore / 9250) + (currentSlayerXp / 1_000_000.0)
			"spider" -> (effectiveScore / 7019.57) + ((currentSlayerXp * 1.6) / 1_000_000)
			"wolf" -> (effectiveScore / 2982.06) + ((currentSlayerXp * 3.6) / 1_000_000)
			"enderman" -> (effectiveScore / 996.3003) + ((currentSlayerXp * 10.0) / 1_000_000)
			"blaze" -> (effectiveScore / 935.0455) + ((currentSlayerXp * 10.0) / 1_000_000)
			else -> return Part.ZERO
		}
		return Part(2 * weightValue, 0.0)
	}

	private fun lilyActualInt(intScore: Int): Double =
		((intScore.toDouble().pow(3) / 6) + (intScore.toDouble().pow(2) / 2) + (intScore / 3.0)) * 100000

	private fun lilyEffectiveInt(intScore: Int, scaleFactor: Double): Double {
		var total = 0.0
		for (k in 0 until intScore) {
			total += ((k + 1).toDouble().pow(2) + (k + 1)) * scaleFactor.pow(k + 1)
		}
		return 1_000_000 * total * (0.05 / scaleFactor)
	}

	private fun lilyDungeonExperience(weight: JsonObject, catacombs: Leveling.Progress?): Double {
		if (catacombs == null || catacombs.level <= 0) return 0.0
		val overallEl = RepoData.path(weight, "lily.dungeons.overall")
		val overall = if (overallEl != null && overallEl.isJsonPrimitive) overallEl.asDouble else 1.0
		if (catacombs.totalXp < CATACOMBS_LEVEL_50_XP) {
			// Float division on purpose: level is a float
			val n = 0.2 * (catacombs.level / 50).toDouble().pow(1.538679118869934)
			return overall * ((1.18340401286164044.pow((catacombs.level + 1).toDouble()) - 1.05994990217254) * (1 + n))
		}
		val extra = 500.0 * ((catacombs.totalXp - CATACOMBS_LEVEL_50_XP) / 142452410.0).pow(1.0 / 1.781925776625157)
		return (4100 + extra) * 2
	}

	/** @param normal true = catacombs completions, false = master */
	private fun lilyCompletionWeight(weight: JsonObject, member: JsonObject, normal: Boolean): Double {
		val worth = objectAt(weight, "lily.dungeons.completion_worth") ?: return 0.0
		val buffs = objectAt(weight, "lily.dungeons.completion_buffs")

		var max1000 = 0.0
		var mMax1000 = 0.0
		for ((key, value) in worth.entrySet()) {
			if (!value.isJsonPrimitive) continue
			if (key.startsWith("catacombs_")) max1000 += value.asDouble else mMax1000 += value.asDouble
		}
		max1000 *= 1000
		mMax1000 *= 1000

		val dungeons = Leveling.obj(member.get("dungeons"))
		val types = dungeons?.let { Leveling.obj(it.get("dungeon_types")) }
		val type = types?.let { Leveling.obj(it.get(if (normal) "catacombs" else "master_catacombs")) }
		val completions = type?.let { Leveling.obj(it.get("tier_completions")) } ?: return 0.0

		var upperBound = 1500.0
		var score = 0.0
		if (normal) {
			for ((floor, value) in completions.entrySet()) {
				if (floor == "total") continue
				val key = "catacombs_$floor"
				if (!worth.has(key) || !value.isJsonPrimitive) continue
				score += floorScore(value.asInt, worth.get(key).asDouble, 7.5)
			}
			return if (max1000 <= 0) 0.0 else score / max1000 * upperBound * 2
		}

		for ((floor, value) in completions.entrySet()) {
			if (floor == "total") continue
			if (buffs != null && buffs.has(floor) && value.isJsonPrimitive) {
				val amount = value.asInt
				val threshold = 20.0
				val buff = buffs.get(floor).asDouble
				upperBound += if (amount >= threshold) buff else buff * (amount / threshold).pow(1.840896416)
			}
			val key = "master_catacombs_$floor"
			if (!worth.has(key) || !value.isJsonPrimitive) continue
			score += floorScore(value.asInt, worth.get(key).asDouble, 6.0)
		}
		return if (mMax1000 <= 0) 0.0 else (score / mMax1000) * upperBound * 2
	}

	// Completions past 1000 count logarithmically
	private fun floorScore(rawAmount: Int, worth: Double, logBase: Double): Double {
		var amount = rawAmount
		var excess = 0.0
		if (amount > 1000) {
			excess = (amount - 1000).toDouble()
			amount = 1000
		}
		var floorScore = amount * worth
		if (excess > 0) floorScore *= ln(excess / 1000 + 1) / ln(logBase) + 1
		return floorScore
	}

	private fun objectAt(root: JsonObject, path: String): JsonObject? =
		RepoData.path(root, path)?.takeIf { it.isJsonObject }?.asJsonObject

	private fun senitherSkill(weight: JsonObject, levelObj: Leveling.Progress?, skillName: String): Part {
		val curWeights = arrayAt(weight, "senither.skills.$skillName")
		if (curWeights == null || curWeights.size() < 2 || levelObj == null || levelObj.totalXp <= 0) return Part.ZERO
		val exponent = curWeights.get(0).asDouble
		val divider = curWeights.get(1).asDouble
		val level = levelObj.level.toDouble()
		val maxLevel = if (levelObj.maxLevel >= 60) 60 else 50
		val maxLevelExp = if (maxLevel == 50) SKILLS_LEVEL_50 else SKILLS_LEVEL_60
		val base = (level * 10).pow(0.5 + exponent + (level / 100)) / 1250
		if (levelObj.totalXp <= maxLevelExp) return Part(base, 0.0)
		return Part(Math.round(base).toDouble(), ((levelObj.totalXp - maxLevelExp) / divider).pow(0.968))
	}

	private fun senitherSlayer(weight: JsonObject, levelObj: Leveling.Progress?, slayer: String): Part {
		val curWeights = arrayAt(weight, "senither.slayer.$slayer")
		if (levelObj == null || curWeights == null || curWeights.size() < 2) return Part.ZERO
		val divider = curWeights.get(0).asDouble
		var modifier = curWeights.get(1).asDouble
		val xp = levelObj.totalXp.toInt()
		if (xp <= 1_000_000) return Part(if (xp == 0) 0.0 else xp / divider, 0.0)
		val base = 1_000_000 / divider
		var remaining = (xp - 1_000_000).toDouble()
		var overflow = 0.0
		val initialModifier = modifier
		while (remaining > 0) {
			val left = min(remaining, 1_000_000.0)
			overflow += (left / (divider * (1.5 + modifier))).pow(0.942)
			modifier += initialModifier
			remaining -= left
		}
		return Part(base, overflow)
	}

	private fun senitherDungeon(weight: JsonObject, level: Leveling.Progress?, path: String): Part {
		if (level == null || level.totalXp <= 0) return Part.ZERO
		val factorEl = RepoData.path(weight, path)
		if (factorEl == null || !factorEl.isJsonPrimitive) return Part.ZERO
		val factor = factorEl.asDouble
		val base = level.level.toDouble().pow(4.5) * factor
		if (level.totalXp <= CATACOMBS_LEVEL_50_XP) return Part(base, 0.0)
		val remaining = level.totalXp - CATACOMBS_LEVEL_50_XP
		val splitter = (4 * CATACOMBS_LEVEL_50_XP) / max(0.0001, base)
		return Part(floor(base), (remaining / splitter).pow(0.968))
	}

	private fun arrayAt(root: JsonObject, path: String): JsonArray? =
		RepoData.path(root, path)?.takeIf { it.isJsonArray }?.asJsonArray

	private fun title(id: String?): String {
		if (id.isNullOrBlank()) return "?"
		return id.substring(0, 1).uppercase(Locale.ROOT) + id.substring(1)
	}

	private data class Part(val base: Double, val overflow: Double) {
		companion object {
			val ZERO = Part(0.0, 0.0)
		}
	}

	@JvmStatic
	fun buildLevels(member: JsonObject?): MutableMap<String, Leveling.Progress> {
		val out = LinkedHashMap<String, Leveling.Progress>()
		if (member == null) return out
		for (skill in SKILL_NAMES) {
			val xp = Leveling.readSkillXpDouble(member, skill)
			val cap = Leveling.skillCap(skill, member)
			out[skill] = Leveling.getLevel(Leveling.skillTable(skill), xp, cap, false)
		}
		for (slayer in SLAYER_NAMES) {
			val xp = Leveling.readSlayerXp(member, slayer)
			val slayerTable = RepoData.slayerXp(slayer)
			val slayerCap = if (slayerTable == null || slayerTable.isEmpty) 9 else slayerTable.size()
			out[slayer] = Leveling.getLevel(slayerTable, xp, slayerCap, true)
		}
		return out
	}
}
