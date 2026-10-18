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
import fishmod.pv.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Home → Misc: lifetime player_stats, profile extras, community upgrades. */
public final class MiscStatsSnapshot {
	public record CountEntry(String id, String label, long count) {
	}

	public record CommunityUpgrade(String upgrade, String label, int tier, long startedMs) {
	}

	public record Section(String id, String title, List<CountEntry> entries) {
		public Section {
			entries = entries == null ? List.of() : List.copyOf(entries);
		}
	}

	/** Experimentation Table progress from {@code members.*.experimentation}. */
	public record ExperimentGame(String id, String label, long claims, long attempts, long bestScore) {
		public ExperimentGame {
			id = id == null ? "" : id;
			label = label == null || label.isBlank() ? id : label;
			claims = Math.max(0L, claims);
			attempts = Math.max(0L, attempts);
			bestScore = Math.max(0L, bestScore);
		}
	}

	public record ExperimentationStats(List<ExperimentGame> games, long serumsDrank, long claimsResets) {
		public ExperimentationStats {
			games = List.copyOf(games == null ? List.of() : games);
			serumsDrank = Math.max(0L, serumsDrank);
			claimsResets = Math.max(0L, claimsResets);
		}

		public static ExperimentationStats empty() {
			return new ExperimentationStats(List.of(), 0L, 0L);
		}

		public boolean present() {
			if (serumsDrank > 0L || claimsResets > 0L) {
				return true;
			}
			for (ExperimentGame g : games) {
				if (g.claims() > 0L || g.attempts() > 0L || g.bestScore() > 0L) {
					return true;
				}
			}
			return false;
		}
	}

	/** {@code player_stats.end_island.dragon_fight} for one dragon type. Fastest kill is in ms. */
	public record DragonStat(String id, String label, long summoned, long eyes, double mostDamage, long fastestKillMs, int bestRank) {
	}

	public record DragonStats(
		List<DragonStat> dragons, long summoned, long eyesPlaced, double bestDamage, long fastestKillMs,
		long eyesCollected, long specialZealots
	) {
		public DragonStats {
			dragons = List.copyOf(dragons == null ? List.of() : dragons);
		}

		public static DragonStats empty() {
			return new DragonStats(List.of(), 0L, 0L, 0D, 0L, 0L, 0L);
		}

		public boolean present() {
			return !dragons.isEmpty() || summoned > 0L || eyesCollected > 0L || specialZealots > 0L;
		}
	}

	public record RaceTime(String mode, long ms) {
	}

	/** One race course; Dungeon Hub courses have several modes, the rest have one. */
	public record RaceGroup(String label, long bestMs, List<RaceTime> modes) {
		public RaceGroup {
			modes = List.copyOf(modes == null ? List.of() : modes);
		}
	}

	/** {@code byRarity} is keyed by the Griffin pet rarity used while digging. */
	public record BurrowCount(String key, String label, long total, Map<String, Long> byRarity) {
		public BurrowCount {
			byRarity = byRarity == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byRarity));
		}
	}

	public record MythosStats(long kills, List<BurrowCount> burrows) {
		public MythosStats {
			burrows = List.copyOf(burrows == null ? List.of() : burrows);
		}

		public static MythosStats empty() {
			return new MythosStats(0L, List.of());
		}

		public boolean present() {
			return kills > 0L || !burrows.isEmpty();
		}
	}

	/**
	 * Jerry's Workshop bests ({@code player_stats.winter}) and Spooky Festival candy / bats.
	 * Best festival is ranked by festival points (green 1, purple 5), not raw candy count.
	 * {@code festivalHistoryCandy} is the candy covered by per-festival entries; Hypixel's history
	 * can be shorter than the lifetime total, in which case the best is only the best recorded.
	 */
	public record SeasonalStats(
		long snowballsHit, long winterDamage, long magmaDamage,
		long candyTotal, long greenCandy, long purpleCandy,
		int festivals, long bestFestivalCandy, int bestFestivalYear, long batsSpawned,
		long bestFestivalPoints, long bestFestivalGreen, long bestFestivalPurple, long festivalHistoryCandy
	) {
		public static SeasonalStats empty() {
			return new SeasonalStats(0L, 0L, 0L, 0L, 0L, 0L, 0, 0L, 0, 0L, 0L, 0L, 0L, 0L);
		}

		public boolean festivalHistoryIncomplete() {
			return festivalHistoryCandy < candyTotal;
		}

		public boolean winterPresent() {
			return snowballsHit > 0L || winterDamage > 0L || magmaDamage > 0L;
		}

		public boolean spookyPresent() {
			return candyTotal > 0L || batsSpawned > 0L;
		}
	}

	private final List<CountEntry> kills;
	private final long killsTotal;
	private final List<CountEntry> deaths;
	private final long deathsTotal;
	private final double highestDamage;
	private final double highestCriticalDamage;
	private final long giftsGiven;
	private final long giftsReceived;
	private final long petOresMined;
	private final long petSeaCreatures;
	private final long petXpTotal;
	private final long seaCreatureKills;
	private final long firstJoinMs;
	private final int fairyCollected;
	private final int fairyExchanges;
	private final int fairyUnspent;
	private final int personalBankUpgrade;
	private final boolean cookieBuffActive;
	private final long soulflow;
	private final int refinedJyrreUses;
	private final List<String> unlockedTemples;
	private final List<CommunityUpgrade> communityUpgrades;
	private final List<Section> extraSections;
	private final ExperimentationStats experimentation;
	private final DragonStats dragons;
	private final List<RaceGroup> races;
	private final MythosStats mythos;
	private final SeasonalStats seasonal;

	private MiscStatsSnapshot(
		List<CountEntry> kills,
		long killsTotal,
		List<CountEntry> deaths,
		long deathsTotal,
		double highestDamage,
		double highestCriticalDamage,
		long giftsGiven,
		long giftsReceived,
		long petOresMined,
		long petSeaCreatures,
		long petXpTotal,
		long seaCreatureKills,
		long firstJoinMs,
		int fairyCollected,
		int fairyExchanges,
		int fairyUnspent,
		int personalBankUpgrade,
		boolean cookieBuffActive,
		long soulflow,
		int refinedJyrreUses,
		List<String> unlockedTemples,
		List<CommunityUpgrade> communityUpgrades,
		List<Section> extraSections,
		ExperimentationStats experimentation,
		DragonStats dragons,
		List<RaceGroup> races,
		MythosStats mythos,
		SeasonalStats seasonal
	) {
		this.kills = List.copyOf(kills == null ? List.of() : kills);
		this.killsTotal = Math.max(0L, killsTotal);
		this.deaths = List.copyOf(deaths == null ? List.of() : deaths);
		this.deathsTotal = Math.max(0L, deathsTotal);
		this.highestDamage = Math.max(0D, highestDamage);
		this.highestCriticalDamage = Math.max(0D, highestCriticalDamage);
		this.giftsGiven = Math.max(0L, giftsGiven);
		this.giftsReceived = Math.max(0L, giftsReceived);
		this.petOresMined = Math.max(0L, petOresMined);
		this.petSeaCreatures = Math.max(0L, petSeaCreatures);
		this.petXpTotal = Math.max(0L, petXpTotal);
		this.seaCreatureKills = Math.max(0L, seaCreatureKills);
		this.firstJoinMs = Math.max(0L, firstJoinMs);
		this.fairyCollected = Math.max(0, fairyCollected);
		this.fairyExchanges = Math.max(0, fairyExchanges);
		this.fairyUnspent = Math.max(0, fairyUnspent);
		this.personalBankUpgrade = Math.max(0, personalBankUpgrade);
		this.cookieBuffActive = cookieBuffActive;
		this.soulflow = Math.max(0L, soulflow);
		this.refinedJyrreUses = Math.max(0, refinedJyrreUses);
		this.unlockedTemples = List.copyOf(unlockedTemples == null ? List.of() : unlockedTemples);
		this.communityUpgrades = List.copyOf(communityUpgrades == null ? List.of() : communityUpgrades);
		this.extraSections = List.copyOf(extraSections == null ? List.of() : extraSections);
		this.experimentation = experimentation == null ? ExperimentationStats.empty() : experimentation;
		this.dragons = dragons == null ? DragonStats.empty() : dragons;
		this.races = List.copyOf(races == null ? List.of() : races);
		this.mythos = mythos == null ? MythosStats.empty() : mythos;
		this.seasonal = seasonal == null ? SeasonalStats.empty() : seasonal;
	}

	public static MiscStatsSnapshot empty() {
		return new MiscStatsSnapshot(
			List.of(), 0L, List.of(), 0L,
			0D, 0D, 0L, 0L, 0L, 0L, 0L, 0L,
			0L, 0, 0, 0, 0, false, 0L, 0, List.of(),
			List.of(), List.of(), ExperimentationStats.empty(),
			DragonStats.empty(), List.of(), MythosStats.empty(), SeasonalStats.empty()
		);
	}

	public static MiscStatsSnapshot from(JsonObject profileRoot, JsonObject member) {
		if (member == null) {
			return empty();
		}
		JsonObject stats = Leveling.obj(member.get("player_stats"));
		JsonObject killsObj = Leveling.obj(stats == null ? null : stats.get("kills"));
		JsonObject deathsObj = Leveling.obj(stats == null ? null : stats.get("deaths"));
		List<CountEntry> kills = countMap(killsObj, true);
		List<CountEntry> deaths = countMap(deathsObj, true);
		long killsTotal = longOf(killsObj, "total");
		if (killsTotal <= 0L) {
			killsTotal = sum(kills);
		}
		long deathsTotal = longOf(deathsObj, "total");
		if (deathsTotal <= 0L) {
			deathsTotal = sum(deaths);
		}

		JsonObject gifts = Leveling.obj(stats == null ? null : stats.get("gifts"));
		JsonObject pets = Leveling.obj(stats == null ? null : stats.get("pets"));
		JsonObject milestones = Leveling.obj(pets == null ? null : pets.get("milestone"));

		JsonObject profile = Leveling.obj(member.get("profile"));
		JsonObject fairy = Leveling.obj(member.get("fairy_soul"));
		JsonObject itemData = Leveling.obj(member.get("item_data"));
		JsonObject winter = Leveling.obj(member.get("winter_player_data"));
		JsonObject temples = Leveling.obj(member.get("temples"));

		List<CommunityUpgrade> upgrades = parseCommunity(profileRoot);
		List<Section> extras = new ArrayList<>();
		addSection(extras, "rift_combat", "Rift Combat", Leveling.obj(stats == null ? null : stats.get("rift")));

		return new MiscStatsSnapshot(
			kills,
			killsTotal,
			deaths,
			deathsTotal,
			doubleOf(stats, "highest_damage"),
			doubleOf(stats, "highest_critical_damage"),
			longOf(gifts, "total_given"),
			longOf(gifts, "total_received"),
			longOf(milestones, "ores_mined"),
			longOf(milestones, "sea_creatures_killed"),
			longOf(pets, "total_exp_gained"),
			longOf(stats, "sea_creature_kills"),
			longOf(profile, "first_join"),
			(int) longOf(fairy, "total_collected"),
			(int) longOf(fairy, "fairy_exchanges"),
			(int) longOf(fairy, "unspent_souls"),
			(int) longOf(profile, "personal_bank_upgrade"),
			boolOf(profile, "cookie_buff_active"),
			longOf(itemData, "soulflow"),
			(int) longOf(winter, "refined_jyrre_uses"),
			stringList(temples == null ? null : temples.get("unlocked_temples")),
			upgrades,
			extras,
			parseExperimentation(Leveling.obj(member.get("experimentation"))),
			parseDragons(Leveling.obj(stats == null ? null : stats.get("end_island"))),
			parseRaces(Leveling.obj(stats == null ? null : stats.get("races"))),
			parseMythos(Leveling.obj(stats == null ? null : stats.get("mythos"))),
			parseSeasonal(stats)
		);
	}

	static SeasonalStats parseSeasonal(JsonObject stats) {
		if (stats == null) {
			return SeasonalStats.empty();
		}
		JsonObject winter = Leveling.obj(stats.get("winter"));
		JsonObject candy = Leveling.obj(stats.get("candy_collected"));
		JsonObject spooky = Leveling.obj(stats.get("spooky"));
		JsonObject bats = Leveling.obj(spooky == null ? null : spooky.get("bats_spawned"));
		int festivals = 0;
		long bestCandy = 0L;
		int bestYear = 0;
		long bestPoints = 0L;
		long bestGreen = 0L;
		long bestPurple = 0L;
		long historyCandy = 0L;
		if (candy != null) {
			for (var e : candy.entrySet()) {
				if (!e.getKey().startsWith("spooky_festival_")) {
					continue;
				}
				JsonObject year = Leveling.obj(e.getValue());
				long total = longOf(year, "total");
				if (total <= 0L) {
					continue;
				}
				festivals++;
				historyCandy += total;
				long green = longOf(year, "green_candy");
				long purple = longOf(year, "purple_candy");
				long points = green + purple * 5L;
				if (points > bestPoints) {
					bestPoints = points;
					bestGreen = green;
					bestPurple = purple;
					bestCandy = total;
					try {
						bestYear = Integer.parseInt(e.getKey().substring("spooky_festival_".length()));
					} catch (NumberFormatException ignored) {
						bestYear = 0;
					}
				}
			}
		}
		return new SeasonalStats(
			longOf(winter, "most_snowballs_hit"),
			longOf(winter, "most_damage_dealt"),
			longOf(winter, "most_magma_damage_dealt"),
			longOf(candy, "total"),
			longOf(candy, "green_candy"),
			longOf(candy, "purple_candy"),
			festivals,
			bestCandy,
			bestYear,
			longOf(bats, "total"),
			bestPoints,
			bestGreen,
			bestPurple,
			historyCandy
		);
	}

	private static final List<String> DRAGON_ORDER = List.of(
		"protector", "old", "wise", "unstable", "young", "strong", "superior", "holy"
	);

	private static DragonStats parseDragons(JsonObject endIsland) {
		if (endIsland == null) {
			return DragonStats.empty();
		}
		JsonObject fight = Leveling.obj(endIsland.get("dragon_fight"));
		JsonObject damage = Leveling.obj(fight == null ? null : fight.get("most_damage"));
		JsonObject fastest = Leveling.obj(fight == null ? null : fight.get("fastest_kill"));
		JsonObject rank = Leveling.obj(fight == null ? null : fight.get("highest_rank"));
		JsonObject summoned = Leveling.obj(fight == null ? null : fight.get("amount_summoned"));
		JsonObject eyes = Leveling.obj(fight == null ? null : fight.get("summoning_eyes_contributed"));

		List<String> ids = new ArrayList<>(DRAGON_ORDER);
		for (JsonObject src : new JsonObject[] { damage, fastest, rank, summoned, eyes }) {
			if (src == null) {
				continue;
			}
			for (String key : src.keySet()) {
				String id = key.toLowerCase(Locale.ROOT);
				if (!"best".equals(id) && !"total".equals(id) && !ids.contains(id)) {
					ids.add(id);
				}
			}
		}
		List<DragonStat> dragons = new ArrayList<>();
		for (String id : ids) {
			DragonStat d = new DragonStat(
				id,
				InventoryDecoder.prettyWords(id),
				longOf(summoned, id),
				longOf(eyes, id),
				doubleOf(damage, id),
				longOf(fastest, id),
				(int) longOf(rank, id)
			);
			if (d.summoned() > 0L || d.mostDamage() > 0D || d.fastestKillMs() > 0L) {
				dragons.add(d);
			}
		}
		return new DragonStats(
			dragons,
			longOf(summoned, "total"),
			longOf(eyes, "total"),
			doubleOf(damage, "best"),
			longOf(fastest, "best"),
			longOf(endIsland, "summoning_eyes_collected"),
			longOf(endIsland, "special_zealot_loot_collected")
		);
	}

	private static List<RaceGroup> parseRaces(JsonObject races) {
		if (races == null) {
			return List.of();
		}
		List<RaceGroup> out = new ArrayList<>();
		Map<String, List<RaceTime>> hub = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : races.entrySet()) {
			String key = e.getKey();
			if (key == null) {
				continue;
			}
			JsonElement val = e.getValue();
			if (val.isJsonObject()) {
				// dungeon_hub: <course>_<mode>_<return>_best_time
				for (Map.Entry<String, JsonElement> sub : val.getAsJsonObject().entrySet()) {
					long ms = longOf(val.getAsJsonObject(), sub.getKey());
					String id = sub.getKey().replace("_best_time", "");
					String course = null;
					for (String c : List.of("crystal_core", "giant_mushroom", "precursor_ruins")) {
						if (id.startsWith(c + "_")) {
							course = c;
							break;
						}
					}
					if (ms <= 0L || course == null) {
						continue;
					}
					String mode = InventoryDecoder.prettyWords(id.substring(course.length() + 1)
						.replace("_no_return", ", no return")
						.replace("_with_return", ", with return"));
					hub.computeIfAbsent(course, k -> new ArrayList<>()).add(new RaceTime(mode, ms));
				}
				continue;
			}
			long ms = longOf(races, key);
			if (ms <= 0L || !key.contains("best_time")) {
				continue;
			}
			String id = key.replaceAll("_best_time(_\\d+)?$", "");
			String label = InventoryDecoder.prettyWords(id);
			if (!label.toLowerCase(Locale.ROOT).endsWith("race")) {
				label = label + " Race";
			}
			out.add(new RaceGroup(label, ms, List.of(new RaceTime(label, ms))));
		}
		for (Map.Entry<String, List<RaceTime>> e : hub.entrySet()) {
			List<RaceTime> modes = new ArrayList<>(e.getValue());
			modes.sort(Comparator.comparingLong(RaceTime::ms));
			out.add(new RaceGroup(InventoryDecoder.prettyWords(e.getKey()), modes.get(0).ms(), modes));
		}
		return out;
	}

	private static final List<String> RARITY_ORDER = List.of(
		"none", "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"
	);

	static MythosStats parseMythos(JsonObject mythos) {
		if (mythos == null) {
			return MythosStats.empty();
		}
		List<BurrowCount> burrows = new ArrayList<>();
		addBurrows(burrows, mythos, "burrows_dug_next", "Arrow");
		addBurrows(burrows, mythos, "burrows_dug_combat", "Mob");
		addBurrows(burrows, mythos, "burrows_dug_treasure", "Treasure");
		addBurrows(burrows, mythos, "burrows_chains_complete", "Chains Completed");
		return new MythosStats(longOf(mythos, "kills"), burrows);
	}

	private static void addBurrows(List<BurrowCount> out, JsonObject mythos, String key, String label) {
		JsonObject obj = Leveling.obj(mythos.get(key));
		if (obj == null) {
			return;
		}
		Map<String, Long> byRarity = new LinkedHashMap<>();
		for (String rarity : RARITY_ORDER) {
			long n = longOf(obj, rarity);
			if (n > 0L) {
				byRarity.put("none".equals(rarity) ? "No Griffin" : InventoryDecoder.prettyWords(rarity.toLowerCase(Locale.ROOT)), n);
			}
		}
		long total = longOf(obj, "total");
		if (total <= 0L) {
			for (long n : byRarity.values()) {
				total += n;
			}
		}
		if (total > 0L) {
			out.add(new BurrowCount(key, label, total, byRarity));
		}
	}

	private static ExperimentationStats parseExperimentation(JsonObject root) {
		if (root == null || root.entrySet().isEmpty()) {
			return ExperimentationStats.empty();
		}
		List<ExperimentGame> games = new ArrayList<>(3);
		addExperimentGame(games, root, "pairings", "Pairings");
		addExperimentGame(games, root, "simon", "Simon");
		addExperimentGame(games, root, "numbers", "Numbers");
		return new ExperimentationStats(
			games,
			longOf(root, "serums_drank"),
			longOf(root, "claims_resets")
		);
	}

	private static void addExperimentGame(List<ExperimentGame> out, JsonObject root, String key, String label) {
		JsonObject game = Leveling.obj(root.get(key));
		if (game == null || game.entrySet().isEmpty()) {
			return;
		}
		long claims = 0L;
		long attempts = 0L;
		long best = 0L;
		for (Map.Entry<String, JsonElement> e : game.entrySet()) {
			String id = e.getKey();
			if (id == null || !e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) {
				continue;
			}
			long n = Math.max(0L, (long) e.getValue().getAsDouble());
			if (id.startsWith("claims_")) {
				claims += n;
			} else if (id.startsWith("attempts_")) {
				attempts += n;
			} else if (id.startsWith("best_score_")) {
				best = Math.max(best, n);
			}
		}
		if (claims <= 0L && attempts <= 0L && best <= 0L) {
			return;
		}
		out.add(new ExperimentGame(key, label, claims, attempts, best));
	}

	private static void addSection(List<Section> out, String id, String title, JsonObject obj) {
		if (obj == null || obj.entrySet().isEmpty()) {
			return;
		}
		List<CountEntry> entries = flattenCounts(obj, "");
		if (entries.isEmpty()) {
			return;
		}
		entries.sort(Comparator
			.comparingLong(CountEntry::count).reversed()
			.thenComparing(e -> e.label().toLowerCase(Locale.ROOT)));
		out.add(new Section(id, title, entries));
	}

	private static List<CountEntry> countMap(JsonObject obj, boolean skipTotal) {
		Map<String, CountEntry> merged = new LinkedHashMap<>();
		if (obj == null) {
			return List.of();
		}
		for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
			String key = e.getKey();
			if (key == null || (skipTotal && "total".equalsIgnoreCase(key))) {
				continue;
			}
			if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) {
				continue;
			}
			long n = Math.max(0L, (long) e.getValue().getAsDouble());
			if (n <= 0L) {
				continue;
			}
			String label = MobNames.pretty(key);
			CountEntry existing = merged.get(label);
			if (existing == null) {
				merged.put(label, new CountEntry(key, label, n));
			} else {
				merged.put(label, new CountEntry(existing.id() + "+" + key, label, existing.count() + n));
			}
		}
		List<CountEntry> out = new ArrayList<>(merged.values());
		out.sort(Comparator
			.comparingLong(CountEntry::count).reversed()
			.thenComparing(e -> e.label().toLowerCase(Locale.ROOT)));
		return out;
	}

	private static List<CountEntry> flattenCounts(JsonObject obj, String prefix) {
		List<CountEntry> out = new ArrayList<>();
		if (obj == null) {
			return out;
		}
		for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
			String key = e.getKey();
			if (key == null) {
				continue;
			}
			String path = prefix.isEmpty() ? key : prefix + "_" + key;
			JsonElement val = e.getValue();
			if (val.isJsonPrimitive() && val.getAsJsonPrimitive().isNumber()) {
				long n = Math.max(0L, (long) val.getAsDouble());
				if (n > 0L) {
					out.add(new CountEntry(path, InventoryDecoder.prettyWords(path), n));
				}
			} else if (val.isJsonObject()) {
				out.addAll(flattenCounts(val.getAsJsonObject(), path));
			}
		}
		return out;
	}

	private static List<CommunityUpgrade> parseCommunity(JsonObject profileRoot) {
		JsonObject root = Leveling.obj(profileRoot == null ? null : profileRoot.get("community_upgrades"));
		JsonArray states = root == null || !root.has("upgrade_states") || !root.get("upgrade_states").isJsonArray()
			? null
			: root.getAsJsonArray("upgrade_states");
		if (states == null) {
			return List.of();
		}
		Map<String, CommunityUpgrade> byId = new LinkedHashMap<>();
		for (JsonElement el : states) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject u = el.getAsJsonObject();
			String upgrade = str(u, "upgrade");
			if (upgrade.isBlank()) {
				continue;
			}
			CommunityUpgrade next = new CommunityUpgrade(
				upgrade,
				InventoryDecoder.prettyWords(upgrade),
				(int) longOf(u, "tier"),
				longOf(u, "started_ms")
			);
			String key = upgrade.toLowerCase(Locale.ROOT);
			CommunityUpgrade prev = byId.get(key);
			if (prev == null || next.tier() >= prev.tier()) {
				byId.put(key, next);
			}
		}
		List<CommunityUpgrade> out = new ArrayList<>(byId.values());
		out.sort(Comparator
			.comparing((CommunityUpgrade c) -> c.upgrade().toLowerCase(Locale.ROOT))
			.thenComparingInt(CommunityUpgrade::tier));
		return out;
	}

	private static long sum(List<CountEntry> entries) {
		long total = 0L;
		for (CountEntry e : entries) {
			total += e.count();
		}
		return total;
	}

	private static long longOf(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return 0L;
		}
		try {
			return Math.max(0L, (long) obj.get(key).getAsDouble());
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return 0L;
		}
	}

	private static double doubleOf(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return 0D;
		}
		try {
			return Math.max(0D, obj.get(key).getAsDouble());
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return 0D;
		}
	}

	private static boolean boolOf(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return false;
		}
		try {
			return obj.get(key).getAsBoolean();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return false;
		}
	}

	private static String str(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return "";
		}
		try {
			String s = obj.get(key).getAsString();
			return s == null ? "" : s;
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static List<String> stringList(JsonElement el) {
		if (el == null || !el.isJsonArray()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (JsonElement item : el.getAsJsonArray()) {
			if (item == null || !item.isJsonPrimitive()) {
				continue;
			}
			try {
				String s = item.getAsString();
				if (s != null && !s.isBlank()) {
					out.add(InventoryDecoder.prettyWords(s));
				}
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			}
		}
		return out;
	}

	public List<CountEntry> kills() { return kills; }
	public long killsTotal() { return killsTotal; }
	public List<CountEntry> deaths() { return deaths; }
	public long deathsTotal() { return deathsTotal; }
	// Zero deaths divides by 1 (Hypixel convention), so the ratio equals total kills.
	public double killDeathRatio() { return killsTotal / (double) Math.max(1L, deathsTotal); }
	public double highestDamage() { return highestDamage; }
	public double highestCriticalDamage() { return highestCriticalDamage; }
	public long giftsGiven() { return giftsGiven; }
	public long giftsReceived() { return giftsReceived; }
	public long petOresMined() { return petOresMined; }
	public long petSeaCreatures() { return petSeaCreatures; }
	public long petXpTotal() { return petXpTotal; }
	public long seaCreatureKills() { return seaCreatureKills; }
	public long firstJoinMs() { return firstJoinMs; }
	public int fairyCollected() { return fairyCollected; }
	public int fairyExchanges() { return fairyExchanges; }
	public int fairyUnspent() { return fairyUnspent; }
	public int personalBankUpgrade() { return personalBankUpgrade; }
	public boolean cookieBuffActive() { return cookieBuffActive; }
	public long soulflow() { return soulflow; }
	public int refinedJyrreUses() { return refinedJyrreUses; }
	public List<String> unlockedTemples() { return unlockedTemples; }
	public List<CommunityUpgrade> communityUpgrades() { return communityUpgrades; }
	public List<Section> extraSections() { return extraSections; }
	public ExperimentationStats experimentation() { return experimentation; }
	public DragonStats dragons() { return dragons; }
	public List<RaceGroup> races() { return races; }
	public MythosStats mythos() { return mythos; }
	public SeasonalStats seasonal() { return seasonal; }
}
