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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GardenSnapshot {
	public static final List<String> CROP_ORDER = List.of(
		"WHEAT", "CARROT_ITEM", "POTATO_ITEM", "PUMPKIN", "SUGAR_CANE", "MELON", "CACTUS",
		"INK_SACK:3", "MUSHROOM_COLLECTION", "NETHER_STALK", "DOUBLE_PLANT", "MOONFLOWER", "WILD_ROSE"
	);

	public static final int COMPOSTER_UPGRADE_MAX = 25;
	private static final int COMPOSTER_ATTRIBUTE_MAX = 10;
	private static final List<String> COMPOSTER_UPGRADE_ORDER = List.of(
		"speed", "multi_drop", "fuel_cap", "organic_matter_cap", "cost_reduction"
	);

	public record CropRow(
		String id, String name, String iconId, long collected, int milestone,
		float milestoneFill, boolean milestoneMaxed, int upgradeLevel, String hover
	) {
		public GardenCosts.Progress upgradeCopper() {
			return GardenCosts.cropUpgradeCopper(upgradeLevel);
		}
	}

	public enum VisitorState { ACCEPTED, VISITED, NEVER }

	public record VisitorRow(
		String id, String name, String npcItemId, long visits, long completed, long rejected
	) {
		public boolean visited() {
			return visits > 0L;
		}

		public VisitorState state() {
			if (completed > 0L) {
				return VisitorState.ACCEPTED;
			}
			return visits > 0L ? VisitorState.VISITED : VisitorState.NEVER;
		}
	}

	public record ActiveVisitor(String id, String name, String status, String detail) {
	}

	public record ComposterUpgrade(String id, String name, String iconId, int level, int maxLevel) {
		public float fill() {
			return maxLevel <= 0 ? 0f : Math.min(1f, level / (float) maxLevel);
		}

		public boolean maxed() {
			return level >= maxLevel;
		}

		public int missing() {
			return Math.max(0, maxLevel - level);
		}
	}

	public record Composter(
		long organicMatter, long fuelUnits, long compostUnits, long compostItems,
		List<ComposterUpgrade> upgrades
	) {
		public int totalUpgradeLevels() {
			int sum = 0;
			for (ComposterUpgrade u : upgrades) {
				sum += u.level();
			}
			return sum;
		}
	}

	public record MedalCounts(int bronze, int silver, int gold) {
		public int total() {
			return bronze + silver + gold;
		}
	}

	public record BracketCount(String bracket, int crops, List<String> cropIds) {
		public BracketCount(String bracket, int crops) {
			this(bracket, crops, List.of());
		}

		public BracketCount {
			cropIds = List.copyOf(cropIds == null ? List.of() : cropIds);
		}
	}

	public record CropMedal(String id, String name, String iconId, int filled) {
		// Highest unique bracket: 0 none … 5 diamond.
		public CropMedal {
			filled = Math.max(0, Math.min(5, filled));
			id = id == null ? "" : id;
			name = name == null ? "" : name;
			iconId = iconId == null ? "" : iconId;
		}
	}

	public record PersonalBest(String id, String name, long amount) {
	}

	/** {@code position} is Hypixel's 0-based claimed position; -1 when the contest was never claimed. */
	public record ContestEntry(
		String crop, String cropName, String iconId, long collected, String medal,
		int position, int participants, long timestampSeconds
	) {
		/** 1-based placement, or 0 when none was recorded. */
		public int placement() {
			return position >= 0 ? position + 1 : 0;
		}
	}

	/** Best recorded 1-based placement (0 = none); unplaced contests were never claimed, so their rank is unknown. */
	public record CropPlacement(int best, int participants, int contests, int unplaced) {
	}

	public record ChipEntry(String id, String name, String iconId, int level) {
		public GardenCosts.Progress sowdust() {
			return GardenCosts.chipSowdust(level);
		}
	}

	public record PlotCell(GardenCosts.Plot plot, boolean unlocked, GardenCosts.Cost unlockCost) {
	}

	public record GreenhouseRow(String id, String name, String iconId, boolean analyzed) {
		public boolean discoveredOnly() {
			return !analyzed;
		}
	}

	public record GreenhouseMeta(
		int slotsUnlocked, int yieldLevel, int plotLimitLevel, int growthSpeedLevel, long lastGrowthStageMs
	) {
		public static GreenhouseMeta empty() {
			return new GreenhouseMeta(0, 0, 0, 0, 0L);
		}
	}

	public record FarmingWeightInfo(
		boolean loaded, boolean loading, String error, double total, double bonus,
		Map<String, Double> cropWeight, Map<String, Double> bonusWeight
	) {
		public static FarmingWeightInfo empty() {
			return new FarmingWeightInfo(false, false, "", 0, 0, Map.of(), Map.of());
		}

		public static FarmingWeightInfo pending() {
			return new FarmingWeightInfo(false, true, "", 0, 0, Map.of(), Map.of());
		}

		public static FarmingWeightInfo failed(String error) {
			return new FarmingWeightInfo(false, false, error == null ? "Weight unavailable" : error, 0, 0, Map.of(), Map.of());
		}

		/** Display total: Elite totalWeight, or crop+bonus if total looks incomplete. */
		public double displayTotal() {
			double cropSum = 0;
			for (double v : cropWeight.values()) {
				cropSum += v;
			}
			double parts = cropSum + bonus;
			if (total + 0.05 < parts) {
				return parts;
			}
			return total;
		}

		public static FarmingWeightInfo fromElite(JsonObject obj) {
			if (obj == null) {
				return failed("Weight unavailable");
			}
			double total = 0;
			JsonElement totalEl = obj.get("totalWeight");
			if (totalEl != null && totalEl.isJsonPrimitive()) {
				try {
					total = totalEl.getAsDouble();
				} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
				}
			}
			Map<String, Double> crops = new LinkedHashMap<>();
			JsonObject cropWeight = Leveling.obj(obj.get("cropWeight"));
			if (cropWeight != null) {
				for (Map.Entry<String, JsonElement> e : cropWeight.entrySet()) {
					Float n = Leveling.num(e.getValue());
					if (n != null) {
						crops.put(e.getKey(), n.doubleValue());
					}
				}
			}
			Map<String, Double> bonuses = new LinkedHashMap<>();
			double bonus = 0;
			JsonObject bonusWeight = Leveling.obj(obj.get("bonusWeight"));
			if (bonusWeight != null) {
				for (Map.Entry<String, JsonElement> e : bonusWeight.entrySet()) {
					Float n = Leveling.num(e.getValue());
					if (n != null) {
						bonuses.put(e.getKey(), n.doubleValue());
						bonus += n;
					}
				}
			}
			return new FarmingWeightInfo(true, false, "", total, bonus, Map.copyOf(crops), Map.copyOf(bonuses));
		}
	}

	public record FarmingToolkitSlot(String cropId, String label, int index, InventorySnapshot.Slot item) {
		public FarmingToolkitSlot {
			cropId = cropId == null ? "" : cropId;
			label = label == null || label.isBlank()
				? (cropId.isBlank() ? "?" : GardenData.prettyCrop(cropId))
				: label;
			index = Math.max(0, index);
		}
	}

	private final boolean islandLoaded;
	private final boolean islandLoading;
	private final String islandError;
	private final boolean contestsLoaded;
	private final boolean contestsLoading;
	private final String contestsError;

	private final int gardenLevel;
	private final float gardenFill;
	private final boolean gardenMaxed;
	private final String gardenHover;
	private final double gardenXp;

	private final int farmingLevel;
	private final float farmingFill;
	private final boolean farmingMaxed;
	private final String farmingHover;

	private final long copper;
	private final long larvaConsumed;
	private final List<String> unlockedPlotIds;
	private final int plotsMax;

	private final long visitorsCompleted;
	private final long uniqueVisitors;
	private final long totalVisits;
	private final long totalRejected;
	private final int visitorMilestone;
	private final float visitorMilestoneFill;
	private final boolean visitorMilestoneMaxed;
	private final String visitorMilestoneHover;
	private final int uniqueVisitorMilestone;
	private final float uniqueVisitorMilestoneFill;
	private final boolean uniqueVisitorMilestoneMaxed;
	private final String uniqueVisitorMilestoneHover;

	private final List<CropRow> crops;
	private final List<VisitorRow> visitors;
	private final List<ActiveVisitor> activeVisitors;
	private final Composter composter;

	private final MedalCounts medals;
	private final List<BracketCount> uniqueBrackets;
	private final List<PersonalBest> personalBests;
	private final Map<String, Integer> perks;
	private final List<ContestEntry> contests;
	private volatile Map<String, CropPlacement> placements;
	private final List<ChipEntry> gardenChips;
	private final List<GreenhouseRow> greenhouse;
	private final GreenhouseMeta greenhouseMeta;
	private final FarmingWeightInfo farmingWeight;
	private final boolean farmingToolkitUnlocked;
	private final List<FarmingToolkitSlot> farmingToolkitSlots;

	public GardenSnapshot(
		boolean islandLoaded, boolean islandLoading, String islandError,
		boolean contestsLoaded, boolean contestsLoading, String contestsError,
		int gardenLevel, float gardenFill, boolean gardenMaxed, String gardenHover, double gardenXp,
		int farmingLevel, float farmingFill, boolean farmingMaxed, String farmingHover,
		long copper, long larvaConsumed, List<String> unlockedPlotIds, int plotsMax,
		long visitorsCompleted, long uniqueVisitors, long totalVisits, long totalRejected,
		int visitorMilestone, float visitorMilestoneFill, boolean visitorMilestoneMaxed, String visitorMilestoneHover,
		int uniqueVisitorMilestone, float uniqueVisitorMilestoneFill, boolean uniqueVisitorMilestoneMaxed, String uniqueVisitorMilestoneHover,
		List<CropRow> crops, List<VisitorRow> visitors, List<ActiveVisitor> activeVisitors, Composter composter,
		MedalCounts medals, List<BracketCount> uniqueBrackets, List<PersonalBest> personalBests,
		Map<String, Integer> perks, List<ContestEntry> contests, List<ChipEntry> gardenChips,
		List<GreenhouseRow> greenhouse, GreenhouseMeta greenhouseMeta, FarmingWeightInfo farmingWeight,
		boolean farmingToolkitUnlocked, List<FarmingToolkitSlot> farmingToolkitSlots
	) {
		this.islandLoaded = islandLoaded;
		this.islandLoading = islandLoading;
		this.islandError = islandError == null ? "" : islandError;
		this.contestsLoaded = contestsLoaded;
		this.contestsLoading = contestsLoading;
		this.contestsError = contestsError == null ? "" : contestsError;
		this.gardenLevel = gardenLevel;
		this.gardenFill = Math.max(0f, Math.min(1f, gardenFill));
		this.gardenMaxed = gardenMaxed;
		this.gardenHover = gardenHover == null ? "" : gardenHover;
		this.gardenXp = gardenXp;
		this.farmingLevel = farmingLevel;
		this.farmingFill = Math.max(0f, Math.min(1f, farmingFill));
		this.farmingMaxed = farmingMaxed;
		this.farmingHover = farmingHover == null ? "" : farmingHover;
		this.copper = Math.max(0L, copper);
		this.larvaConsumed = Math.max(0L, larvaConsumed);
		this.unlockedPlotIds = List.copyOf(unlockedPlotIds == null ? List.of() : unlockedPlotIds);
		this.plotsMax = Math.max(1, plotsMax);
		this.visitorsCompleted = Math.max(0L, visitorsCompleted);
		this.uniqueVisitors = Math.max(0L, uniqueVisitors);
		this.totalVisits = Math.max(0L, totalVisits);
		this.totalRejected = Math.max(0L, totalRejected);
		this.visitorMilestone = visitorMilestone;
		this.visitorMilestoneFill = Math.max(0f, Math.min(1f, visitorMilestoneFill));
		this.visitorMilestoneMaxed = visitorMilestoneMaxed;
		this.visitorMilestoneHover = visitorMilestoneHover == null ? "" : visitorMilestoneHover;
		this.uniqueVisitorMilestone = uniqueVisitorMilestone;
		this.uniqueVisitorMilestoneFill = Math.max(0f, Math.min(1f, uniqueVisitorMilestoneFill));
		this.uniqueVisitorMilestoneMaxed = uniqueVisitorMilestoneMaxed;
		this.uniqueVisitorMilestoneHover = uniqueVisitorMilestoneHover == null ? "" : uniqueVisitorMilestoneHover;
		this.crops = List.copyOf(crops == null ? List.of() : crops);
		this.visitors = List.copyOf(visitors == null ? List.of() : visitors);
		this.activeVisitors = List.copyOf(activeVisitors == null ? List.of() : activeVisitors);
		this.composter = composter == null ? emptyComposter() : composter;
		this.medals = medals == null ? new MedalCounts(0, 0, 0) : medals;
		this.uniqueBrackets = List.copyOf(uniqueBrackets == null ? List.of() : uniqueBrackets);
		this.personalBests = List.copyOf(personalBests == null ? List.of() : personalBests);
		this.perks = Map.copyOf(perks == null ? Map.of() : perks);
		this.contests = List.copyOf(contests == null ? List.of() : contests);
		this.gardenChips = List.copyOf(gardenChips == null ? List.of() : gardenChips);
		this.greenhouse = List.copyOf(greenhouse == null ? List.of() : greenhouse);
		this.greenhouseMeta = greenhouseMeta == null ? GreenhouseMeta.empty() : greenhouseMeta;
		this.farmingWeight = farmingWeight == null ? FarmingWeightInfo.empty() : farmingWeight;
		this.farmingToolkitUnlocked = farmingToolkitUnlocked;
		this.farmingToolkitSlots = List.copyOf(farmingToolkitSlots == null ? List.of() : farmingToolkitSlots);
	}

	public static GardenSnapshot empty() {
		GardenData.ensureLoaded();
		return new GardenSnapshot(
			false, false, "", false, false, "",
			0, 0f, false, "", 0,
			0, 0f, false, "",
			0, 0, List.of(), GardenData.maxPlots(),
			0, 0, 0, 0, 0, 0f, false, "", 0, 0f, false, "",
			List.of(), List.of(), List.of(), null,
			new MedalCounts(0, 0, 0), List.of(), List.of(), Map.of(), List.of(), List.of(),
			List.of(), GreenhouseMeta.empty(), FarmingWeightInfo.empty(),
			false, List.of()
		);
	}

	public static GardenSnapshot fromMember(JsonObject member) {
		GardenData.ensureLoaded();
		if (member == null) {
			return empty();
		}
		double farmingXp = Leveling.readSkillXpDouble(member, "farming");
		int farmingCap = Leveling.skillCap("farming", member);
		Leveling.Progress farming = Leveling.getLevel(Leveling.skillTable("farming"), farmingXp, farmingCap, false);

		JsonObject gpd = Leveling.obj(member.get("garden_player_data"));
		long copper = longOf(gpd, "copper");
		long larva = longOf(gpd, "larva_consumed");
		List<String> discovered = rawStringList(gpd == null ? null : gpd.get("discovered_greenhouse_crops"));
		List<String> analyzed = rawStringList(gpd == null ? null : gpd.get("analyzed_greenhouse_crops"));
		List<GreenhouseRow> greenhouse = buildGreenhouse(discovered, analyzed);

		JsonObject playerData = Leveling.obj(member.get("player_data"));
		List<ChipEntry> chips = parseChips(Leveling.obj(playerData == null ? null : playerData.get("garden_chips")));

		JsonObject jacobs = Leveling.obj(member.get("jacobs_contest"));
		MedalCounts medals = parseMedals(jacobs);
		List<BracketCount> brackets = parseBrackets(jacobs);
		List<PersonalBest> pbs = parsePersonalBests(jacobs);
		Map<String, Integer> perks = parsePerks(jacobs);
		List<ContestEntry> contests = parseHypixelContests(jacobs);
		JsonObject toolkit = Leveling.obj(gpd == null ? null : gpd.get("farming_toolkit"));
		boolean toolkitUnlocked = boolOf(toolkit, "IS_UNLOCKED");
		List<FarmingToolkitSlot> toolkitSlots = parseFarmingToolkit(toolkit);

		return new GardenSnapshot(
			false, false, "", !contests.isEmpty(), false, "",
			0, 0f, false, "Open Garden tab to load island", 0,
			(int) Math.floor(farming.level()), farming.fill(), farming.maxed(), farming.skillHoverText("Farming"),
			copper, larva, List.of(), GardenData.maxPlots(),
			0, 0, 0, 0, 0, 0f, false, "", 0, 0f, false, "",
			List.of(), List.of(), List.of(), null,
			medals, brackets, pbs, perks, contests, chips,
			greenhouse, GreenhouseMeta.empty(), FarmingWeightInfo.empty(),
			toolkitUnlocked, toolkitSlots
		);
	}

	public GardenSnapshot withIslandLoading() {
		return copy(false, true, "", contestsLoaded, contestsLoading, contestsError);
	}

	public GardenSnapshot withIslandError(String error) {
		return copy(false, false, error == null ? "Couldn't load garden" : error, contestsLoaded, contestsLoading, contestsError);
	}

	public GardenSnapshot withContestsLoading() {
		return copy(islandLoaded, islandLoading, islandError, false, true, "");
	}

	public GardenSnapshot withContestsError(String error) {
		return copy(islandLoaded, islandLoading, islandError, false, false, error == null ? "Contests unavailable" : error);
	}

	public GardenSnapshot withContestsReady() {
		return copy(islandLoaded, islandLoading, islandError, true, false, "");
	}

	public GardenSnapshot withEliteContests(JsonArray array) {
		List<ContestEntry> list = parseEliteContests(array);
		// Prefer Elite history when present; keep Hypixel contests if Elite returned nothing useful.
		if (list.isEmpty() && !this.contests.isEmpty()) {
			return withContestsReady();
		}
		return new GardenSnapshot(
			islandLoaded, islandLoading, islandError, true, false, "",
			gardenLevel, gardenFill, gardenMaxed, gardenHover, gardenXp,
			farmingLevel, farmingFill, farmingMaxed, farmingHover,
			copper, larvaConsumed, unlockedPlotIds, plotsMax,
			visitorsCompleted, uniqueVisitors, totalVisits, totalRejected,
			visitorMilestone, visitorMilestoneFill, visitorMilestoneMaxed, visitorMilestoneHover,
			uniqueVisitorMilestone, uniqueVisitorMilestoneFill, uniqueVisitorMilestoneMaxed, uniqueVisitorMilestoneHover,
			crops, visitors, activeVisitors, composter,
			medals, uniqueBrackets, personalBests, perks, list, gardenChips,
			greenhouse, greenhouseMeta, farmingWeight,
			farmingToolkitUnlocked, farmingToolkitSlots
		);
	}

	public GardenSnapshot withFarmingWeight(FarmingWeightInfo info) {
		return new GardenSnapshot(
			islandLoaded, islandLoading, islandError, contestsLoaded, contestsLoading, contestsError,
			gardenLevel, gardenFill, gardenMaxed, gardenHover, gardenXp,
			farmingLevel, farmingFill, farmingMaxed, farmingHover,
			copper, larvaConsumed, unlockedPlotIds, plotsMax,
			visitorsCompleted, uniqueVisitors, totalVisits, totalRejected,
			visitorMilestone, visitorMilestoneFill, visitorMilestoneMaxed, visitorMilestoneHover,
			uniqueVisitorMilestone, uniqueVisitorMilestoneFill, uniqueVisitorMilestoneMaxed, uniqueVisitorMilestoneHover,
			crops, visitors, activeVisitors, composter,
			medals, uniqueBrackets, personalBests, perks, contests, gardenChips,
			greenhouse, greenhouseMeta, info == null ? FarmingWeightInfo.empty() : info,
			farmingToolkitUnlocked, farmingToolkitSlots
		);
	}

	public GardenSnapshot withIsland(JsonObject garden) {
		GardenData.ensureLoaded();
		if (garden == null) {
			return withIslandError("No garden data");
		}
		double xp = doubleOf(garden, "garden_experience");
		GardenData.LevelProgress gl = GardenData.gardenLevel(xp);
		String gardenHover = gardenHover(gl, GardenData.gardenOverflow(xp), xp);

		List<CropRow> cropRows = buildCrops(
			Leveling.obj(garden.get("resources_collected")),
			Leveling.obj(garden.get("crop_upgrade_levels"))
		);

		List<String> plots = new ArrayList<>(new LinkedHashSet<>(rawStringList(garden.get("unlocked_plots_ids"))));

		JsonObject commission = Leveling.obj(garden.get("commission_data"));
		long totalCompleted = longOf(commission, "total_completed");
		long unique = longOf(commission, "unique_npcs_served");
		List<VisitorRow> visitorRows = parseVisitors(commission);
		long visitsSum = 0L;
		long rejectedSum = 0L;
		for (VisitorRow row : visitorRows) {
			visitsSum += row.visits();
			rejectedSum += row.rejected();
		}
		long apiVisits = longOf(commission, "total_visits");
		long apiRejected = longOf(commission, "total_rejected");
		long totalVisits = apiVisits > 0L ? apiVisits : visitsSum;
		long totalRejected = apiRejected > 0L ? apiRejected : rejectedSum;

		GardenData.LevelProgress vl = GardenData.visitorMilestone(totalCompleted);
		String visitorHover = vl.maxed()
			? "Offers accepted milestone " + vl.level() + " - MAX"
			: "Offers accepted milestone " + vl.level() + " - "
				+ FormatUtil.commas(vl.intoLevel()) + " / " + FormatUtil.commas(vl.needForNext());

		GardenData.LevelProgress uvl = GardenData.uniqueVisitorMilestone(unique);
		String uniqueHover = uvl.maxed()
			? "Unique visitors milestone " + uvl.level() + " - MAX"
			: "Unique visitors milestone " + uvl.level() + " - "
				+ FormatUtil.commas(uvl.intoLevel()) + " / " + FormatUtil.commas(uvl.needForNext());

		GreenhouseMeta ghMeta = parseGreenhouseMeta(garden);

		return new GardenSnapshot(
			true, false, "", contestsLoaded, contestsLoading, contestsError,
			gl.level(), gl.fill(), gl.maxed(), gardenHover, xp,
			farmingLevel, farmingFill, farmingMaxed, farmingHover,
			copper, larvaConsumed, plots, GardenData.maxPlots(),
			totalCompleted, unique, totalVisits, totalRejected,
			vl.level(), vl.fill(), vl.maxed(), visitorHover,
			uvl.level(), uvl.fill(), uvl.maxed(), uniqueHover,
			cropRows, visitorRows, parseActive(garden.get("active_commissions")),
			parseComposter(Leveling.obj(garden.get("composter_data"))),
			medals, uniqueBrackets, personalBests, perks, contests, gardenChips,
			greenhouse, ghMeta, farmingWeight,
			farmingToolkitUnlocked, farmingToolkitSlots
		);
	}

	private GardenSnapshot copy(
		boolean islLoaded, boolean islLoading, String islError,
		boolean cLoaded, boolean cLoading, String cError
	) {
		return new GardenSnapshot(
			islLoaded, islLoading, islError, cLoaded, cLoading, cError,
			gardenLevel, gardenFill, gardenMaxed, gardenHover, gardenXp,
			farmingLevel, farmingFill, farmingMaxed, farmingHover,
			copper, larvaConsumed, unlockedPlotIds, plotsMax,
			visitorsCompleted, uniqueVisitors, totalVisits, totalRejected,
			visitorMilestone, visitorMilestoneFill, visitorMilestoneMaxed, visitorMilestoneHover,
			uniqueVisitorMilestone, uniqueVisitorMilestoneFill, uniqueVisitorMilestoneMaxed, uniqueVisitorMilestoneHover,
			crops, visitors, activeVisitors, composter,
			medals, uniqueBrackets, personalBests, perks, contests, gardenChips,
			greenhouse, greenhouseMeta, farmingWeight,
			farmingToolkitUnlocked, farmingToolkitSlots
		);
	}

	private static List<CropRow> buildCrops(JsonObject resources, JsonObject upgrades) {
		Map<String, Long> amounts = new LinkedHashMap<>();
		if (resources != null) {
			for (Map.Entry<String, JsonElement> e : resources.entrySet()) {
				long amount = Leveling.longAmount(e.getValue());
				if (amount > 0L) {
					amounts.put(e.getKey(), amount);
				}
			}
		}
		List<CropRow> rows = new ArrayList<>();
		Set<String> used = new LinkedHashSet<>();
		for (String id : CROP_ORDER) {
			long amount = takeAmount(amounts, id);
			used.add(GardenData.normalizeCropKey(id));
			rows.add(cropRow(id, amount, upgrades));
		}
		List<Map.Entry<String, Long>> extras = new ArrayList<>(amounts.entrySet());
		extras.sort(Map.Entry.comparingByKey());
		for (Map.Entry<String, Long> e : extras) {
			if (used.contains(GardenData.normalizeCropKey(e.getKey()))) {
				continue;
			}
			rows.add(cropRow(e.getKey(), e.getValue(), upgrades));
		}
		return rows;
	}

	private static long takeAmount(Map<String, Long> amounts, String id) {
		if (amounts.containsKey(id)) {
			return amounts.remove(id);
		}
		for (String alias : List.of(
			id.replace("_ITEM", ""),
			GardenData.normalizeCropKey(id),
			GardenData.cropIconId(id)
		)) {
			if (amounts.containsKey(alias)) {
				return amounts.remove(alias);
			}
		}
		// DOUBLE_PLANT is sunflower in Hypixel resources
		if ("DOUBLE_PLANT".equals(id) && amounts.containsKey("SUNFLOWER")) {
			return amounts.remove("SUNFLOWER");
		}
		return 0L;
	}

	static String gardenHover(GardenData.LevelProgress gl, GardenData.LevelProgress overflow, double xp) {
		if (!gl.maxed()) {
			return "Garden " + gl.level() + " - " + FormatUtil.commas(gl.intoLevel()) + " / " + FormatUtil.commas(gl.needForNext())
				+ " (" + FormatUtil.oneDecimal(gl.fill() * 100) + "%)";
		}
		StringBuilder out = new StringBuilder("Garden " + gl.level() + " - MAX");
		if (overflow != null && gl.intoLevel() > 0L) {
			out.append("\nOverflow Level\t").append(overflow.level());
			out.append("\nNext Level\t").append(FormatUtil.commas(overflow.intoLevel())).append(" / ")
				.append(FormatUtil.commas(overflow.needForNext()))
				.append(" (").append(FormatUtil.oneDecimal(overflow.fill() * 100)).append("%)");
			out.append("\nOverflow XP\t").append(FormatUtil.commas(gl.intoLevel()));
		}
		out.append("\nTotal XP\t").append(FormatUtil.commas(Math.round(xp)));
		return out.toString();
	}

	private static CropRow cropRow(String id, long amount, JsonObject upgrades) {
		GardenData.LevelProgress ms = GardenData.cropMilestone(id, amount);
		int upgrade = upgradeOf(upgrades, id);
		String hover = ms.maxed()
			? "Overflow: " + FormatUtil.commas(ms.intoLevel())
			: FormatUtil.commas(ms.intoLevel()) + " / " + FormatUtil.commas(ms.needForNext());
		return new CropRow(
			id, GardenData.prettyCrop(id), GardenData.cropIconId(id), amount,
			ms.level(), ms.fill(), ms.maxed(), upgrade, hover
		);
	}

	private static int upgradeOf(JsonObject upgrades, String id) {
		int upgrade = intOf(upgrades, id);
		if (upgrade > 0) {
			return upgrade;
		}
		for (String alias : List.of(
			GardenData.normalizeCropKey(id),
			id.replace("_ITEM", ""),
			"COCOA_BEANS", "COCOA", "SUNFLOWER", "MUSHROOM", "NETHER_WART"
		)) {
			upgrade = Math.max(upgrade, intOf(upgrades, alias));
		}
		return upgrade;
	}

	private static List<VisitorRow> parseVisitors(JsonObject commission) {
		JsonObject visits = commission == null ? null : Leveling.obj(commission.get("visits"));
		JsonObject completed = commission == null ? null : Leveling.obj(commission.get("completed"));
		Map<String, long[]> stats = new LinkedHashMap<>();
		for (String id : GardenData.allVisitorIds()) {
			stats.put(id.toLowerCase(Locale.ROOT), new long[] {0L, 0L});
		}
		if (visits != null) {
			for (Map.Entry<String, JsonElement> e : visits.entrySet()) {
				String id = e.getKey().toLowerCase(Locale.ROOT);
				long v = Leveling.longAmount(e.getValue());
				stats.computeIfAbsent(id, k -> new long[] {0L, 0L})[0] = v;
			}
		}
		if (completed != null) {
			for (Map.Entry<String, JsonElement> e : completed.entrySet()) {
				String id = e.getKey().toLowerCase(Locale.ROOT);
				long c = Leveling.longAmount(e.getValue());
				stats.computeIfAbsent(id, k -> new long[] {0L, 0L})[1] = c;
			}
		}
		List<VisitorRow> rows = new ArrayList<>();
		for (Map.Entry<String, long[]> e : stats.entrySet()) {
			long v = e.getValue()[0];
			long c = e.getValue()[1];
			rows.add(new VisitorRow(
				e.getKey(),
				GardenData.prettyVisitor(e.getKey()),
				GardenData.visitorNpcId(e.getKey()),
				v, c, Math.max(0L, v - c)
			));
		}
		rows.sort(Comparator
			.comparingLong(VisitorRow::visits).reversed()
			.thenComparing(VisitorRow::name));
		return rows;
	}

	private static List<ActiveVisitor> parseActive(JsonElement element) {
		List<ActiveVisitor> out = new ArrayList<>();
		if (element == null || element.isJsonNull()) {
			return out;
		}
		if (element.isJsonObject()) {
			JsonObject obj = element.getAsJsonObject();
			boolean map = false;
			for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
				if (e.getValue().isJsonObject()) {
					map = true;
					out.add(activeFrom(e.getKey(), e.getValue().getAsJsonObject()));
				}
			}
			if (!map && (obj.has("visitor") || obj.has("npc"))) {
				String id = stringOf(obj, "visitor");
				if (id.isBlank()) {
					id = stringOf(obj, "npc");
				}
				out.add(activeFrom(id, obj));
			}
		} else if (element.isJsonArray()) {
			for (JsonElement el : element.getAsJsonArray()) {
				if (!el.isJsonObject()) {
					continue;
				}
				JsonObject v = el.getAsJsonObject();
				String id = stringOf(v, "visitor");
				if (id.isBlank()) {
					id = stringOf(v, "npc");
				}
				out.add(activeFrom(id, v));
			}
		}
		return out;
	}

	private static ActiveVisitor activeFrom(String id, JsonObject v) {
		String status = stringOf(v, "status");
		if (status.isBlank()) {
			status = stringOf(v, "state");
		}
		String detail = "";
		JsonObject req = Leveling.obj(v.get("requirements"));
		if (req != null && !req.entrySet().isEmpty()) {
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<String, JsonElement> e : req.entrySet()) {
				if (sb.length() > 0) {
					sb.append(", ");
				}
				Float n = Leveling.num(e.getValue());
				sb.append(GardenData.prettyCrop(e.getKey()));
				if (n != null) {
					sb.append(' ').append(FormatUtil.shortXp(n));
				}
			}
			detail = sb.toString();
		}
		return new ActiveVisitor(id, GardenData.prettyVisitor(id), status, detail);
	}

	private static Composter emptyComposter() {
		return new Composter(0, 0, 0, 0, List.of());
	}

	private static Composter parseComposter(JsonObject data) {
		if (data == null) {
			return emptyComposter();
		}
		JsonObject upgrades = Leveling.obj(data.get("upgrades"));
		List<ComposterUpgrade> rows = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (String key : COMPOSTER_UPGRADE_ORDER) {
			int max = upgradeMax(key);
			int level = clampUpgrade(intOf(upgrades, key), max);
			rows.add(new ComposterUpgrade(
				key, GardenData.prettyComposterUpgrade(key), GardenData.composterUpgradeIconId(key),
				level, max
			));
			seen.add(key);
		}
		if (upgrades != null) {
			for (Map.Entry<String, JsonElement> e : upgrades.entrySet()) {
				String key = e.getKey().toLowerCase(Locale.ROOT);
				if (seen.contains(key)) {
					continue;
				}
				Float n = Leveling.num(e.getValue());
				int max = upgradeMax(key);
				int level = clampUpgrade(n == null ? 0 : Math.round(n), max);
				rows.add(new ComposterUpgrade(
				key, GardenData.prettyComposterUpgrade(key), GardenData.composterUpgradeIconId(key),
				level, max
			));
			}
		}
		return new Composter(
			longOf(data, "organic_matter"),
			longOf(data, "fuel_units"),
			longOf(data, "compost_unit") + longOf(data, "compost_units"),
			longOf(data, "compost_item") + longOf(data, "compost_items"),
			List.copyOf(rows)
		);
	}

	private static int upgradeMax(String key) {
		if (key == null || key.isBlank()) {
			return COMPOSTER_ATTRIBUTE_MAX;
		}
		return switch (key.toLowerCase(Locale.ROOT)) {
			case "speed", "multi_drop", "fuel_cap", "organic_matter_cap", "cost_reduction" -> COMPOSTER_UPGRADE_MAX;
			default -> COMPOSTER_ATTRIBUTE_MAX;
		};
	}

	private static int clampUpgrade(int level, int max) {
		return Math.max(0, Math.min(max, level));
	}

	private static MedalCounts parseMedals(JsonObject jacobs) {
		JsonObject medals = jacobs == null ? null : Leveling.obj(jacobs.get("medals_inv"));
		return new MedalCounts(intOf(medals, "bronze"), intOf(medals, "silver"), intOf(medals, "gold"));
	}

	private static List<BracketCount> parseBrackets(JsonObject jacobs) {
		JsonObject brackets = jacobs == null ? null : Leveling.obj(jacobs.get("unique_brackets"));
		if (brackets == null) {
			return List.of();
		}
		List<BracketCount> out = new ArrayList<>();
		for (String key : List.of("BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND")) {
			// Hypixel stores lowercase keys (bronze/silver/…).
			JsonElement el = brackets.get(key);
			if (el == null) {
				el = brackets.get(key.toLowerCase(Locale.ROOT));
			}
			List<String> cropIds = bracketCropIds(el);
			int count = cropIds.isEmpty() ? bracketCountFallback(el) : cropIds.size();
			if (count > 0) {
				out.add(new BracketCount(title(key), count, cropIds));
			}
		}
		return out;
	}

	private static List<String> bracketCropIds(JsonElement el) {
		if (el == null) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		if (el.isJsonArray()) {
			for (JsonElement item : el.getAsJsonArray()) {
				if (item != null && item.isJsonPrimitive()) {
					String id = item.getAsString();
					if (id != null && !id.isBlank()) {
						out.add(id);
					}
				}
			}
		} else if (el.isJsonObject()) {
			out.addAll(el.getAsJsonObject().keySet());
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	private static int bracketCountFallback(JsonElement el) {
		if (el == null) {
			return 0;
		}
		if (el.isJsonArray()) {
			return el.getAsJsonArray().size();
		}
		if (el.isJsonObject()) {
			return el.getAsJsonObject().size();
		}
		Float n = Leveling.num(el);
		return n == null ? 0 : Math.round(n);
	}

	private static List<PersonalBest> parsePersonalBests(JsonObject jacobs) {
		JsonObject pbs = jacobs == null ? null : Leveling.obj(jacobs.get("personal_bests"));
		if (pbs == null) {
			return List.of();
		}
		List<PersonalBest> out = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : pbs.entrySet()) {
			long amount = Leveling.longAmount(e.getValue());
			if (amount <= 0L) {
				continue;
			}
			out.add(new PersonalBest(e.getKey(), GardenData.prettyCrop(e.getKey()), amount));
		}
		out.sort(Comparator.comparingLong(PersonalBest::amount).reversed());
		return out;
	}

	private static Map<String, Integer> parsePerks(JsonObject jacobs) {
		JsonObject perks = jacobs == null ? null : Leveling.obj(jacobs.get("perks"));
		if (perks == null) {
			return Map.of();
		}
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> e : perks.entrySet()) {
			Float n = Leveling.num(e.getValue());
			out.put(e.getKey(), n == null ? 0 : Math.round(n));
		}
		return out;
	}

	private static List<ChipEntry> parseChips(JsonObject chips) {
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (String id : GardenData.CHIP_ORDER) {
			levels.put(id, 0);
		}
		if (chips != null) {
			for (Map.Entry<String, JsonElement> e : chips.entrySet()) {
				String key = GardenData.normalizeChipKey(e.getKey());
				if (key.isBlank()) {
					continue;
				}
				Float n = Leveling.num(e.getValue());
				if (n == null && e.getValue() != null && e.getValue().isJsonObject()) {
					n = Leveling.num(e.getValue().getAsJsonObject().get("level"));
				}
				levels.put(key, n == null ? 0 : Math.round(n));
			}
		}
		List<ChipEntry> out = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (String id : GardenData.CHIP_ORDER) {
			seen.add(id);
			out.add(new ChipEntry(
				id, GardenData.prettyChip(id), GardenData.chipIconId(id), levels.getOrDefault(id, 0)
			));
		}
		List<String> extras = new ArrayList<>();
		for (String id : levels.keySet()) {
			if (!seen.contains(id)) {
				extras.add(id);
			}
		}
		extras.sort(String::compareTo);
		for (String id : extras) {
			out.add(new ChipEntry(
				id, GardenData.prettyChip(id), GardenData.chipIconId(id), levels.getOrDefault(id, 0)
			));
		}
		return out;
	}

	private static GreenhouseMeta parseGreenhouseMeta(JsonObject garden) {
		int slots = 0;
		JsonElement slotsEl = garden.get("greenhouse_slots");
		if (slotsEl != null && slotsEl.isJsonArray()) {
			slots = slotsEl.getAsJsonArray().size();
		}
		JsonObject upgrades = Leveling.obj(garden.get("garden_upgrades"));
		int yield = intOf(upgrades, "greenhouse_yield");
		if (yield == 0) {
			yield = intOf(upgrades, "GREENHOUSE_YIELD");
		}
		int plotLimit = intOf(upgrades, "greenhouse_plot_limit");
		if (plotLimit == 0) {
			plotLimit = intOf(upgrades, "GREENHOUSE_PLOT_LIMIT");
		}
		int growth = intOf(upgrades, "greenhouse_growth_speed");
		if (growth == 0) {
			growth = intOf(upgrades, "GREENHOUSE_GROWTH_SPEED");
		}
		long last = longOf(garden, "last_growth_stage_time");
		return new GreenhouseMeta(slots, yield, plotLimit, growth, last);
	}

	private static List<ContestEntry> parseHypixelContests(JsonObject jacobs) {
		JsonObject contests = jacobs == null ? null : Leveling.obj(jacobs.get("contests"));
		if (contests == null) {
			return List.of();
		}
		List<ContestEntry> out = new ArrayList<>();
		for (Map.Entry<String, JsonElement> e : contests.entrySet()) {
			if (!e.getValue().isJsonObject()) {
				continue;
			}
			JsonObject c = e.getValue().getAsJsonObject();
			String crop = cropFromContestKey(e.getKey());
			String medal = stringOf(c, "claimed_medal");
			if (medal.isBlank()) {
				medal = "none";
			}
			out.add(contestEntry(crop, longOf(c, "collected"), medal.toLowerCase(Locale.ROOT),
				positionOf(c, "claimed_position"), intOf(c, "claimed_participants"), 0L));
		}
		return out;
	}

	private static List<ContestEntry> parseEliteContests(JsonArray array) {
		if (array == null) {
			return List.of();
		}
		List<ContestEntry> out = new ArrayList<>();
		for (JsonElement el : array) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject c = el.getAsJsonObject();
			String crop = eliteCrop(c.get("crop"));
			String medal = eliteMedal(c.get("medal"));
			out.add(contestEntry(
				crop,
				longOf(c, "collected"),
				medal,
				positionOf(c, "position"),
				intOf(c, "participants"),
				longOf(c, "timestamp")
			));
		}
		out.sort(Comparator.comparingLong(ContestEntry::timestampSeconds).reversed());
		return out;
	}

	private static ContestEntry contestEntry(
		String crop, long collected, String medal, int position, int participants, long timestamp
	) {
		String cropId = crop == null ? "" : crop;
		return new ContestEntry(
			cropId,
			GardenData.prettyCrop(cropId),
			GardenData.cropIconId(cropId),
			collected,
			medal == null || medal.isBlank() ? "none" : medal.toLowerCase(Locale.ROOT),
			position,
			participants,
			timestamp
		);
	}

	private static String eliteCrop(JsonElement el) {
		if (el == null || el.isJsonNull()) {
			return "";
		}
		if (el.isJsonPrimitive()) {
			if (el.getAsJsonPrimitive().isNumber()) {
				return GardenData.eliteCropId(el.getAsInt());
			}
			return GardenData.eliteCropId(el.getAsString());
		}
		return "";
	}

	private static String eliteMedal(JsonElement el) {
		if (el == null || el.isJsonNull()) {
			return "none";
		}
		if (el.isJsonPrimitive()) {
			if (el.getAsJsonPrimitive().isNumber()) {
				return GardenData.eliteMedalName(el.getAsInt());
			}
			String raw = el.getAsString();
			if (raw == null || raw.isBlank()) {
				return "none";
			}
			try {
				return GardenData.eliteMedalName(Integer.parseInt(raw.trim()));
			} catch (NumberFormatException ignored) {
				return raw.toLowerCase(Locale.ROOT);
			}
		}
		return "none";
	}

	static String cropFromContestKey(String key) {
		if (key == null || key.isBlank()) {
			return "";
		}
		int first = key.indexOf(':');
		if (first < 0) {
			return key;
		}
		int second = key.indexOf(':', first + 1);
		return second >= 0 ? key.substring(second + 1) : key.substring(first + 1);
	}

	static List<GreenhouseRow> buildGreenhouse(List<String> discovered, List<String> analyzed) {
		Set<String> analyzedSet = new LinkedHashSet<>();
		for (String id : analyzed) {
			if (id != null && !id.isBlank()) {
				analyzedSet.add(id.toUpperCase(Locale.ROOT));
			}
		}
		Set<String> ids = new LinkedHashSet<>();
		for (String id : discovered) {
			if (id != null && !id.isBlank()) {
				ids.add(id.toUpperCase(Locale.ROOT));
			}
		}
		ids.addAll(analyzedSet);
		// Dead Plant is what a failed mutation leaves behind; the API lists it as discovered but it can't be analyzed.
		ids.remove("DEAD_PLANT");
		List<GreenhouseRow> rows = new ArrayList<>();
		for (String id : ids) {
			rows.add(new GreenhouseRow(
				id,
				GardenData.prettyGreenhouse(id),
				GardenData.greenhouseIconId(id),
				analyzedSet.contains(id)
			));
		}
		rows.sort(Comparator
			.comparing(GreenhouseRow::analyzed).reversed()
			.thenComparing(GreenhouseRow::name, String.CASE_INSENSITIVE_ORDER));
		return rows;
	}

	/** Raw API ids - do not pretty-print (greenhouse crops are not visitors). */
	private static List<String> rawStringList(JsonElement el) {
		if (el == null || !el.isJsonArray()) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (JsonElement item : el.getAsJsonArray()) {
			if (item.isJsonPrimitive()) {
				String raw = item.getAsString();
				if (raw != null && !raw.isBlank()) {
					out.add(raw);
				}
			}
		}
		return out;
	}

	private static long longOf(JsonObject obj, String key) {
		if (obj == null || !obj.has(key)) {
			return 0L;
		}
		return Leveling.longAmount(obj.get(key));
	}

	private static double doubleOf(JsonObject obj, String key) {
		if (obj == null || !obj.has(key)) {
			return 0;
		}
		Double n = Leveling.numDouble(obj.get(key));
		return n == null ? 0 : n;
	}

	private static int intOf(JsonObject obj, String key) {
		return (int) longOf(obj, key);
	}

	// Elite sends position -1 for unclaimed contests; longOf clamps negatives to 0, which would read as first place.
	static int positionOf(JsonObject obj, String key) {
		Double n = obj == null || !obj.has(key) ? null : Leveling.numDouble(obj.get(key));
		if (n == null || n.isNaN() || n.isInfinite() || n < 0) {
			return -1;
		}
		return (int) Math.round(n);
	}

	private static String stringOf(JsonObject obj, String key) {
		if (obj == null || !obj.has(key) || !obj.get(key).isJsonPrimitive()) {
			return "";
		}
		try {
			return obj.get(key).getAsString();
		} catch (IllegalStateException | ClassCastException | NumberFormatException | UnsupportedOperationException ignored) {
			return "";
		}
	}

	private static String title(String raw) {
		if (raw == null || raw.isBlank()) {
			return "?";
		}
		return raw.charAt(0) + raw.substring(1).toLowerCase(Locale.ROOT);
	}

	public boolean islandLoaded() { return islandLoaded; }
	public boolean islandLoading() { return islandLoading; }
	public String islandError() { return islandError; }
	public boolean contestsLoaded() { return contestsLoaded; }
	public boolean contestsLoading() { return contestsLoading; }
	public String contestsError() { return contestsError; }
	public int gardenLevel() { return gardenLevel; }
	public int gardenOverflowLevel() {
		GardenData.LevelProgress overflow = GardenData.gardenOverflow(gardenXp);
		return overflow == null ? gardenLevel : overflow.level();
	}
	public float gardenFill() { return gardenFill; }
	public boolean gardenMaxed() { return gardenMaxed; }
	public String gardenHover() { return gardenHover; }
	public int farmingLevel() { return farmingLevel; }
	public float farmingFill() { return farmingFill; }
	public boolean farmingMaxed() { return farmingMaxed; }
	public String farmingHover() { return farmingHover; }
	public long copper() { return copper; }
	public long larvaConsumed() { return larvaConsumed; }
	public int plotsUnlocked() { return unlockedPlotIds.size(); }
	public List<String> unlockedPlotIds() { return unlockedPlotIds; }

	/** Static plot layout with this profile's unlock state; locked plots carry their tier's next price. */
	public List<PlotCell> plotCells() {
		Set<String> unlocked = new LinkedHashSet<>();
		for (String id : unlockedPlotIds) {
			unlocked.add(id.toLowerCase(Locale.ROOT));
		}
		Map<String, Integer> unlockedPerTier = new LinkedHashMap<>();
		for (GardenCosts.Plot plot : GardenCosts.plots()) {
			if (unlocked.contains(plot.id())) {
				unlockedPerTier.merge(plot.tier(), 1, Integer::sum);
			}
		}
		List<PlotCell> cells = new ArrayList<>();
		for (GardenCosts.Plot plot : GardenCosts.plots()) {
			boolean open = unlocked.contains(plot.id());
			GardenCosts.Cost cost = open ? null : GardenCosts.nextPlotCost(plot.tier(), unlockedPerTier.getOrDefault(plot.tier(), 0));
			cells.add(new PlotCell(plot, open, cost));
		}
		return cells;
	}

	public GardenCosts.ComposterRemaining composterCostToMax() {
		Map<String, Integer> levels = new LinkedHashMap<>();
		for (ComposterUpgrade upgrade : composter.upgrades()) {
			levels.put(upgrade.id(), upgrade.level());
		}
		return GardenCosts.composterRemaining(levels);
	}
	public int plotsMax() { return plotsMax; }
	public long visitorsCompleted() { return visitorsCompleted; }
	public long uniqueVisitors() { return uniqueVisitors; }
	public long totalVisits() { return totalVisits; }
	public long totalRejected() { return totalRejected; }
	public int visitorMilestone() { return visitorMilestone; }
	public float visitorMilestoneFill() { return visitorMilestoneFill; }
	public boolean visitorMilestoneMaxed() { return visitorMilestoneMaxed; }
	public String visitorMilestoneHover() { return visitorMilestoneHover; }
	public int uniqueVisitorMilestone() { return uniqueVisitorMilestone; }
	public float uniqueVisitorMilestoneFill() { return uniqueVisitorMilestoneFill; }
	public boolean uniqueVisitorMilestoneMaxed() { return uniqueVisitorMilestoneMaxed; }
	public String uniqueVisitorMilestoneHover() { return uniqueVisitorMilestoneHover; }
	public List<CropRow> crops() { return crops; }
	public List<VisitorRow> visitors() { return visitors; }
	public List<ActiveVisitor> activeVisitors() { return activeVisitors; }
	public Composter composter() { return composter; }
	public MedalCounts medals() { return medals; }
	public List<BracketCount> uniqueBrackets() { return uniqueBrackets; }

	/** Crop ids that have earned a unique GOLD Jacob bracket. */
	public List<String> uniqueGoldCrops() {
		for (BracketCount b : uniqueBrackets) {
			if (b != null && "Gold".equalsIgnoreCase(b.bracket()) && !b.cropIds().isEmpty()) {
				return b.cropIds();
			}
		}
		return List.of();
	}

	/**
	 * Per-crop highest unique medal as filled orb count (bronze→silver→gold→platinum→diamond).
	 * Prefers Hypixel {@code unique_brackets} crop lists; merges Elite/Hypixel contest medals
	 * when bracket crop ids are missing so the Jacob crop-medal row is not stuck on "None yet".
	 */
	public List<CropMedal> cropMedals() {
		Map<String, Integer> filledByCrop = new LinkedHashMap<>();
		for (BracketCount b : uniqueBrackets) {
			if (b == null) {
				continue;
			}
			int rank = bracketRank(b.bracket());
			if (rank <= 0) {
				continue;
			}
			for (String cropId : b.cropIds()) {
				if (cropId == null || cropId.isBlank()) {
					continue;
				}
				String key = GardenData.normalizeCropKey(cropId);
				filledByCrop.merge(key.isBlank() ? cropId : key, rank, Math::max);
			}
		}
		// Always allow contest medals to fill gaps (Elite async / count-only unique_brackets).
		for (ContestEntry contest : contests) {
			if (contest == null) {
				continue;
			}
			int rank = bracketRank(contest.medal());
			if (rank <= 0) {
				continue;
			}
			String cropId = contest.crop();
			if (cropId == null || cropId.isBlank()) {
				continue;
			}
			String key = GardenData.normalizeCropKey(cropId);
			filledByCrop.merge(key.isBlank() ? cropId : key, rank, Math::max);
		}
		List<CropMedal> out = new ArrayList<>(filledByCrop.size());
		for (Map.Entry<String, Integer> e : filledByCrop.entrySet()) {
			String id = e.getKey();
			out.add(new CropMedal(id, GardenData.prettyCrop(id), GardenData.cropIconId(id), e.getValue()));
		}
		out.sort(Comparator.comparing(CropMedal::name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private static int bracketRank(String bracket) {
		if (bracket == null || bracket.isBlank()) {
			return 0;
		}
		return switch (bracket.trim().toLowerCase(Locale.ROOT)) {
			case "bronze" -> 1;
			case "silver" -> 2;
			case "gold" -> 3;
			case "platinum" -> 4;
			case "diamond" -> 5;
			default -> 0;
		};
	}

	public boolean hasUniqueGold(String cropId) {
		if (cropId == null || cropId.isBlank()) {
			return false;
		}
		String key = GardenData.normalizeCropKey(cropId);
		for (String id : uniqueGoldCrops()) {
			if (key.equals(GardenData.normalizeCropKey(id))) {
				return true;
			}
		}
		return false;
	}

	public List<PersonalBest> personalBests() { return personalBests; }
	public Map<String, Integer> perks() { return perks; }
	public List<ContestEntry> contests() { return contests; }

	/** Keyed by {@link GardenData#normalizeCropKey}. */
	public Map<String, CropPlacement> placements() {
		if (placements == null) {
			placements = placementsByCrop(contests);
		}
		return placements;
	}

	public CropPlacement placement(String cropId) {
		return placements().get(GardenData.normalizeCropKey(cropId));
	}

	static Map<String, CropPlacement> placementsByCrop(List<ContestEntry> contests) {
		Map<String, CropPlacement> out = new LinkedHashMap<>();
		for (ContestEntry c : contests) {
			String key = GardenData.normalizeCropKey(c.crop());
			if (key.isBlank()) {
				continue;
			}
			CropPlacement prev = out.getOrDefault(key, new CropPlacement(0, 0, 0, 0));
			int place = c.placement();
			boolean better = place > 0 && (prev.best() == 0 || place < prev.best());
			out.put(key, new CropPlacement(
				better ? place : prev.best(),
				better ? c.participants() : prev.participants(),
				prev.contests() + 1,
				prev.unplaced() + (place > 0 ? 0 : 1)
			));
		}
		return out;
	}
	public List<ChipEntry> gardenChips() { return gardenChips; }
	public List<GreenhouseRow> greenhouse() { return greenhouse; }
	public GreenhouseMeta greenhouseMeta() { return greenhouseMeta; }
	public FarmingWeightInfo farmingWeight() { return farmingWeight; }
	public boolean farmingToolkitUnlocked() { return farmingToolkitUnlocked; }
	public List<FarmingToolkitSlot> farmingToolkitSlots() { return farmingToolkitSlots; }

	private static List<FarmingToolkitSlot> parseFarmingToolkit(JsonObject toolkit) {
		if (toolkit == null) {
			return List.of();
		}
		List<FarmingToolkitSlot> out = new ArrayList<>();
		for (InventoryDecoder.ToolkitItem item : InventoryDecoder.parseToolkit(toolkit)) {
			InventorySnapshot.Slot slot = item.stack() == null ? null : new InventorySnapshot.Slot(
				item.stack().id(), 1, item.stack().lore(), null, null, null, null, item.stack().extraAttributes(), false
			);
			out.add(new FarmingToolkitSlot(item.category(), GardenData.prettyCrop(item.category()), item.index(), slot));
		}
		return out;
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
