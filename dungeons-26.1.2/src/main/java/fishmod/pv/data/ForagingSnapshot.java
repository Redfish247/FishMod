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
import fishmod.pv.dungeons.EssenceShopData;
import fishmod.pv.networth.InventoryDecoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ForagingSnapshot {
	public record CollectionRow(String id, String name, long amount) {
	}

	public record AttrStack(String id, String name, int level) {
	}

	public record TreeGift(String id, String name, long count, int milestoneTier) {
	}

	public record StarlynBest(String id, String name, long amount) {
	}

	public record HarpSong(String id, String name, double best, int completions, int perfects) {
	}

	public record HotfNode(String id, String name, int level, int maxLevel, boolean enabled, boolean ability) {
	}

	public record OwnedShard(String type, String name, long amount, long capturedMs) {
	}

	public record ToolkitSlot(String group, int index, boolean inUse, InventorySnapshot.Slot item) {
	}

	public record AttributeShardRow(
		String stackId, String name, String iconId, String rarity, String priceId,
		int level, int maxLevel, int shardsOwned, int shardsForMax, boolean unlocked
	) {
		/** Highest rarity first, then alphabetical within a rarity. */
		public static List<AttributeShardRow> byRarity(List<AttributeShardRow> rows) {
			List<AttributeShardRow> out = new ArrayList<>(rows);
			out.sort(Comparator
				.comparingInt((AttributeShardRow r) -> AttributeShardsData.rarityRank(r.rarity())).reversed()
				.thenComparing(AttributeShardRow::name, String.CASE_INSENSITIVE_ORDER));
			return List.copyOf(out);
		}
	}

	public record SafariInfo(
		List<String> discoveredCritters,
		Map<String, Long> biomeCaptures,
		Map<String, Long> tickets,
		Map<String, Integer> milestoneTiers,
		List<String> sparklingDiscovered,
		long sparklingCaptured
	) {
		public SafariInfo {
			discoveredCritters = List.copyOf(discoveredCritters == null ? List.of() : discoveredCritters);
			biomeCaptures = Map.copyOf(biomeCaptures == null ? Map.of() : biomeCaptures);
			tickets = Map.copyOf(tickets == null ? Map.of() : tickets);
			milestoneTiers = Map.copyOf(milestoneTiers == null ? Map.of() : milestoneTiers);
			sparklingDiscovered = List.copyOf(sparklingDiscovered == null ? List.of() : sparklingDiscovered);
			sparklingCaptured = Math.max(0L, sparklingCaptured);
		}

		public static SafariInfo empty() {
			return new SafariInfo(List.of(), Map.of(), Map.of(), Map.of(), List.of(), 0L);
		}

		public boolean present() {
			return !discoveredCritters.isEmpty()
				|| !biomeCaptures.isEmpty()
				|| !tickets.isEmpty()
				|| !milestoneTiers.isEmpty();
		}

		public long totalCaptures() {
			long n = 0L;
			for (Long v : biomeCaptures.values()) {
				n += v == null ? 0L : Math.max(0L, v);
			}
			return n;
		}

		public long totalTickets() {
			long n = 0L;
			for (Long v : tickets.values()) {
				n += v == null ? 0L : Math.max(0L, v);
			}
			return n;
		}
	}

	public record HoneyInfo(int smearedTrees, int refills, List<String> treeNames) {
		public HoneyInfo {
			smearedTrees = Math.max(0, smearedTrees);
			refills = Math.max(0, refills);
			treeNames = List.copyOf(treeNames == null ? List.of() : treeNames);
		}

		public static HoneyInfo empty() {
			return new HoneyInfo(0, 0, List.of());
		}

		public boolean present() {
			return smearedTrees > 0 || refills > 0;
		}
	}

	private final int foragingLevel;
	private final float foragingFill;
	private final boolean foragingMaxed;
	private final String foragingHover;
	private final int huntingLevel;
	private final float huntingFill;
	private final boolean huntingMaxed;
	private final String huntingHover;

	private final long raceBestMs;
	private final List<CollectionRow> collections;
	private final List<AttrStack> attributes;

	/**
	 * {@code whispers.<pool>.total} is lifetime earned; {@code whispers.<pool>.<slot>.spent} is per HOTF
	 * loadout, so {@code spent} is the selected loadout's spend only.
	 */
	public record WhisperPool(
		String id,
		String label,
		long total,
		long spent,
		int loadout,
		Map<Integer, Long> spentByLoadout
	) {
		public WhisperPool {
			id = id == null ? "" : id;
			label = label == null || label.isBlank() ? prettyWhisperLabel(id) : label;
			total = Math.max(0L, total);
			spent = Math.max(0L, spent);
			loadout = Math.max(1, loadout);
			spentByLoadout = Map.copyOf(spentByLoadout == null ? Map.of() : spentByLoadout);
		}

		public long balance() {
			return Math.max(0L, total - spent);
		}

		public boolean present() {
			return total > 0L || spent > 0L || !spentByLoadout.isEmpty();
		}

		private static String prettyWhisperLabel(String id) {
			if (id == null || id.isBlank()) {
				return "Whispers";
			}
			return switch (id.toLowerCase(Locale.ROOT)) {
				case "forest" -> "Forest";
				case "desert" -> "Desert";
				default -> InventoryDecoder.prettyWords(id);
			};
		}
	}

	private final List<TreeGift> treeGifts;
	private final List<StarlynBest> starlynBests;
	private final List<WhisperPool> whisperPools;

	private final List<String> fishFamily;
	private final int hinaTier;
	private final Map<String, Long> hinaProgress;
	private final List<String> hinaCompleted;
	private final List<String> hinaClaimed;

	private final boolean harpTalisman;
	private final String harpSelected;
	private final List<HarpSong> harpSongs;
	// Moonglade/Galatea beacon signal strength; -1 = not present.
	private final int galateaBeacon;

	private final double hotfXp;
	private final int forestTokensSpent;
	private final long hotfLastResetMs;
	private final String dailyEffect;
	private final int dailyEffectChanged;
	private final long dailyTreesCut;
	private final long dailyTreesDay;
	private final long dailyGifts;
	private final List<String> dailyLogs;
	private final long dailyLogsDay;
	private final List<HotfNode> hotfNodes;
	private final Map<String, Integer> centerPages;
	private final String selectedAbility;
	private final boolean refundAbilityFree;

	private final long fusedShards;
	private final List<OwnedShard> ownedShards;
	private final Map<String, Long> huntStats;
	private final boolean toolkitUnlocked;
	private final List<ToolkitSlot> toolkitSlots;
	private final List<AttributeShardRow> attributeShards;
	private final DungeonSnapshot.EssenceShop forestShop;
	private final SafariInfo safari;
	private final long uniqueShards;
	private final DungeonSnapshot.EssenceShop safariShop;
	private final long peltCount;
	private final HoneyInfo honey;

	private ForagingSnapshot(
		int foragingLevel, float foragingFill, boolean foragingMaxed, String foragingHover,
		int huntingLevel, float huntingFill, boolean huntingMaxed, String huntingHover,
		long raceBestMs, List<CollectionRow> collections, List<AttrStack> attributes,
		List<TreeGift> treeGifts, List<StarlynBest> starlynBests, List<WhisperPool> whisperPools,
		List<String> fishFamily, int hinaTier, Map<String, Long> hinaProgress,
		List<String> hinaCompleted, List<String> hinaClaimed,
		boolean harpTalisman, String harpSelected, List<HarpSong> harpSongs, int galateaBeacon,
		double hotfXp, int forestTokensSpent, long hotfLastResetMs,
		String dailyEffect, int dailyEffectChanged,
		long dailyTreesCut, long dailyTreesDay, long dailyGifts,
		List<String> dailyLogs, long dailyLogsDay,
		List<HotfNode> hotfNodes, Map<String, Integer> centerPages,
		String selectedAbility, boolean refundAbilityFree,
		long fusedShards, List<OwnedShard> ownedShards, Map<String, Long> huntStats,
		boolean toolkitUnlocked, List<ToolkitSlot> toolkitSlots,
		List<AttributeShardRow> attributeShards,
		DungeonSnapshot.EssenceShop forestShop,
		SafariInfo safari,
		long uniqueShards,
		DungeonSnapshot.EssenceShop safariShop,
		long peltCount,
		HoneyInfo honey
	) {
		this.foragingLevel = foragingLevel;
		this.foragingFill = Math.max(0f, Math.min(1f, foragingFill));
		this.foragingMaxed = foragingMaxed;
		this.foragingHover = foragingHover == null ? "" : foragingHover;
		this.huntingLevel = huntingLevel;
		this.huntingFill = Math.max(0f, Math.min(1f, huntingFill));
		this.huntingMaxed = huntingMaxed;
		this.huntingHover = huntingHover == null ? "" : huntingHover;
		this.raceBestMs = Math.max(0L, raceBestMs);
		this.collections = List.copyOf(collections == null ? List.of() : collections);
		this.attributes = List.copyOf(attributes == null ? List.of() : attributes);
		this.treeGifts = List.copyOf(treeGifts == null ? List.of() : treeGifts);
		this.starlynBests = List.copyOf(starlynBests == null ? List.of() : starlynBests);
		this.whisperPools = List.copyOf(whisperPools == null ? List.of() : whisperPools);
		this.fishFamily = List.copyOf(fishFamily == null ? List.of() : fishFamily);
		this.hinaTier = Math.max(0, hinaTier);
		this.hinaProgress = Map.copyOf(hinaProgress == null ? Map.of() : hinaProgress);
		this.hinaCompleted = List.copyOf(hinaCompleted == null ? List.of() : hinaCompleted);
		this.hinaClaimed = List.copyOf(hinaClaimed == null ? List.of() : hinaClaimed);
		this.harpTalisman = harpTalisman;
		this.harpSelected = harpSelected == null ? "" : harpSelected;
		this.harpSongs = List.copyOf(harpSongs == null ? List.of() : harpSongs);
		this.galateaBeacon = galateaBeacon;
		this.hotfXp = Math.max(0.0, hotfXp);
		this.forestTokensSpent = Math.max(0, forestTokensSpent);
		this.hotfLastResetMs = Math.max(0L, hotfLastResetMs);
		this.dailyEffect = dailyEffect == null ? "" : dailyEffect;
		this.dailyEffectChanged = dailyEffectChanged;
		this.dailyTreesCut = Math.max(0L, dailyTreesCut);
		this.dailyTreesDay = Math.max(0L, dailyTreesDay);
		this.dailyGifts = Math.max(0L, dailyGifts);
		this.dailyLogs = List.copyOf(dailyLogs == null ? List.of() : dailyLogs);
		this.dailyLogsDay = Math.max(0L, dailyLogsDay);
		this.hotfNodes = List.copyOf(hotfNodes == null ? List.of() : hotfNodes);
		this.centerPages = Map.copyOf(centerPages == null ? Map.of() : centerPages);
		this.selectedAbility = selectedAbility == null ? "" : selectedAbility;
		this.refundAbilityFree = refundAbilityFree;
		this.fusedShards = Math.max(0L, fusedShards);
		this.ownedShards = List.copyOf(ownedShards == null ? List.of() : ownedShards);
		this.huntStats = Map.copyOf(huntStats == null ? Map.of() : huntStats);
		this.toolkitUnlocked = toolkitUnlocked;
		this.toolkitSlots = List.copyOf(toolkitSlots == null ? List.of() : toolkitSlots);
		this.attributeShards = List.copyOf(attributeShards == null ? List.of() : attributeShards);
		this.forestShop = forestShop == null
			? DungeonSnapshot.EssenceShop.empty("forest", "Forest")
			: forestShop;
		this.safari = safari == null ? SafariInfo.empty() : safari;
		this.uniqueShards = Math.max(0L, uniqueShards);
		this.safariShop = safariShop == null
			? DungeonSnapshot.EssenceShop.empty("safari", "Safari")
			: safariShop;
		this.peltCount = Math.max(0L, peltCount);
		this.honey = honey == null ? HoneyInfo.empty() : honey;
	}

	public static ForagingSnapshot empty() {
		return new ForagingSnapshot(
			0, 0f, false, "", 0, 0f, false, "",
			0L, List.of(), List.of(),
			List.of(), List.of(), List.of(),
			List.of(), 0, Map.of(), List.of(), List.of(),
			false, "", List.of(), -1,
			0, 0, 0L, "", 0, 0L, 0L, 0L, List.of(), 0L,
			List.of(), Map.of(), "", false,
			0L, List.of(), Map.of(), false, List.of(), List.of(),
			DungeonSnapshot.EssenceShop.empty("forest", "Forest"),
			SafariInfo.empty(),
			0L,
			DungeonSnapshot.EssenceShop.empty("safari", "Safari"),
			0L,
			HoneyInfo.empty()
		);
	}

	public static ForagingSnapshot fromMember(JsonObject member) {
		if (member == null) {
			return empty();
		}
		ForagingHotfData.ensureLoaded();
		AttributeShardsData.ensureLoaded();
		RepoData.ensureLoaded();

		double foragingXp = Leveling.readSkillXpDouble(member, "foraging");
		int foragingCap = Leveling.skillCap("foraging", member);
		Leveling.Progress foraging = Leveling.getLevel(Leveling.skillTable("foraging"), foragingXp, foragingCap, false);

		double huntingXp = Leveling.readSkillXpDouble(member, "hunting");
		int huntingCap = Leveling.skillCap("hunting", member);
		Leveling.Progress hunting = Leveling.getLevel(Leveling.skillTable("hunting"), huntingXp, huntingCap, false);

		JsonObject stats = Leveling.obj(member.get("player_stats"));
		JsonObject races = Leveling.obj(stats == null ? null : stats.get("races"));
		long raceBest = longOf(races, "foraging_race_best_time");

		List<CollectionRow> collections = parseCollections(Leveling.obj(member.get("collection")));
		JsonObject attributesRoot = Leveling.obj(member.get("attributes"));
		JsonObject stacks = Leveling.obj(attributesRoot == null ? null : attributesRoot.get("stacks"));
		List<AttrStack> attributes = parseAttributes(stacks);
		List<AttributeShardRow> attributeShards = parseAttributeShards(stacks);

		JsonObject foragingObj = Leveling.obj(member.get("foraging"));
		JsonObject core = Leveling.obj(member.get("foraging_core"));
		JsonObject skillTree = Leveling.obj(member.get("skill_tree"));

		List<TreeGift> gifts = parseTreeGifts(Leveling.obj(foragingObj == null ? null : foragingObj.get("tree_gifts")));
		JsonObject starlynRoot = Leveling.obj(foragingObj == null ? null : foragingObj.get("starlyn"));
		List<StarlynBest> starlyn = parseStarlyn(
			Leveling.obj(starlynRoot == null ? null : starlynRoot.get("personal_bests"))
		);

		long whispers = longOf(core, "forests_whispers");
		long whispersSpent = longOf(core, "forests_whispers_spent");
		List<WhisperPool> whisperPools = parseWhisperPools(core, whispers, whispersSpent,
			SkillTreePresets.selectedSlot(skillTree, "foraging"));

		List<String> fish = stringList(foragingObj == null ? null : foragingObj.get("fish_family"));
		JsonObject hina = Leveling.obj(foragingObj == null ? null : foragingObj.get("hina"));
		JsonObject hinaTasks = Leveling.obj(hina == null ? null : hina.get("tasks"));
		int hinaTier = (int) longOf(hinaTasks, "tier_claimed");
		Map<String, Long> hinaProgress = longMap(Leveling.obj(hinaTasks == null ? null : hinaTasks.get("task_progress")));
		List<String> hinaCompleted = stringList(hinaTasks == null ? null : hinaTasks.get("completed_tasks"));
		List<String> hinaClaimed = stringList(hinaTasks == null ? null : hinaTasks.get("claimed_rewards"));

		JsonObject songsRoot = Leveling.obj(foragingObj == null ? null : foragingObj.get("songs"));
		JsonObject harp = Leveling.obj(songsRoot == null ? null : songsRoot.get("harp"));
		if (harp == null) {
			harp = Leveling.obj(foragingObj == null ? null : foragingObj.get("harp"));
		}
		JsonObject quests = Leveling.obj(member.get("quests"));
		JsonObject harpQuest = Leveling.obj(quests == null ? null : quests.get("harp_quest"));
		boolean talisman = boolOf(harp, "claimed_talisman")
			|| boolOf(songsRoot, "claimed_talisman")
			|| boolOf(harpQuest, "claimed_talisman")
			|| objectiveComplete(member, "talk_to_melody_2")
			|| memberOwnsItem(member, "MELODY_HAIR")
			|| memberJsonContains(member, "MELODY_HAIR");
		String selectedSong = str(harp, "selected_song");
		if (selectedSong.isBlank()) {
			selectedSong = str(harpQuest, "selected_song");
		}
		List<HarpSong> harpSongs = parseHarpSongs(harp != null ? harp : harpQuest);
		int galateaBeacon = parseGalateaBeacon(member, foragingObj, core);

		JsonObject treeXp = Leveling.obj(skillTree == null ? null : skillTree.get("experience"));
		double hotfXp = doubleOf(treeXp, "foraging");
		Integer selectedTokens = SkillTreePresets.selectedTokensSpent(skillTree, "foraging");
		int forestTokens = selectedTokens == null ? 0 : selectedTokens;
		JsonObject lastReset = Leveling.obj(skillTree == null ? null : skillTree.get("last_reset"));
		long resetMs = longOf(lastReset, "foraging");
		JsonObject selectedAbility = Leveling.obj(skillTree == null ? null : skillTree.get("selected_ability"));
		String ability = str(selectedAbility, "foraging");
		if (ability.isBlank()) {
			ability = str(selectedAbility, "forest");
		}
		boolean refundFree = boolOf(skillTree, "refund_ability_free");

		String dailyEffect = str(core, "current_daily_effect");
		int dailyChanged = (int) longOf(core, "current_daily_effect_last_changed");
		long dailyTrees = longOf(core, "daily_trees_cut");
		long dailyTreesDay = longOf(core, "daily_trees_cut_day");
		long dailyGifts = longOf(core, "daily_gifts");
		List<String> dailyLogs = stringList(core == null ? null : core.get("daily_log_cut"));
		long dailyLogsDay = longOf(core, "daily_log_cut_day");

		JsonObject nodesRoot = Leveling.obj(skillTree == null ? null : skillTree.get("nodes"));
		List<HotfNode> nodes = parseHotfNodes(SkillTreePresets.selectedNodes(skillTree, "foraging"));
		Map<String, Integer> centers = new LinkedHashMap<>();
		for (int i = 2; i <= 5; i++) {
			JsonObject page = Leveling.obj(nodesRoot == null ? null : nodesRoot.get("foraging_" + i));
			int center = (int) longOf(page, "center_of_the_forest");
			if (center > 0) {
				centers.put("Page " + i, center);
			}
		}

		JsonObject shards = Leveling.obj(member.get("shards"));
		long fused = longOf(shards, "fused");
		List<OwnedShard> owned = parseOwnedShards(shards == null ? null : shards.get("owned"));
		Map<String, Long> hunts = parseHuntStats(stats);

		JsonObject toolkit = Leveling.obj(foragingObj == null ? null : foragingObj.get("hunting_toolkit"));
		boolean toolkitUnlocked = boolOf(toolkit, "IS_UNLOCKED");
		List<ToolkitSlot> toolkitSlots = parseToolkit(toolkit);
		SafariInfo safari = parseSafari(Leveling.obj(member.get("safari")));
		JsonObject trapper = Leveling.obj(quests == null ? null : quests.get("trapper_quest"));
		long pelts = longOf(trapper, "pelt_count");
		HoneyInfo honey = parseHoney(Leveling.obj(foragingObj == null ? null : foragingObj.get("honey")));

		return new ForagingSnapshot(
			foraging.cappedLevel(), foraging.fill(), foraging.maxed(), foraging.skillHoverText("Foraging"),
			hunting.cappedLevel(), hunting.fill(), hunting.maxed(), hunting.skillHoverText("Hunting"),
			raceBest, collections, attributes,
			gifts, starlyn, whisperPools,
			fish, hinaTier, hinaProgress, hinaCompleted, hinaClaimed,
			talisman, selectedSong, harpSongs, galateaBeacon,
			hotfXp, forestTokens, resetMs,
			dailyEffect, dailyChanged, dailyTrees, dailyTreesDay, dailyGifts, dailyLogs, dailyLogsDay,
			nodes, centers, ability, refundFree,
			fused, owned, hunts, toolkitUnlocked, toolkitSlots, attributeShards,
			EssenceShopData.forest(member),
			safari,
			longOf(stats, "unique_shards"),
			EssenceShopData.safari(member),
			pelts,
			honey
		);
	}

	static List<WhisperPool> parseWhisperPools(JsonObject core, long legacyBalance, long legacySpent, int loadout) {
		JsonObject root = Leveling.obj(core == null ? null : core.get("whispers"));
		if (root != null && !root.entrySet().isEmpty()) {
			List<WhisperPool> out = new ArrayList<>();
			LinkedHashMap<String, JsonObject> ordered = new LinkedHashMap<>();
			for (String id : List.of("forest", "desert")) {
				JsonObject pool = Leveling.obj(root.get(id));
				if (pool != null) {
					ordered.put(id, pool);
				}
			}
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				if (e.getKey() == null || ordered.containsKey(e.getKey())) {
					continue;
				}
				JsonObject pool = Leveling.obj(e.getValue());
				if (pool != null) {
					ordered.put(e.getKey(), pool);
				}
			}
			for (Map.Entry<String, JsonObject> e : ordered.entrySet()) {
				WhisperPool pool = parseWhisperPool(e.getKey(), e.getValue(), loadout);
				if (pool != null) {
					out.add(pool);
				}
			}
			if (!out.isEmpty()) {
				return List.copyOf(out);
			}
		}
		if (legacyBalance > 0L || legacySpent > 0L) {
			return List.of(new WhisperPool("forest", "Forest", legacyBalance + legacySpent, legacySpent, 1, Map.of()));
		}
		return List.of();
	}

	private static WhisperPool parseWhisperPool(String id, JsonObject pool, int loadout) {
		if (pool == null || pool.entrySet().isEmpty()) {
			return null;
		}
		long total = firstLong(pool, "total");
		long spentField = firstLong(pool, "spent");
		Map<Integer, Long> bySlot = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : pool.entrySet()) {
			String key = e.getKey();
			if (key == null || isWhisperMetaKey(key)) {
				continue;
			}
			int slot;
			try {
				slot = Integer.parseInt(key);
			} catch (NumberFormatException ignored) {
				continue;
			}
			JsonObject slotObj = Leveling.obj(e.getValue());
			long slotSpent = slotObj != null ? longOf(slotObj, "spent") : 0L;
			if (slotSpent <= 0L && e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) {
				slotSpent = Math.max(0L, (long) e.getValue().getAsDouble());
			}
			if (slotSpent > 0L) {
				bySlot.put(slot, slotSpent);
			}
		}
		long spent = bySlot.isEmpty() ? spentField : bySlot.getOrDefault(loadout, 0L);
		if (total <= 0L && spent <= 0L && bySlot.isEmpty()) {
			return null;
		}
		return new WhisperPool(id, WhisperPool.prettyWhisperLabel(id), total, spent, loadout, bySlot);
	}

	private static boolean isWhisperMetaKey(String key) {
		return "total".equalsIgnoreCase(key)
			|| "current".equalsIgnoreCase(key)
			|| "balance".equalsIgnoreCase(key)
			|| "spent".equalsIgnoreCase(key);
	}

	private static long firstLong(JsonObject obj, String... keys) {
		if (obj == null) {
			return 0L;
		}
		for (String key : keys) {
			long value = longOf(obj, key);
			if (value > 0L) {
				return value;
			}
		}
		return 0L;
	}

	private static SafariInfo parseSafari(JsonObject root) {
		if (root == null || root.entrySet().isEmpty()) {
			return SafariInfo.empty();
		}
		List<String> critters = stringList(root.get("discovered_critters"));
		Map<String, Long> captures = longMap(Leveling.obj(root.get("biome_captures")));
		Map<String, Long> tickets = longMap(Leveling.obj(root.get("tickets")));
		Map<String, Integer> milestones = new LinkedHashMap<>();
		JsonObject tiers = Leveling.obj(root.get("milestone_claimed_tiers"));
		if (tiers != null) {
			for (Map.Entry<String, JsonElement> e : tiers.entrySet()) {
				if (e.getKey() == null || !e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) {
					continue;
				}
				int n = Math.max(0, (int) e.getValue().getAsDouble());
				if (n > 0) {
					milestones.put(e.getKey(), n);
				}
			}
		}
		return new SafariInfo(critters, captures, tickets, milestones,
			stringList(root.get("discovered_sparkling_critters")),
			longOf(root, "total_captured_sparkling_critters"));
	}

	private static HoneyInfo parseHoney(JsonObject root) {
		if (root == null || root.entrySet().isEmpty()) {
			return HoneyInfo.empty();
		}
		JsonElement smeared = root.get("smeared_trees");
		JsonElement refills = root.get("refill_times");
		int smearedCount = countEntries(smeared);
		int refillCount = countEntries(refills);
		List<String> names = new ArrayList<>();
		if (smeared != null && smeared.isJsonObject()) {
			for (String key : smeared.getAsJsonObject().keySet()) {
				if (key != null && !key.isBlank()) {
					names.add(InventoryDecoder.prettyWords(key));
				}
			}
		} else if (smeared != null && smeared.isJsonArray()) {
			for (JsonElement el : smeared.getAsJsonArray()) {
				if (el != null && el.isJsonPrimitive()) {
					try {
						String id = el.getAsString();
						if (id != null && !id.isBlank()) {
							names.add(InventoryDecoder.prettyWords(id));
						}
					} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
					}
				}
			}
		}
		if (smearedCount <= 0 && refillCount <= 0) {
			return HoneyInfo.empty();
		}
		return new HoneyInfo(smearedCount, refillCount, names);
	}

	private static int countEntries(JsonElement el) {
		if (el == null || el.isJsonNull()) {
			return 0;
		}
		if (el.isJsonObject()) {
			return el.getAsJsonObject().size();
		}
		if (el.isJsonArray()) {
			return el.getAsJsonArray().size();
		}
		return 0;
	}

	private static List<CollectionRow> parseCollections(JsonObject collection) {
		if (collection == null) {
			return List.of();
		}
		String[] priority = {
			"FIG_LOG", "MANGROVE_LOG", "LOG", "LOG:1", "LOG:2", "LOG:3", "LOG_2", "LOG_2:1",
			"TENDER_WOOD", "CADUCOUS_STEM", "VINESAP"
		};
		List<CollectionRow> out = new ArrayList<>();
		for (String id : priority) {
			long n = longOf(collection, id);
			if (n > 0L) {
				out.add(new CollectionRow(id, prettyId(id), n));
			}
		}
		for (Map.Entry<String, JsonElement> e : collection.entrySet()) {
			String id = e.getKey();
			if (id == null || id.isBlank()) {
				continue;
			}
			boolean already = false;
			for (CollectionRow row : out) {
				if (row.id().equals(id)) {
					already = true;
					break;
				}
			}
			if (already) {
				continue;
			}
			String upper = id.toUpperCase(Locale.ROOT);
			if (!(upper.contains("LOG") || upper.contains("WOOD") || upper.contains("SAP")
				|| upper.contains("STEM") || upper.contains("VINE") || upper.contains("FIG")
				|| upper.contains("MANGROVE"))) {
				continue;
			}
			long n = longOf(collection, id);
			if (n > 0L) {
				out.add(new CollectionRow(id, prettyId(id), n));
			}
		}
		return out;
	}

	private static List<AttributeShardRow> parseAttributeShards(JsonObject stacks) {
		AttributeShardsData.ensureLoaded();
		List<AttributeShardRow> out = new ArrayList<>();
		for (AttributeShardsData.Def def : AttributeShardsData.all()) {
			int owned = stacks == null ? 0 : (int) longOf(stacks, def.stackId());
			int level = AttributeShardsData.levelFromShards(def.rarity(), owned);
			int maxLevel = AttributeShardsData.maxLevel(def.rarity());
			int forMax = AttributeShardsData.shardsForMax(def.rarity());
			boolean unlocked = level > 0 || owned > 0;
			String name = def.abilityName().isBlank() ? def.displayName() : def.abilityName();
			out.add(new AttributeShardRow(
				def.stackId(), name, def.iconId(), def.rarity(), def.priceId(),
				level, Math.max(1, maxLevel), owned, forMax, unlocked
			));
		}
		out.sort((a, b) -> {
			int u = Boolean.compare(b.unlocked(), a.unlocked());
			return u != 0 ? u : a.name().compareToIgnoreCase(b.name());
		});
		return out;
	}

	private static final String[] ATTR_KEYS = {
		"foraging_wisdom", "fig_collector", "fig_sharpening", "mangrove_collector", "mangrove_sharpening",
		"forest_elemental", "forest_essence", "forest_fishing", "forest_strength", "forest_trap",
		"forest_speed", "berry_mogul", "berry_enjoyer", "spirit_axe"
	};

	private static List<AttrStack> parseAttributes(JsonObject stacks) {
		if (stacks == null) {
			return List.of();
		}
		List<AttrStack> out = new ArrayList<>();
		for (String id : ATTR_KEYS) {
			int n = (int) longOf(stacks, id);
			if (n > 0) {
				out.add(new AttrStack(id, prettyId(id), n));
			}
		}
		return out;
	}

	static List<TreeGift> parseTreeGifts(JsonObject gifts) {
		if (gifts == null) {
			return List.of();
		}
		JsonObject milestones = Leveling.obj(gifts.get("milestone_tier_claimed"));
		List<TreeGift> out = new ArrayList<>();
		for (String id : List.of("FIG", "MANGROVE", "HELIX")) {
			long count = longOf(gifts, id);
			int tier = (int) longOf(milestones, id);
			if (count > 0L || tier > 0) {
				out.add(new TreeGift(id, prettyId(id), count, tier));
			}
		}
		return out;
	}

	private static List<StarlynBest> parseStarlyn(JsonObject pbs) {
		if (pbs == null) {
			return List.of();
		}
		List<StarlynBest> out = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : pbs.entrySet()) {
			long n = longOf(pbs, e.getKey());
			if (n <= 0L) {
				continue;
			}
			String name = "agatha".equalsIgnoreCase(e.getKey()) ? "Agatha" : prettyId(e.getKey());
			out.add(new StarlynBest(e.getKey(), name, n));
		}
		out.sort(Comparator.comparingLong(StarlynBest::amount).reversed());
		return out;
	}

	private static List<HarpSong> parseHarpSongs(JsonObject harp) {
		if (harp == null) {
			return List.of();
		}
		Map<String, HarpSong> map = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : harp.entrySet()) {
			String key = e.getKey();
			if (key == null || !key.startsWith("song_")) {
				continue;
			}
			String rest = key.substring("song_".length());
			String id;
			String kind;
			if (rest.endsWith("_perfect_completions")) {
				id = rest.substring(0, rest.length() - "_perfect_completions".length());
				kind = "perfect";
			} else if (rest.endsWith("_best_completion")) {
				id = rest.substring(0, rest.length() - "_best_completion".length());
				kind = "best";
			} else if (rest.endsWith("_completions")) {
				id = rest.substring(0, rest.length() - "_completions".length());
				kind = "completions";
			} else {
				continue;
			}
			HarpSong prev = map.getOrDefault(id, new HarpSong(id, prettyId(id), 0, 0, 0));
			if ("best".equals(kind)) {
				map.put(id, new HarpSong(id, prev.name(), doubleOf(harp, key), prev.completions(), prev.perfects()));
			} else if ("completions".equals(kind)) {
				map.put(id, new HarpSong(id, prev.name(), prev.best(), (int) longOf(harp, key), prev.perfects()));
			} else {
				map.put(id, new HarpSong(id, prev.name(), prev.best(), prev.completions(), (int) longOf(harp, key)));
			}
		}
		List<HarpSong> out = new ArrayList<>(map.values());
		out.sort(Comparator.comparing(HarpSong::name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private static List<HotfNode> parseHotfNodes(JsonObject nodes) {
		if (nodes == null) {
			return List.of();
		}
		List<HotfNode> out = new ArrayList<>();
		for (ForagingHotfData.PerkDef perk : ForagingHotfData.perks()) {
			int level = (int) longOf(nodes, perk.id());
			if (level <= 0) {
				continue;
			}
			boolean enabled = true;
			String toggleKey = "toggle_" + perk.id();
			if (nodes.has(toggleKey) && nodes.get(toggleKey).isJsonPrimitive()) {
				try {
					enabled = nodes.get(toggleKey).getAsBoolean();
				} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
					enabled = true;
				}
			}
			out.add(new HotfNode(perk.id(), perk.name(), level, perk.maxLevel(), enabled, perk.ability()));
		}
		// Any API nodes missing from layout
		for (Map.Entry<String, JsonElement> e : nodes.entrySet()) {
			String id = e.getKey();
			if (id == null || id.startsWith("toggle_")) {
				continue;
			}
			boolean known = false;
			for (HotfNode n : out) {
				if (n.id().equals(id)) {
					known = true;
					break;
				}
			}
			if (known) {
				continue;
			}
			int level = (int) longOf(nodes, id);
			if (level <= 0) {
				continue;
			}
			boolean enabled = true;
			String toggleKey = "toggle_" + id;
			if (nodes.has(toggleKey) && nodes.get(toggleKey).isJsonPrimitive()) {
				try {
					enabled = nodes.get(toggleKey).getAsBoolean();
				} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
					enabled = true;
				}
			}
			out.add(new HotfNode(id, ForagingHotfData.displayName(id), level, ForagingHotfData.maxLevel(id), enabled, false));
		}
		out.sort(Comparator
			.comparing(HotfNode::ability).reversed()
			.thenComparing(HotfNode::name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private static List<OwnedShard> parseOwnedShards(JsonElement ownedEl) {
		if (ownedEl == null || !ownedEl.isJsonArray()) {
			return List.of();
		}
		List<OwnedShard> out = new ArrayList<>();
		for (JsonElement el : ownedEl.getAsJsonArray()) {
			if (el == null || !el.isJsonObject()) {
				continue;
			}
			JsonObject o = el.getAsJsonObject();
			String type = str(o, "type");
			if (type.isBlank()) {
				continue;
			}
			long amount = longOf(o, "amount_owned");
			long captured = longOf(o, "captured");
			out.add(new OwnedShard(type, prettyId(type), amount, captured));
		}
		out.sort(Comparator.comparingLong(OwnedShard::amount).reversed());
		return out;
	}

	private static Map<String, Long> parseHuntStats(JsonObject stats) {
		Map<String, Long> out = new LinkedHashMap<>();
		if (stats == null) {
			return out;
		}
		// Stable display order for Hunting tab.
		String[] order = {
			"shard_fishing_hunts", "shard_combat_hunts", "shard_salt_hunts",
			"shard_forest_hunts", "shard_trap_hunts"
		};
		for (String key : order) {
			long n = longOf(stats, key);
			if (n > 0L) {
				out.put(key, n);
			}
		}
		for (Map.Entry<String, JsonElement> e : stats.entrySet()) {
			String key = e.getKey();
			if (key == null || !key.startsWith("shard_") || !key.endsWith("_hunts") || out.containsKey(key)) {
				continue;
			}
			long n = longOf(stats, key);
			if (n > 0L) {
				out.put(key, n);
			}
		}
		return out;
	}

	private static int parseGalateaBeacon(JsonObject member, JsonObject foraging, JsonObject core) {
		String[] keys = {
			"beacon_signal_strength", "moonglade_beacon", "moonglade_beacon_level",
			"galatea_beacon", "galatea_beacon_level", "signal_strength", "beacon_level"
		};
		int found = firstPresentInt(core, keys);
		if (found >= 0) {
			return Math.min(10, found);
		}
		found = firstPresentInt(foraging, keys);
		if (found >= 0) {
			return Math.min(10, found);
		}
		JsonObject playerData = Leveling.obj(member == null ? null : member.get("player_data"));
		found = firstPresentInt(playerData, keys);
		if (found >= 0) {
			return Math.min(10, found);
		}
		JsonObject perks = Leveling.obj(playerData == null ? null : playerData.get("perks"));
		found = firstPresentInt(perks, keys);
		if (found >= 0) {
			return Math.min(10, found);
		}
		JsonObject stats = Leveling.obj(member == null ? null : member.get("player_stats"));
		found = firstPresentInt(stats, keys);
		if (found >= 0) {
			return Math.min(10, found);
		}
		return -1;
	}

	private static int firstPresentInt(JsonObject obj, String[] keys) {
		if (obj == null) {
			return -1;
		}
		for (String key : keys) {
			if (obj.has(key) && !obj.get(key).isJsonNull()) {
				return Math.max(0, (int) longOf(obj, key));
			}
		}
		return -1;
	}

	private static boolean objectiveComplete(JsonObject member, String id) {
		if (member == null || id == null || id.isBlank()) {
			return false;
		}
		JsonObject objectives = Leveling.obj(member.get("objectives"));
		JsonObject row = Leveling.obj(objectives == null ? null : objectives.get(id));
		if (row == null) {
			return false;
		}
		String status = str(row, "status");
		return "COMPLETE".equalsIgnoreCase(status);
	}

	private static List<ToolkitSlot> parseToolkit(JsonObject toolkit) {
		if (toolkit == null) {
			return List.of();
		}
		JsonObject inUse = Leveling.obj(toolkit.get("IN_USE"));
		List<ToolkitSlot> out = new ArrayList<>();
		addToolkitGroup(out, toolkit, inUse, "TRAP", 5);
		addToolkitGroup(out, toolkit, inUse, "FISHING_NET", 1);
		addToolkitGroup(out, toolkit, inUse, "LASSO", 1);
		addToolkitGroup(out, toolkit, inUse, "POCKET_BLACK_HOLE", 1);
		addToolkitGroup(out, toolkit, inUse, "HUNTING_TOOLKIT", 1);
		addToolkitGroup(out, toolkit, inUse, "HUNTING_SCYTHE", 1);
		return out;
	}

	private static void addToolkitGroup(
		List<ToolkitSlot> out, JsonObject toolkit, JsonObject inUseRoot, String group, int expected
	) {
		JsonElement el = toolkit.get(group);
		JsonObject useGroup = Leveling.obj(inUseRoot == null ? null : inUseRoot.get(group));
		if (el != null && el.isJsonArray()) {
			JsonArray arr = el.getAsJsonArray();
			int n = Math.max(expected, arr.size());
			for (int i = 0; i < n; i++) {
				InventorySnapshot.Slot slot = null;
				if (i < arr.size()) {
					JsonElement item = arr.get(i);
					if (item != null && !item.isJsonNull()) {
						slot = InventoryDecoder.slotFromItemBytes(item);
					}
				}
				out.add(new ToolkitSlot(group, i, toolkitInUse(useGroup, i), slot));
			}
			return;
		}
		JsonObject map = Leveling.obj(el);
		if (map != null) {
			int maxIdx = expected - 1;
			for (String key : map.keySet()) {
				try {
					// Cap so a bogus huge key can't spin a giant slot loop.
					maxIdx = Math.max(maxIdx, Math.min(63, Integer.parseInt(key)));
				} catch (NumberFormatException ignored) {
				}
			}
			for (int i = 0; i <= maxIdx; i++) {
				InventorySnapshot.Slot slot = null;
				JsonElement item = map.get(String.valueOf(i));
				if (item != null && !item.isJsonNull()) {
					slot = InventoryDecoder.slotFromItemBytes(item);
				}
				out.add(new ToolkitSlot(group, i, toolkitInUse(useGroup, i), slot));
			}
			return;
		}
		for (int i = 0; i < expected; i++) {
			out.add(new ToolkitSlot(group, i, toolkitInUse(useGroup, i), null));
		}
	}

	private static boolean toolkitInUse(JsonObject useGroup, int index) {
		if (useGroup == null || !useGroup.has(String.valueOf(index))) {
			return false;
		}
		JsonElement el = useGroup.get(String.valueOf(index));
		return el != null && el.isJsonPrimitive() && el.getAsBoolean();
	}

	private static boolean boolOf(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return false;
		}
		JsonElement el = obj.get(key);
		if (!el.isJsonPrimitive()) {
			return false;
		}
		try {
			if (el.getAsJsonPrimitive().isBoolean()) {
				return el.getAsBoolean();
			}
			if (el.getAsJsonPrimitive().isNumber()) {
				return el.getAsDouble() != 0.0;
			}
			String s = el.getAsString();
			return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return false;
		}
	}

	private static boolean memberJsonContains(JsonObject member, String needle) {
		if (member == null || needle == null || needle.isBlank()) {
			return false;
		}
		try {
			return member.toString().contains(needle);
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return false;
		}
	}

	/** Decodes bags/inventories for a SkyBlock item id (gzip NBT is not plain in member JSON). */
	private static boolean memberOwnsItem(JsonObject member, String skyblockId) {
		if (member == null || skyblockId == null || skyblockId.isBlank()) {
			return false;
		}
		String want = skyblockId.toUpperCase(Locale.ROOT);
		try {
			Map<String, List<InventoryDecoder.Stack>> cats = InventoryDecoder.parseCategories(member, null);
			for (List<InventoryDecoder.Stack> stacks : cats.values()) {
				if (stacks == null) {
					continue;
				}
				for (InventoryDecoder.Stack stack : stacks) {
					if (stack == null || stack.id() == null) {
						continue;
					}
					String id = stack.id().toUpperCase(Locale.ROOT);
					int semi = id.indexOf(';');
					if (semi > 0) {
						id = id.substring(0, semi);
					}
					if (want.equals(id)) {
						return true;
					}
				}
			}
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return false;
		}
		return false;
	}

	private static Map<String, Long> longMap(JsonObject obj) {
		Map<String, Long> out = new LinkedHashMap<>();
		if (obj == null) {
			return out;
		}
		for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
			long n = longOf(obj, e.getKey());
			if (n > 0L) {
				out.put(e.getKey(), n);
			}
		}
		return out;
	}

	private static List<String> stringList(JsonElement el) {
		if (el == null || !el.isJsonArray()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (JsonElement item : el.getAsJsonArray()) {
			if (item != null && item.isJsonPrimitive()) {
				String s = item.getAsString();
				if (s != null && !s.isBlank()) {
					out.add(s);
				}
			}
		}
		return out;
	}

	private static String prettyId(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		return InventoryDecoder.prettyWords(id.replace(':', '_'));
	}

	private static String str(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return "";
		}
		try {
			return obj.get(key).getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static long longOf(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return 0L;
		}
		try {
			return obj.get(key).getAsLong();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			try {
				return (long) obj.get(key).getAsDouble();
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored2) {
				return 0L;
			}
		}
	}

	private static double doubleOf(JsonObject obj, String key) {
		if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
			return 0.0;
		}
		try {
			return obj.get(key).getAsDouble();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return 0.0;
		}
	}

	public int foragingLevel() { return foragingLevel; }
	public float foragingFill() { return foragingFill; }
	public boolean foragingMaxed() { return foragingMaxed; }
	public String foragingHover() { return foragingHover; }
	public int huntingLevel() { return huntingLevel; }
	public float huntingFill() { return huntingFill; }
	public boolean huntingMaxed() { return huntingMaxed; }
	public String huntingHover() { return huntingHover; }
	public long raceBestMs() { return raceBestMs; }
	public List<CollectionRow> collections() { return collections; }
	public List<AttrStack> attributes() { return attributes; }
	public List<TreeGift> treeGifts() { return treeGifts; }
	public List<StarlynBest> starlynBests() { return starlynBests; }
	public List<WhisperPool> whisperPools() { return whisperPools; }

	public long whispers() {
		return whisperBalance("forest");
	}

	public long whispersSpent() {
		return whisperSpent("forest");
	}

	public long whisperBalance(String id) {
		WhisperPool pool = whisperPool(id);
		return pool == null ? 0L : pool.balance();
	}

	public long whisperSpent(String id) {
		WhisperPool pool = whisperPool(id);
		return pool == null ? 0L : pool.spent();
	}

	public WhisperPool whisperPool(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		for (WhisperPool pool : whisperPools) {
			if (id.equalsIgnoreCase(pool.id())) {
				return pool;
			}
		}
		return null;
	}
	public List<String> fishFamily() { return fishFamily; }
	public int hinaTier() { return hinaTier; }
	public Map<String, Long> hinaProgress() { return hinaProgress; }
	public List<String> hinaCompleted() { return hinaCompleted; }
	public List<String> hinaClaimed() { return hinaClaimed; }
	public boolean harpTalisman() { return harpTalisman; }
	public String harpSelected() { return harpSelected; }
	public List<HarpSong> harpSongs() { return harpSongs; }
	public int galateaBeacon() { return galateaBeacon; }
	public boolean hasGalateaBeacon() { return galateaBeacon >= 0; }
	public double hotfXp() { return hotfXp; }
	public int forestTokensSpent() { return forestTokensSpent; }
	public long hotfLastResetMs() { return hotfLastResetMs; }
	public String dailyEffect() { return dailyEffect; }
	public int dailyEffectChanged() { return dailyEffectChanged; }
	public long dailyTreesCut() { return dailyTreesCut; }
	public long dailyTreesDay() { return dailyTreesDay; }
	public long dailyGifts() { return dailyGifts; }
	public List<String> dailyLogs() { return dailyLogs; }
	public long dailyLogsDay() { return dailyLogsDay; }

	/**
	 * Hypixel {@code daily_*_day} values are UTC days since epoch
	 * ({@code floor(unixMs / 86400000)}). Verified against profile data where
	 * {@code daily_trees_cut_day} matched that formula on the capture date.
	 */
	public static long currentUtcDayId() {
		return System.currentTimeMillis() / 86_400_000L;
	}

	public static boolean isCurrentDailyDay(long dayId) {
		return dayId > 0L && dayId == currentUtcDayId();
	}

	/** Trees cut in the current daily period only; 0 when {@link #dailyTreesDay()} is stale. */
	public long currentDailyTreesCut() {
		if (dailyTreesDay > 0L && !isCurrentDailyDay(dailyTreesDay)) {
			return 0L;
		}
		return dailyTreesCut;
	}

	/**
	 * Tree gifts for the current daily period.
	 * {@code daily_gifts} has no own day field; it shares the foraging daily with
	 * {@code daily_trees_cut_day}. When that day is present and stale, gifts are 0.
	 */
	public long currentDailyGifts() {
		if (dailyTreesDay > 0L && !isCurrentDailyDay(dailyTreesDay)) {
			return 0L;
		}
		return dailyGifts;
	}

	/** Log types cut in the current daily period; empty when {@link #dailyLogsDay()} is stale. */
	public List<String> currentDailyLogs() {
		if (dailyLogsDay > 0L && !isCurrentDailyDay(dailyLogsDay)) {
			return List.of();
		}
		return dailyLogs;
	}
	public List<HotfNode> hotfNodes() { return hotfNodes; }

	/** Perks the API reports that NEU's HOTF layout has no slot for (newer Hypixel perks). */
	public List<HotfNode> unplacedHotfNodes() {
		List<HotfNode> out = new ArrayList<>();
		for (HotfNode node : hotfNodes) {
			if (ForagingHotfData.perk(node.id()) == null) {
				out.add(node);
			}
		}
		return out;
	}
	public Map<String, Integer> centerPages() { return centerPages; }
	public String selectedAbility() { return selectedAbility; }
	public boolean refundAbilityFree() { return refundAbilityFree; }
	public long fusedShards() { return fusedShards; }
	public List<OwnedShard> ownedShards() { return ownedShards; }
	public Map<String, Long> huntStats() { return huntStats; }
	public boolean toolkitUnlocked() { return toolkitUnlocked; }
	public List<ToolkitSlot> toolkitSlots() { return toolkitSlots; }
	public List<AttributeShardRow> attributeShards() { return attributeShards; }
	public DungeonSnapshot.EssenceShop forestShop() { return forestShop; }
	public SafariInfo safari() { return safari; }
	public long uniqueShards() { return uniqueShards; }
	public DungeonSnapshot.EssenceShop safariShop() { return safariShop; }
	public long safariEssence() { return safariShop.balance(); }

	public long peltCount() { return peltCount; }

	public HoneyInfo honey() { return honey; }

	public int hotfNodeLevel(String id) {
		if (id == null || id.isBlank()) {
			return 0;
		}
		for (HotfNode node : hotfNodes) {
			if (id.equals(node.id())) {
				return node.level();
			}
		}
		return 0;
	}

	public boolean hotfNodeEnabled(String id) {
		if (id == null || id.isBlank()) {
			return true;
		}
		for (HotfNode node : hotfNodes) {
			if (id.equals(node.id())) {
				return node.enabled();
			}
		}
		return true;
	}
}
