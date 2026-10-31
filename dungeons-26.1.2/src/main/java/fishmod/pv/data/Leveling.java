/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.price.HypixelCollectionsCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Leveling {
	public record Progress(
		float level,
		double maxXpForLevel,
		boolean maxed,
		int maxLevel,
		double totalXp,
		double xpIntoLevel,
		double overflowXp,
		float overflowLevel
	) {
		public Progress(
			float level,
			double maxXpForLevel,
			boolean maxed,
			int maxLevel,
			double totalXp,
			double xpIntoLevel,
			double overflowXp
		) {
			this(
				level,
				maxXpForLevel,
				maxed,
				maxLevel,
				totalXp,
				xpIntoLevel,
				overflowXp,
				repeatedStepOverflowLevel(maxed, level, maxLevel, overflowXp, maxXpForLevel)
			);
		}

		public float fill() {
			if (maxed || maxXpForLevel <= 0D) {
				return 1.0f;
			}
			return (float) Math.max(0D, Math.min(1D, xpIntoLevel / maxXpForLevel));
		}

		/** Uncapped skill level including overflow (SkyHanni / in-game Skills menu). */
		public int displayLevel() {
			if (maxed) {
				return Math.max(maxLevel, (int) Math.floor(overflowLevel));
			}
			return (int) Math.floor(level);
		}

		/** Soft-cap level for skill bar labels. Overflow stays hover-only. */
		public int cappedLevel() {
			if (maxed) {
				return maxLevel;
			}
			return (int) Math.floor(level);
		}

		public String hoverText() {
			if (maxed) {
				return overflowHoverText();
			}
			long into = Math.round(xpIntoLevel);
			long need = Math.round(maxXpForLevel);
			if (need <= 0L) {
				return FormatUtil.commas(Math.round(totalXp)) + "/" + FormatUtil.commas(Math.round(totalXp)) + " (100%)";
			}
			double pct = fill() * 100.0;
			return FormatUtil.commas(into) + "/" + FormatUtil.commas(need) + " (" + FormatUtil.oneDecimal(pct) + "%)";
		}

		/** Skill hover used by Mining HOTM bars, e.g. {@code HOTM 7 - 60k / 300k to Level 8}. */
		public String skillHover(String name) {
			int lvl = cappedLevel();
			if (maxed) {
				return name + " " + lvl + " - " + overflowHoverText();
			}
			int next = Math.min(maxLevel, lvl + 1);
			long into = Math.round(xpIntoLevel);
			long need = Math.round(maxXpForLevel);
			return name + " " + lvl + " - " + FormatUtil.shortXp(into) + " / " + FormatUtil.shortXp(need)
				+ " to Level " + next;
		}

		public List<PvTooltip.Line> skillHoverLines(String name) {
			// Match Hypixel Skills menu: title is the soft cap, overflow is listed separately.
			List<PvTooltip.Line> lines = new ArrayList<>(6);
			lines.add(PvTooltip.Line.of(skillTitle(name), PvDraw.COLOR_ACCENT));
			for (String[] row : skillHoverRows()) {
				lines.add(PvTooltip.Line.row(row[0], PvDraw.COLOR_MUTED, row[1], PvDraw.COLOR_GOLD));
			}
			return lines;
		}

		/** {@link #skillHoverLines} flattened for bars that only carry a String; see {@link #hoverTextLines}. */
		public String skillHoverText(String name) {
			StringBuilder text = new StringBuilder(skillTitle(name));
			for (String[] row : skillHoverRows()) {
				text.append('\n').append(row[0]).append('\t').append(row[1]);
			}
			return text.toString();
		}

		private String skillTitle(String name) {
			return (name == null ? "?" : name) + " " + cappedLevel();
		}

		private List<String[]> skillHoverRows() {
			List<String[]> rows = new ArrayList<>(4);
			rows.add(new String[] {"Total XP", FormatUtil.commas(Math.round(Math.max(0D, totalXp)))});
			if (overflowXp > 0.5D) {
				rows.add(new String[] {"Overflow XP", FormatUtil.commas(Math.round(overflowXp))});
			}
			if (maxed) {
				if (overflowLevel > maxLevel) {
					int current = (int) Math.floor(overflowLevel);
					double pct = (overflowLevel - current) * 100.0;
					rows.add(new String[] {"Overflow Level", FormatUtil.oneDecimal(overflowLevel)});
					rows.add(new String[] {"Progress to " + (current + 1), FormatUtil.oneDecimal(pct) + "%"});
				}
			} else {
				int next = Math.min(maxLevel, cappedLevel() + 1);
				long into = Math.round(xpIntoLevel);
				long need = Math.round(maxXpForLevel);
				rows.add(new String[] {"Progress to " + next, FormatUtil.shortXp(into) + " / " + FormatUtil.shortXp(need)
					+ " (" + FormatUtil.oneDecimal(fill() * 100.0) + "%)"});
			}
			return rows;
		}

		private String overflowHoverText() {
			String text = "Overflow Level: " + FormatUtil.oneDecimal(overflowLevel)
				+ " · Overflow XP: " + FormatUtil.shortXp(Math.max(0F, overflowXp));
			if (maxXpForLevel > 0F) {
				text += " · " + FormatUtil.commas(Math.round(xpIntoLevel))
					+ " / " + FormatUtil.commas(Math.round(maxXpForLevel))
					+ " to " + (maxLevel + 1);
			}
			return text;
		}

		/** e.g. {@code Revenant 9 - 1,240,000 / 2,000,000 XP} or overflow when maxed. */
		public String slayerHover(String name) {
			int tier = (int) Math.floor(level);
			if (maxed) {
				return name + " " + tier + " - Overflow: " + FormatUtil.shortXp(Math.max(0F, overflowXp));
			}
			long into = Math.round(xpIntoLevel);
			long need = Math.round(maxXpForLevel);
			return name + " " + tier + " - " + FormatUtil.commas(into) + " / " + FormatUtil.commas(need) + " XP";
		}

		public List<PvTooltip.Line> slayerHoverLines(String name) {
			int tier = (int) Math.floor(level);
			String title = (name == null ? "?" : name) + " " + tier;
			List<PvTooltip.Line> lines = new ArrayList<>(5);
			lines.add(PvTooltip.Line.of(title, PvDraw.COLOR_ACCENT));
			lines.add(PvTooltip.Line.row(
				"Total XP",
				PvDraw.COLOR_MUTED,
				FormatUtil.commas(Math.round(Math.max(0F, totalXp))),
				PvDraw.COLOR_GOLD
			));
			if (overflowXp > 0.5F) {
				lines.add(PvTooltip.Line.row(
					"Overflow",
					PvDraw.COLOR_MUTED,
					FormatUtil.shortXp(overflowXp),
					PvDraw.COLOR_GOLD
				));
			}
			if (!maxed) {
				long into = Math.round(xpIntoLevel);
				long need = Math.round(maxXpForLevel);
				lines.add(PvTooltip.Line.of(
					FormatUtil.commas(into) + " / " + FormatUtil.commas(need) + " XP",
					PvDraw.COLOR_GOLD
				));
			}
			return lines;
		}

		/**
		 * XP line plus kill counts ({@code T1: n  T2: n …}).
		 * When {@code leveling.json} has reliable XP-per-kill, also estimates kills to next level.
		 */
		public String slayerHoverWithKills(String name, String slayerId, int[] tierKills) {
			StringBuilder sb = new StringBuilder(slayerHover(name));
			if (tierKills != null && tierKills.length > 0) {
				sb.append('\n');
				for (int i = 0; i < tierKills.length; i++) {
					if (i > 0) {
						sb.append("  ");
					}
					sb.append('T').append(i + 1).append(": ").append(FormatUtil.commas(Math.max(0, tierKills[i])));
				}
			}
			JsonArray bossXp = RepoData.slayerBossXp(slayerId);
			if (bossXp != null && !bossXp.isEmpty() && !maxed && maxXpForLevel > 0F) {
				double xpNeeded = Math.max(0D, maxXpForLevel - xpIntoLevel);
				int highest = Math.min(bossXp.size(), RepoData.slayerHighestTier(slayerId));
				int idx = Math.max(0, highest - 1);
				float perKill = bossXp.get(idx).getAsFloat();
				if (perKill > 0F && xpNeeded > 0F) {
					long killsNeeded = (long) Math.ceil(xpNeeded / perKill);
					sb.append('\n').append("≈ ").append(FormatUtil.commas(killsNeeded))
						.append(" T").append(idx + 1).append(" kills to next");
				}
			}
			return sb.toString();
		}

		public List<PvTooltip.Line> slayerHoverLinesWithKills(String name, String slayerId, int[] tierKills) {
			List<PvTooltip.Line> lines = new ArrayList<>(slayerHoverLines(name));
			if (tierKills != null && tierKills.length > 0) {
				StringBuilder kills = new StringBuilder();
				for (int i = 0; i < tierKills.length; i++) {
					if (i > 0) {
						kills.append("  ");
					}
					kills.append('T').append(i + 1).append(": ").append(FormatUtil.commas(Math.max(0, tierKills[i])));
				}
				lines.add(PvTooltip.Line.of(kills.toString(), PvDraw.COLOR_MUTED));
			}
			JsonArray bossXp = RepoData.slayerBossXp(slayerId);
			if (bossXp != null && !bossXp.isEmpty() && !maxed && maxXpForLevel > 0F) {
				double xpNeeded = Math.max(0D, maxXpForLevel - xpIntoLevel);
				int highest = Math.min(bossXp.size(), RepoData.slayerHighestTier(slayerId));
				int idx = Math.max(0, highest - 1);
				float perKill = bossXp.get(idx).getAsFloat();
				if (perKill > 0F && xpNeeded > 0F) {
					long killsNeeded = (long) Math.ceil(xpNeeded / perKill);
					lines.add(PvTooltip.Line.of(
						"≈ " + FormatUtil.commas(killsNeeded) + " T" + (idx + 1) + " kills to next",
						PvDraw.COLOR_MUTED
					));
				}
			}
			return lines;
		}
	}

	private Leveling() {
	}

	/** Single-line hovers stay plain; {@link Progress#skillHoverText} output becomes a title plus label/value rows. */
	public static List<PvTooltip.Line> hoverTextLines(String hover) {
		if (hover == null || hover.isBlank()) {
			return List.of();
		}
		if (hover.indexOf('\n') < 0) {
			return List.of(PvTooltip.Line.of(hover, PvDraw.COLOR_TEXT));
		}
		String[] parts = hover.split("\n");
		List<PvTooltip.Line> lines = new ArrayList<>(parts.length);
		lines.add(PvTooltip.Line.of(parts[0], PvDraw.COLOR_ACCENT));
		for (int i = 1; i < parts.length; i++) {
			int tab = parts[i].indexOf('\t');
			if (tab < 0) {
				lines.add(PvTooltip.Line.of(parts[i], PvDraw.COLOR_TEXT));
			} else {
				lines.add(PvTooltip.Line.row(parts[i].substring(0, tab), PvDraw.COLOR_MUTED,
					parts[i].substring(tab + 1), PvDraw.COLOR_GOLD));
			}
		}
		return lines;
	}

	/**
	 * SkyHanni {@code SkillUtil.calculateSkillLevel}: after 60 the step starts at 7.6M and
	 * the slope doubles every tenth level. Matches the in-game Skills menu overflow level.
	 */
	private static final int HYPIXEL_OVERFLOW_START = 60;
	private static final long HYPIXEL_OVERFLOW_BASE = 7_000_000L;
	private static final long HYPIXEL_OVERFLOW_SLOPE = 600_000L;

	/** Non-cumulative table (skills / cata): each entry is XP required for that level step. */
	public static Progress getLevel(JsonArray table, float xp, int levelCap, boolean cumulative) {
		return getLevel(table, (double) xp, levelCap, cumulative);
	}

	public static Progress getLevel(JsonArray table, double xp, int levelCap, boolean cumulative) {
		double xpF = Math.max(0D, xp);
		if (table == null || table.isEmpty()) {
			return new Progress(0, 0, false, levelCap, xpF, 0, 0);
		}
		if (!cumulative && usesHypixelSkillOverflow(table)) {
			return hypixelSkillProgress(table, xp, levelCap);
		}
		double xpToCap = xpRequiredForLevel(table, levelCap, cumulative);
		float overflowStep = overflowStepXp(table, levelCap, cumulative);
		double remaining = Math.max(0D, xp);
		for (int level = 0; level < table.size(); level++) {
			double levelXp = table.get(level).getAsDouble();
			if (levelXp > remaining) {
				float maxXpForLevel;
				float resultLevel;
				float into;
				if (cumulative) {
					double previous = level > 0 ? table.get(level - 1).getAsDouble() : 0D;
					maxXpForLevel = (float) (levelXp - previous);
					into = (float) (remaining - previous);
					resultLevel = level + into / Math.max(1F, maxXpForLevel);
				} else {
					maxXpForLevel = (float) levelXp;
					into = (float) remaining;
					resultLevel = level + (float) (remaining / Math.max(1D, levelXp));
				}
				boolean maxed = resultLevel >= levelCap;
				if (maxed) {
					float step = overflowStep > 0F ? overflowStep : maxXpForLevel;
					return new Progress(
						levelCap, step, true, levelCap, xpF, step, Math.max(0D, xp - xpToCap)
					);
				}
				return new Progress(resultLevel, maxXpForLevel, false, levelCap, xpF, into, 0);
			}
			if (!cumulative) {
				remaining -= levelXp;
			}
		}
		int capped = Math.min(table.size(), levelCap);
		float step = overflowStep > 0F ? overflowStep : 0F;
		return new Progress(capped, step, true, levelCap, xpF, 0, Math.max(0D, xp - xpToCap));
	}

	/**
	 * Walk the 60-step skill table, then Hypixel's overflow slope (SkyHanni SkillUtil).
	 * {@code level} stays capped; {@code overflowLevel} is the uncapped in-game level.
	 */
	private static Progress hypixelSkillProgress(JsonArray table, double xp, int levelCap) {
		double remaining = Math.max(0D, xp);
		int level = 0;
		int tableLevels = Math.min(HYPIXEL_OVERFLOW_START, table.size());
		while (level < tableLevels) {
			double need = table.get(level).getAsDouble();
			if (remaining < need) {
				break;
			}
			remaining -= need;
			level++;
		}
		double xpForNext;
		if (level >= HYPIXEL_OVERFLOW_START) {
			long slope = HYPIXEL_OVERFLOW_SLOPE;
			xpForNext = HYPIXEL_OVERFLOW_BASE + slope;
			while (remaining >= xpForNext) {
				level++;
				remaining -= xpForNext;
				xpForNext += slope;
				if (level % 10 == 0) {
					slope *= 2L;
				}
			}
		} else if (level < table.size()) {
			xpForNext = table.get(level).getAsDouble();
		} else {
			xpForNext = 0D;
		}
		double xpToCap = 0D;
		int capSteps = Math.min(Math.max(1, levelCap), table.size());
		for (int i = 0; i < capSteps; i++) {
			xpToCap += table.get(i).getAsDouble();
		}
		double overflowXp = Math.max(0D, xp - xpToCap);
		boolean maxed = level >= levelCap || xp + 0.5D >= xpToCap;
		float overflowLevel = (float) (level + (xpForNext > 0D ? remaining / xpForNext : 0D));
		float cappedLevel = maxed ? levelCap : overflowLevel;
		double into;
		double step;
		if (maxed) {
			// Hypixel Skills menu: progress toward (cap+1) can exceed 100% while soft-capped.
			if (levelCap < table.size()) {
				step = table.get(levelCap).getAsDouble();
			} else {
				step = overflowStepXp(table, levelCap, false);
			}
			if (step <= 0D) {
				step = xpForNext;
			}
			into = overflowXp;
		} else {
			into = remaining;
			step = xpForNext;
		}
		if (maxed && step <= 0D) {
			step = overflowStepXp(table, levelCap, false);
			into = step;
		}
		return new Progress(
			cappedLevel, step, maxed, levelCap, Math.max(0D, xp), into, overflowXp, overflowLevel
		);
	}

	private static boolean usesHypixelSkillOverflow(JsonArray table) {
		if (table == null || table.size() < HYPIXEL_OVERFLOW_START) {
			return false;
		}
		try {
			return Math.abs(table.get(0).getAsDouble() - 50D) < 0.01D
				&& Math.abs(table.get(HYPIXEL_OVERFLOW_START - 1).getAsDouble() - 7_000_000D) < 0.01D;
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return false;
		}
	}

	private static float repeatedStepOverflowLevel(
		boolean maxed,
		float level,
		int maxLevel,
		double overflowXp,
		double maxXpForLevel
	) {
		if (!maxed) {
			return level;
		}
		if (maxXpForLevel <= 0D || overflowXp <= 0D) {
			return maxLevel;
		}
		return (float) (maxLevel + overflowXp / maxXpForLevel);
	}

	/** XP for one overflow level past the soft cap (repeats the last capped step). */
	private static float overflowStepXp(JsonArray table, int levelCap, boolean cumulative) {
		if (table == null || table.isEmpty() || levelCap <= 0) {
			return 0F;
		}
		int capped = Math.min(levelCap, table.size());
		if (cumulative) {
			float atCap = table.get(capped - 1).getAsFloat();
			float previous = capped > 1 ? table.get(capped - 2).getAsFloat() : 0F;
			return Math.max(0F, atCap - previous);
		}
		return Math.max(0F, table.get(capped - 1).getAsFloat());
	}

	public static float xpRequiredForLevel(JsonArray table, int levelCap, boolean cumulative) {
		if (table == null || table.isEmpty() || levelCap <= 0) {
			return 0F;
		}
		int capped = Math.min(levelCap, table.size());
		if (cumulative) {
			return table.get(capped - 1).getAsFloat();
		}
		double sum = 0D;
		for (int i = 0; i < capped; i++) {
			sum += table.get(i).getAsDouble();
		}
		return (float) sum;
	}

	public static float readSkillXp(JsonObject member, String skill) {
		return (float) readSkillXpDouble(member, skill);
	}

	public static double readSkillXpDouble(JsonObject member, String skill) {
		String apiSkill = skill.equals("social") ? "SOCIAL" : skill.toUpperCase(Locale.ROOT);
		JsonObject playerData = obj(member == null ? null : member.get("player_data"));
		JsonObject experience = playerData == null ? null : obj(playerData.get("experience"));
		if (experience != null) {
			String[] keys = {
				"SKILL_" + apiSkill,
				"SKILL_" + skill.toUpperCase(Locale.ROOT),
				skill.toUpperCase(Locale.ROOT)
			};
			for (String key : keys) {
				Double value = numDouble(experience.get(key));
				if (value != null && value > 0D) {
					return value;
				}
			}
			for (var entry : experience.entrySet()) {
				String key = entry.getKey();
				if (key == null) {
					continue;
				}
				String upper = key.toUpperCase(Locale.ROOT);
				if (upper.contains("EXTRA_LEVEL_CAP")) {
					continue;
				}
				if (upper.contains(apiSkill)) {
					Double value = numDouble(entry.getValue());
					if (value != null && value > 0D) {
						return value;
					}
				}
			}
		}
		Double legacy = numDouble(member == null ? null
			: member.get("experience_skill_" + (skill.equals("social") ? "social2" : skill)));
		return legacy == null ? 0D : legacy;
	}

	public static float readSlayerXp(JsonObject member, String slayer) {
		JsonObject boss = slayerBossObject(member, slayer);
		Float xp = boss == null ? null : num(boss.get("xp"));
		return xp == null ? 0F : xp;
	}

	public static JsonObject slayerBossObject(JsonObject member, String slayer) {
		if (member == null || slayer == null) {
			return null;
		}
		JsonObject slayerRoot = obj(member.get("slayer"));
		JsonObject bosses = slayerRoot == null ? null : obj(slayerRoot.get("slayer_bosses"));
		if (bosses == null) {
			bosses = obj(member.get("slayer_bosses"));
		}
		return bosses == null ? null : obj(bosses.get(slayer));
	}

	/**
	 * Tier kill counts for a slayer boss. Tries {@code boss_kills_tier_0..4} first,
	 * then {@code boss_kills_tier_1..5}. Returns a length-5 array (T1…T5).
	 */
	public static int[] readSlayerBossKills(JsonObject member, String slayer) {
		int[] kills = new int[5];
		JsonObject boss = slayerBossObject(member, slayer);
		if (boss == null) {
			return kills;
		}
		boolean foundZeroBased = false;
		for (int i = 0; i < 5; i++) {
			Float value = num(boss.get("boss_kills_tier_" + i));
			if (value != null) {
				kills[i] = Math.max(0, Math.round(value));
				foundZeroBased = true;
			}
		}
		if (foundZeroBased) {
			return kills;
		}
		for (int i = 0; i < 5; i++) {
			Float value = num(boss.get("boss_kills_tier_" + (i + 1)));
			if (value != null) {
				kills[i] = Math.max(0, Math.round(value));
			}
		}
		return kills;
	}

	public static float readClassXp(JsonObject member, String className) {
		JsonObject dungeons = obj(member.get("dungeons"));
		JsonObject classes = dungeons == null ? null : obj(dungeons.get("player_classes"));
		JsonObject clazz = classes == null ? null : obj(classes.get(className));
		if (clazz == null) {
			return 0F;
		}
		Float xp = num(clazz.get("experience"));
		if (xp == null) {
			xp = num(clazz.get("xp"));
		}
		return xp == null ? 0F : xp;
	}

	public static float readCatacombsXp(JsonObject member) {
		JsonObject dungeons = obj(member.get("dungeons"));
		JsonObject types = dungeons == null ? null : obj(dungeons.get("dungeon_types"));
		JsonObject type = types == null ? null : obj(types.get("catacombs"));
		Float xp = type == null ? null : num(type.get("experience"));
		return xp == null ? 0F : xp;
	}

	/** Whole skill level as the skill pages show it, using the profile's own cap. */
	public static int skillLevel(JsonObject member, String skill) {
		double xp = readSkillXpDouble(member, skill);
		return (int) Math.floor(getLevel(skillTable(skill), xp, skillCap(skill, member), false).level());
	}

	public static int skillCap(String skill, JsonObject member) {
		int base = RepoData.skillCap(skill);
		if (member == null) {
			return "runecrafting".equals(skill) || "social".equals(skill) ? Math.min(base, 25) : base;
		}
		if ("farming".equals(skill)) {
			JsonObject jacobs = obj(member.get("jacobs_contest"));
			if (jacobs == null) {
				jacobs = obj(member.get("jacob2"));
			}
			JsonObject perks = jacobs == null ? null : obj(jacobs.get("perks"));
			Float extra = perks == null ? null : num(perks.get("farming_level_cap"));
			if (extra != null) {
				base += Math.round(extra);
			}
		}
		if ("foraging".equals(skill)) {
			base += foragingExtraLevelCap(member);
		}
		if ("taming".equals(skill)) {
			// George pet sacrifices raise the taming cap by 1 each (50 → 60).
			JsonObject petsData = obj(member.get("pets_data"));
			JsonObject petCare = petsData == null ? null : obj(petsData.get("pet_care"));
			if (petCare != null && petCare.get("pet_types_sacrificed") != null
				&& petCare.get("pet_types_sacrificed").isJsonArray()) {
				int sacrificed = petCare.getAsJsonArray("pet_types_sacrificed").size();
				base = Math.min(60, base + Math.max(0, sacrificed));
			}
		}
		if ("runecrafting".equals(skill) || "social".equals(skill)) {
			return Math.min(base, 25);
		}
		return base;
	}

	/**
	 * Extra Foraging levels past the base soft cap (50 → up to 57).
	 *
	 * <p>Uses {@code SKILL_FORAGING_extra_level_cap} when present, and also recomputes from
	 * Fig/Mangrove/Helix IX collections + Heart of the Forest (same approach as Skyblocker),
	 * taking the higher of the two so a stale API field cannot under-report the in-game cap.
	 */
	public static int foragingExtraLevelCap(JsonObject member) {
		int fromApi = foragingExtraLevelCapFromApi(member);
		int fromProgress = foragingExtraLevelCapFromProgress(member);
		return Math.min(7, Math.max(fromApi, fromProgress));
	}

	private static int foragingExtraLevelCapFromApi(JsonObject member) {
		JsonObject playerData = obj(member == null ? null : member.get("player_data"));
		JsonObject experience = playerData == null ? null : obj(playerData.get("experience"));
		if (experience == null) {
			return 0;
		}
		Float extra = num(experience.get("SKILL_FORAGING_extra_level_cap"));
		if (extra == null || extra.isNaN() || extra.isInfinite()) {
			return 0;
		}
		return Math.max(0, Math.round(extra));
	}

	/**
	 * Skyblocker-style recompute: +1 per Fig/Mangrove/Helix collection IX, +2 Agatha
	 * once any foraging-cap progress exists, +2 when HOTF level is at least 4 (Miria).
	 */
	private static int foragingExtraLevelCapFromProgress(JsonObject member) {
		int collections = 0;
		if (collectionTierAtLeast(member, "FIG_LOG", 9)) {
			collections++;
		}
		if (collectionTierAtLeast(member, "MANGROVE_LOG", 9)) {
			collections++;
		}
		if (collectionTierAtLeast(member, "HELIX_LOG", 9)) {
			collections++;
		}
		int hotf = hotfLevel(member);
		int api = foragingExtraLevelCapFromApi(member);
		int extra = collections;
		// Agatha shop pair. Skyblocker always adds these once foraging-cap progress exists.
		if (collections > 0 || hotf > 0 || api > 0) {
			extra += 2;
		}
		if (hotf >= 4) {
			extra += 2;
		}
		return extra;
	}

	private static boolean collectionTierAtLeast(JsonObject member, String collectionId, int minTier) {
		if (member == null || collectionId == null || collectionId.isBlank() || minTier <= 0) {
			return false;
		}
		int best = unlockedCollectionTier(member, collectionId);
		if (best >= minTier) {
			return true;
		}
		// Fallback: derive tier from collection amounts when unlocked_coll_tiers is incomplete.
		JsonObject collection = obj(member.get("collection"));
		if (collection == null) {
			return false;
		}
		Double amount = numDouble(collection.get(collectionId));
		if (amount == null) {
			amount = numDouble(collection.get(collectionId.toUpperCase(Locale.ROOT)));
		}
		if (amount == null || amount <= 0D) {
			return false;
		}
		for (HypixelCollectionsCache.Category category : HypixelCollectionsCache.categories()) {
			for (HypixelCollectionsCache.Item item : category.items()) {
				if (item == null || item.id() == null) {
					continue;
				}
				if (!item.id().equalsIgnoreCase(collectionId)) {
					continue;
				}
				return item.tierFor(Math.round(amount)) >= minTier;
			}
		}
		return false;
	}

	private static int unlockedCollectionTier(JsonObject member, String collectionId) {
		JsonObject playerData = obj(member.get("player_data"));
		JsonArray array = null;
		if (playerData != null && playerData.get("unlocked_coll_tiers") != null
			&& playerData.get("unlocked_coll_tiers").isJsonArray()) {
			array = playerData.getAsJsonArray("unlocked_coll_tiers");
		} else if (member.get("unlocked_coll_tiers") != null && member.get("unlocked_coll_tiers").isJsonArray()) {
			array = member.getAsJsonArray("unlocked_coll_tiers");
		}
		if (array == null) {
			return 0;
		}
		String prefix = collectionId.trim().toUpperCase(Locale.ROOT) + "_";
		int best = 0;
		for (JsonElement el : array) {
			if (el == null || !el.isJsonPrimitive()) {
				continue;
			}
			String key;
			try {
				key = el.getAsString();
			} catch (RuntimeException exception) {
				if (!SoftDataFailure.isSoft(exception)) {
					throw exception;
				}
				continue;
			}
			if (key == null) {
				continue;
			}
			String upper = key.trim().toUpperCase(Locale.ROOT);
			if (!upper.startsWith(prefix)) {
				continue;
			}
			String tierPart = upper.substring(prefix.length());
			try {
				best = Math.max(best, Integer.parseInt(tierPart));
			} catch (NumberFormatException ignored) {
				// ignore malformed tier suffixes
			}
		}
		return best;
	}

	private static int hotfLevel(JsonObject member) {
		JsonObject skillTree = obj(member == null ? null : member.get("skill_tree"));
		JsonObject experience = skillTree == null ? null : obj(skillTree.get("experience"));
		Double xp = experience == null ? null : numDouble(experience.get("foraging"));
		if (xp == null || xp <= 0D) {
			return 0;
		}
		JsonObject leveling = RepoData.leveling();
		JsonArray table = leveling != null && leveling.has("HOTF") && leveling.get("HOTF").isJsonArray()
			? leveling.getAsJsonArray("HOTF")
			: null;
		if (table == null || table.isEmpty()) {
			return 0;
		}
		int cap = Math.max(1, RepoData.skillCap("HOTF"));
		Progress progress = getLevel(table, xp, cap, false);
		return (int) Math.floor(progress.level());
	}

	public static JsonArray skillTable(String skill) {
		JsonObject leveling = RepoData.leveling();
		if (leveling == null) {
			return null;
		}
		if ("runecrafting".equals(skill) && leveling.get("runecrafting_xp") instanceof JsonArray) {
			return leveling.getAsJsonArray("runecrafting_xp");
		}
		if ("social".equals(skill) && leveling.get("social") instanceof JsonArray) {
			return leveling.getAsJsonArray("social");
		}
		return RepoData.levelingXp();
	}

	public static Float num(JsonElement element) {
		Double value = numDouble(element);
		return value == null ? null : value.floatValue();
	}

	public static Double numDouble(JsonElement element) {
		if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
			return null;
		}
		try {
			return element.getAsDouble();
		} catch (RuntimeException exception) {
			if (!SoftDataFailure.isSoft(exception)) {
				throw exception;
			}
			return null;
		}
	}

	/**
	 * Large counts (collections, crop milestones, etc.). Uses {@link Math#round(double)} so values
	 * above {@link Integer#MAX_VALUE} are not clipped the way {@link Math#round(float)} is.
	 */
	public static long longAmount(JsonElement element) {
		Double value = numDouble(element);
		if (value == null || value.isNaN() || value.isInfinite()) {
			return 0L;
		}
		return Math.max(0L, Math.round(value));
	}

	public static JsonObject obj(JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}
}
