/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api;

import com.google.gson.JsonObject;
import fishmod.pv.BetterPV;
import fishmod.pv.data.DungeonChestHistory;
import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.Leveling;
import fishmod.pv.data.SoftDataFailure;
import fishmod.pv.dungeons.CataXpMath;
import fishmod.pv.dungeons.DungeonModifierScanner;
import fishmod.pv.dungeons.EssenceShopData;
import fishmod.pv.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Parses catacombs / dungeon snapshot fields from member JSON. */
final class ProfileDungeonParser {
	private static final String[] DUNGEON_CLASSES = {
		"healer", "mage", "berserk", "archer", "tank"
	};

	private ProfileDungeonParser() {
	}

	static DungeonSnapshot parseDungeons(
		JsonObject member,
		JsonObject museumMember,
		JsonObject electionRoot,
		Map<String, List<InventoryDecoder.Stack>> inventoryCategories
	) {
		float cataXp = Leveling.readCatacombsXp(member);
		Leveling.Progress cata = CataXpMath.progress(cataXp);

		JsonObject dungeons = Leveling.obj(member.get("dungeons"));
		String selected = "";
		long secrets = 0L;
		if (dungeons != null) {
			if (dungeons.has("selected_dungeon_class") && dungeons.get("selected_dungeon_class").isJsonPrimitive()) {
				selected = dungeons.get("selected_dungeon_class").getAsString();
			}
			Float secretsF = Leveling.num(dungeons.get("secrets"));
			if (secretsF != null) {
				secrets = Math.round(secretsF);
			}
		}

		List<DungeonSnapshot.ClassEntry> classes = new ArrayList<>();
		float classSum = 0F;
		int softMaxedCount = 0;
		for (String classId : DUNGEON_CLASSES) {
			float classXp = Leveling.readClassXp(member, classId);
			Leveling.Progress progress = CataXpMath.progress(classXp);
			int level = progress.displayLevel();
			boolean selectedClass = classId.equalsIgnoreCase(selected);
			if (level >= CataXpMath.SOFT_CAP) {
				softMaxedCount++;
			}
			classSum += progress.level();
			classes.add(new DungeonSnapshot.ClassEntry(
				classId,
				ProfileParseSupport.title(classId),
				level,
				classXp,
				progress.fill(),
				progress.maxed(),
				selectedClass,
				progress.skillHover(ProfileParseSupport.title(classId))
			));
		}

		float classAverage = classSum / DUNGEON_CLASSES.length;
		boolean classAvgMaxed = softMaxedCount == DUNGEON_CLASSES.length;
		float classAvgProgress = classAvgMaxed
			? 1F
			: Math.max(0F, Math.min(1F, classAverage / CataXpMath.SOFT_CAP));
		String classAvgHover = classAvgMaxed
			? "Class Average " + FormatUtil.oneDecimal(classAverage) + " - MAX"
			: "Class Average " + FormatUtil.oneDecimal(classAverage) + " / " + CataXpMath.SOFT_CAP;

		DungeonSnapshot.ModeStats normal = parseMode(member, "catacombs", false);
		DungeonSnapshot.ModeStats master = parseMode(member, "master_catacombs", true);
		long allRuns = Math.max(1L, normal.totalRuns() + master.totalRuns());
		double secretsPerRun = secrets / (double) allRuns;

		DungeonModifierScanner.Mods mods = DungeonModifierScanner.Mods.none();
		try {
			Map<String, List<InventoryDecoder.Stack>> categories = inventoryCategories != null
				? inventoryCategories
				: InventoryDecoder.parseCategories(member, museumMember);
			mods = DungeonModifierScanner.scan(categories);
		} catch (RuntimeException ignored) {
			if (!SoftDataFailure.isSoft(ignored)) {
				throw ignored;
			}
			BetterPV.LOGGER.debug("Dungeon modifier scan failed", ignored);
		}

		double mayorFactor = CataXpMath.mayorXpFactor(electionRoot);
		String mayorName = CataXpMath.mayorName(electionRoot);

		java.util.Map<String, Double> essenceBonuses = DungeonModifierScanner.readEssenceClassBonuses(member);
		double graduateBonus = DungeonModifierScanner.readCatacombsGraduateBonus(member);

		JsonObject dailyRuns = Leveling.obj(dungeons == null ? null : dungeons.get("daily_runs"));
		int dailyCount = 0;
		if (dailyRuns != null) {
			Float n = Leveling.num(dailyRuns.get("completed_runs_count"));
			if (n != null) {
				dailyCount = Math.max(0, Math.round(n));
			}
		}
		JsonObject journal = Leveling.obj(dungeons == null ? null : dungeons.get("dungeon_journal"));
		int journals = 0;
		if (journal != null && journal.has("unlocked_journals") && journal.get("unlocked_journals").isJsonArray()) {
			journals = journal.getAsJsonArray("unlocked_journals").size();
		}
		DungeonSnapshot.HubRace race = DungeonSnapshot.parseHubRace(
			dungeons == null ? null : dungeons.get("dungeon_hub_race_settings")
		);

		return new DungeonSnapshot(
			cata.displayLevel(),
			cataXp,
			cata.fill(),
			cata.maxed(),
			cata.skillHover("Catacombs"),
			secrets,
			secretsPerRun,
			classAverage,
			classAvgProgress,
			classAvgMaxed,
			classAvgHover,
			classes,
			normal,
			master,
			mods.expertRing(),
			mods.hecatombLevel(),
			mods.scarfBonus(),
			graduateBonus,
			essenceBonuses,
			mayorFactor,
			mayorName,
			EssenceShopData.wither(member),
			EssenceShopData.undead(member),
			EssenceShopData.ice(member),
			EssenceShopData.spider(member),
			EssenceShopData.dragon(member),
			dailyCount,
			journals,
			race,
			DungeonChestHistory.from(member)
		);
	}

	private static DungeonSnapshot.ModeStats parseMode(JsonObject member, String typeKey, boolean master) {
		JsonObject dungeons = Leveling.obj(member.get("dungeons"));
		JsonObject types = dungeons == null ? null : Leveling.obj(dungeons.get("dungeon_types"));
		JsonObject type = types == null ? null : Leveling.obj(types.get(typeKey));
		JsonObject completions = type == null ? null : Leveling.obj(type.get("tier_completions"));
		JsonObject milestones = type == null ? null : Leveling.obj(type.get("milestone_completions"));
		JsonObject mobsKilled = type == null ? null : Leveling.obj(type.get("mobs_killed"));
		JsonObject bestScore = type == null ? null : Leveling.obj(type.get("best_score"));
		JsonObject mostMobs = type == null ? null : Leveling.obj(type.get("most_mobs_killed"));
		JsonObject fastest = type == null ? null : Leveling.obj(type.get("fastest_time"));
		JsonObject fastestS = type == null ? null : Leveling.obj(type.get("fastest_time_s"));
		JsonObject sPlus = type == null ? null : Leveling.obj(type.get("fastest_time_s_plus"));
		JsonObject mostHealing = type == null ? null : Leveling.obj(type.get("most_healing"));

		List<DungeonSnapshot.FloorEntry> floors = new ArrayList<>();
		long total = 0L;
		int start = master ? 1 : 0;
		for (int floor = start; floor <= 7; floor++) {
			String key = String.valueOf(floor);
			long runs = ProfileParseSupport.readLong(completions, key);
			total += runs;
			String label = master ? "M" + floor : (floor == 0 ? "E" : "F" + floor);
			DamagePeak damage = bestDamage(type, key);
			floors.add(new DungeonSnapshot.FloorEntry(
				key,
				label,
				runs,
				ProfileParseSupport.readLong(milestones, key),
				ProfileParseSupport.readLong(mobsKilled, key),
				ProfileParseSupport.readLong(bestScore, key),
				ProfileParseSupport.readLong(mostMobs, key),
				ProfileParseSupport.readLong(fastest, key),
				ProfileParseSupport.readLong(fastestS, key),
				ProfileParseSupport.readLong(sPlus, key),
				ProfileParseSupport.readDouble(mostHealing, key),
				damage.classId(),
				damage.value()
			));
		}
		return new DungeonSnapshot.ModeStats(total, floors);
	}

	private record DamagePeak(String classId, double value) {
		static DamagePeak none() {
			return new DamagePeak(null, 0D);
		}
	}

	private static DamagePeak bestDamage(JsonObject type, String floorKey) {
		if (type == null) {
			return DamagePeak.none();
		}
		String bestClass = null;
		double best = 0D;
		for (String classId : new String[] {"mage", "berserk", "archer", "healer", "tank"}) {
			JsonObject map = Leveling.obj(type.get("most_damage_" + classId));
			double value = ProfileParseSupport.readDouble(map, floorKey);
			if (value > best) {
				best = value;
				bestClass = classId;
			}
		}
		return bestClass == null ? DamagePeak.none() : new DamagePeak(bestClass, best);
	}
}
