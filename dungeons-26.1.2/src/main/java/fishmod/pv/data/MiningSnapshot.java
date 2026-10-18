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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MiningSnapshot {
	public record Powder(long available, long spent) {
		public long total() {
			return available + spent;
		}

		public static Powder zero() {
			return new Powder(0L, 0L);
		}
	}

	public record Crystal(String id, String name, String state, long totalPlaced) {
		public boolean placed() {
			return "PLACED".equalsIgnoreCase(state);
		}

		public boolean found() {
			return placed() || "FOUND".equalsIgnoreCase(state);
		}
	}

	public record ForgeProcess(String id, String name, int slot, long startTimeMs) {
	}

	public record CorpseCounts(long lapis, long umber, long tungsten, long vanguard) {
		public long total() {
			return lapis + umber + tungsten + vanguard;
		}

		public static CorpseCounts zero() {
			return new CorpseCounts(0L, 0L, 0L, 0L);
		}
	}

	public record CorpseMilestone(int tier, long needLapis, long needUmber, long needTungsten, long needVanguard) {
		public boolean met(CorpseCounts counts) {
			return counts.lapis() >= needLapis
				&& counts.umber() >= needUmber
				&& counts.tungsten() >= needTungsten
				&& counts.vanguard() >= needVanguard;
		}

		public float fill(CorpseCounts counts, CorpseMilestone previous) {
			long[] need = {
				needLapis - (previous == null ? 0L : previous.needLapis),
				needUmber - (previous == null ? 0L : previous.needUmber),
				needTungsten - (previous == null ? 0L : previous.needTungsten),
				needVanguard - (previous == null ? 0L : previous.needVanguard)
			};
			long[] have = {
				counts.lapis() - (previous == null ? 0L : previous.needLapis),
				counts.umber() - (previous == null ? 0L : previous.needUmber),
				counts.tungsten() - (previous == null ? 0L : previous.needTungsten),
				counts.vanguard() - (previous == null ? 0L : previous.needVanguard)
			};
			float worst = 1f;
			boolean any = false;
			for (int i = 0; i < need.length; i++) {
				if (need[i] <= 0L) {
					continue;
				}
				any = true;
				worst = Math.min(worst, Math.max(0f, Math.min(1f, have[i] / (float) need[i])));
			}
			return any ? worst : (met(counts) ? 1f : 0f);
		}
	}

	public static final List<CorpseMilestone> CORPSE_MILESTONES = List.of(
		new CorpseMilestone(1, 10, 0, 0, 0),
		new CorpseMilestone(2, 25, 1, 1, 0),
		new CorpseMilestone(3, 50, 5, 5, 0),
		new CorpseMilestone(4, 100, 10, 10, 1),
		new CorpseMilestone(5, 250, 25, 25, 5),
		new CorpseMilestone(6, 500, 50, 50, 10),
		new CorpseMilestone(7, 1000, 100, 100, 20)
	);

	private final int hotmLevel;
	private final float hotmFill;
	private final boolean hotmMaxed;
	private final String hotmHover;
	private final double hotmXp;

	private final int miningLevel;
	private final float miningFill;
	private final boolean miningMaxed;
	private final String miningHover;

	private final Powder mithril;
	private final Powder gemstone;
	private final Powder glacite;
	private final int tokensSpent;
	private final String selectedAbilityId;
	private final String selectedAbilityName;
	private final String skyMallEffect;

	private final Map<String, Integer> nodes;
	private final List<Crystal> crystals;
	private final List<ForgeProcess> forge;
	private final List<String> fossilsDonated;
	private final long fossilDust;
	private final long mineshaftsEntered;
	private final CorpseCounts corpses;
	private final int corpseMilestone;
	private final int commissionMilestone;
	private final ColeWeight.Result coleWeight;
	private final DungeonSnapshot.EssenceShop goldShop;
	private final DungeonSnapshot.EssenceShop diamondShop;
	private final DungeonSnapshot.EssenceShop fossilShop;
	private final int goblinKingQuests;
	private final boolean jungleTempleOpen;
	private final boolean precursorTalked;
	private final long miningFiestaOres;
	private final long apiNucleusRuns;

	private MiningSnapshot(
		int hotmLevel, float hotmFill, boolean hotmMaxed, String hotmHover, double hotmXp,
		int miningLevel, float miningFill, boolean miningMaxed, String miningHover,
		Powder mithril, Powder gemstone, Powder glacite, int tokensSpent,
		String selectedAbilityId, String selectedAbilityName, String skyMallEffect,
		Map<String, Integer> nodes, List<Crystal> crystals, List<ForgeProcess> forge,
		List<String> fossilsDonated, long fossilDust, long mineshaftsEntered,
		CorpseCounts corpses, int corpseMilestone, int commissionMilestone,
		ColeWeight.Result coleWeight,
		DungeonSnapshot.EssenceShop goldShop,
		DungeonSnapshot.EssenceShop diamondShop,
		DungeonSnapshot.EssenceShop fossilShop,
		int goblinKingQuests,
		boolean jungleTempleOpen,
		boolean precursorTalked,
		long miningFiestaOres,
		long apiNucleusRuns
	) {
		this.hotmLevel = hotmLevel;
		this.hotmFill = hotmFill;
		this.hotmMaxed = hotmMaxed;
		this.hotmHover = hotmHover;
		this.hotmXp = hotmXp;
		this.miningLevel = miningLevel;
		this.miningFill = Math.max(0f, Math.min(1f, miningFill));
		this.miningMaxed = miningMaxed;
		this.miningHover = miningHover == null ? "" : miningHover;
		this.mithril = mithril;
		this.gemstone = gemstone;
		this.glacite = glacite;
		this.tokensSpent = tokensSpent;
		this.selectedAbilityId = selectedAbilityId;
		this.selectedAbilityName = selectedAbilityName;
		this.skyMallEffect = skyMallEffect;
		this.nodes = nodes;
		this.crystals = crystals;
		this.forge = forge;
		this.fossilsDonated = fossilsDonated;
		this.fossilDust = fossilDust;
		this.mineshaftsEntered = mineshaftsEntered;
		this.corpses = corpses;
		this.corpseMilestone = corpseMilestone;
		this.commissionMilestone = commissionMilestone;
		this.coleWeight = coleWeight == null ? ColeWeight.Result.empty() : coleWeight;
		this.goldShop = goldShop == null ? DungeonSnapshot.EssenceShop.empty("gold", "Gold") : goldShop;
		this.diamondShop = diamondShop == null ? DungeonSnapshot.EssenceShop.empty("diamond", "Diamond") : diamondShop;
		this.fossilShop = fossilShop == null ? DungeonSnapshot.EssenceShop.empty("fossil", "Fossil") : fossilShop;
		this.goblinKingQuests = Math.max(0, goblinKingQuests);
		this.jungleTempleOpen = jungleTempleOpen;
		this.precursorTalked = precursorTalked;
		this.miningFiestaOres = Math.max(0L, miningFiestaOres);
		this.apiNucleusRuns = Math.max(0L, apiNucleusRuns);
	}

	public static MiningSnapshot empty() {
		return new MiningSnapshot(
			0, 0f, false, "", 0,
			0, 0f, false, "",
			Powder.zero(), Powder.zero(), Powder.zero(), 0,
			"", "", "",
			Map.of(), List.of(), List.of(),
			List.of(), 0L, 0L,
			CorpseCounts.zero(), 0, 0,
			ColeWeight.Result.empty(),
			DungeonSnapshot.EssenceShop.empty("gold", "Gold"),
			DungeonSnapshot.EssenceShop.empty("diamond", "Diamond"),
			DungeonSnapshot.EssenceShop.empty("fossil", "Fossil"),
			0, false, false, 0L, 0L
		);
	}

	public MiningSnapshot withColeWeight(ColeWeight.Result coleWeight) {
		return new MiningSnapshot(
			hotmLevel, hotmFill, hotmMaxed, hotmHover, hotmXp,
			miningLevel, miningFill, miningMaxed, miningHover,
			mithril, gemstone, glacite, tokensSpent,
			selectedAbilityId, selectedAbilityName, skyMallEffect,
			nodes, crystals, forge,
			fossilsDonated, fossilDust, mineshaftsEntered,
			corpses, corpseMilestone, commissionMilestone,
			coleWeight,
			goldShop,
			diamondShop,
			fossilShop,
			goblinKingQuests,
			jungleTempleOpen,
			precursorTalked,
			miningFiestaOres,
			apiNucleusRuns
		);
	}

	public static MiningSnapshot fromMember(JsonObject member) {
		if (member == null) {
			return empty();
		}
		RepoData.ensureLoaded();
		MiningHotmData.ensureLoaded();

		JsonObject core = Leveling.obj(member.get("mining_core"));
		JsonObject skillTree = Leveling.obj(member.get("skill_tree"));

		// Modern API stores HOTM XP / tokens / ability under skill_tree; legacy under mining_core.
		JsonObject treeXp = Leveling.obj(skillTree == null ? null : skillTree.get("experience"));
		double xp;
		if (treeXp != null && treeXp.has("mining")) {
			xp = doubleOf(treeXp, "mining");
		} else {
			xp = doubleOf(core, "experience");
		}

		Map<String, Integer> nodes = parseSkillTreeMiningNodes(skillTree);
		if (nodes.isEmpty()) {
			nodes = parseNodes(Leveling.obj(core == null ? null : core.get("nodes")));
		}

		JsonArray hotmTable = RepoData.leveling() != null && RepoData.leveling().has("HOTM")
			? RepoData.leveling().getAsJsonArray("HOTM") : null;
		int cap = Math.max(1, RepoData.skillCap("HOTM"));
		int hotmLevel;
		float hotmFill;
		boolean hotmMaxed;
		String hotmHover;
		if (xp <= 0 && nodes.isEmpty()) {
			hotmLevel = 0;
			hotmFill = 0f;
			hotmMaxed = false;
			hotmHover = "HOTM 0";
		} else {
			// HOTM table is per-level step XP (SkyCrypt HOTM_XP), not cumulative thresholds.
			Leveling.Progress hotm = Leveling.getLevel(hotmTable, (float) xp, cap, false);
			hotmLevel = (int) Math.floor(hotm.level());
			hotmFill = hotm.fill();
			hotmMaxed = hotm.maxed();
			hotmHover = hotm.skillHoverText("HOTM");
		}

		Powder mithril = powder(core, "mithril");
		Powder gemstone = powder(core, "gemstone");
		Powder glacite = powder(core, "glacite");

		// Prefer skill_tree even when 0 so we do not resurrect stale mining_core.tokens_spent.
		Integer treeTokens = SkillTreePresets.selectedTokensSpent(skillTree, "mining");
		int tokensSpent = treeTokens != null ? treeTokens : intOf(core, "tokens_spent");

		JsonObject treeAbility = Leveling.obj(skillTree == null ? null : skillTree.get("selected_ability"));
		String abilityId;
		if (treeAbility != null && treeAbility.has("mining")) {
			abilityId = stringOf(treeAbility, "mining");
		} else {
			abilityId = stringOf(core, "selected_pickaxe_ability");
		}
		String abilityName = abilityId.isBlank() ? "-" : MiningHotmData.displayName(abilityId);
		String skyMall = prettyEffect(stringOf(core, "current_daily_effect"));

		List<Crystal> crystals = parseCrystals(Leveling.obj(core == null ? null : core.get("crystals")));
		List<ForgeProcess> forge = parseForge(Leveling.obj(member.get("forge")));

		JsonObject glaciteData = Leveling.obj(member.get("glacite_player_data"));
		List<String> fossils = stringList(glaciteData == null ? null : glaciteData.get("fossils_donated"));
		long dust = longOf(glaciteData, "fossil_dust");
		// API exposes entered only - no mineshafts_spawned field.
		long shafts = longOf(glaciteData, "mineshafts_entered");
		CorpseCounts corpses = parseCorpses(Leveling.obj(glaciteData == null ? null : glaciteData.get("corpses_looted")));
		int corpseMs = highestCorpseMilestone(corpses);
		int commissionMs = parseCommissionMilestone(member);

		double miningXp = Leveling.readSkillXpDouble(member, "mining");
		int miningCap = Leveling.skillCap("mining", member);
		Leveling.Progress mining = Leveling.getLevel(Leveling.skillTable("mining"), miningXp, miningCap, false);

		JsonObject biomes = Leveling.obj(core == null ? null : core.get("biomes"));
		JsonObject goblin = Leveling.obj(biomes == null ? null : biomes.get("goblin"));
		JsonObject jungle = Leveling.obj(biomes == null ? null : biomes.get("jungle"));
		JsonObject precursor = Leveling.obj(biomes == null ? null : biomes.get("precursor"));
		JsonObject leveling = Leveling.obj(member.get("leveling"));
		JsonObject completions = Leveling.obj(leveling == null ? null : leveling.get("completions"));

		return new MiningSnapshot(
			hotmLevel,
			hotmFill,
			hotmMaxed,
			hotmHover,
			xp,
			(int) Math.floor(mining.level()),
			mining.fill(),
			mining.maxed(),
			mining.skillHoverText("Mining"),
			mithril,
			gemstone,
			glacite,
			tokensSpent,
			abilityId,
			abilityName,
			skyMall,
			nodes,
			crystals,
			forge,
			fossils,
			dust,
			shafts,
			corpses,
			corpseMs,
			commissionMs,
			ColeWeight.Result.empty(),
			EssenceShopData.gold(member),
			EssenceShopData.diamond(member),
			EssenceShopData.fossil(member),
			intOf(goblin, "king_quests_completed"),
			boolOf(jungle, "jungle_temple_open"),
			boolOf(precursor, "talked_to_professor"),
			longOf(leveling, "mining_fiesta_ores_mined"),
			longOf(completions, "NUCLEUS_RUNS")
		);
	}

	public int hotmLevel() {
		return hotmLevel;
	}

	public float hotmFill() {
		return hotmFill;
	}

	public boolean hotmMaxed() {
		return hotmMaxed;
	}

	public String hotmHover() {
		return hotmHover;
	}

	public double hotmXp() {
		return hotmXp;
	}

	public int miningLevel() {
		return miningLevel;
	}

	public float miningFill() {
		return miningFill;
	}

	public boolean miningMaxed() {
		return miningMaxed;
	}

	public String miningHover() {
		return miningHover;
	}

	public Powder mithril() {
		return mithril;
	}

	public Powder gemstone() {
		return gemstone;
	}

	public Powder glacite() {
		return glacite;
	}

	public int tokensSpent() {
		return tokensSpent;
	}

	public String selectedAbilityId() {
		return selectedAbilityId;
	}

	public String selectedAbilityName() {
		return selectedAbilityName;
	}

	public String skyMallEffect() {
		return skyMallEffect;
	}

	public Map<String, Integer> nodes() {
		return nodes;
	}

	public int nodeLevel(String layoutId) {
		if (layoutId == null || layoutId.isBlank()) {
			return 0;
		}
		Integer direct = nodes.get(layoutId);
		if (direct != null) {
			return direct;
		}
		for (Map.Entry<String, Integer> e : nodes.entrySet()) {
			if (layoutId.equals(MiningHotmData.layoutId(e.getKey()))) {
				return e.getValue();
			}
		}
		return 0;
	}

	public List<Crystal> crystals() {
		return crystals;
	}

	public DungeonSnapshot.EssenceShop goldShop() {
		return goldShop;
	}

	public DungeonSnapshot.EssenceShop diamondShop() {
		return diamondShop;
	}

	public DungeonSnapshot.EssenceShop fossilShop() {
		return fossilShop;
	}

	public int goblinKingQuests() {
		return goblinKingQuests;
	}

	public boolean jungleTempleOpen() {
		return jungleTempleOpen;
	}

	public boolean precursorTalked() {
		return precursorTalked;
	}

	public long miningFiestaOres() {
		return miningFiestaOres;
	}

	public long apiNucleusRuns() {
		return apiNucleusRuns;
	}

	public List<ForgeProcess> forge() {
		return forge;
	}

	/** Epoch ms the process finishes (recipe time with Quick Forge), or 0 when the duration is unknown. */
	public long forgeEndMs(ForgeProcess process) {
		if (process == null || process.startTimeMs() <= 0L) {
			return 0L;
		}
		long duration = ForgeTimes.durationMs(process.id(), nodeLevel("forge_time"));
		return duration <= 0L ? 0L : process.startTimeMs() + duration;
	}

	public List<String> fossilsDonated() {
		return fossilsDonated;
	}

	public long fossilDust() {
		return fossilDust;
	}

	public long mineshaftsEntered() {
		return mineshaftsEntered;
	}

	public CorpseCounts corpses() {
		return corpses;
	}

	public int corpseMilestone() {
		return corpseMilestone;
	}

	public int commissionMilestone() {
		return commissionMilestone;
	}

	public ColeWeight.Result coleWeight() {
		return coleWeight;
	}

	/**
	 * Nucleus completions: minimum {@code total_placed} among the five Crystal Hollows
	 * gemstone crystals (SkyCrypt-style). Jade alone can over-count when other gems lag.
	 */
	public long nucleusRuns() {
		long min = Long.MAX_VALUE;
		boolean any = false;
		for (Crystal c : crystals) {
			String id = c.id() == null ? "" : c.id().toLowerCase(Locale.ROOT);
			if (!(id.contains("jade") || id.contains("amber") || id.contains("amethyst")
				|| id.contains("sapphire") || id.contains("topaz"))) {
				continue;
			}
			long placed = Math.max(0L, c.totalPlaced());
			if (placed <= 0L) {
				continue;
			}
			any = true;
			min = Math.min(min, placed);
		}
		return any ? min : 0L;
	}

	public CorpseMilestone nextCorpseMilestone() {
		if (corpseMilestone >= CORPSE_MILESTONES.size()) {
			return null;
		}
		return CORPSE_MILESTONES.get(corpseMilestone);
	}

	public CorpseMilestone currentCorpseMilestoneDef() {
		if (corpseMilestone <= 0) {
			return null;
		}
		return CORPSE_MILESTONES.get(corpseMilestone - 1);
	}

	private static int highestCorpseMilestone(CorpseCounts counts) {
		int best = 0;
		for (CorpseMilestone m : CORPSE_MILESTONES) {
			if (m.met(counts)) {
				best = m.tier();
			} else {
				break;
			}
		}
		return best;
	}

	// Despite the names, powder_<kind> is lifetime earned and powder_<kind>_total is the spendable
	// balance (lifetime - spent). Checked against the in-game HOTM menu.
	private static Powder powder(JsonObject core, String kind) {
		long lifetime = Math.max(0L, longOf(core, "powder_" + kind));
		long spent = Math.max(0L, longOf(core, "powder_spent_" + kind));
		return new Powder(Math.max(0L, lifetime - spent), spent);
	}

	/** Selected HOTM preset from {@code skill_tree.nodes} as layout-id → level. */
	private static Map<String, Integer> parseSkillTreeMiningNodes(JsonObject skillTree) {
		return parseNodes(SkillTreePresets.selectedNodes(skillTree, "mining"));
	}

	private static Map<String, Integer> parseNodes(JsonObject nodes) {
		Map<String, Integer> out = new LinkedHashMap<>();
		mergeNodes(out, nodes);
		return Map.copyOf(out);
	}

	private static void mergeNodes(Map<String, Integer> out, JsonObject nodes) {
		if (nodes == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> e : nodes.entrySet()) {
			String id = e.getKey();
			if (id == null || id.startsWith("toggle_")) {
				continue;
			}
			JsonElement v = e.getValue();
			if (v == null) {
				continue;
			}
			int level = 0;
			if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
				level = v.getAsBoolean() ? 1 : 0;
			} else {
				Float n = Leveling.num(v);
				if (n != null) {
					level = Math.round(n);
				}
			}
			if (level <= 0) {
				continue;
			}
			String layout = MiningHotmData.layoutId(id);
			int prev = out.getOrDefault(layout, 0);
			if (level > prev) {
				out.put(layout, level);
			}
		}
	}

	private static List<Crystal> parseCrystals(JsonObject crystals) {
		if (crystals == null) {
			return List.of();
		}
		List<Crystal> out = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : crystals.entrySet()) {
			JsonObject o = Leveling.obj(e.getValue());
			String state = o == null ? "NOT_FOUND" : stringOf(o, "state");
			if (state.isBlank()) {
				state = "NOT_FOUND";
			}
			String id = e.getKey();
			String name = titleCrystal(id);
			long placed = longOf(o, "total_placed");
			out.add(new Crystal(id, name, state.toUpperCase(Locale.ROOT), placed));
		}
		out.sort(Comparator.comparing(Crystal::name));
		return List.copyOf(out);
	}

	private static List<ForgeProcess> parseForge(JsonObject forge) {
		if (forge == null) {
			return List.of();
		}
		JsonObject processes = Leveling.obj(forge.get("forge_processes"));
		JsonObject forge1 = processes == null ? null : Leveling.obj(processes.get("forge_1"));
		if (forge1 == null) {
			return List.of();
		}
		List<ForgeProcess> out = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : forge1.entrySet()) {
			JsonObject o = Leveling.obj(e.getValue());
			if (o == null) {
				continue;
			}
			String id = stringOf(o, "id");
			if (id.isBlank()) {
				id = e.getKey();
			}
			int slot = intOf(o, "slot");
			long start = longOf(o, "startTime");
			out.add(new ForgeProcess(id, titleId(id), slot, start));
		}
		out.sort(Comparator.comparingInt(ForgeProcess::slot));
		return List.copyOf(out);
	}

	private static CorpseCounts parseCorpses(JsonObject corpses) {
		if (corpses == null) {
			return CorpseCounts.zero();
		}
		return new CorpseCounts(
			longOf(corpses, "lapis"),
			longOf(corpses, "umber"),
			longOf(corpses, "tungsten"),
			longOf(corpses, "vanguard")
		);
	}

	private static int parseCommissionMilestone(JsonObject member) {
		JsonObject objectives = Leveling.obj(member.get("objectives"));
		JsonElement tutorial = objectives == null ? null : objectives.get("tutorial");
		if (tutorial == null || !tutorial.isJsonArray()) {
			return 0;
		}
		int best = 0;
		String prefix = "commission_milestone_reward_mining_xp_tier_";
		for (JsonElement el : tutorial.getAsJsonArray()) {
			if (!el.isJsonPrimitive()) {
				continue;
			}
			String key;
			try {
				key = el.getAsString();
			} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				continue;
			}
			if (!key.startsWith(prefix)) {
				continue;
			}
			try {
				best = Math.max(best, Integer.parseInt(key.substring(prefix.length())));
			} catch (NumberFormatException ignored) {
			}
		}
		return best;
	}

	private static List<String> stringList(JsonElement el) {
		if (el == null || !el.isJsonArray()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (JsonElement item : el.getAsJsonArray()) {
			if (item != null && item.isJsonPrimitive()) {
				try {
					out.add(item.getAsString());
				} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				}
			}
		}
		return List.copyOf(out);
	}

	private static String prettyEffect(String raw) {
		if (raw == null || raw.isBlank()) {
			return "-";
		}
		return titleId(raw);
	}

	private static String titleCrystal(String id) {
		String base = id == null ? "" : id;
		if (base.endsWith("_crystal")) {
			base = base.substring(0, base.length() - "_crystal".length());
		}
		return titleId(base);
	}

	private static String titleId(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String[] parts = id.replace('-', '_').split("_");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				sb.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return sb.toString();
	}

	private static long longOf(JsonObject obj, String key) {
		if (obj == null) {
			return 0L;
		}
		Float n = Leveling.num(obj.get(key));
		return n == null ? 0L : Math.round(n.doubleValue());
	}

	private static double doubleOf(JsonObject obj, String key) {
		if (obj == null) {
			return 0;
		}
		Float n = Leveling.num(obj.get(key));
		return n == null ? 0 : n.doubleValue();
	}

	private static int intOf(JsonObject obj, String key) {
		return (int) longOf(obj, key);
	}

	private static String stringOf(JsonObject obj, String key) {
		if (obj == null) {
			return "";
		}
		JsonElement el = obj.get(key);
		if (el == null || !el.isJsonPrimitive()) {
			return "";
		}
		try {
			return el.getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
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
}
