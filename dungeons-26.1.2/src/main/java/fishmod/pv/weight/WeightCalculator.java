/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.weight;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.data.Leveling;
import fishmod.pv.data.RepoData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Senither + Lily weight from NEU weight.json coefficients (formulas reimplemented).
 */
public final class WeightCalculator {
	private static final List<String> SKILL_NAMES = List.of(
		"farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "taming"
	);
	private static final List<String> SLAYER_NAMES = List.of("zombie", "spider", "wolf", "enderman", "blaze", "vampire");
	private static final List<String> DUNGEON_CLASSES = List.of("healer", "mage", "berserk", "archer", "tank");
	private static final double SKILLS_LEVEL_50 = 55_172_425;
	private static final double SKILLS_LEVEL_60 = 111_672_425;
	private static final double CATACOMBS_LEVEL_50_XP = 569_809_640;

	private WeightCalculator() {
	}

	public static WeightBreakdown senither(JsonObject member, Map<String, Leveling.Progress> levels) {
		JsonObject weight = RepoData.weight();
		if (weight == null || member == null || levels == null) {
			return WeightBreakdown.empty(WeightSystem.SENITHER);
		}
		List<WeightBreakdown.Line> skillLines = new ArrayList<>();
		double skillBase = 0;
		double skillOverflow = 0;
		for (String skill : SKILL_NAMES) {
			Part part = senitherSkill(weight, levels.get(skill), skill);
			skillBase += part.base;
			skillOverflow += part.overflow;
			skillLines.add(new WeightBreakdown.Line(title(skill), part.base, part.overflow));
		}

		List<WeightBreakdown.Line> slayerLines = new ArrayList<>();
		double slayerBase = 0;
		double slayerOverflow = 0;
		for (String slayer : SLAYER_NAMES) {
			Part part = senitherSlayer(weight, levels.get(slayer), slayer);
			if (part.base + part.overflow <= 0) {
				continue;
			}
			slayerBase += part.base;
			slayerOverflow += part.overflow;
			slayerLines.add(new WeightBreakdown.Line(title(slayer), part.base, part.overflow));
		}

		List<WeightBreakdown.Line> dungeonLines = new ArrayList<>();
		double dungeonBase = 0;
		double dungeonOverflow = 0;
		float cataXp = Leveling.readCatacombsXp(member);
		Leveling.Progress cata = Leveling.getLevel(RepoData.catacombsXp(), cataXp, 50, false);
		Part cataPart = senitherDungeon(weight, cata, "senither.dungeons.catacombs");
		dungeonBase += cataPart.base;
		dungeonOverflow += cataPart.overflow;
		dungeonLines.add(new WeightBreakdown.Line("Catacombs", cataPart.base, cataPart.overflow));
		for (String className : DUNGEON_CLASSES) {
			float xp = Leveling.readClassXp(member, className);
			Leveling.Progress progress = Leveling.getLevel(RepoData.catacombsXp(), xp, 50, false);
			Part part = senitherDungeon(weight, progress, "senither.dungeons.classes." + className);
			dungeonBase += part.base;
			dungeonOverflow += part.overflow;
			dungeonLines.add(new WeightBreakdown.Line(title(className), part.base, part.overflow));
		}

		double base = skillBase + slayerBase + dungeonBase;
		double overflow = skillOverflow + slayerOverflow + dungeonOverflow;
		List<WeightBreakdown.Category> categories = List.of(
			new WeightBreakdown.Category("Skills", skillBase, skillOverflow, skillLines),
			new WeightBreakdown.Category("Slayers", slayerBase, slayerOverflow, slayerLines),
			new WeightBreakdown.Category("Dungeons", dungeonBase, dungeonOverflow, dungeonLines)
		);
		return new WeightBreakdown(WeightSystem.SENITHER, base, overflow, WeightStages.senitherStage(base + overflow), categories);
	}

	public static WeightBreakdown lily(JsonObject member, Map<String, Leveling.Progress> levels) {
		JsonObject weight = RepoData.weight();
		if (weight == null || member == null || levels == null) {
			return WeightBreakdown.empty(WeightSystem.LILY);
		}
		// Lily always uses the shared leveling_xp table capped at 60 (NEU / official Lily).
		Map<String, Leveling.Progress> lilySkills = lilySkillLevels(member);

		double skillAverage = 0;
		for (String skill : SKILL_NAMES) {
			Leveling.Progress progress = lilySkills.get(skill);
			skillAverage += progress == null ? 0 : (int) progress.level();
		}
		skillAverage /= SKILL_NAMES.size();

		double skillBase = 0;
		double skillOverflow = 0;
		for (String skill : SKILL_NAMES) {
			Part part = lilySkill(weight, lilySkills.get(skill), skill, skillAverage);
			skillBase += part.base;
			skillOverflow += part.overflow;
		}

		double slayerBase = 0;
		List<WeightBreakdown.Line> slayerLines = new ArrayList<>();
		for (String slayer : List.of("zombie", "spider", "wolf", "enderman", "blaze")) {
			Part part = lilySlayer(weight, levels.get(slayer), slayer);
			if (part.base <= 0) {
				continue;
			}
			slayerBase += part.base;
			slayerLines.add(new WeightBreakdown.Line(title(slayer), part.base, 0));
		}

		float cataXp = Leveling.readCatacombsXp(member);
		Leveling.Progress cata = Leveling.getLevel(RepoData.catacombsXp(), cataXp, 50, false);
		double dungeonExp = lilyDungeonExperience(weight, cata);
		double normalCompletion = lilyCompletionWeight(weight, member, true);
		double masterCompletion = lilyCompletionWeight(weight, member, false);

		List<WeightBreakdown.Category> categories = List.of(
			new WeightBreakdown.Category("Skills", skillBase, skillOverflow, List.of(
				new WeightBreakdown.Line("Base", skillBase, 0),
				new WeightBreakdown.Line("Overflow", 0, skillOverflow)
			)),
			new WeightBreakdown.Category("Slayers", slayerBase, 0, slayerLines),
			new WeightBreakdown.Category("Dungeons", dungeonExp + normalCompletion + masterCompletion, 0, List.of(
				new WeightBreakdown.Line("Experience", dungeonExp, 0),
				new WeightBreakdown.Line("Catacombs Completion", normalCompletion, 0),
				new WeightBreakdown.Line("Master Catacombs Completion", masterCompletion, 0)
			))
		);
		double base = skillBase + slayerBase + dungeonExp + normalCompletion + masterCompletion;
		double overflow = skillOverflow;
		return new WeightBreakdown(WeightSystem.LILY, base, overflow, WeightStages.lilyRank(base + overflow), categories);
	}

	/** Lily skill levels ignore Jacob/farming caps - always leveling_xp to 60. */
	private static Map<String, Leveling.Progress> lilySkillLevels(JsonObject member) {
		Map<String, Leveling.Progress> out = new LinkedHashMap<>();
		JsonArray table = RepoData.levelingXp();
		for (String skill : SKILL_NAMES) {
			double xp = Leveling.readSkillXpDouble(member, skill);
			out.put(skill, Leveling.getLevel(table, xp, 60, false));
		}
		return out;
	}

	private static Part lilySkill(JsonObject weight, Leveling.Progress levelObj, String skillName, double skillAverage) {
		if (levelObj == null) {
			return Part.ZERO;
		}
		JsonArray srwTable = arrayAt(weight, "lily.skills.ratio_weight." + skillName);
		JsonElement overallEl = RepoData.path(weight, "lily.skills.overall");
		if (srwTable == null || srwTable.isEmpty() || overallEl == null || !overallEl.isJsonPrimitive()) {
			return Part.ZERO;
		}
		int currentLevel = Math.min((int) levelObj.level(), srwTable.size() - 1);
		double overall = overallEl.getAsDouble();
		double base =
			((12 * Math.pow((skillAverage / 60), 2.44780217148309))
				* srwTable.get(currentLevel).getAsDouble()
				* srwTable.get(srwTable.size() - 1).getAsDouble())
				+ (srwTable.get(srwTable.size() - 1).getAsDouble() * Math.pow(currentLevel / 60.0, Math.pow(2, 0.5)));
		base *= overall;

		double overflow = 0;
		if (levelObj.totalXp() > SKILLS_LEVEL_60) {
			JsonElement factorEl = RepoData.path(weight, "lily.skills.factors." + skillName);
			JsonElement multEl = RepoData.path(weight, "lily.skills.overflow_multipliers." + skillName);
			if (factorEl != null && factorEl.isJsonPrimitive() && multEl != null && multEl.isJsonPrimitive()) {
				double factor = factorEl.getAsDouble();
				double effectiveOver = Math.pow(levelObj.totalXp() - SKILLS_LEVEL_60, factor);
				double t = (effectiveOver / SKILLS_LEVEL_60) * multEl.getAsDouble();
				if (t > 0) {
					overflow = overall * t;
				}
			}
		}
		return new Part(base, overflow);
	}

	private static Part lilySlayer(JsonObject weight, Leveling.Progress levelObj, String slayerName) {
		if (levelObj == null || levelObj.totalXp() <= 0) {
			return Part.ZERO;
		}
		int currentSlayerXp = (int) levelObj.totalXp();
		double d = currentSlayerXp / 100000.0;
		double score;
		if (currentSlayerXp >= 6416) {
			double D = (d - Math.pow(3, (-5.0 / 2))) * (d + Math.pow(3, -5.0 / 2));
			double u = Math.cbrt(3 * (d + Math.sqrt(Math.max(0, D))));
			double v = Math.cbrt(3 * (d - Math.sqrt(Math.max(0, D))));
			score = u + v - 1;
		} else {
			score = Math.sqrt(4.0 / 3) * Math.cos(Math.acos(Math.min(1, Math.max(-1, d * Math.pow(3, 5.0 / 2)))) / 3) - 1;
		}

		JsonElement scaleEl = RepoData.path(weight, "lily.slayer.deprecation_scaling." + slayerName);
		if (scaleEl == null || !scaleEl.isJsonPrimitive()) {
			return Part.ZERO;
		}
		double scaleFactor = scaleEl.getAsDouble();
		int intScore = (int) score;
		double distance = currentSlayerXp - lilyActualInt(intScore);
		double effectiveDistance = distance * Math.pow(scaleFactor, intScore);
		double effectiveScore = lilyEffectiveInt(intScore, scaleFactor) + effectiveDistance;
		double weightValue;
		switch (slayerName) {
			case "zombie" -> weightValue = (effectiveScore / 9250) + (currentSlayerXp / 1_000_000.0);
			case "spider" -> weightValue = (effectiveScore / 7019.57) + ((currentSlayerXp * 1.6) / 1_000_000);
			case "wolf" -> weightValue = (effectiveScore / 2982.06) + ((currentSlayerXp * 3.6) / 1_000_000);
			case "enderman" -> weightValue = (effectiveScore / 996.3003) + ((currentSlayerXp * 10.0) / 1_000_000);
			case "blaze" -> weightValue = (effectiveScore / 935.0455) + ((currentSlayerXp * 10.0) / 1_000_000);
			default -> {
				return Part.ZERO;
			}
		}
		return new Part(2 * weightValue, 0);
	}

	private static double lilyActualInt(int intScore) {
		return (((Math.pow(intScore, 3) / 6) + (Math.pow(intScore, 2) / 2) + (intScore / 3.0)) * 100000);
	}

	private static double lilyEffectiveInt(int intScore, double scaleFactor) {
		double total = 0;
		for (int k = 0; k < intScore; k++) {
			total += (Math.pow((k + 1), 2) + (k + 1)) * Math.pow(scaleFactor, (k + 1));
		}
		return 1_000_000 * total * (0.05 / scaleFactor);
	}

	private static double lilyDungeonExperience(JsonObject weight, Leveling.Progress catacombs) {
		if (catacombs == null || catacombs.level() <= 0) {
			return 0;
		}
		JsonElement overallEl = RepoData.path(weight, "lily.dungeons.overall");
		double overall = overallEl != null && overallEl.isJsonPrimitive() ? overallEl.getAsDouble() : 1;
		if (catacombs.totalXp() < CATACOMBS_LEVEL_50_XP) {
			double n = 0.2 * Math.pow(catacombs.level() / 50, 1.538679118869934);
			return overall * ((Math.pow(1.18340401286164044, (catacombs.level() + 1)) - 1.05994990217254) * (1 + n));
		}
		double extra = 500.0 * Math.pow((catacombs.totalXp() - CATACOMBS_LEVEL_50_XP) / 142452410.0, 1.0 / 1.781925776625157);
		return (4100 + extra) * 2;
	}

	/** @param normal true = catacombs completions, false = master */
	private static double lilyCompletionWeight(JsonObject weight, JsonObject member, boolean normal) {
		JsonObject worth = objectAt(weight, "lily.dungeons.completion_worth");
		JsonObject buffs = objectAt(weight, "lily.dungeons.completion_buffs");
		if (worth == null) {
			return 0;
		}

		double max1000 = 0;
		double mMax1000 = 0;
		for (var entry : worth.entrySet()) {
			if (!entry.getValue().isJsonPrimitive()) {
				continue;
			}
			if (entry.getKey().startsWith("catacombs_")) {
				max1000 += entry.getValue().getAsDouble();
			} else {
				mMax1000 += entry.getValue().getAsDouble();
			}
		}
		max1000 *= 1000;
		mMax1000 *= 1000;

		JsonObject dungeons = Leveling.obj(member.get("dungeons"));
		JsonObject types = dungeons == null ? null : Leveling.obj(dungeons.get("dungeon_types"));
		JsonObject type = types == null ? null : Leveling.obj(types.get(normal ? "catacombs" : "master_catacombs"));
		JsonObject completions = type == null ? null : Leveling.obj(type.get("tier_completions"));
		if (completions == null) {
			return 0;
		}

		double upperBound = 1500;
		double score = 0;
		if (normal) {
			for (var floor : completions.entrySet()) {
				if ("total".equals(floor.getKey())) {
					continue;
				}
				String key = "catacombs_" + floor.getKey();
				if (!worth.has(key) || !floor.getValue().isJsonPrimitive()) {
					continue;
				}
				int amount = floor.getValue().getAsInt();
				double excess = 0;
				if (amount > 1000) {
					excess = amount - 1000;
					amount = 1000;
				}
				double floorScore = amount * worth.get(key).getAsDouble();
				if (excess > 0) {
					floorScore *= Math.log(excess / 1000 + 1) / Math.log(7.5) + 1;
				}
				score += floorScore;
			}
			return max1000 <= 0 ? 0 : score / max1000 * upperBound * 2;
		}

		for (var floor : completions.entrySet()) {
			if ("total".equals(floor.getKey())) {
				continue;
			}
			if (buffs != null && buffs.has(floor.getKey()) && floor.getValue().isJsonPrimitive()) {
				int amount = floor.getValue().getAsInt();
				double threshold = 20;
				double buff = buffs.get(floor.getKey()).getAsDouble();
				if (amount >= threshold) {
					upperBound += buff;
				} else {
					upperBound += buff * Math.pow((amount / threshold), 1.840896416);
				}
			}
			String key = "master_catacombs_" + floor.getKey();
			if (!worth.has(key) || !floor.getValue().isJsonPrimitive()) {
				continue;
			}
			int amount = floor.getValue().getAsInt();
			double excess = 0;
			if (amount > 1000) {
				excess = amount - 1000;
				amount = 1000;
			}
			double floorScore = amount * worth.get(key).getAsDouble();
			if (excess > 0) {
				floorScore *= (Math.log((excess / 1000) + 1) / Math.log(6)) + 1;
			}
			score += floorScore;
		}
		return mMax1000 <= 0 ? 0 : (score / mMax1000) * upperBound * 2;
	}

	private static JsonObject objectAt(JsonObject root, String path) {
		JsonElement el = RepoData.path(root, path);
		return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
	}

	private static Part senitherSkill(JsonObject weight, Leveling.Progress levelObj, String skillName) {
		JsonArray curWeights = arrayAt(weight, "senither.skills." + skillName);
		if (curWeights == null || curWeights.size() < 2 || levelObj == null || levelObj.totalXp() <= 0) {
			return Part.ZERO;
		}
		double exponent = curWeights.get(0).getAsDouble();
		double divider = curWeights.get(1).getAsDouble();
		double level = levelObj.level();
		int maxLevel = levelObj.maxLevel() >= 60 ? 60 : 50;
		double maxLevelExp = maxLevel == 50 ? SKILLS_LEVEL_50 : SKILLS_LEVEL_60;
		double base = Math.pow(level * 10, 0.5 + exponent + (level / 100)) / 1250;
		if (levelObj.totalXp() <= maxLevelExp) {
			return new Part(base, 0);
		}
		return new Part(Math.round(base), Math.pow((levelObj.totalXp() - maxLevelExp) / divider, 0.968));
	}

	private static Part senitherSlayer(JsonObject weight, Leveling.Progress levelObj, String slayer) {
		JsonArray curWeights = arrayAt(weight, "senither.slayer." + slayer);
		if (levelObj == null || curWeights == null || curWeights.size() < 2) {
			return Part.ZERO;
		}
		double divider = curWeights.get(0).getAsDouble();
		double modifier = curWeights.get(1).getAsDouble();
		int xp = (int) levelObj.totalXp();
		if (xp <= 1_000_000) {
			return new Part(xp == 0 ? 0 : xp / divider, 0);
		}
		double base = 1_000_000 / divider;
		double remaining = xp - 1_000_000;
		double overflow = 0;
		double initialModifier = modifier;
		while (remaining > 0) {
			double left = Math.min(remaining, 1_000_000);
			overflow += Math.pow(left / (divider * (1.5 + modifier)), 0.942);
			modifier += initialModifier;
			remaining -= left;
		}
		return new Part(base, overflow);
	}

	private static Part senitherDungeon(JsonObject weight, Leveling.Progress level, String path) {
		if (level == null || level.totalXp() <= 0) {
			return Part.ZERO;
		}
		JsonElement factorEl = RepoData.path(weight, path);
		if (factorEl == null || !factorEl.isJsonPrimitive()) {
			return Part.ZERO;
		}
		double factor = factorEl.getAsDouble();
		double base = Math.pow(level.level(), 4.5) * factor;
		if (level.totalXp() <= CATACOMBS_LEVEL_50_XP) {
			return new Part(base, 0);
		}
		double remaining = level.totalXp() - CATACOMBS_LEVEL_50_XP;
		double splitter = (4 * CATACOMBS_LEVEL_50_XP) / Math.max(0.0001, base);
		return new Part(Math.floor(base), Math.pow(remaining / splitter, 0.968));
	}

	private static JsonArray arrayAt(JsonObject root, String path) {
		JsonElement el = RepoData.path(root, path);
		return el != null && el.isJsonArray() ? el.getAsJsonArray() : null;
	}

	private static String title(String id) {
		if (id == null || id.isBlank()) {
			return "?";
		}
		return id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
	}

	private record Part(double base, double overflow) {
		static final Part ZERO = new Part(0, 0);
	}

	public static Map<String, Leveling.Progress> buildLevels(JsonObject member) {
		Map<String, Leveling.Progress> out = new LinkedHashMap<>();
		if (member == null) {
			return out;
		}
		for (String skill : SKILL_NAMES) {
			double xp = Leveling.readSkillXpDouble(member, skill);
			int cap = Leveling.skillCap(skill, member);
			out.put(skill, Leveling.getLevel(Leveling.skillTable(skill), xp, cap, false));
		}
		for (String slayer : SLAYER_NAMES) {
			float xp = Leveling.readSlayerXp(member, slayer);
			JsonArray slayerTable = RepoData.slayerXp(slayer);
			int slayerCap = slayerTable == null || slayerTable.isEmpty() ? 9 : slayerTable.size();
			out.put(slayer, Leveling.getLevel(slayerTable, xp, slayerCap, true));
		}
		return out;
	}
}
