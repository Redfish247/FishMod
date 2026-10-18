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
import com.google.gson.JsonParser;
import fishmod.pv.BetterPV;
import fishmod.pv.dungeons.CataXpMath;
import fishmod.pv.networth.InventoryDecoder;
import fishmod.pv.networth.PetWorth;
import java.io.Reader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel does not expose final stats - values are reconstructed from NEU constants,
 * skills/slayers, SkyBlock level, bestiary, essence permanents, consumables, Maxwell power, tuning,
 * active pet, and item lore on armor / equipment / accessories.
 */
public final class PlayerStatsCalculator {
	private static final String STAT_END = ": ((?:\\+|-)([0-9]+(?:\\.[0-9]+)?))%?";
	private static final List<StatDef> DISPLAY = List.of(
		new StatDef("health", "HP", Pattern.compile("^Health" + STAT_END)),
		new StatDef("defense", "Def", Pattern.compile("^(?:Defence|Defense)" + STAT_END)),
		new StatDef("strength", "STR", Pattern.compile("^Strength" + STAT_END)),
		new StatDef("speed", "SPD", Pattern.compile("^Speed" + STAT_END)),
		new StatDef("critical_chance", "CC", Pattern.compile("^Crit Chance" + STAT_END)),
		new StatDef("critical_damage", "CD", Pattern.compile("^Crit Damage" + STAT_END)),
		new StatDef("attack_speed", "AS", Pattern.compile("^(?:Bonus Attack Speed|Attack Speed)" + STAT_END)),
		new StatDef("intelligence", "INT", Pattern.compile("^Intelligence" + STAT_END)),
		new StatDef("ferocity", "Fero", Pattern.compile("^Ferocity" + STAT_END)),
		new StatDef("ability_damage", "AD", Pattern.compile("^Ability Damage" + STAT_END)),
		new StatDef("magic_find", "MF", Pattern.compile("^Magic Find" + STAT_END)),
		new StatDef("pet_luck", "PL", Pattern.compile("^Pet Luck" + STAT_END)),
		new StatDef("true_defense", "TD", Pattern.compile("^(?:True Defence|True Defense)" + STAT_END)),
		new StatDef("health_regen", "RG", Pattern.compile("^Health Regen" + STAT_END)),
		new StatDef("vitality", "VIT", Pattern.compile("^Vitality" + STAT_END)),
		new StatDef("mending", "MD", Pattern.compile("^Mending" + STAT_END)),
		new StatDef("swing_range", "Swing", Pattern.compile("^Swing Range" + STAT_END)),
		new StatDef("mining_fortune", "MiF", Pattern.compile("^Mining Fortune" + STAT_END)),
		new StatDef("farming_fortune", "FaF", Pattern.compile("^Farming Fortune" + STAT_END)),
		new StatDef("foraging_fortune", "FoF", Pattern.compile("^Foraging Fortune" + STAT_END)),
		new StatDef("timber", "Timb", Pattern.compile("^Timber" + STAT_END)),
		new StatDef("mining_speed", "MS", Pattern.compile("^Mining Speed" + STAT_END)),
		new StatDef("mining_spread", "Spr", Pattern.compile("^Mining Spread" + STAT_END)),
		new StatDef("pristine", "Pris", Pattern.compile("^Pristine" + STAT_END)),
		new StatDef("sea_creature_chance", "SCC", Pattern.compile("^Sea Creature Chance" + STAT_END)),
		new StatDef("fishing_speed", "FS", Pattern.compile("^Fishing Speed" + STAT_END))
	);

	/** Stats Hypixel always starts at these values; NEU misc.base_stats omits healing stats. */
	private static final Map<String, Double> FALLBACK_BASE = Map.of(
		"vitality", 100.0,
		"health_regen", 100.0,
		"mending", 100.0,
		"swing_range", 3.0
	);

	/**
	 * Unconditional Hunting attribute → flat player stats (per level).
	 * {@code attributes.stacks.mending} is the Vitality shard (API id ≠ combat Mending).
	 */
	private static final List<AttributeFlat> ATTRIBUTE_FLAT = List.of(
		new AttributeFlat("life_regeneration", "health_regen", 1.25),
		new AttributeFlat("mending", "vitality", 2.0),
		new AttributeFlat("magic_find", "magic_find", 0.5),
		new AttributeFlat("speed", "speed", 1.0),
		new AttributeFlat("attack_speed", "attack_speed", 1.0),
		new AttributeFlat("veil", "true_defense", 1.0),
		new AttributeFlat("fishing_speed", "fishing_speed", 3.0),
		new AttributeFlat("rotten_pickaxe", "mining_speed", 3.0),
		new AttributeFlat("earth_elemental", "health", 2.0),
		new AttributeFlat("forest_elemental", "health", 2.0),
		new AttributeFlat("nature_elemental", "health", 2.0),
		new AttributeFlat("shadow_elemental", "health", 2.0),
		new AttributeFlat("wood_elemental", "health", 2.0),
		new AttributeFlat("light_elemental", "strength", 1.0),
		new AttributeFlat("lightning_elemental", "strength", 1.0),
		new AttributeFlat("stone_elemental", "strength", 1.0),
		new AttributeFlat("storm_elemental", "strength", 1.0),
		new AttributeFlat("wind_elemental", "strength", 1.0),
		new AttributeFlat("fog_elemental", "intelligence", 1.0),
		new AttributeFlat("frost_elemental", "intelligence", 1.0),
		new AttributeFlat("snow_elemental", "intelligence", 1.0),
		new AttributeFlat("torrent_elemental", "intelligence", 1.0),
		new AttributeFlat("water_elemental", "intelligence", 1.0),
		new AttributeFlat("ultimate_dna", "mining_fortune", 1.0),
		new AttributeFlat("ultimate_dna", "farming_fortune", 1.0),
		new AttributeFlat("ultimate_dna", "foraging_fortune", 1.0),
		new AttributeFlat("rare_bird", "pet_luck", 1.0)
	);

	/** Wither Essence Forbidden perks - cumulative total at each level (wiki). */
	private static final Map<String, int[]> FORBIDDEN = Map.of(
		"permanent_health", new int[]{0, 2, 4, 6, 8, 10},
		"permanent_defense", new int[]{0, 1, 2, 3, 4, 5},
		"permanent_speed", new int[]{0, 1, 2},
		"permanent_intelligence", new int[]{0, 1, 2, 3, 4, 5},
		"permanent_strength", new int[]{0, 1, 2, 3, 4, 5}
	);

	private static final Map<String, String> FORBIDDEN_STAT = Map.of(
		"permanent_health", "health",
		"permanent_defense", "defense",
		"permanent_speed", "speed",
		"permanent_intelligence", "intelligence",
		"permanent_strength", "strength"
	);

	private static volatile JsonObject miscCache;
	private static volatile JsonObject bonusesCache;
	private static volatile JsonObject petnumsCache;
	private static volatile JsonObject attributeShardsCache;

	private PlayerStatsCalculator() {
	}

	// Worn gear only: parseCategories also merges every saved equipment loadout into "equipment".
	public static PlayerStatsSnapshot fromMember(JsonObject member) {
		return fromMember(member, member == null ? Map.of() : InventoryDecoder.parseHomeGear(member));
	}

	public static PlayerStatsSnapshot fromMember(
		JsonObject member,
		Map<String, List<InventoryDecoder.Stack>> categories
	) {
		if (member == null) {
			return PlayerStatsSnapshot.empty();
		}
		Map<String, Double> totals = new LinkedHashMap<>();
		boolean anySource = false;

		JsonObject misc = misc();
		if (misc != null && misc.has("base_stats") && misc.get("base_stats").isJsonObject()) {
			JsonObject base = misc.getAsJsonObject("base_stats");
			for (StatDef def : DISPLAY) {
				Double baseValue = readBaseStat(base, def.id);
				if (baseValue != null) {
					add(totals, def.id, baseValue);
					anySource = true;
				}
			}
		}
		for (var e : FALLBACK_BASE.entrySet()) {
			if (!totals.containsKey(e.getKey())) {
				add(totals, e.getKey(), e.getValue());
				anySource = true;
			}
		}

		anySource |= addBonusStats(totals, member);
		anySource |= addZombieSlayerRegen(totals, member);
		anySource |= addHuntingAttributes(totals, member);
		anySource |= addSkyBlockLevel(totals, member);
		anySource |= addBestiary(totals, member);
		anySource |= addCatacombs(totals, member);
		anySource |= addEssencePermanents(totals, member);
		anySource |= addConsumables(totals, member);
		anySource |= addHotm(totals, member);
		anySource |= addMaxwellAndTuning(totals, member);
		anySource |= addPetScore(totals, member);
		anySource |= addActivePet(totals, member);

		if (categories != null) {
			anySource |= addItemLore(totals, categories.get("armor"), false);
			anySource |= addItemLore(totals, categories.get("equipment"), false);
			anySource |= addItemLore(totals, categories.get("accessories"), true);
		}

		anySource |= applyDragonGreed(totals, member);

		List<PlayerStatsSnapshot.Entry> entries = new ArrayList<>(DISPLAY.size());
		for (StatDef def : DISPLAY) {
			OptionalDouble value = OptionalDouble.empty();
			if (anySource && totals.containsKey(def.id)) {
				value = OptionalDouble.of(totals.get(def.id));
			}
			entries.add(new PlayerStatsSnapshot.Entry(def.id, def.label, value));
		}
		return new PlayerStatsSnapshot(entries);
	}

	/** Skill + slayer milestone bonuses from NEU {@code bonuses.json}. */
	private static boolean addBonusStats(Map<String, Double> totals, JsonObject member) {
		JsonObject bonuses = bonuses();
		if (bonuses == null || !bonuses.has("bonus_stats") || !bonuses.get("bonus_stats").isJsonObject()) {
			return false;
		}
		JsonObject bonusStats = bonuses.getAsJsonObject("bonus_stats");
		boolean added = false;

		for (var skillEntry : skillLevels(member).entrySet()) {
			added |= applyMilestoneBonuses(totals, bonusStats, "skill_" + skillEntry.getKey(), skillEntry.getValue());
		}
		for (String slayer : List.of("zombie", "spider", "wolf", "enderman", "blaze", "vampire")) {
			float xp = Leveling.readSlayerXp(member, slayer);
			JsonArray table = RepoData.slayerXp(slayer);
			int cap = table == null || table.isEmpty() ? 9 : table.size();
			Leveling.Progress progress = Leveling.getLevel(table, xp, cap, true);
			int level = progress == null ? 0 : (int) Math.floor(progress.level());
			if (level > 0) {
				added |= applyMilestoneBonuses(totals, bonusStats, "slayer_" + slayer, level);
			}
		}
		return added;
	}

	/**
	 * NEU milestone tables only list breakpoints; the last defined bonus sticks and is applied
	 * once per level up to the player's level (combat CC is +0.5 each level, etc.).
	 */
	private static boolean applyMilestoneBonuses(
		Map<String, Double> totals,
		JsonObject bonusStats,
		String key,
		int level
	) {
		if (level <= 0 || !bonusStats.has(key) || !bonusStats.get(key).isJsonObject()) {
			return false;
		}
		JsonObject perLevel = bonusStats.getAsJsonObject(key);
		Map<String, Double> current = Map.of();
		boolean added = false;
		for (int lvl = 1; lvl <= level; lvl++) {
			String k = String.valueOf(lvl);
			if (perLevel.has(k) && perLevel.get(k).isJsonObject()) {
				current = readStatMap(perLevel.getAsJsonObject(k));
			}
			for (var e : current.entrySet()) {
				if (wanted(e.getKey())) {
					add(totals, e.getKey(), e.getValue());
					added = true;
				}
			}
		}
		return added;
	}

	/** Zombie Slayer VIII+ permanently grants +50 Health Regen (not in NEU bonuses.json). */
	private static boolean addZombieSlayerRegen(Map<String, Double> totals, JsonObject member) {
		float xp = Leveling.readSlayerXp(member, "zombie");
		JsonArray table = RepoData.slayerXp("zombie");
		int cap = table == null || table.isEmpty() ? 9 : table.size();
		Leveling.Progress progress = Leveling.getLevel(table, xp, cap, true);
		int level = progress == null ? 0 : (int) Math.floor(progress.level());
		if (level < 8) {
			return false;
		}
		add(totals, "health_regen", 50.0);
		return true;
	}

	/**
	 * Hunting Box attribute levels from {@code attributes.stacks} + NEU shard costs.
	 * Only unconditional flat stat grants (no island/mob-conditional bonuses).
	 */
	private static boolean addHuntingAttributes(Map<String, Double> totals, JsonObject member) {
		JsonObject attributes = obj(member.get("attributes"));
		JsonObject stacks = attributes == null ? null : obj(attributes.get("stacks"));
		if (stacks == null || stacks.entrySet().isEmpty()) {
			return false;
		}
		JsonObject shardsFile = attributeShards();
		if (shardsFile == null) {
			return false;
		}
		Map<String, int[]> costsByRarity = readAttributeLevelling(shardsFile);
		Map<String, String> rarityById = readAttributeRarities(shardsFile);
		boolean added = false;
		for (AttributeFlat flat : ATTRIBUTE_FLAT) {
			int shards = intOr(stacks, flat.stackId());
			if (shards <= 0) {
				continue;
			}
			String rarity = rarityById.getOrDefault(flat.stackId(), "COMMON");
			int level = attributeLevel(costsByRarity.get(rarity), shards);
			if (level <= 0) {
				continue;
			}
			add(totals, flat.statId(), flat.perLevel() * level);
			added = true;
		}
		return added;
	}

	/** Attribute levels for the given stack ids; empty when the NEU shard table isn't available. */
	static Map<String, Integer> attributeLevels(JsonObject member, List<String> stackIds) {
		JsonObject attributes = member == null ? null : obj(member.get("attributes"));
		JsonObject stacks = attributes == null ? null : obj(attributes.get("stacks"));
		JsonObject shardsFile = stacks == null ? null : attributeShards();
		if (shardsFile == null) {
			return Map.of();
		}
		Map<String, int[]> costsByRarity = readAttributeLevelling(shardsFile);
		Map<String, String> rarityById = readAttributeRarities(shardsFile);
		Map<String, Integer> out = new HashMap<>();
		for (String id : stackIds) {
			int level = attributeLevel(costsByRarity.get(rarityById.getOrDefault(id, "COMMON")), intOr(stacks, id));
			if (level > 0) {
				out.put(id, level);
			}
		}
		return out;
	}

	private static Map<String, int[]> readAttributeLevelling(JsonObject shardsFile) {
		JsonObject levelling = obj(shardsFile.get("attribute_levelling"));
		if (levelling == null) {
			return Map.of();
		}
		Map<String, int[]> out = new HashMap<>();
		for (var entry : levelling.entrySet()) {
			if (!entry.getValue().isJsonArray()) {
				continue;
			}
			JsonArray arr = entry.getValue().getAsJsonArray();
			int[] costs = new int[arr.size()];
			for (int i = 0; i < arr.size(); i++) {
				try {
					costs[i] = arr.get(i).getAsInt();
				} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
					costs[i] = 0;
				}
			}
			out.put(entry.getKey().toUpperCase(Locale.ROOT), costs);
		}
		return out;
	}

	private static Map<String, String> readAttributeRarities(JsonObject shardsFile) {
		if (!shardsFile.has("attributes") || !shardsFile.get("attributes").isJsonArray()) {
			return Map.of();
		}
		Map<String, String> out = new HashMap<>();
		for (JsonElement el : shardsFile.getAsJsonArray("attributes")) {
			JsonObject row = obj(el);
			if (row == null) {
				continue;
			}
			String internal = row.has("internalName") && row.get("internalName").isJsonPrimitive()
				? row.get("internalName").getAsString()
				: "";
			String rarity = row.has("rarity") && row.get("rarity").isJsonPrimitive()
				? row.get("rarity").getAsString().toUpperCase(Locale.ROOT)
				: "COMMON";
			String id = attributeStackId(internal);
			if (!id.isEmpty()) {
				out.put(id, rarity);
			}
		}
		return out;
	}

	private static String attributeStackId(String internalName) {
		if (internalName == null || internalName.isBlank()) {
			return "";
		}
		String id = internalName;
		if (id.startsWith("ATTRIBUTE_SHARD_")) {
			id = id.substring("ATTRIBUTE_SHARD_".length());
		}
		int semi = id.indexOf(';');
		if (semi >= 0) {
			id = id.substring(0, semi);
		}
		return id.toLowerCase(Locale.ROOT);
	}

	private static int attributeLevel(int[] costs, int shards) {
		if (costs == null || costs.length == 0 || shards <= 0) {
			return 0;
		}
		int level = 0;
		int remaining = shards;
		for (int cost : costs) {
			if (cost <= 0 || remaining < cost) {
				break;
			}
			remaining -= cost;
			level++;
		}
		return level;
	}

	/** +5 HP per SB level, +5 extra every 10 levels (wiki → 2750 at 500). */
	private static boolean addSkyBlockLevel(Map<String, Double> totals, JsonObject member) {
		JsonObject leveling = obj(member.get("leveling"));
		Float exp = leveling == null ? null : Leveling.num(leveling.get("experience"));
		if (exp == null || exp <= 0F) {
			return false;
		}
		int level = (int) Math.floor(exp / 100F);
		if (level <= 0) {
			return false;
		}
		add(totals, "health", 5.0 * level + 5.0 * (level / 10));
		return true;
	}

	private static boolean addBestiary(Map<String, Double> totals, JsonObject member) {
		JsonObject bestiary = obj(member.get("bestiary"));
		JsonObject milestone = bestiary == null ? null : obj(bestiary.get("milestone"));
		int claimed = intOr(milestone, "last_claimed_milestone");
		if (claimed <= 0) {
			return false;
		}
		add(totals, "health", 1.0 * claimed);
		return true;
	}

	private static boolean addCatacombs(Map<String, Double> totals, JsonObject member) {
		float xp = Leveling.readCatacombsXp(member);
		if (xp <= 0F) {
			return false;
		}
		Leveling.Progress progress = CataXpMath.progress(xp);
		// Stat bonuses stop at the soft cap; mastery levels are cosmetic only.
		int level = progress == null ? 0 : Math.min(CataXpMath.SOFT_CAP, progress.displayLevel());
		if (level <= 0) {
			return false;
		}
		add(totals, "health", 2.0 * level);
		return true;
	}

	private static boolean addEssencePermanents(Map<String, Double> totals, JsonObject member) {
		JsonObject playerData = obj(member.get("player_data"));
		JsonObject perks = playerData == null ? null : obj(playerData.get("perks"));
		if (perks == null) {
			JsonObject dungeons = obj(member.get("dungeons"));
			perks = dungeons == null ? null : obj(dungeons.get("perks"));
		}
		if (perks == null) {
			return false;
		}
		boolean added = false;
		for (var entry : FORBIDDEN.entrySet()) {
			int level = Math.max(0, Math.min(entry.getValue().length - 1, intOr(perks, entry.getKey())));
			if (level <= 0) {
				continue;
			}
			String stat = FORBIDDEN_STAT.get(entry.getKey());
			add(totals, stat, entry.getValue()[level]);
			added = true;
		}
		return added;
	}

	// Each consumable caps at 5 eaten.
	private static boolean addConsumables(Map<String, Double> totals, JsonObject member) {
		JsonObject playerData = obj(member.get("player_data"));
		if (playerData == null) {
			return false;
		}
		int peppers = Math.max(0, Math.min(5, intOr(playerData, "reaper_peppers_eaten")));
		int saliva = Math.max(0, Math.min(5, intOr(playerData, "bee_saliva_eaten")));
		int husks = Math.max(0, Math.min(5, intOr(playerData, "isopod_husks_eaten")));
		if (peppers > 0) {
			add(totals, "health", peppers);
			add(totals, "cold_resistance", peppers);
		}
		if (saliva > 0) {
			add(totals, "foraging_fortune", saliva);
		}
		if (husks > 0) {
			add(totals, "respiration", husks);
		}
		return peppers + saliva + husks > 0;
	}

	private static boolean addHotm(Map<String, Double> totals, JsonObject member) {
		Map<String, Integer> skillLevels = skillLevels(member);
		int miningLevel = skillLevels.getOrDefault("mining", 0);
		JsonObject hotmNodes = hotmPerkNodes(member);
		if (hotmNodes == null && miningLevel <= 0) {
			return false;
		}
		boolean added = false;
		double fortune = 4.0 * miningLevel
			+ 5.0 * intOr(hotmNodes, "mining_fortune")
			+ 5.0 * intOr(hotmNodes, "mining_fortune_2")
			+ 5.0 * intOr(hotmNodes, "fortunate_mineman");
		if (fortune > 0) {
			add(totals, "mining_fortune", fortune);
			added = true;
		}
		double miningSpeed = 20.0 * intOr(hotmNodes, "mining_speed")
			+ 40.0 * intOr(hotmNodes, "mining_speed_2")
			+ 40.0 * intOr(hotmNodes, "speedy_mineman");
		if (miningSpeed > 0) {
			add(totals, "mining_speed", miningSpeed);
			added = true;
		}
		return added;
	}

	private static boolean addMaxwellAndTuning(Map<String, Double> totals, JsonObject member) {
		JsonObject storage = obj(member.get("accessory_bag_storage"));
		if (storage == null) {
			return false;
		}
		boolean added = false;
		int mp = MagicalPowerCalculator.fromMember(member);
		if (mp <= 0) {
			mp = intOr(storage, "highest_magical_power");
		}
		String power = storage.has("selected_power") && storage.get("selected_power").isJsonPrimitive()
			? storage.get("selected_power").getAsString()
			: "";
		for (var e : MaxwellPowers.statsFor(power, mp).entrySet()) {
			if (wanted(e.getKey())) {
				add(totals, e.getKey(), e.getValue());
				added = true;
			}
		}

		JsonObject tuning = obj(storage.get("tuning"));
		if (tuning != null) {
			// Hypixel does not expose which template is equipped. Prefer the lowest
			// slot index with points (slot_0 is the primary Maxwell template). Picking
			// "most points" wrongly selects an alternate preset (e.g. CD instead of SPD).
			JsonObject best = null;
			int bestIndex = Integer.MAX_VALUE;
			for (var entry : tuning.entrySet()) {
				String key = entry.getKey();
				if (key == null || !key.startsWith("slot_")) {
					continue;
				}
				Integer index = tryParseInt(key.substring("slot_".length()));
				if (index == null || index >= bestIndex) {
					continue;
				}
				JsonObject slot = obj(entry.getValue());
				if (slot == null) {
					continue;
				}
				int points = 0;
				for (var stat : slot.entrySet()) {
					if (stat.getValue().isJsonPrimitive()) {
						try {
							points += Math.abs(stat.getValue().getAsInt());
						} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
						}
					}
				}
				if (points > 0) {
					bestIndex = index;
					best = slot;
				}
			}
			if (best != null) {
				for (var e : readStatMap(best).entrySet()) {
					add(totals, e.getKey(), e.getValue());
					added = true;
				}
			}
		}
		return added;
	}

	private static boolean addPetScore(Map<String, Double> totals, JsonObject member) {
		JsonObject bonuses = bonuses();
		if (bonuses == null || !bonuses.has("pet_rewards") || !bonuses.get("pet_rewards").isJsonObject()) {
			return false;
		}
		JsonObject leveling = obj(member.get("leveling"));
		int score = intOr(leveling, "highest_pet_score");
		if (score <= 0) {
			return false;
		}
		JsonObject rewards = bonuses.getAsJsonObject("pet_rewards");
		Map<String, Double> best = Map.of();
		int bestKey = -1;
		for (var entry : rewards.entrySet()) {
			Integer key = tryParseInt(entry.getKey());
			if (key == null || key > score || key < bestKey || !entry.getValue().isJsonObject()) {
				continue;
			}
			bestKey = key;
			best = readStatMap(entry.getValue().getAsJsonObject());
		}
		boolean added = false;
		for (var e : best.entrySet()) {
			if (wanted(e.getKey())) {
				add(totals, e.getKey(), e.getValue());
				added = true;
			}
		}
		return added;
	}

	private static boolean addActivePet(Map<String, Double> totals, JsonObject member) {
		JsonObject active = activePet(member);
		if (active == null) {
			return false;
		}
		Map<String, Double> stats = new HashMap<>(petStatNums(active));
		String type = active.has("type") && active.get("type").isJsonPrimitive()
			? active.get("type").getAsString()
			: "";
		PetWorth.LevelInfo info = PetWorth.levelInfo(active);
		int level = Math.max(1, info.level());

		if ("GOLDEN_DRAGON".equals(type)) {
			applyGoldenDragonAbilities(stats, member, level);
		}

		applyPetHeldItem(stats, active);

		boolean added = false;
		for (var e : stats.entrySet()) {
			if (wanted(e.getKey())) {
				add(totals, e.getKey(), e.getValue());
				added = true;
			}
		}
		return added;
	}

	private static JsonObject activePet(JsonObject member) {
		JsonArray pets = null;
		JsonObject petsData = obj(member.get("pets_data"));
		if (petsData != null && petsData.has("pets") && petsData.get("pets").isJsonArray()) {
			pets = petsData.getAsJsonArray("pets");
		} else if (member.has("pets") && member.get("pets").isJsonArray()) {
			pets = member.getAsJsonArray("pets");
		}
		if (pets == null) {
			return null;
		}
		for (JsonElement el : pets) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject pet = el.getAsJsonObject();
			if (pet.has("active") && pet.get("active").isJsonPrimitive() && pet.get("active").getAsBoolean()) {
				return pet;
			}
		}
		return null;
	}

	/**
	 * GDRAG Shining Scales: +11.1 STR / +2.2 MF per digit of personal Gold collection (cap 100M).
	 * Dragon's Greed is applied later once total Magic Find is known.
	 */
	private static void applyGoldenDragonAbilities(Map<String, Double> stats, JsonObject member, int level) {
		if (level < 100) {
			return;
		}
		long gold = goldCollection(member);
		long capped = Math.min(Math.max(0L, gold), 100_000_000L);
		int digits = capped <= 0L ? 1 : String.valueOf(capped).length();
		digits = Math.min(digits, 9);
		addMap(stats, "strength", digits * 11.1);
		addMap(stats, "magic_find", digits * 2.2);
	}

	/** Percent Strength from GDRAG Dragon's Greed using final Magic Find. */
	private static boolean applyDragonGreed(Map<String, Double> totals, JsonObject member) {
		JsonObject active = activePet(member);
		if (active == null) {
			return false;
		}
		String type = active.has("type") && active.get("type").isJsonPrimitive()
			? active.get("type").getAsString()
			: "";
		if (!"GOLDEN_DRAGON".equals(type)) {
			return false;
		}
		PetWorth.LevelInfo info = PetWorth.levelInfo(active);
		int level = Math.max(1, info.level());
		if (level < 100) {
			return false;
		}
		double t = Math.min(1.0, Math.max(0.0, (level - 100) / 100.0));
		double perFive = 0.25 + 0.25 * t;
		double maxPct = 2.5 + 2.5 * t;
		double mf = totals.getOrDefault("magic_find", 0.0);
		double pct = Math.min(maxPct, (mf / 5.0) * perFive);
		if (pct <= 0) {
			return false;
		}
		double strength = totals.getOrDefault("strength", 0.0);
		if (strength <= 0) {
			return false;
		}
		add(totals, "strength", strength * (pct / 100.0));
		return true;
	}

	private static void applyPetHeldItem(Map<String, Double> stats, JsonObject pet) {
		if (!pet.has("heldItem") || !pet.get("heldItem").isJsonPrimitive()) {
			return;
		}
		String held = pet.get("heldItem").getAsString();
		if (held == null || held.isBlank()) {
			return;
		}
		switch (held) {
			case "MINOS_RELIC" -> {
				for (String key : List.copyOf(stats.keySet())) {
					stats.put(key, stats.get(key) * 1.333);
				}
			}
			case "HEPHAESTUS_REMEDIES" -> {
				if (stats.containsKey("strength")) {
					stats.put("strength", stats.get("strength") * 2.0);
				}
			}
			case "ANTIQUE_REMEDIES" -> {
				if (stats.containsKey("strength")) {
					stats.put("strength", stats.get("strength") * 1.8);
				}
			}
			default -> {
			}
		}
	}

	private static long goldCollection(JsonObject member) {
		JsonObject collection = obj(member.get("collection"));
		if (collection == null || !collection.has("GOLD_INGOT") || !collection.get("GOLD_INGOT").isJsonPrimitive()) {
			return 0L;
		}
		try {
			return Math.max(0L, collection.get("GOLD_INGOT").getAsLong());
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return 0L;
		}
	}

	private static void addMap(Map<String, Double> map, String id, double value) {
		map.merge(id, value, Double::sum);
	}

	private static Map<String, Double> petStatNums(JsonObject pet) {
		JsonObject petnums = petnums();
		if (petnums == null || !pet.has("type") || !pet.get("type").isJsonPrimitive()) {
			return Map.of();
		}
		String type = pet.get("type").getAsString();
		if (!petnums.has(type) || !petnums.get(type).isJsonObject()) {
			return Map.of();
		}
		JsonObject byTier = petnums.getAsJsonObject(type);
		String tier = pet.has("tier") && pet.get("tier").isJsonPrimitive()
			? pet.get("tier").getAsString().toUpperCase(Locale.ROOT)
			: "COMMON";
		if (!byTier.has(tier) || !byTier.get(tier).isJsonObject()) {
			return Map.of();
		}
		JsonObject tierObj = byTier.getAsJsonObject(tier);
		PetWorth.LevelInfo info = PetWorth.levelInfo(pet);
		int level = Math.max(1, info.level());

		List<Integer> keys = new ArrayList<>();
		for (var entry : tierObj.entrySet()) {
			Integer key = tryParseInt(entry.getKey());
			if (key != null && entry.getValue().isJsonObject()) {
				JsonObject node = entry.getValue().getAsJsonObject();
				if (node.has("statNums") && node.get("statNums").isJsonObject()) {
					keys.add(key);
				}
			}
		}
		if (keys.isEmpty()) {
			return Map.of();
		}
		keys.sort(Integer::compareTo);
		int lo = keys.get(0);
		int hi = keys.get(keys.size() - 1);
		for (int i = 0; i < keys.size(); i++) {
			if (keys.get(i) <= level) {
				lo = keys.get(i);
			}
			if (keys.get(i) >= level) {
				hi = keys.get(i);
				break;
			}
		}
		Map<String, Double> lowStats = readPetStatNums(tierObj.getAsJsonObject(String.valueOf(lo)));
		if (lo == hi || level <= lo) {
			return lowStats;
		}
		if (level >= hi) {
			Map<String, Double> highStats = readPetStatNums(tierObj.getAsJsonObject(String.valueOf(hi)));
			if (level <= hi) {
				return highStats;
			}
			// Extrapolate past the last key using the gain from first→last (GDRAG 101-200).
			int first = keys.get(0);
			int last = keys.get(keys.size() - 1);
			if (last <= first) {
				return highStats;
			}
			Map<String, Double> firstStats = readPetStatNums(tierObj.getAsJsonObject(String.valueOf(first)));
			Map<String, Double> out = new HashMap<>(highStats);
			double t = (level - last) / (double) (last - first);
			for (var e : highStats.entrySet()) {
				double gain = e.getValue() - firstStats.getOrDefault(e.getKey(), 0.0);
				out.put(e.getKey(), e.getValue() + gain * t);
			}
			return out;
		}
		Map<String, Double> highStats = readPetStatNums(tierObj.getAsJsonObject(String.valueOf(hi)));
		double t = (level - lo) / (double) (hi - lo);
		Map<String, Double> out = new HashMap<>();
		Set<String> ids = new HashSet<>();
		ids.addAll(lowStats.keySet());
		ids.addAll(highStats.keySet());
		for (String id : ids) {
			double a = lowStats.getOrDefault(id, 0.0);
			double b = highStats.getOrDefault(id, 0.0);
			out.put(id, a + (b - a) * t);
		}
		return out;
	}

	private static Map<String, Double> readPetStatNums(JsonObject node) {
		if (node == null || !node.has("statNums") || !node.get("statNums").isJsonObject()) {
			return Map.of();
		}
		Map<String, Double> out = new HashMap<>();
		for (var entry : node.getAsJsonObject("statNums").entrySet()) {
			if (!entry.getValue().isJsonPrimitive()) {
				continue;
			}
			String key = normalizeStatKey(entry.getKey());
			if (wanted(key)) {
				out.put(key, entry.getValue().getAsDouble());
			}
		}
		return out;
	}

	private static boolean addItemLore(
		Map<String, Double> totals,
		List<InventoryDecoder.Stack> stacks,
		boolean uniqueById
	) {
		if (stacks == null || stacks.isEmpty()) {
			return false;
		}
		Set<String> seen = uniqueById ? new HashSet<>() : null;
		boolean added = false;
		for (InventoryDecoder.Stack stack : stacks) {
			if (stack == null || stack.id() == null || stack.id().isBlank()) {
				continue;
			}
			if (seen != null && !seen.add(stack.id())) {
				continue;
			}
			List<String> lore = stack.lore();
			if (lore == null || lore.isEmpty()) {
				continue;
			}
			for (String line : lore) {
				String clean = stripFormatting(line);
				if (clean.isBlank()) {
					continue;
				}
				for (StatDef def : DISPLAY) {
					Matcher matcher = def.pattern.matcher(clean);
					if (matcher.find()) {
						try {
							add(totals, def.id, Double.parseDouble(matcher.group(1)));
							added = true;
						} catch (NumberFormatException ignored) {
						}
					}
				}
			}
		}
		return added;
	}

	/** Sum of one stat's lore lines on a single item, matched the same way as worn gear. */
	static double loreStat(List<String> lore, String id) {
		if (lore == null || lore.isEmpty()) {
			return 0D;
		}
		double total = 0D;
		for (StatDef def : DISPLAY) {
			if (!def.id.equals(id)) {
				continue;
			}
			for (String line : lore) {
				Matcher matcher = def.pattern.matcher(stripFormatting(line));
				if (matcher.find()) {
					try {
						total += Double.parseDouble(matcher.group(1));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return total;
	}

	private static Map<String, Integer> skillLevels(JsonObject member) {
		Map<String, Integer> out = new HashMap<>();
		for (String skill : List.of(
			"combat", "mining", "farming", "foraging", "fishing", "enchanting", "alchemy", "taming", "carpentry",
			"runecrafting", "social"
		)) {
			double xp = Leveling.readSkillXpDouble(member, skill);
			if (xp <= 0F) {
				continue;
			}
			int cap = Leveling.skillCap(skill, member);
			Leveling.Progress progress = Leveling.getLevel(RepoData.levelingXp(), xp, cap, false);
			int level = progress == null ? 0 : (int) Math.floor(progress.level());
			if (level > 0) {
				out.put(skill, level);
			}
		}
		return out;
	}

	private static Map<String, Double> readStatMap(JsonObject object) {
		Map<String, Double> out = new HashMap<>();
		for (var entry : object.entrySet()) {
			if (!entry.getValue().isJsonPrimitive()) {
				continue;
			}
			String key = normalizeStatKey(entry.getKey());
			if (wanted(key)) {
				try {
					out.put(key, entry.getValue().getAsDouble());
				} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				}
			}
		}
		return out;
	}

	private static String normalizeStatKey(String key) {
		if (key == null) {
			return "";
		}
		String k = key.toLowerCase(Locale.ROOT);
		return switch (k) {
			case "defence" -> "defense";
			case "walk_speed" -> "speed";
			case "crit_chance", "critchance", "criticalchance" -> "critical_chance";
			case "crit_damage", "critdamage", "criticaldamage" -> "critical_damage";
			case "true_defence" -> "true_defense";
			case "bonus_attack_speed" -> "attack_speed";
			default -> k;
		};
	}

	/** NEU misc.base_stats uses defence / crit_chance naming. */
	private static Double readBaseStat(JsonObject base, String id) {
		for (String key : baseStatKeys(id)) {
			if (base.has(key) && base.get(key).isJsonPrimitive()) {
				return base.get(key).getAsDouble();
			}
		}
		return null;
	}

	private static List<String> baseStatKeys(String id) {
		return switch (id) {
			case "defense" -> List.of("defense", "defence");
			case "speed" -> List.of("speed", "walk_speed");
			case "critical_chance" -> List.of("critical_chance", "crit_chance");
			case "critical_damage" -> List.of("critical_damage", "crit_damage");
			case "true_defense" -> List.of("true_defense", "true_defence");
			case "attack_speed" -> List.of("attack_speed", "bonus_attack_speed");
			default -> List.of(id);
		};
	}

	private static boolean wanted(String id) {
		for (StatDef def : DISPLAY) {
			if (def.id.equals(id)) {
				return true;
			}
		}
		return false;
	}

	private static void add(Map<String, Double> totals, String id, double value) {
		totals.merge(id, value, Double::sum);
	}

	static String stripFormatting(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); ) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				i += 2;
				continue;
			}
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			if (cp >= 0xE000 && cp <= 0xF8FF) {
				continue;
			}
			out.appendCodePoint(cp);
		}
		return out.toString().trim();
	}

	private static JsonObject misc() {
		JsonObject cached = miscCache;
		if (cached != null) {
			return cached;
		}
		miscCache = loadConstant("misc.json");
		return miscCache;
	}

	private static JsonObject bonuses() {
		JsonObject cached = bonusesCache;
		if (cached != null) {
			return cached;
		}
		bonusesCache = loadConstant("bonuses.json");
		return bonusesCache;
	}

	private static JsonObject petnums() {
		JsonObject cached = petnumsCache;
		if (cached != null) {
			return cached;
		}
		petnumsCache = loadConstant("petnums.json");
		return petnumsCache;
	}

	private static JsonObject attributeShards() {
		JsonObject cached = attributeShardsCache;
		if (cached != null) {
			return cached;
		}
		attributeShardsCache = loadConstant("attribute_shards.json");
		return attributeShardsCache;
	}

	private static JsonObject loadConstant(String fileName) {
		Path path = Path.of(System.getProperty("user.home"), ".betterpv", "neu-repo", "repo", "constants", fileName);
		if (!Files.isRegularFile(path)) {
			path = Path.of(System.getProperty("user.home"), ".betterpv", "neu-repo", "constants", fileName);
		}
		if (!Files.isRegularFile(path)) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement el = JsonParser.parseReader(reader);
			return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof RuntimeException runtime && !SoftDataFailure.isSoft(runtime)) {
				throw runtime;
			}
			BetterPV.LOGGER.debug("Failed loading NEU {}", fileName, exception);
			return null;
		}
	}

	/**
	 * HOTM perk levels: the selected {@code skill_tree} preset, else legacy {@code mining_core.nodes}.
	 */
	private static JsonObject hotmPerkNodes(JsonObject member) {
		JsonObject selected = SkillTreePresets.selectedNodes(obj(member.get("skill_tree")), "mining");
		if (selected != null) {
			JsonObject perks = new JsonObject();
			for (var perk : selected.entrySet()) {
				String id = perk.getKey();
				if (id == null || id.startsWith("toggle_")) {
					continue;
				}
				perks.add(id, perk.getValue());
			}
			if (!perks.entrySet().isEmpty()) {
				return perks;
			}
		}
		JsonObject miningCore = obj(member.get("mining_core"));
		return miningCore == null ? null : obj(miningCore.get("nodes"));
	}

	private static JsonObject obj(JsonElement element) {
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static int intOr(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
			return 0;
		}
		try {
			return object.get(key).getAsInt();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return 0;
		}
	}

	private static Integer tryParseInt(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private record StatDef(String id, String label, Pattern pattern) {
	}

	private record AttributeFlat(String stackId, String statId, double perLevel) {
	}
}
